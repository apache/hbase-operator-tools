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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HBase fixup tool version 2, for hbase-2.0.0+ clusters.
 */
// TODO:
// + Add bulk assign/unassigns. If 60k OPENING regions, doing it via shell takes 10-60 seconds each.
public class HBCK2 {
  private static final Logger logger = LogManager.getLogger(HBCK2.class);
  private static final String SET_TABLE_STATE = "setTableState";

  private static final String getCommandUsage() {
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    writer.println("Commands:");
    writer.println(" " + SET_TABLE_STATE + " TABLENAME STATE");
    writer.println("Help:");
    writer.println(" Possible table states: " + Arrays.stream(TableState.State.values()).
        map(i -> i.toString()).collect(Collectors.joining(", ")));
    writer.println("Examples:");
    writer.println(" $ HBCK2 setTableState users=ENABLED");
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

  static void setTableState(TableName tableName, TableState.State state) throws IOException {
    Configuration conf = HBaseConfiguration.create();
    try (ClusterConnection conn = (ClusterConnection)ConnectionFactory.createConnection(conf)) {
      try (Hbck hbck = conn.getHbck()) {
        hbck.setTableStateInMeta(new TableState(tableName, state));
      }
    }
  }

  public static void main(String [] args) throws ParseException, IOException {
    // Configure Options.
    Options options = new Options();
    Option help = Option.builder("h").longOpt("help").desc("output this help message").build();
    options.addOption(help);
    Option debug = Option.builder("d").longOpt("debug").desc("run with debug output").build();
    options.addOption(debug);

    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(options, args);

    // Process general options.
    if (commandLine.hasOption(help.getOpt()) || commandLine.getArgList().isEmpty()) {
      usage(options);
      System.exit(0);
    }
    if (commandLine.hasOption(debug.getOpt())) {
      Configurator.setRootLevel(Level.DEBUG);
    }

    // Now process commands.
    String [] commands = commandLine.getArgs();
    String command = commands[0];
    switch (command) {
      case SET_TABLE_STATE:
        if (commands.length < 3) {
          usage(options, command + " takes a table name = state argument, e.g. user ENABLED");
          System.exit(1);
        }
        setTableState(TableName.valueOf(commands[1]), TableState.State.valueOf(commands[2]));
        break;

      default:
        usage(options, "Unsupported command: " + command);
        System.exit(1);
    }
  }
}