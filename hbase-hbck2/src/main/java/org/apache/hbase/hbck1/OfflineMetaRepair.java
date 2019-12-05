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
package org.apache.hbase.hbck1;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.util.FSUtils;
import org.apache.hadoop.io.MultipleIOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This code is used to rebuild meta offline from metadata hbase drops into the
 * filesystem. If there are any problem detected, it will fail suggesting actions
 * for the user to "fix" problems. If it succeeds, it will backup the previous
 * hbase:meta and write new tables in place.
 *
 * <p>This is an advanced feature, so is only exposed for use if explicitly
 * mentioned.
 *
 * <p>It came from hbck1 retains same class name and arguments (though it has
 * been moved to a different package)</p>
 *
 *
 * <p>Run the below to see options. Passing no argument will set the tool
 * running trying to effect a repair so pass '-h' to see output:
 * <code>$ hbase org.apache.hbase.hbck1.OfflineMetaRepair ...</code>
 */
public final class OfflineMetaRepair {
  private static final Logger LOG = LoggerFactory.getLogger(OfflineMetaRepair.class.getName());

  /**
   * Hide constructor.
   */
  private OfflineMetaRepair() {}

  protected static void printUsageAndExit() {
    StringBuilder sb = new StringBuilder();
    sb.append("Usage: OfflineMetaRepair [opts]\n").
       append("Where [opts] are:\n").
       append(" -details               execute of all regions before hbase:meta rebuild.\n").
       append(" -base <hdfs://>        Base HBase Data directory to read from.\n").
       append(" -sidelineDir <hdfs://> path of where to backup existing hbase:meta.\n").
       append("Master should be down (Script runs regardless!)");
    System.err.println(sb.toString());
    Runtime.getRuntime().exit(-2);
  }

  public static void main(String[] args) throws Exception {
    // create a fsck object
    Configuration conf = HBaseConfiguration.create();
    // Cover both bases, the old way of setting default fs and the new.
    // We're supposed to run on 0.20 and 0.21 anyways.
    FSUtils.setFsDefault(conf, FSUtils.getRootDir(conf));
    HBaseFsck fsck = new HBaseFsck(conf);

    // Process command-line args.
    for (int i = 0; i < args.length; i++) {
      String cmd = args[i];
      if (cmd.equals("-details")) {
        HBaseFsck.setDisplayFullReport();
      } else if (cmd.equals("-base")) {
        if (i == args.length - 1) {
          System.err.println("OfflineMetaRepair: -base needs an HDFS path.");
          printUsageAndExit();
        }
        // update hbase root dir to user-specified base
        i++;
        FSUtils.setRootDir(conf, new Path(args[i]));
        FSUtils.setFsDefault(conf, FSUtils.getRootDir(conf));
      } else if (cmd.equals("-sidelineDir")) {
        if (i == args.length - 1) {
          System.err.println("OfflineMetaRepair: -sidelineDir needs an HDFS path.");
          printUsageAndExit();
        }
        // set the hbck sideline dir to user-specified one
        i++;
        fsck.setSidelineDir(args[i]);
      } else {
        String str = "Unknown command line option : " + cmd;
        LOG.info(str);
        System.out.println(str);
        printUsageAndExit();
      }
    }

    System.out.println("OfflineMetaRepair command line options: " + String.join(" ", args));

    // Fsck doesn't shutdown and and doesn't provide a way to shutdown its
    // threads cleanly, so we do a System.exit.
    boolean success = false;
    try {
      success = fsck.rebuildMeta();
    } catch (MultipleIOException mioes) {
      for (IOException ioe : mioes.getExceptions()) {
        LOG.error("Bailed out due to:", ioe);
      }
    } catch (Exception e) {
      LOG.error("Bailed out due to: ", e);
    } finally {
      System.exit(success ? 0 : 1);
    }
  }
}
