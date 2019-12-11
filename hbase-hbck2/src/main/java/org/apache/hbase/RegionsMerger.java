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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class RegionsMerger extends Configured implements org.apache.hadoop.util.Tool {

  private static final Logger LOG = LoggerFactory.getLogger(RegionsMerger.class.getName());

  private Configuration conf;
  private FileSystem fs;

  public RegionsMerger(Configuration conf) throws IOException {
    this.conf = conf;
    fs = FileSystem.get(this.conf);
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

  private boolean canMerge(Path path, RegionInfo region1, RegionInfo region2) throws IOException {
    long size1 = sumSizeInFS(new Path(path, region1.getEncodedName()));
    long size2 = sumSizeInFS(new Path(path, region2.getEncodedName()));
    long threshold = this.conf.getLong(HConstants.HREGION_MAX_FILESIZE,
      HConstants.DEFAULT_MAX_FILE_SIZE);
    boolean mergeable = ((threshold*0.9) > (size1 + size2));
    if(!mergeable) {
      LOG.info("Not merging regions {} and {} because resulting region size would get close to "
          + "the {} limit. {} total size: {}; {} total size:{}", region1.getEncodedName(),
        region2.getEncodedName(), threshold,
        region1.getEncodedName(), size1,
        region2.getEncodedName(), size2);
    }
    return mergeable;
  }

  public void mergeRegions(String tblName, int numRegions) throws Exception {
    TableName table = TableName.valueOf(tblName);
    Path tableDir = getTablePath(table);
    try(Connection conn = ConnectionFactory.createConnection(conf)) {
      Admin admin = conn.getAdmin();
      int counter = 0;
      List<RegionInfo> regions = admin.getRegions(table);
      while (regions.size() > numRegions) {
        LOG.info("Iteration: {}", counter);
        RegionInfo previous = null;
        Map<Future, Pair<RegionInfo, RegionInfo>> futures = new HashMap<>();
        LOG.info("About to merge {} regions...", regions.size());
        for (RegionInfo current : regions) {
          if (!current.isSplit()) {
            if (previous == null) {
              previous = current;
            } else {
              if (RegionInfo.areAdjacent(previous, current)) {
                  if(canMerge(tableDir, previous, current)) {
                    Future f = admin.mergeRegionsAsync(current.getEncodedNameAsBytes(),
                      previous.getEncodedNameAsBytes(), true);
                    futures.put(f, new Pair<>(previous, current));
                    previous = null;
                  }
              } else {
                LOG.warn(
                  "WARNING: Can't merge regions {} and {} because those are not adjacent.",
                  current.getEncodedName(),
                  previous.getEncodedName());
              }
            }
          }
        }
        counter++;
        LOG.info("Sleeping for 2 seconds before next iteration...");
        Thread.sleep(2000);
        futures.keySet().forEach(f-> {
          Pair<RegionInfo, RegionInfo> mergedRegions = futures.get(f);
          if(f.isDone()){
            LOG.info("Merged regions {} and {} together.",
              mergedRegions.getFirst().getEncodedName(),
              mergedRegions.getSecond().getEncodedName());
          } else {
            LOG.warn("Couldn't merge regions {} and {} together.",
              mergedRegions.getFirst(),
              mergedRegions.getSecond());
          }
        });
        regions = admin.getRegions(table);
      }
    }
  }

  @Override
  public int run(String[] args) throws Exception {
    if(args.length!=2){
      LOG.error("Wrong number of arguments. Arguments are: <TABLE_NAME> <NUMBER_OF_REGIONS>");
      return -1;
    }
    this.mergeRegions(args[0], Integer.parseInt(args[1]));
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
