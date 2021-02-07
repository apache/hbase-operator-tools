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
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.tool.LoadIncrementalHFiles;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MissingRegionDirsRepairTool extends Configured implements org.apache.hadoop.util.Tool {

  private static final Logger LOG =
    LoggerFactory.getLogger(MissingRegionDirsRepairTool.class.getName());

  private static final String WORKING_DIR = ".missing_dirs_repair";

  private Configuration conf;
  private HBCK2 hbck;
  private LoadIncrementalHFiles bulkLoad;

  public MissingRegionDirsRepairTool(Configuration conf) {
    this.conf=conf;
    this.hbck = new HBCK2(conf);
    this.bulkLoad = new LoadIncrementalHFiles(conf);
  }

  @Override
  public int run(String[] strings) throws Exception {
    Map<TableName,List<Path>> result = hbck
      .reportTablesWithMissingRegionsInMeta(new String[]{});
    Path runPath = new Path(new Path(HBCKFsUtils.getRootDir(conf),
      WORKING_DIR), "" + System.currentTimeMillis());
    FileSystem fs = runPath.getFileSystem(conf);
    LOG.info("creating temp dir at: " + runPath.getName());
    fs.mkdirs(runPath);
    try(Connection conn = ConnectionFactory.createConnection(conf)) {
      Admin admin = conn.getAdmin();
      result.forEach((t, p) -> {
        if(!p.isEmpty()) {
          Path tblPath =
            new Path(runPath, new Path(t.getNameWithNamespaceInclAsString()
              .replaceAll(":", "_")));
          try {
            fs.mkdirs(tblPath);
            Path sidelined = new Path(tblPath, "sidelined");
            fs.mkdirs(sidelined);
            Path bulkload = new Path(tblPath, "bulkload");
            fs.mkdirs(bulkload);
            p.stream().forEach(region -> {
              try {
                Path sidelinedRegionDir = new Path(sidelined, region.getName());
                fs.mkdirs(sidelinedRegionDir);
                HBCKFsUtils.copyFilesParallel(fs, region, fs, sidelinedRegionDir, conf, 3);
                admin.getDescriptor(t).getColumnFamilyNames().forEach(cf -> {
                  Path cfDir = new Path(region, Bytes.toString(cf));
                  Path tempCfDir = new Path(bulkload, cfDir.getName());
                  try {
                    if (!fs.exists(tempCfDir)) {
                      fs.mkdirs(tempCfDir);
                    }
                    FileStatus[] files = fs.listStatus(cfDir);
                    for (FileStatus file : files) {
                      fs.rename(file.getPath(),
                        new Path(tempCfDir,
                          region.getName() + "-" + file.getPath().getName()));
                    }
                  } catch (IOException e) {
                    LOG.error("Error trying to move files from inconsistent region dir: ", e);
                  }
                });
                fs.delete(region, true);
                LOG.info("region dir {} moved to {}", region.toUri().getRawPath(),
                  sidelinedRegionDir.toUri().getRawPath());
              } catch (IOException e) {
                LOG.error("Error trying to fetch table descriptor: ", e);
              }
            });
            LOG.info("Calling bulk load for: " + tblPath.toUri().getRawPath());
            bulkLoad.run(bulkload.toUri().getRawPath(), t);
          } catch (IOException e) {
            LOG.error("Error trying to create temp dir for sideline files: ", e);
          }
        }
      });
      admin.close();
    }
    return 0;
  }

  public static void main(String [] args) throws Exception {
    Configuration conf = HBaseConfiguration.create();
    int errCode = ToolRunner.run(new MissingRegionDirsRepairTool(conf), args);
    if (errCode != 0) {
      System.exit(errCode);
    }
  }
}
