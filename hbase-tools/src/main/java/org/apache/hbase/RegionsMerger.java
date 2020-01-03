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

import static org.apache.hadoop.hbase.HConstants.CATALOG_FAMILY;
import static org.apache.hadoop.hbase.HConstants.REGIONINFO_QUALIFIER;
import static org.apache.hadoop.hbase.HConstants.STATE_QUALIFIER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.RowFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HBase maintenance tool for merging regions of a specific table, until a target number of regions
 * for the table is reached, or no more merges can complete due to limit in resulting merged
 * region size.
 */
public class RegionsMerger extends Configured implements org.apache.hadoop.util.Tool {

  private static final Logger LOG = LoggerFactory.getLogger(RegionsMerger.class.getName());
  public static final String RESULTING_REGION_UPPER_MARK = "hbase.tools.merge.upper.mark";
  public static final String SLEEP = "hbase.tools.merge.sleep";
  public static final String MAX_ROUNDS_IDDLE = "hbase.tools.max.iterations.blocked";

  private final Configuration conf;
  private final FileSystem fs;
  private final double resultSizeThreshold;
  private final int sleepBetweenCycles;
  private final long maxRoundsStuck;

  public RegionsMerger(Configuration conf) throws IOException {
    this.conf = conf;
    Path basePath = new Path(conf.get(HConstants.HBASE_DIR));
    fs = basePath.getFileSystem(conf);
    resultSizeThreshold = this.conf.getDouble(RESULTING_REGION_UPPER_MARK, 0.9) *
      this.conf.getLong(HConstants.HREGION_MAX_FILESIZE, HConstants.DEFAULT_MAX_FILE_SIZE);
    sleepBetweenCycles = this.conf.getInt(SLEEP, 2000);
    this.maxRoundsStuck = this.conf.getInt(MAX_ROUNDS_IDDLE, 10);
  }

  private Path getTablePath(TableName table){
    Path basePath = new Path(conf.get(HConstants.HBASE_DIR));
    basePath = new Path(basePath, "data");
    Path tablePath = new Path(basePath, table.getNamespaceAsString());
    return new Path(tablePath, table.getNameAsString());
  }

  private long sumSizeInFS(Path parentPath) throws IOException {
    long size = 0;
    FileStatus[] files = this.fs.listStatus(parentPath);
    for(FileStatus f : files) {
      if(f.isFile()) {
        size += f.getLen();
      } else if(f.isDirectory()) {
        size += sumSizeInFS(f.getPath());
      }
    }
    return size;
  }

  private List<RegionInfo> getOpenRegions(Connection connection, TableName table) throws Exception {
    List<RegionInfo> regions = new ArrayList<>();
    Table metaTbl = connection.getTable(TableName.valueOf("hbase:meta"));
    String tblName = table.getNamespaceAsString().equals("default") ? table.getNameAsString() :
      table.getNamespaceAsString() + "." + table.getNameAsString();
    RowFilter rowFilter = new RowFilter(CompareOperator.EQUAL,
      new SubstringComparator(tblName+","));
    SingleColumnValueFilter colFilter = new SingleColumnValueFilter(REGIONINFO_QUALIFIER,
      STATE_QUALIFIER, CompareOperator.EQUAL, Bytes.toBytes("OPEN"));
    Scan scan = new Scan();
    FilterList filter = new FilterList(FilterList.Operator.MUST_PASS_ALL);
    filter.addFilter(rowFilter);
    filter.addFilter(colFilter);
    scan.setFilter(filter);
    ResultScanner rs = metaTbl.getScanner(scan);
    Result r;
    while((r = rs.next())!=null) {
      RegionInfo region =
        RegionInfo.parseFrom(r.getValue(CATALOG_FAMILY, REGIONINFO_QUALIFIER));
      LOG.info("adding region: {} , at state: {}", region,
        Bytes.toString(r.getValue(REGIONINFO_QUALIFIER, STATE_QUALIFIER)));
      regions.add(region);
    }
    rs.close();
    return regions;
  }

