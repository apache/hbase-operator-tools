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

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.replication.ReplicationException;
import org.apache.hbase.hbck1.HBaseFsck;
import org.apache.hbase.thirdparty.org.apache.commons.cli.CommandLine;
import org.apache.hbase.thirdparty.org.apache.commons.cli.CommandLineParser;
import org.apache.hbase.thirdparty.org.apache.commons.cli.DefaultParser;
import org.apache.hbase.thirdparty.org.apache.commons.cli.Option;
import org.apache.hbase.thirdparty.org.apache.commons.cli.Options;
import org.apache.hbase.thirdparty.org.apache.commons.cli.ParseException;

/**
 * Checks and repairs for hbase replication.
 */
public class ReplicationFsck implements Closeable {
  private final Configuration configuration;

  ReplicationFsck(Configuration conf) throws IOException {
    this.configuration = conf;
  }

  @Override
  public void close() {
    // Nothing to do.
  }

  int fsck(Options hbck2Options, String[] args) throws IOException {
    Options options = new Options();
    Option fixOption = Option.builder("f").longOpt("fix").build();
    options.addOption(fixOption);
    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine;
    try {
      commandLine = parser.parse(options, args, false);
    } catch(ParseException e) {
      HBCK2.usage(hbck2Options, e.getMessage());
      return -1;
    }
    boolean fix = commandLine.hasOption(fixOption.getOpt());
    try (HBaseFsck hbaseFsck = new HBaseFsck(this.configuration)) {
      hbaseFsck.setFixReplication(fix);
      hbaseFsck.checkAndFixReplication();
      Collection<String> tables = commandLine.getArgList();
      if (tables != null && !tables.isEmpty()) {
        // Below needs connection to be up; uses admin.
        hbaseFsck.connect();
        hbaseFsck.setCleanReplicationBarrier(fix);
        for (String table: tables) {
          hbaseFsck.setCleanReplicationBarrierTable(table);
          hbaseFsck.cleanReplicationBarrier();
        }
      }
    } catch (ClassNotFoundException | ReplicationException e) {
      throw new IOException(e);
    }
    return 0;
  }
}
