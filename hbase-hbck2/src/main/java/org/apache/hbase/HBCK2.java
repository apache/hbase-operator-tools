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
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Hbck;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HBase fixup tool version 2, for hbase-2.0.0+ clusters.
 * Supercedes hbck1.
 */
// TODO:
// + Add bulk assign/unassigns. If 60k OPENING regions, doing it via shell takes 10-60 seconds each.
// + On assign, can we look to see if existing assign and if so fail until cancelled?
// + Add test of Master version to ensure it supports hbck functionality.
// + Doc how we just take pointer to zk ensemble... If want to do more exotic config. on client,
// then add a hbase-site.xml onto CLASSPATH for this tool to pick up.
// + Kerberized cluster
public class HBCK2 extends Configured implements Tool {
  private static final Logger LOG = LogManager.getLogger(HBCK2.class);
  public static final int EXIT_SUCCESS = 0;
  public static final int EXIT_FAILURE = 1;
  // Commands
  private static final String SET_TABLE_STATE = "setTableState";
  private static final String ASSIGNS = "assigns";
  private static final String UNASSIGNS = "unassigns";
  private static final String BYPASS = "bypass";
  private Configuration conf;

  TableState setTableState(TableName tableName, TableState.State state)
      throws IOException {
    try (ClusterConnection conn =
             (ClusterConnection)ConnectionFactory.createConnection(getConf())) {
      try (Hbck hbck = conn.getHbck()) {
        return hbck.setTableStateInMeta(new TableState(tableName, state));
      }
    }
  }

  List<Long> assigns(List<String> encodedRegionNames)
  throws IOException {
    try (ClusterConnection conn =
             (ClusterConnection)ConnectionFactory.createConnection(getConf())) {
      try (Hbck hbck = conn.getHbck()) {
        return hbck.assigns(encodedRegionNames);
      }
    }
  }

  List<Long> unassigns(List<String> encodedRegionNames)
  throws IOException {
    try (ClusterConnection conn =
             (ClusterConnection)ConnectionFactory.createConnection(getConf())) {
      try (Hbck hbck = conn.getHbck()) {
        return hbck.unassigns(encodedRegionNames);
      }
    }
  }

