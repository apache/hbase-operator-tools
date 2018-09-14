/**
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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Hbck;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * HBase fixup tool version 2, for hbase-2.0.0+ clusters.
 * Supercedes hbck1.
 */
// TODO:
// + Add bulk assign/unassigns. If 60k OPENING regions, doing it via shell takes 10-60 seconds each.
// + Add test of Master version to ensure it supports hbck functionality.
// + Doc how we just take pointer to zk ensemble... If want to do more exotic config. on client,
// then add a hbase-site.xml onto CLASSPATH for this tool to pick up.
public class HBCK2 {
  private static final Logger LOG = LogManager.getLogger(HBCK2.class);
  public static final int EXIT_SUCCESS = 0;
  public static final int EXIT_FAILURE = 1;
  private static final String SET_TABLE_STATE = "setTableState";

  private static final String getCommandUsage() {
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    writer.println("Commands:");
    writer.println(" " + SET_TABLE_STATE + " <TABLENAME> <STATE>");
    writer.println("   Possible table states: " + Arrays.stream(TableState.State.values()).
        map(i -> i.toString()).collect(Collectors.joining(", ")));
    writer.println("   To read current table state, in the hbase shell run: ");
    writer.println("     hbase> get 'hbase:meta', '<TABLENAME>', 'table:state'");
    writer.println("   A value of \\x08\\x00 == ENABLED, \\x08\\x01 == DISABLED, etc.");
    writer.println("   An example making table name 'user' ENABLED:");
    writer.println("     $ HBCK2 setTableState users ENABLED");
    writer.close();
    return sw.toString();
  }

  static void usage(Options options) {
    usage(options, null);
  }

  static void usage(Options options, String error) {
    if (error != null) {
      System.out.println("ERROR: " + error);
    }
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp( "HBCK2 <OPTIONS> COMMAND [<ARGS>]",
        "Options:", options, getCommandUsage());
  }

  /**
   * Call the HbckService#setTableState
   * @throws IOException
   */
  static void setTableState(Configuration conf, TableName tableName, TableState.State state)
  throws IOException {
    try (ClusterConnection conn = (ClusterConnection)ConnectionFactory.createConnection(conf)) {
      try (Hbck hbck = conn.getHbck()) {
        hbck.setTableStateInMeta(new TableState(tableName, state));
      }
    }
  }

  /**
   * @return Return what to exit with.
   */
  static int doWork(String [] args) throws IOException {
    // Configure Options. The below article was more helpful than the commons-cli doc:
    // https://dzone.com/articles/java-command-line-interfaces-part-1-apache-commons
    Options options = new Options();
    Option help = Option.builder("h").longOpt("help").desc("output this help message").build();
    options.addOption(help);
    Option debug = Option.builder("d").longOpt("debug").desc("run with debug output").build();
    options.addOption(debug);
    Option quorum = Option.builder().longOpt("hbase.zookeeper.quorum").
        desc("ensemble of target hbase").build();
    options.addOption(quorum);
    Option parent = Option.builder().longOpt("zookeeper.znode.parent").
        desc("parent znode of target hbase").build();
    options.addOption(parent);
    Option peerPort = Option.builder().longOpt("hbase.zookeeper.peerport").
        desc("peerport of target hbase ensemble").type(Integer.class).build();
    options.addOption(peerPort);

    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(options, args);
    } catch (ParseException e) {
      usage(options, e.getMessage());
      return EXIT_FAILURE;
    }

    // Process general options.
    if (commandLine.hasOption(help.getOpt()) || commandLine.getArgList().isEmpty()) {
      usage(options);
      return EXIT_SUCCESS;
    }
    if (commandLine.hasOption(debug.getOpt())) {
      Configurator.setRootLevel(Level.DEBUG);
    }

    // Build up Configuration for client to use connecting to hbase zk ensemble.
    Configuration conf = HBaseConfiguration.create();
    if (options.hasOption(quorum.getOpt())) {
      conf.set(HConstants.ZOOKEEPER_QUORUM, commandLine.getOptionValue(quorum.getOpt()));
    }
    if (options.hasOption(peerPort.getOpt())) {
      conf.setInt(HConstants.ZOOKEEPER_CLIENT_PORT,
          Integer.valueOf(commandLine.getOptionValue(peerPort.getOpt())));
    }
    if (options.hasOption(parent.getOpt())) {
      conf.set(HConstants.ZOOKEEPER_ZNODE_PARENT, commandLine.getOptionValue(parent.getOpt()));
    }

    // Now process commands.
    String [] commands = commandLine.getArgs();
    String command = commands[0];
    switch (command) {
      case SET_TABLE_STATE:
        if (commands.length < 3) {
          usage(options, command + " takes tablename and state arguments: e.g. user ENABLED");
          return EXIT_FAILURE;
        }
        setTableState(conf, TableName.valueOf(commands[1]), TableState.State.valueOf(commands[2]));
        break;

      default:
        usage(options, "Unsupported command: " + command);
        System.exit(1);
    }
    return EXIT_SUCCESS;
  }

  public static void main(String [] args) throws IOException {
    int exitCode = doWork(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
    return;
  }
}