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

import java.io.IOException;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.yetus.audience.InterfaceAudience;

/**
 * This class is strictly for usage with hbck2 testing tool located at dev-support/testingtool
 */
@InterfaceAudience.Private
public class HBCKActions {
  private Configuration conf;

  public HBCKActions(Configuration conf) {
    this.conf = conf;
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    Configuration conf = HBaseConfiguration.create();
    HBCKActions tool = new HBCKActions(conf);

    if (args.length < 1 || args[0].equals("help")) {
      printUsageAndExit();
    }
    if (args[0].equals("deleteRegionFromMeta")) {
      if (args.length != 2) {
        System.err.println("ERROR: Number of parameters not correct");
        printUsageAndExit();
      }
      String tableName = args[1];
      tool.deleteRegionFromMeta(tableName);
    } else {
      System.err.println("ERROR: Unknown option passed.");
      printUsageAndExit();
    }
  }

  private static void printUsageAndExit() {
    System.err.println("hbckActions <options>");
    System.err.println("OPTIONS:");
    System.err.println("help");
    System.err.println("      Prints this help message.\n");
    System.err.println("deleteRegionFromMeta <tableName>");
    System.err.println("      Deletes the middle region from the regions of the\n"
      + "      given table from Meta table. Removes whole of the\n" + "      info column family");
    System.exit(1);
  }

  /**
   * Deletes the middle region from the regions of the given table from Meta table Removes whole of
   * the "info" column family
   */
  private void deleteRegionFromMeta(String tname) throws IOException, InterruptedException {
    TableName tn = TableName.valueOf(tname);
    try (Connection connection = ConnectionFactory.createConnection(conf)) {
      Table metaTable = connection.getTable(TableName.valueOf("hbase:meta"));
      List<RegionInfo> ris = HBCKMetaTableAccessor.getTableRegions(connection, tn);
      System.out.println(String.format("Current Regions of the table " + tn.getNameAsString()
        + " in Meta before deletion of the region are: " + ris));
      RegionInfo ri = ris.get(ris.size() / 2);
      System.out.println("Deleting Region " + ri);
      byte[] key = HBCKMetaTableAccessor.getMetaKeyForRegion(ri);

      Delete delete = new Delete(key);
      delete.addFamily(Bytes.toBytes("info"));
      metaTable.delete(delete);

      Thread.sleep(500);

      ris = HBCKMetaTableAccessor.getTableRegions(connection, tn);
      System.out.println("Current Regions of the table " + tn.getNameAsString()
        + " in Meta after deletion of the region are: " + ris);
    }
  }
}
