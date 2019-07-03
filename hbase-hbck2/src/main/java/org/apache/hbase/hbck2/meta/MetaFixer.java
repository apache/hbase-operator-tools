/**
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
package org.apache.hbase.hbck2.meta;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.regionserver.HRegionFileSystem;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class MetaFixer implements Closeable {
  private static final String HBASE_DATA_DIR = "/data/";
  private static final String HBASE_DEFAULT_NAMESPACE = "default/";
  private static final String TABLE_DESC_FILE = ".tabledesc";
  private static final String TEMP_DIR = ".tmp";
  private FileSystem fs;
  private Connection conn;
  private Configuration config;

  public MetaFixer(Configuration configuration) throws IOException {
    this.config = configuration;
    this.fs = FileSystem.get(configuration);
    this.conn = ConnectionFactory.createConnection(configuration);
  }

  /*Initially defined for test only purposes */
  MetaFixer(Configuration configuration, Connection connection, FileSystem fileSystem){
    this.config = configuration;
    this.conn = connection;
    this.fs = fileSystem;
  }

  private FileStatus[] getTableRegionsDirs(String table) throws Exception {
    String hbaseRoot = this.config.get("hbase.rootdir");
    String tableRootDir = hbaseRoot + (table.indexOf(":") < 0 ?
      HBASE_DATA_DIR + HBASE_DEFAULT_NAMESPACE + table.trim() :
        HBASE_DATA_DIR + table.trim().replaceAll(":", "/"));
    return fs.listStatus(new Path(tableRootDir));
  }

  public Map<TableName,List<Path>> reportTablesMissingRegions(final List<String> namespacesOrTables)
      throws IOException {
    final Map<TableName,List<Path>> result = new HashMap<>();
    List<TableName> tableNames = MetaTableAccessor.getTableStates(this.conn).keySet().stream()
      .filter(tableName -> {
        if(namespacesOrTables==null || namespacesOrTables.size()==0){
          return true;
        } else {
          Optional<String> findings = namespacesOrTables.stream().filter(
            name -> (name.indexOf(":") > 0) ?
              tableName.getNameWithNamespaceInclAsString().equals(name) :
              tableName.getNamespaceAsString().equals(name)).findFirst();
          return findings.isPresent();
        }
      }).collect(Collectors.toList());
    tableNames.stream().forEach(tableName -> {
      try {
        result.put(tableName,
          findMissingRegionsInMETA(tableName.getNameWithNamespaceInclAsString()));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    return result;
  }

  public List<Path> findMissingRegionsInMETA(String table) throws Exception {
    final List<Path> missingRegions = new ArrayList<>();
    final FileStatus[] regionsDirs = getTableRegionsDirs(table);
    TableName tableName = TableName.valueOf(table);
    List<RegionInfo> regionInfos = MetaTableAccessor.
      getTableRegions(this.conn, tableName, false);
    for(final FileStatus regionDir : regionsDirs){
      if(!regionDir.getPath().getName().equals(TABLE_DESC_FILE) &&
          !regionDir.getPath().getName().equals(TEMP_DIR)) {
        boolean foundInMeta = regionInfos.stream()
          .anyMatch(info -> info.getEncodedName().equals(regionDir.getPath().getName()));
        if (!foundInMeta) {
          System.out.println(regionDir + "is not in META.");
          missingRegions.add(regionDir.getPath());
        }
      }
    }
    return missingRegions;
  }

  public void putRegionInfoFromHdfsInMeta(Path region) throws IOException {
    RegionInfo info = HRegionFileSystem.loadRegionInfoFileContent(fs, region);
    MetaTableAccessor.addRegionToMeta(conn, info);
  }

  @Override public void close() throws IOException {
    this.conn.close();
  }
}