  /**
   * @return List of results OR null if failed to run.
   */
  List<Boolean> bypass(List<String> args)
      throws IOException {
    // Bypass has two options....
    Options options =  new Options();
    Option force = Option.builder("f").longOpt("force").
        desc("'force' the procedure finish").build();
    options.addOption(force);
    Option wait = Option.builder("w").longOpt("waitTime").hasArg().
        desc("time to wait on entity lock before giving up").type(Integer.class).build();
    options.addOption(wait);
    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(options, args.toArray(new String [] {}), false);
    } catch (ParseException e) {
      usage(options, e.getMessage());
      return null;
    }
    boolean b = commandLine.hasOption(force.getOpt());
    long waitTime = 0;
    if (commandLine.hasOption(wait.getOpt())) {
      waitTime = Integer.valueOf(commandLine.getOptionValue(wait.getOpt()));
      waitTime *= 1000; // Because time is in seconds.
    }
    String [] pidStrs = commandLine.getArgs();
    if (pidStrs == null || pidStrs.length <= 0) {
      usage(options, "No pids supplied.");
      return null;
    }
    List<Long> pids = Arrays.stream(pidStrs).map(i -> Long.valueOf(i)).collect(Collectors.toList());
    try (ClusterConnection c = (ClusterConnection)ConnectionFactory.createConnection(getConf())) {
      try (Hbck hbck = c.getHbck()) {
        return hbck.bypassProcedure(pids, waitTime, b);
      }
    }
  }

  private static final String getCommandUsage() {
    // NOTE: List commands belonw alphabetically!
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    writer.println();
    writer.println("Commands:");
    writer.println(" " + ASSIGNS + " <ENCODED_REGIONNAME>...");
    writer.println("   A 'raw' assign that can be used even during Master initialization.");
    writer.println("   Skirts Coprocessors. Pass one or more encoded RegionNames:");
    writer.println("   e.g. 1588230740 is hard-coded encoding for hbase:meta region and");
    writer.println("   de00010733901a05f5a2a3a382e27dd4 is an example of what a random");
    writer.println("   user-space encoded Region name looks like. For example:");
    writer.println("     $ HBCK2 assign 1588230740 de00010733901a05f5a2a3a382e27dd4");
    writer.println("   Returns the pid of the created AssignProcedure or -1 if none.");
    writer.println();
    writer.println(" " + BYPASS + " [OPTIONS] <PID>...");
    writer.println("   Pass one (or more) procedure 'pid's to skip to the procedure finish.");
    writer.println("   Parent of this procedures will also skip to its finish. Entities will");
    writer.println("   be left in an inconsistent state and will require manual fixup.");
    writer.println("   Pass --force to break any outstanding locks.");
    writer.println("   Pass --waitTime=<seconds> to wait on entity lock before giving up.");
    writer.println("   Default: force=false and waitTime=0. Returns true if succeeded.");
    writer.println();
    writer.println(" " + UNASSIGNS + " <ENCODED_REGIONNAME>...");
    writer.println("   A 'raw' unassign that can be used even during Master initialization.");
    writer.println("   Skirts Coprocessors. Pass one or more encoded RegionNames:");
    writer.println("   Skirts Coprocessors. Pass one or more encoded RegionNames:");
    writer.println("   de00010733901a05f5a2a3a382e27dd4 is an example of what a random");
    writer.println("   user-space encoded Region name looks like. For example:");
    writer.println("     $ HBCK2 unassign 1588230740 de00010733901a05f5a2a3a382e27dd4");
    writer.println("   Returns the pid of the created UnassignProcedure or -1 if none.");
    writer.println();
    writer.println(" " + SET_TABLE_STATE + " <TABLENAME> <STATE>");
    writer.println("   Possible table states: " + Arrays.stream(TableState.State.values()).
        map(i -> i.toString()).collect(Collectors.joining(", ")));
    writer.println("   To read current table state, in the hbase shell run: ");
    writer.println("     hbase> get 'hbase:meta', '<TABLENAME>', 'table:state'");
    writer.println("   A value of \\x08\\x00 == ENABLED, \\x08\\x01 == DISABLED, etc.");
    writer.println("   An example making table name 'user' ENABLED:");
    writer.println("     $ HBCK2 setTableState users ENABLED");
    writer.println("   Returns whatever the previous table state was.");
    writer.println();

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
    formatter.printHelp( "HBCK2 [OPTIONS] COMMAND <ARGS>",
        "\nOptions:", options, getCommandUsage());
  }

  @Override
  public void setConf(Configuration configuration) {
    this.conf = configuration;
  }

  @Override
  public Configuration getConf() {
    return this.conf;
  }

  @Override
  public int run(String [] args) throws IOException {
    // Configure Options. The below article was more helpful than the commons-cli doc:
    // https://dzone.com/articles/java-command-line-interfaces-part-1-apache-commons
    Options options = new Options();
    Option help = Option.builder("h").longOpt("help").desc("output this help message").build();
    options.addOption(help);
    Option debug = Option.builder("d").longOpt("debug").desc("run with debug output").build();
    options.addOption(debug);
    Option quorum = Option.builder("q").longOpt(HConstants.ZOOKEEPER_QUORUM).hasArg().
        desc("ensemble of target hbase").build();
    options.addOption(quorum);
    Option parent = Option.builder("z").longOpt(HConstants.ZOOKEEPER_ZNODE_PARENT).
        desc("parent znode of target hbase").build();
    options.addOption(parent);
    Option peerPort = Option.builder("p").longOpt(HConstants.ZOOKEEPER_CLIENT_PORT).
        desc("peerport of target hbase ensemble").type(Integer.class).build();
    options.addOption(peerPort);

    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(options, args, true);
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
    if (commandLine.hasOption(quorum.getOpt())) {
      getConf().set(HConstants.ZOOKEEPER_QUORUM, commandLine.getOptionValue(quorum.getOpt()));
    }
    if (commandLine.hasOption(peerPort.getOpt())) {
      getConf().setInt(HConstants.ZOOKEEPER_CLIENT_PORT,
          Integer.valueOf(commandLine.getOptionValue(peerPort.getOpt())));
    }
    if (commandLine.hasOption(parent.getOpt())) {
      getConf().set(HConstants.ZOOKEEPER_ZNODE_PARENT, commandLine.getOptionValue(parent.getOpt()));
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
        System.out.println(setTableState(TableName.valueOf(commands[1]),
            TableState.State.valueOf(commands[2])));
        break;

      case ASSIGNS:
        if (commands.length < 2) {
          usage(options, command + " takes one or more encoded region names");
          return EXIT_FAILURE;
        }
        System.out.println(
            toString(assigns(Arrays.stream(commands).skip(1).collect(Collectors.toList()))));
        break;

      case BYPASS:
        if (commands.length < 2) {
          usage(options, command + " takes one or more pids");
          return EXIT_FAILURE;
        }
        List<Boolean> bs = bypass(Arrays.stream(commands).skip(1).collect(Collectors.toList()));
        if (bs == null) {
          // Something went wrong w/ the parse and command didn't run.
          return EXIT_FAILURE;
        }
        System.out.println(toString(bs));
        break;

      case UNASSIGNS:
        if (commands.length < 2) {
          usage(options, command + " takes one or more encoded region names");
          return EXIT_FAILURE;
        }
        System.out.println(toString(
            unassigns(Arrays.stream(commands).skip(1).collect(Collectors.toList()))));
        break;

      default:
        usage(options, "Unsupported command: " + command);
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
  }

  private static String toString(List<?> things) {
    return things.stream().map(i -> i.toString()).collect(Collectors.joining(", "));
  }

  HBCK2(Configuration conf) {
    super(conf);
  }

  public static void main(String [] args) throws Exception {
    Configuration conf = HBaseConfiguration.create();
    int errCode = ToolRunner.run(new HBCK2(conf), args);
    if (errCode != 0) {
      System.exit(errCode);
    }
    return;
  }
}
