/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hbase;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.regionserver.HRegionFileSystem;
import org.apache.hadoop.hbase.util.CommonFSUtils;
import org.apache.hadoop.hbase.util.FSUtils;

import org.apache.hadoop.hbase.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the inner works required for checking and recover regions that wrongly
 * went missing in META, or are left present in META but with no equivalent FS dir.
 * Normally HBCK2 fix options rely on Master self-contained information to recover/fix
 * inconsistencies, but this an exception case where META table is in a broken state.
 * So, it assumes HDFS state as the source of truth, in other words, methods provided here consider
 * meta information found on HDFS region dirs as the valid ones.
 */
public class FsRegionsMetaRecoverer implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(FsRegionsMetaRecoverer.class);
  private final FileSystem fs;
  private final Connection conn;
  private final Configuration config;

  public FsRegionsMetaRecoverer(Configuration configuration) throws IOException {
    this.config = configuration;
    this.fs = CommonFSUtils.getRootDirFileSystem(configuration);
    this.conn = ConnectionFactory.createConnection(configuration);
  }

  /*Initially defined for test only purposes */
  FsRegionsMetaRecoverer(Configuration configuration, Connection connection, FileSystem fileSystem){
    this.config = configuration;
    this.conn = connection;
    this.fs = fileSystem;
  }

  private List<Path> getTableRegionsDirs(String table) throws IOException {
    String hbaseRoot = this.config.get(HConstants.HBASE_DIR);
    Path tableDir = FSUtils.getTableDir(new Path(hbaseRoot), TableName.valueOf(table));
    return FSUtils.getRegionDirs(fs, tableDir);
  }

  public Map<TableName,List<Path>> reportTablesMissingRegions(final List<String> namespacesOrTables)
      throws IOException {
    InternalMetaChecker<Path> missingChecker = new InternalMetaChecker<>();
    return missingChecker.reportTablesRegions(namespacesOrTables, this::findMissingRegionsInMETA);
  }

  public Map<TableName,List<RegionInfo>>
      reportTablesExtraRegions(final List<String> namespacesOrTables) throws IOException {
    InternalMetaChecker<RegionInfo> extraChecker = new InternalMetaChecker<>();
    return extraChecker.reportTablesRegions(namespacesOrTables, this::findExtraRegionsInMETA);
  }

  List<Path> findMissingRegionsInMETA(String table) throws IOException {
    InternalMetaChecker<Path> missingChecker = new InternalMetaChecker<>();
    return missingChecker.checkRegionsInMETA(table, (regions, dirs) -> {
      final List<Path> missingRegions = new ArrayList<>();
      HashSet<String> regionsInMeta = regions.stream().map(info ->
        info.getEncodedName()).collect(Collectors.toCollection(HashSet::new));
      dirs.forEach(dir -> {
        if(!regionsInMeta.contains(dir.getName())){
          LOG.debug("{} is not in META.", dir);
          missingRegions.add(dir);
        }
      });
      return missingRegions;
    });
  }

  List<RegionInfo> findExtraRegionsInMETA(String table) throws IOException {
    InternalMetaChecker<RegionInfo> extraChecker = new InternalMetaChecker<>();
    return extraChecker.checkRegionsInMETA(table, (regions,dirs) -> {
      final List<RegionInfo> extraRegions = new ArrayList<>();
      HashSet<String> regionsInHDFS = dirs.stream().map(dir -> dir.getName())
        .collect(Collectors.toCollection(HashSet::new));
      regions.forEach(region -> {
        if(!regionsInHDFS.contains(region.getEncodedName())) {
          LOG.debug("Region {} found in META, but not in HDFS.", region.getEncodedName());
          extraRegions.add(region);
        }
      });
      return extraRegions;
    });
  }

  void putRegionInfoFromHdfsInMeta(Path region) throws IOException {
    RegionInfo info = HRegionFileSystem.loadRegionInfoFileContent(fs, region);
    MetaTableAccessor.addRegionToMeta(conn, info);
  }

  List<String> addMissingRegionsInMeta(List<Path> regionsPath) throws IOException {
    List<String> reAddedRegionsEncodedNames = new ArrayList<>();
    for(Path regionPath : regionsPath){
      this.putRegionInfoFromHdfsInMeta(regionPath);
      reAddedRegionsEncodedNames.add(regionPath.getName());
    }
    return reAddedRegionsEncodedNames;
  }

  public Pair<List<String>, List<ExecutionException>> addMissingRegionsInMetaForTables(
      List<String> nameSpaceOrTable) throws IOException {
    InternalMetaChecker<Path> missingChecker = new InternalMetaChecker<>();
    return missingChecker.processRegionsMetaCleanup(this::reportTablesMissingRegions,
      this::addMissingRegionsInMeta, nameSpaceOrTable);
  }

  public Pair<List<String>, List<ExecutionException>> removeExtraRegionsFromMetaForTables(
    List<String> nameSpaceOrTable) throws IOException {
    InternalMetaChecker<RegionInfo> extraChecker = new InternalMetaChecker<>();
    return extraChecker.processRegionsMetaCleanup(this::reportTablesExtraRegions,
      regions -> {
        MetaTableAccessor.deleteRegionInfos(conn, regions);
        return regions.stream().map(r->r.getEncodedName()).collect(Collectors.toList());
      }, nameSpaceOrTable);
  }


  @Override
  public void close() throws IOException {
    this.conn.close();
  }

  private class InternalMetaChecker<T> {

    List<T> checkRegionsInMETA(String table,
        CheckingFunction<List<RegionInfo>, List<Path>, T> checkingFunction) throws IOException {
      final List<Path> regionsDirs = getTableRegionsDirs(table);
      TableName tableName = TableName.valueOf(table);
      List<RegionInfo> regions = MetaTableAccessor.
        getTableRegions(FsRegionsMetaRecoverer.this.conn, tableName, false);
      return checkingFunction.check(regions, regionsDirs);
    }

    Map<TableName,List<T>> reportTablesRegions(final List<String> namespacesOrTables,
      ExecFunction<List<T>, String> checkingFunction) throws IOException {
      final Map<TableName,List<T>> result = new HashMap<>();
      List<TableName> tableNames = MetaTableAccessor.
        getTableStates(FsRegionsMetaRecoverer.this.conn).keySet().stream()
          .filter(tableName -> {
            if(namespacesOrTables==null || namespacesOrTables.isEmpty()){
              return true;
            } else {
              Optional<String> findings = namespacesOrTables.stream().filter(
                name -> (name.indexOf(":") > 0) ?
                  tableName.equals(TableName.valueOf(name)) :
                  tableName.getNamespaceAsString().equals(name)).findFirst();
              return findings.isPresent();
            }
          }).collect(Collectors.toList());
      tableNames.stream().forEach(tableName -> {
        try {
          result.put(tableName,
            checkingFunction.execute(tableName.getNameWithNamespaceInclAsString()));
        } catch (Exception e) {
          LOG.warn("Can't get related regions execute from meta", e);
        }
      });
      return result;
    }

    Pair<List<String>, List<ExecutionException>> processRegionsMetaCleanup(
        ExecFunction<Map<TableName, List<T>>, List<String>> reportFunction,
        ExecFunction<List<String>, List<T>> execFunction,
        List<String> nameSpaceOrTable) throws IOException {
      ExecutorService executorService = Executors.newFixedThreadPool(
        (nameSpaceOrTable == null ||
          nameSpaceOrTable.size() > Runtime.getRuntime().availableProcessors()) ?
          Runtime.getRuntime().availableProcessors() :
          nameSpaceOrTable.size());
      List<Future<List<String>>> futures =
        new ArrayList<>(nameSpaceOrTable == null ? 1 : nameSpaceOrTable.size());
      final List<String> processedRegionNames = new ArrayList<>();
      List<ExecutionException> executionErrors = new ArrayList<>();
      try {
        try(final Admin admin = conn.getAdmin()) {
          Map<TableName,List<T>> report = reportFunction.execute(nameSpaceOrTable);
          if(report.size() < 1) {
            LOG.info("\nNo mismatches found in meta. Worth using related reporting function " +
              "first.\nYou are likely passing non-existent " +
              "namespace or table. Note that table names should include the namespace " +
              "portion even for tables in the default namespace. " +
              "See also the command usage.\n");
          }
          for (TableName tableName : report.keySet()) {
            if(admin.tableExists(tableName)) {
              futures.add(executorService.submit(new Callable<List<String>>() {
                @Override
                public List<String> call() throws Exception {
                  LOG.debug("running thread for {}", tableName.getNameWithNamespaceInclAsString());
                  return execFunction.execute(report.get(tableName));
                }
              }));
            } else {
              LOG.warn("Table {} does not exist! Skipping...",
                tableName.getNameWithNamespaceInclAsString());
            }
          }
          for(Future<List<String>> f : futures){
            try {
              processedRegionNames.addAll(f.get());
            } catch (ExecutionException e){
              //we want to allow potential running threads to finish, so we collect execution
              //errors and show those later
              LOG.debug("Caught execution error: ", e);
              executionErrors.add(e);
            }
          }
        }
      } catch (IOException | InterruptedException e) {
        LOG.error("ERROR executing thread: ", e);
        throw new IOException(e);
      } finally {
        executorService.shutdown();
      }
      Pair<List<String>, List<ExecutionException>> result = new Pair<>();
      result.setFirst(processedRegionNames);
      result.setSecond(executionErrors);
      return result;
    }
  }

  @FunctionalInterface
  interface CheckingFunction <RegionsList, DirList, T> {
    List<T> check(RegionsList regions, DirList dirs) throws IOException;
  }

  @FunctionalInterface
  interface ExecFunction<T, NamespaceOrTable> {
    T execute(NamespaceOrTable name) throws IOException;
  }

  public static void main(String[] args){
    System.out.println("hi");
  }

}
