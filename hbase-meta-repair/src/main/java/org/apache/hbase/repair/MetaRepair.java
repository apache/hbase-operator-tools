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
package org.apache.hbase.repair;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.regionserver.HRegionFileSystem;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repair HBase MetaData for [Apache HBase&trade;](https://hbase.apache.org)
 * versions before 2.0.3 and 2.1.1 (HBase versions without HBCK2).
 */

public class MetaRepair extends Configured implements org.apache.hadoop.util.Tool {

  private static final Logger LOG = LoggerFactory.getLogger(MetaRepair.class.getName());

  public static final String HBASE_META_TABLE = "hbase:meta";
  public static final String HBASE_META_FAMILY = "info";
  public static final String HBASE_META_QUALIFIER_SN = "sn";
  public static final String HBASE_META_QUALIFIER_SERVER = "server";
  public static final String HBASE_META_QUALIFIER_STATE = "state";

  private final Configuration conf;

  public MetaRepair(Configuration conf) {
    this.conf = conf;
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = HBaseConfiguration.create();
    int errCode = ToolRunner.run(new MetaRepair(conf), args);
    if (errCode != 0) {
      System.exit(errCode);
    }
  }

  private Path getTablePath(TableName table) {
    Path basePath = new Path(conf.get(HConstants.HBASE_DIR));
    basePath = new Path(basePath, "data");
    Path tablePath = new Path(basePath, table.getNamespaceAsString());
    return new Path(tablePath, table.getQualifierAsString());
  }

  /**
   * Get HBase region info from the table name.
   */
  public Map<String, byte[]> getMetaRegions(String tableName) throws IOException {
    Connection conn = ConnectionFactory.createConnection(conf);
    Table table = conn.getTable(TableName.valueOf(HBASE_META_TABLE));
    Scan scan = new Scan().setFilter(new PrefixFilter(Bytes.toBytes(tableName + ",")));
    Map<String, byte[]> metaRegions = new HashMap<>();
    Iterator<Result> iterator = table.getScanner(scan).iterator();
    while (iterator.hasNext()) {
      Result result = iterator.next();
      // Usage Bytes.toStringBinary(), Be consistent with HDFS RegionInfo encoding.
      metaRegions.put(Bytes.toStringBinary(result.getRow()), result.getRow());
    }
    conn.close();
    return metaRegions;
  }

  /**
   * Get HDFS region info from the table name.
   */
  public Map<String, RegionInfo> getHdfsRegions(String tableName) throws IOException {
    FileSystem fs = FileSystem.get(conf);
    TableName table = TableName.valueOf(tableName);
    Path path = this.getTablePath(table);
    Map<String, RegionInfo> hdfsRegions = new HashMap<>();
    FileStatus[] list = fs.listStatus(path);
    for (FileStatus status : list) {
      if (status.isDirectory()) {
        boolean isRegion = false;
        FileStatus[] regions = fs.listStatus(status.getPath());
        for (FileStatus regionStatus : regions) {
          // Search the .regioninfo file.
          if (regionStatus.toString().contains(HRegionFileSystem.REGION_INFO_FILE)) {
            isRegion = true;
            break;
          }
        }
        if (isRegion) {
          // Load regioninfo file content.
          RegionInfo regionInfo = HRegionFileSystem.loadRegionInfoFileContent(fs, status.getPath());
          hdfsRegions.put(regionInfo.getRegionNameAsString(), regionInfo);
        }
      }
    }
    return hdfsRegions;
  }

  public void repairMetadata(String tableName) throws Exception {
    Map<String, byte[]> metaRegions = getMetaRegions(tableName);
    LOG.debug("HBase meta regions: {}", metaRegions.keySet());
    Map<String, RegionInfo> hdfsRegions = getHdfsRegions(tableName);
    LOG.debug("HDFS region infos: {}", hdfsRegions.keySet());
    Set<String> hdfsRegionNames = hdfsRegions.keySet();
    for (String hdfsRegionName : hdfsRegionNames) {
      if (metaRegions.containsKey(hdfsRegionName)) {
        // remove meta not in hdfs region info.
        metaRegions.remove(hdfsRegionName);
      }
    }
    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();
    ServerName[] regionServers = admin.getRegionServers().toArray(new ServerName[0]);
    LOG.info("HBase Region Servers: {}", Arrays.asList(regionServers));
    Table table = conn.getTable(TableName.valueOf(HBASE_META_TABLE));
    if (!metaRegions.isEmpty()) {
      for (String regionName : metaRegions.keySet()) {
        table.delete(new Delete(metaRegions.get(regionName)));
      }
      LOG.warn("Delete HBase Metadata: {}", metaRegions.keySet());
    }
    int rsLength = regionServers.length, i = 0;
    for (String regionName : hdfsRegionNames) {
      String sn = regionServers[i % rsLength].getServerName();
      String[] snSig = sn.split(",");
      RegionInfo regionInfo = hdfsRegions.get(regionName);
      Put info = MetaTableAccessor.makePutFromRegionInfo(regionInfo,
          EnvironmentEdgeManager.currentTime());
      info.addColumn(Bytes.toBytes(HBASE_META_FAMILY),
          Bytes.toBytes(HBASE_META_QUALIFIER_SN), Bytes.toBytes(sn));
      info.addColumn(Bytes.toBytes(HBASE_META_FAMILY),
          Bytes.toBytes(HBASE_META_QUALIFIER_SERVER), Bytes.toBytes(snSig[0] + ":" + snSig[1]));
      info.addColumn(Bytes.toBytes(HBASE_META_FAMILY),
          Bytes.toBytes(HBASE_META_QUALIFIER_STATE), Bytes.toBytes("OPEN"));
      table.put(info);
      i++;
    }
    LOG.info("Repair HBase Metadata: {}", hdfsRegionNames);
    conn.close();
  }

  @Override
  public int run(String[] args) {
    if (args.length != 1) {
      LOG.error("Wrong number of arguments. "
              + "Arguments are: <TABLE_NAME> ");
      return 1;
    }
    try {
      this.repairMetadata(args[0]);
    } catch (Exception e) {
      LOG.error("Repairing metadata failed:", e);
      return 2;
    }
    return 0;
  }
}