  private boolean canMerge(Path path, RegionInfo region1, RegionInfo region2,
      Set<RegionInfo> alreadyMerging) throws IOException {
    if(alreadyMerging.contains(region1) || alreadyMerging.contains(region2)){
      return false;
    }
    if (RegionInfo.areAdjacent(region1, region2)) {
      long size1 = sumSizeInFS(new Path(path, region1.getEncodedName()));
      long size2 = sumSizeInFS(new Path(path, region2.getEncodedName()));
      boolean mergeable = (resultSizeThreshold > (size1 + size2));
      if (!mergeable) {
        LOG.warn("Not merging regions {} and {} because resulting region size would get close to " +
            "the {} limit. {} total size: {}; {} total size:{}", region1.getEncodedName(),
          region2.getEncodedName(), resultSizeThreshold, region1.getEncodedName(), size1,
          region2.getEncodedName(), size2);
      }
      return mergeable;
    } else {
      LOG.warn(
        "WARNING: Can't merge regions {} and {} because those are not adjacent.",
        region1.getEncodedName(),
        region2.getEncodedName());
      return false;
    }
  }

  public void mergeRegions(String tblName, int targetRegions) throws Exception {
    TableName table = TableName.valueOf(tblName);
    Path tableDir = getTablePath(table);
    try(Connection conn = ConnectionFactory.createConnection(conf)) {
      Admin admin = conn.getAdmin();
      LongAdder counter = new LongAdder();
      LongAdder lastTimeProgessed = new LongAdder();
      List<RegionInfo> regions = getOpenRegions(conn, table);
      Map<RegionInfo, Future> regionsMerging = new HashMap<>();
      long roundsNoProgress = 0;
      while (regions.size() > targetRegions && roundsNoProgress < this.maxRoundsStuck) {
        LOG.info("Iteration: {}", counter);
        RegionInfo previous = null;
        LOG.info("Attempting to merge {} regions...", regions.size());
        for (RegionInfo current : regions) {
          if (!current.isSplit()) {
            if (previous == null) {
              previous = current;
            } else {
              if(canMerge(tableDir, previous, current, regionsMerging.keySet())) {
                Future f = admin.mergeRegionsAsync(current.getEncodedNameAsBytes(),
                  previous.getEncodedNameAsBytes(), true);
                regionsMerging.put(previous, f);
                regionsMerging.put(current, f);
                previous = null;
                if((regions.size()-1) <= targetRegions){
                  break;
                }
              } else {
                previous = current;
              }

            }
          }
        }
        counter.increment();
        LOG.info("Sleeping for {} seconds before next iteration...", (sleepBetweenCycles/1000));
        Thread.sleep(sleepBetweenCycles);
        Pair<RegionInfo, RegionInfo> completedPair = new Pair<>();
        regionsMerging.keySet().forEach(r-> {
          Future f = regionsMerging.get(r);
          if(completedPair.getFirst()==null){
            completedPair.setFirst(r);
          } else if(completedPair.getSecond()==null) {
            completedPair.setSecond(r);
          }
          if(completedPair.getFirst()!=null && completedPair.getSecond()!=null) {
            if (f.isDone()) {
              LOG.info("Merged regions {} and {} together.",
                completedPair.getFirst().getEncodedName(),
                completedPair.getSecond().getEncodedName());
              regionsMerging.remove(completedPair.getFirst());
              regionsMerging.remove(completedPair.getSecond());
              lastTimeProgessed.reset();
              lastTimeProgessed.add(counter.longValue());
            } else {
              LOG.warn("Merge of regions {} and {} isn't completed yet.",
                completedPair.getFirst(),
                completedPair.getSecond());
            }
            completedPair.setFirst(null);
            completedPair.setSecond(null);
          }
        });
        regions = getOpenRegions(conn, table);
        roundsNoProgress = counter.longValue() - lastTimeProgessed.longValue();
        if(roundsNoProgress == this.maxRoundsStuck){
          LOG.warn("Reached {} iterations without progressing with new merges. Aborting...",
            roundsNoProgress);
        }
      }
    }
  }

  @Override
  public int run(String[] args) {
    if(args.length!=2){
      LOG.error("Wrong number of arguments. "
        + "Arguments are: <TABLE_NAME> <TARGET_NUMBER_OF_REGIONS>");
      return -1;
    }
    try {
      this.mergeRegions(args[0], Integer.parseInt(args[1]));
    } catch(Exception e){
      LOG.error(e.getMessage());
      return -1;
    }
    return 0;
  }

  public static void main(String [] args) throws Exception {
    Configuration conf = HBaseConfiguration.create();
    int errCode = org.apache.hadoop.util.ToolRunner.run(new RegionsMerger(conf), args);
    if (errCode != 0) {
      System.exit(errCode);
    }
  }
}
