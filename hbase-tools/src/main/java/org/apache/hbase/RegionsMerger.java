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
import static org.apache.hadoop.hbase.TableName.META_TABLE_NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.apache.hadoop.util.ToolRunner;
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
  public static final String MAX_ROUNDS_IDLE = "hbase.tools.max.iterations.blocked";

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
    this.maxRoundsStuck = this.conf.getInt(MAX_ROUNDS_IDLE, 10);
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
    Table metaTbl = connection.getTable(META_TABLE_NAME);
    String tblName = table.getNameAsString();
    RowFilter rowFilter = new RowFilter(CompareOperator.EQUAL,
      new SubstringComparator(tblName+","));
    SingleColumnValueFilter colFilter = new SingleColumnValueFilter(CATALOG_FAMILY,
      STATE_QUALIFIER, CompareOperator.EQUAL, Bytes.toBytes("OPEN"));
    Scan scan = new Scan();
    FilterList filter = new FilterList(FilterList.Operator.MUST_PASS_ALL);
    filter.addFilter(rowFilter);
    filter.addFilter(colFilter);
    scan.setFilter(filter);
    try(ResultScanner rs = metaTbl.getScanner(scan)){
      Result r;
      while ((r = rs.next()) != null) {
        RegionInfo region = RegionInfo.parseFrom(r.getValue(CATALOG_FAMILY, REGIONINFO_QUALIFIER));
        regions.add(region);
      }
    }
    return regions;
  }

  private boolean canMerge(Path path, RegionInfo region1, RegionInfo region2,
      Collection<Pair<RegionInfo, RegionInfo>> alreadyMerging) throws IOException {
    if(alreadyMerging.stream().anyMatch(regionPair ->
        region1.equals(regionPair.getFirst()) ||
        region2.equals(regionPair.getFirst()) ||
        region1.equals(regionPair.getSecond()) ||
        region2.equals(regionPair.getSecond()))){
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
      //need to get all regions for the table, regardless of region state
      List<RegionInfo> regions = admin.getRegions(table);
      Map<Future, Pair<RegionInfo, RegionInfo>> regionsMerging = new ConcurrentHashMap<>();
      long roundsNoProgress = 0;
      while (regions.size() > targetRegions) {
        LOG.info("Iteration: {}", counter);
        RegionInfo previous = null;
        int regionSize = regions.size();
        LOG.info("Attempting to merge {} regions to reach the target {} ...", regionSize, targetRegions);
        //to request merge, regions must be OPEN, though
        regions = getOpenRegions(conn, table);
        for (RegionInfo current : regions) {
          if (!current.isSplit()) {
            if (previous != null && canMerge(tableDir, previous, current, regionsMerging.values())) {
              Future f = admin.mergeRegionsAsync(current.getEncodedNameAsBytes(),
                  previous.getEncodedNameAsBytes(), true);
              Pair<RegionInfo, RegionInfo> regionPair = new Pair<>(previous, current);
              regionsMerging.put(f,regionPair);
              previous = null;
              if ((regionSize - regionsMerging.size()) <= targetRegions) {
                break;
              }
            } else {
              previous = current;
            }
          }
          else{
            LOG.debug("Skipping split region: {}", current.getEncodedName());
          }
        }
        counter.increment();
        LOG.info("Sleeping for {} seconds before next iteration...", (sleepBetweenCycles/1000));
        Thread.sleep(sleepBetweenCycles);
        regionsMerging.forEach((f, currentPair)-> {
          if (f.isDone()) {
            LOG.info("Merged regions {} and {} together.",
              currentPair.getFirst().getEncodedName(),
              currentPair.getSecond().getEncodedName());
            regionsMerging.remove(f);
            lastTimeProgessed.reset();
            lastTimeProgessed.add(counter.longValue());
          } else {
            LOG.warn("Merge of regions {} and {} isn't completed yet.",
              currentPair.getFirst(),
              currentPair.getSecond());
          }
        });
        roundsNoProgress = counter.longValue() - lastTimeProgessed.longValue();
        if(roundsNoProgress == this.maxRoundsStuck){
          LOG.warn("Reached {} iterations without progressing with new merges. Aborting...",
            roundsNoProgress);
          break;
        }

        //again, get all regions, regardless of the state,
        // in order to avoid breaking the loop prematurely
        regions = admin.getRegions(table);
      }
    }
  }

  @Override
  public int run(String[] args) {
    if(args.length!=2){
      LOG.error("Wrong number of arguments. "
        + "Arguments are: <TABLE_NAME> <TARGET_NUMBER_OF_REGIONS>");
      return 1;
    }
    try {
      this.mergeRegions(args[0], Integer.parseInt(args[1]));
    } catch(Exception e){
      LOG.error("Merging regions failed:", e);
      return 2;
    }
    return 0;
  }

  public static void main(String [] args) throws Exception {
    Configuration conf = HBaseConfiguration.create();
    int errCode = ToolRunner.run(new RegionsMerger(conf), args);
    if (errCode != 0) {
      System.exit(errCode);
    }
  }
}
