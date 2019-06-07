package org.apache.hbase.hbck2.meta;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.regionserver.HRegionFileSystem;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RegionMetaBuilder implements Closeable {
  private static final String HBASE_DATA_DIR = "/hbase/data/";
  private static final String HBASE_DEFAULT_NAMESPACE = "default/";
  private FileSystem fs;
  private Connection conn;

  public RegionMetaBuilder() throws IOException {
    Configuration config = HBaseConfiguration.create();
    this.fs = FileSystem.get(config);
    this.conn = ConnectionFactory.createConnection(config);
  }

  public List<String> readTablesInputFile(String tablesInputFile) throws Exception{
    final List<String> tablesRootDir = new ArrayList<>();
    try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(tablesInputFile)))){
      String currentTable = null;
      while((currentTable = reader.readLine())!=null){
        //formats table name to "/hbase/data/NAMESPACE/TABLE/"
        currentTable = currentTable.trim().split(" ")[0];
        System.out.println(currentTable);
        if(currentTable.indexOf(":")>0){
          currentTable = HBASE_DATA_DIR + currentTable.trim().replaceAll(":", "/");
        } else {
          currentTable = HBASE_DATA_DIR + HBASE_DEFAULT_NAMESPACE + currentTable.trim();
        };
        System.out.println("adding " + currentTable);
        tablesRootDir.add(currentTable);
      }
    }
    return tablesRootDir;
  }

  public List<Path> findMissingRegionsInMETA(String tableRootDir) throws Exception {
    final List<Path> missingRegions = new ArrayList<>();
    final FileStatus[] regionsDirs = fs.listStatus(new Path(tableRootDir));
    TableName tableName = tableRootDir.indexOf(HBASE_DEFAULT_NAMESPACE)>0 ?
      TableName.valueOf(tableRootDir.substring(tableRootDir.lastIndexOf("/")+1)) :
      TableName.valueOf(tableRootDir.substring(tableRootDir.lastIndexOf(HBASE_DATA_DIR) + HBASE_DATA_DIR.length())
        .replaceAll("/",":"));
    List<RegionInfo> regionInfos = MetaTableAccessor.
      getTableRegions(this.conn, tableName, true);
    for(final FileStatus regionDir : regionsDirs){
      if(!regionDir.getPath().getName().equals(".tabledesc")&&!regionDir.getPath().getName().equals(".tmp")) {
        System.out.println("looking for " + regionDir + " in META.");
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

  public void putRegionInfoFromHdfsInMeta(Path region) throws Exception{
    RegionInfo info = HRegionFileSystem.loadRegionInfoFileContent(fs, region);
    MetaTableAccessor.addRegionToMeta(conn, info);
  }

  public String printHbck2AssignsCommand(List<String> regions) {
    final StringBuilder builder = new StringBuilder();
    builder.append("assigns ");
    regions.forEach(region -> builder.append(region).append(" "));
    return builder.toString();
  }

  @Override public void close() throws IOException {
    this.conn.close();
  }
}
