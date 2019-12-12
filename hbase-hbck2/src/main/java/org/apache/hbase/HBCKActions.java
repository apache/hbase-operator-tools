package org.apache.hbase;

import static java.lang.System.exit;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * This class is strictly for usage with hbck2 testing tool located at dev-support/testingtool
 */
public class HBCKActions {
  private Configuration conf;

  public HBCKActions(Configuration conf) {
    this.conf = conf;
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    Configuration conf = HBaseConfiguration.create();
    HBCKActions tool = new HBCKActions(conf);

    if (args.length < 1) {
      printUsageAndExit();
    }
    if (args[0].equals("deleteRegionFromMeta")) {
      if (args.length != 2) {
        System.out.println("ERROR: Number of parameters not correct");
        printUsageAndExit();
      }
      String tableName = args[1];
      tool.deleteRegionFromMeta(tableName);
    }
    else {
      System.out.println("ERROR: Unknown option passed.");
      printUsageAndExit();
    }
  }



  private static void printUsageAndExit() {
    System.out.println("hbckActions <options>");
    System.out.println("OPTIONS:");
    System.out.println("help");
    System.out.println("      Prints this help message.\n");
    System.out.println("deleteRegionFromMeta <tableName>");
    System.out.println("      Deletes the middle region from the regions of the\n"
                     + "      given table from Meta table. Removes whole of the\n"
                     + "      info column family");
    exit(1);
  }

  /**
   * Deletes the middle region from the regions of the given table from Meta table
   * Removes whole of the "info" column family
   */
  private void deleteRegionFromMeta(String tname) throws IOException, InterruptedException {
    TableName tn = TableName.valueOf(tname);
    try (Connection connection = ConnectionFactory.createConnection(conf)) {
      Table MetaTable = connection.getTable(TableName.valueOf("hbase:meta"));
      List<RegionInfo> ris = MetaTableAccessor.getTableRegions(connection, tn);
      System.out.println(String.format("Current Regions of the table " + tn.getNameAsString()
          + " in Meta before deletion of the region are: " + ris));
      RegionInfo ri = ris.get(ris.size() / 2);
      System.out.println("Deleting Region " + ri.getRegionNameAsString());
      byte[] key = MetaTableAccessor.getMetaKeyForRegion(ri);

      Delete delete = new Delete(key);
      delete.addFamily(Bytes.toBytes("info"));
      MetaTable.delete(delete);

      Thread.sleep(500);

      ris = MetaTableAccessor.getTableRegions(connection, tn);
      System.out.println(String.format("Current Regions of the table " + tn.getNameAsString()
          + " in Meta after deletion of the region are: " + ris));
    }
  }
}
