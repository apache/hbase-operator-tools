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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.regionserver.HRegionFileSystem;
import org.apache.hadoop.hbase.util.CommonFSUtils;
import org.apache.hadoop.hbase.util.FSUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the inner works required for checking and recovering regions that wrongly
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
      ListUtils<Path, RegionInfo> utils = new ListUtils<>();
      return utils.complement(dirs, regions, d -> d.getName(), r -> r.getEncodedName());
    });
  }

  List<RegionInfo> findExtraRegionsInMETA(String table) throws IOException {
    InternalMetaChecker<RegionInfo> extraChecker = new InternalMetaChecker<>();
    return extraChecker.checkRegionsInMETA(table, (regions,dirs) -> {
      ListUtils<RegionInfo, Path> utils = new ListUtils<>();
      return utils.complement(regions, dirs, r -> r.getEncodedName(), d -> d.getName());
    });
  }

  void putRegionInfoFromHdfsInMeta(Path region) throws IOException {
    RegionInfo info = HRegionFileSystem.loadRegionInfoFileContent(fs, region);
    HBCKMetaTableAccessor.addRegionToMeta(conn, info);
  }

  List<String> addMissingRegionsInMeta(List<Path> regionsPath) throws IOException {
    List<String> reAddedRegionsEncodedNames = new ArrayList<>();
    for(Path regionPath : regionsPath){
      this.putRegionInfoFromHdfsInMeta(regionPath);
      reAddedRegionsEncodedNames.add(regionPath.getName());
    }
    return reAddedRegionsEncodedNames;
  }

  public List<Future<List<String>>> addMissingRegionsInMetaForTables(
      List<String> nameSpaceOrTable) throws IOException {
    InternalMetaChecker<Path> missingChecker = new InternalMetaChecker<>();
    return missingChecker.processRegionsMetaCleanup(this::reportTablesMissingRegions,
      this::addMissingRegionsInMeta, nameSpaceOrTable);
  }

  public List<Future<List<String>>> removeExtraRegionsFromMetaForTables(
    List<String> nameSpaceOrTable) throws IOException {
    if(nameSpaceOrTable.size()>0) {
      InternalMetaChecker<RegionInfo> extraChecker = new InternalMetaChecker<>();
      return extraChecker.processRegionsMetaCleanup(this::reportTablesExtraRegions,
        this::deleteAllRegions, nameSpaceOrTable);
    }
    return null;
  }

  private List<String> deleteAllRegions(List<RegionInfo> regions) throws IOException {
    List<String> resulting = new ArrayList<>();
    for(RegionInfo r : regions){
      HBCKMetaTableAccessor.deleteRegionInfo(conn, r);
      resulting.add(r.getEncodedName());
    }
    return resulting;
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
      List<RegionInfo> regions = HBCKMetaTableAccessor.
        getTableRegions(FsRegionsMetaRecoverer.this.conn, tableName);
      return checkingFunction.check(regions, regionsDirs);
    }

    Map<TableName,List<T>> reportTablesRegions(final List<String> namespacesOrTables,
      ExecFunction<List<T>, String> checkingFunction) throws IOException {
      final Map<TableName,List<T>> result = new HashMap<>();
      List<TableName> tableNames = HBCKMetaTableAccessor.
        getTables(FsRegionsMetaRecoverer.this.conn).stream()
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
          LOG.warn("Can't get related regions report from meta", e);
        }
      });
      return result;
    }

    List<Future<List<String>>> processRegionsMetaCleanup(
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
          boolean allDone;
          do {
            allDone = true;
            for (Future<List<String>> f : futures) {
              allDone &= f.isDone();
            }
          } while(!allDone);
        }
      } finally {
        executorService.shutdown();
      }
      return futures;
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

  public class ListUtils<T1, T2> {
    public List<T1> complement(List<T1> list1, List<T2> list2,
        Function<T1, String> convertT1, Function<T2, String> convertT2) {
      final List<T1> extraRegions = new ArrayList<>();
      HashSet<String> baseSet = list2.stream().map(info ->
        convertT2.apply(info)).collect(Collectors.toCollection(HashSet::new));
      list1.forEach(region -> {
        if(!baseSet.contains(convertT1.apply(region))) {
          extraRegions.add(region);
        }
      });
      return extraRegions;
    }
  }

}
