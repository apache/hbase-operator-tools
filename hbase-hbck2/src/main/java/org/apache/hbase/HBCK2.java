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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Hbck;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.hbase.filter.RowFilter;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.master.RegionState;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.collect.Lists;
import org.apache.hbase.thirdparty.org.apache.commons.cli.CommandLine;
import org.apache.hbase.thirdparty.org.apache.commons.cli.CommandLineParser;
import org.apache.hbase.thirdparty.org.apache.commons.cli.DefaultParser;
import org.apache.hbase.thirdparty.org.apache.commons.cli.HelpFormatter;
import org.apache.hbase.thirdparty.org.apache.commons.cli.Option;
import org.apache.hbase.thirdparty.org.apache.commons.cli.Options;
import org.apache.hbase.thirdparty.org.apache.commons.cli.ParseException;

import org.apache.hadoop.hbase.shaded.protobuf.generated.HBaseProtos;

/**
 * HBase fixup tool version 2, for hbase-2.0.0+ clusters. Supercedes hbck1.
 */
// TODO:
// + On assign, can we look to see if existing assign and if so fail until cancelled?
// + Doc how we just take pointer to zk ensemble... If want to do more exotic config. on client,
// then add a hbase-site.xml onto CLASSPATH for this tool to pick up.
// + Add --version
public class HBCK2 extends Configured implements org.apache.hadoop.util.Tool {
  private static final Logger LOG = LoggerFactory.getLogger(HBCK2.class);
  private static final int EXIT_SUCCESS = 0;
  static final int EXIT_FAILURE = 1;
  /** The delimiter for meta columns for replicaIds &gt; 0 */
  private static final char META_REPLICA_ID_DELIMITER = '_';

  // Commands
  private static final String SET_TABLE_STATE = "setTableState";
  private static final String ASSIGNS = "assigns";
  private static final String UNASSIGNS = "unassigns";
  private static final String BYPASS = "bypass";
  private static final String FILESYSTEM = "filesystem";
  private static final String REPLICATION = "replication";
  private static final String VERSION = "version";
  private static final String SET_REGION_STATE = "setRegionState";
  private static final String SCHEDULE_RECOVERIES = "scheduleRecoveries";
  private static final String RECOVER_UNKNOWN = "recoverUnknown";
  private static final String GENERATE_TABLE_INFO = "generateMissingTableDescriptorFile";
  private static final String FIX_META = "fixMeta";
  private static final String REGIONINFO_MISMATCH = "regionInfoMismatch";
  // TODO update this map in case of the name of a method changes in Hbck interface
  // in org.apache.hadoop.hbase.client package. Or a new command is added and the hbck command
  // does not equals to the method name in Hbck interface.
  private static final Map<String, List<String>> FUNCTION_NAME_MAP =
    Collections.unmodifiableMap(new HashMap<String, List<String>>() {
      {
        put(SET_TABLE_STATE, Arrays.asList("setTableStateInMeta"));
        put(BYPASS, Arrays.asList("bypassProcedure"));
        put(SCHEDULE_RECOVERIES,
          Arrays.asList("scheduleServerCrashProcedure", "scheduleServerCrashProcedures"));
        put(RECOVER_UNKNOWN, Arrays.asList("scheduleSCPsForUnknownServers"));
      }
    });

  private static final String ADD_MISSING_REGIONS_IN_META_FOR_TABLES = "addFsRegionsMissingInMeta";
  private static final String REPORT_MISSING_REGIONS_IN_META = "reportMissingRegionsInMeta";
  private static final String EXTRA_REGIONS_IN_META = "extraRegionsInMeta";

  private Configuration conf;
  static final String[] MINIMUM_HBCK2_VERSION = { "2.0.3", "2.1.1", "2.2.0", "3.0.0" };
  private boolean skipCheck = false;

  /**
   * Wait 1ms on lock by default.
   */
  private static final long DEFAULT_LOCK_WAIT = 1;

  /**
   * Value which would represent no batching.
   */
  private static final int NO_BATCH_SIZE = -1;

  /**
   * Number of units to process in a single call. By default, it is set to -1, that is no batching
   * would be done.
   */
  private static final int DEFAULT_BATCH_SIZE = NO_BATCH_SIZE;

  /**
   * Check for HBCK support. Expects created connection.
   * @param supportedVersions list of zero or more supported versions.
   */
  void checkHBCKSupport(ClusterConnection connection, String cmd, String... supportedVersions)
    throws IOException {
    if (skipCheck) {
      LOG.info("Skipped {} command version check; 'skip' set", cmd);
      return;
    }
    try (Admin admin = connection.getAdmin()) {
      String serverVersion =
        admin.getClusterMetrics(EnumSet.of(ClusterMetrics.Option.HBASE_VERSION)).getHBaseVersion();
      String[] thresholdVersions = supportedVersions == null || supportedVersions.length == 0
        ? MINIMUM_HBCK2_VERSION
        : supportedVersions;
      boolean supported = Version.check(serverVersion, thresholdVersions);
      if (!supported) {
        throw new UnsupportedOperationException(cmd + " not supported on server version="
          + serverVersion + "; needs at least a server that matches or exceeds "
          + Arrays.toString(thresholdVersions));
      }
    }
  }

  void checkFunctionSupported(ClusterConnection connection, String cmd) throws IOException {
    if (skipCheck) {
      LOG.info("Skipped {} command version check; 'skip' set", cmd);
      return;
    }
    List<Method> methods = Arrays.asList(connection.getHbck().getClass().getDeclaredMethods());
    List<String> finalCmds = FUNCTION_NAME_MAP.getOrDefault(cmd, Collections.singletonList(cmd));
    boolean supported = methods.stream().anyMatch(method -> finalCmds.contains(method.getName()));
    if (!supported) {
      throw new UnsupportedOperationException(
        "This HBase cluster does not support command: " + cmd);
    }
  }

  public static byte[] getRegionStateColumn(int replicaId) {
    try {
      return replicaId == 0
        ? HConstants.STATE_QUALIFIER
        : (HConstants.STATE_QUALIFIER_STR + META_REPLICA_ID_DELIMITER
          + String.format(RegionInfo.REPLICA_ID_FORMAT, replicaId))
            .getBytes(StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      // should never happen!
      throw new IllegalArgumentException("UTF8 decoding is not supported", e);
    }
  }

  void setTableState(Hbck hbck, String[] args) throws IOException {
    Options options = new Options();
    Option inputFile = Option.builder("i").longOpt("inputFiles").build();
    options.addOption(inputFile);
    CommandLine commandLine = getCommandLine(args, options);
    if (commandLine == null) {
      return;
    }
    String[] argList = commandLine.getArgs();
    if (!commandLine.hasOption(inputFile.getOpt())) {
      System.out.println(setTableStateByArgs(hbck, argList));
    } else {
      List<String> inputList = getFromArgsOrFiles(stringArrayToList(argList), true);
      for (String line : inputList) {
        String[] params = line.split("\\s+");
        System.out.println(setTableStateByArgs(hbck, params));
      }
    }
  }

  TableState setTableStateByArgs(Hbck hbck, String[] args) throws IOException {
    if (args == null || args.length < 2) {
      showErrorMessage(
        SET_TABLE_STATE + " takes tablename and state arguments: e.g. user ENABLED, you entered: "
          + Arrays.toString(args));
      return null;
    }
    return setTableState(hbck, TableName.valueOf(args[0]), TableState.State.valueOf(args[1]));
  }

  TableState setTableState(Hbck hbck, TableName tableName, TableState.State state)
    throws IOException {
    return hbck.setTableStateInMeta(new TableState(tableName, state));
  }

  int setRegionState(ClusterConnection connection, String region, RegionState.State newState)
    throws IOException {
    return setRegionState(connection, region, 0, newState);
  }

  int setRegionState(ClusterConnection connection, String[] args) throws IOException {
    Options options = new Options();
    Option inputFile = Option.builder("i").longOpt("inputFiles").build();
    options.addOption(inputFile);
    CommandLine commandLine = getCommandLine(args, options);
    if (commandLine == null) {
      return EXIT_FAILURE;
    }
    String[] argList = commandLine.getArgs();
    if (argList == null) {
      return EXIT_FAILURE;
    }

    if (!commandLine.hasOption(inputFile.getOpt())) {
      String[] params = formatSetRegionStateCommand(argList);
      return setRegionStateByArgs(connection, params);
    } else {
      List<String> inputList = getFromArgsOrFiles(stringArrayToList(argList), true);
      for (String line : inputList) {
        String[] params = formatSetRegionStateCommand(line.split("\\s+"));
        if (setRegionStateByArgs(connection, params) == EXIT_FAILURE) {
          showErrorMessage("setRegionState failed to set " + Arrays.toString(args));
        }
      }
      return EXIT_SUCCESS;
    }
  }

  int setRegionStateByArgs(ClusterConnection connection, String[] args) throws IOException {
    if (args == null || args.length < 3) {
      return EXIT_FAILURE;
    }
    RegionState.State state = RegionState.State.valueOf(args[2]);
    int replicaId = Integer.parseInt(args[1]);
    return setRegionState(connection, args[0], replicaId, state);
  }

  int setRegionState(ClusterConnection connection, String region, int replicaId,
    RegionState.State newState) throws IOException {
    if (newState == null) {
      throw new IllegalArgumentException("State can't be null.");
    }
    RegionState.State currentState = null;
    Table table = connection.getTable(TableName.valueOf("hbase:meta"));
    RowFilter filter = new RowFilter(CompareOperator.EQUAL, new SubstringComparator(region));
    Scan scan = new Scan();
    scan.setFilter(filter);
    Result result = table.getScanner(scan).next();
    if (result != null) {
      byte[] currentStateValue =
        result.getValue(HConstants.CATALOG_FAMILY, getRegionStateColumn(replicaId));
      if (currentStateValue == null) {
        System.out.println("WARN: Region state info on meta was NULL");
      } else {
        currentState =
          RegionState.State.valueOf(org.apache.hadoop.hbase.util.Bytes.toString(currentStateValue));
      }
      Put put = new Put(result.getRow());
      put.addColumn(HConstants.CATALOG_FAMILY, getRegionStateColumn(replicaId),
        org.apache.hadoop.hbase.util.Bytes.toBytes(newState.name()));
      table.put(put);

      if (replicaId == 0) {
        System.out
          .println("Changed region " + region + " STATE from " + currentState + " to " + newState);
      } else {
        System.out.println("Changed STATE for replica reigon " + replicaId + " of primary region "
          + region + "from " + currentState + " to " + newState);
      }

      return EXIT_SUCCESS;
    } else {
      System.out.println("ERROR: Could not find region " + region + " in meta.");
    }
    return EXIT_FAILURE;
  }

  Map<TableName, List<Path>> reportTablesWithMissingRegionsInMeta(String... args)
    throws IOException {
    Map<TableName, List<Path>> report;
    try (
      final FsRegionsMetaRecoverer fsRegionsMetaRecoverer = new FsRegionsMetaRecoverer(this.conf)) {
      report = fsRegionsMetaRecoverer.reportTablesMissingRegions((getInputList(args)));
    } catch (IOException e) {
      LOG.error("Error reporting missing regions: ", e);
      throw e;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(formatMissingRegionsInMetaReport(report));
    }
    return report;
  }

  Map<TableName, List<String>> extraRegionsInMeta(String[] args) throws Exception {
    Options options = new Options();
    Option fixOption = Option.builder("f").longOpt("fix").build();
    options.addOption(fixOption);
    Option inputFile = Option.builder("i").longOpt("inputFiles").build();
    options.addOption(inputFile);
    Map<TableName, List<String>> result = new HashMap<>();
    // Parse command-line.
    CommandLine commandLine = getCommandLine(args, options);
    if (commandLine == null) {
      return result;
    }
    boolean fix = commandLine.hasOption(fixOption.getOpt());
    boolean inputFileFlag = commandLine.hasOption(inputFile.getOpt());

    try (
      final FsRegionsMetaRecoverer fsRegionsMetaRecoverer = new FsRegionsMetaRecoverer(this.conf)) {
      List<String> namespacesTables = getFromArgsOrFiles(commandLine.getArgList(), inputFileFlag);
      Map<TableName, List<HBCKMetaEntry>> reportMap =
        fsRegionsMetaRecoverer.reportTablesExtraRegions(namespacesTables);
      final List<String> toFix = new ArrayList<>();
      reportMap.entrySet().forEach(e -> {
        result.put(e.getKey(),
          e.getValue().stream().map(r -> r.getEncodedRegionName()).collect(Collectors.toList()));
        if (fix && e.getValue().size() > 0) {
          toFix.add(e.getKey().getNameWithNamespaceInclAsString());
        }
      });
      if (fix) {
        List<Future<List<String>>> removeResult =
          fsRegionsMetaRecoverer.removeExtraRegionsFromMetaForTables(toFix);
        if (removeResult != null) {
          int totalRegions = 0;
          List<Exception> errors = new ArrayList<>();
          for (Future<List<String>> f : removeResult) {
            try {
              totalRegions += f.get().size();
            } catch (ExecutionException | InterruptedException e) {
              errors.add(e);
            }
          }
          System.out.println(formatRemovedRegionsMessage(totalRegions, errors));
        }
      }
    } catch (IOException e) {
      LOG.error("Error on checking extra regions: ", e);
      throw e;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(formatExtraRegionsReport(result));
    }
    return result;
  }

  List<Future<List<String>>> addMissingRegionsInMetaForTables(String... nameSpaceOrTable)
    throws IOException {
    try (
      final FsRegionsMetaRecoverer fsRegionsMetaRecoverer = new FsRegionsMetaRecoverer(this.conf)) {
      return fsRegionsMetaRecoverer
        .addMissingRegionsInMetaForTables(getInputList(nameSpaceOrTable));
    } catch (IOException e) {
      LOG.error("Error adding missing regions: ", e);
      throw e;
    }
  }

  private Pair<List<String>, List<Exception>>
    addMissingRegionsInMetaForTablesWrapper(String... args) throws IOException {
    // Init
    Options options = new Options();
    Option outputFile = Option.builder("o").longOpt("outputFile").hasArg().build();
    options.addOption(outputFile);
    Option numberOfLinesPerFile = Option.builder("n").longOpt("numLines").hasArg().build();
    options.addOption(numberOfLinesPerFile);
    Option inputFile = Option.builder("i").longOpt("inputFiles").build();
    options.addOption(inputFile);

    // 1st element is region name list and 2nd element is error list
    final Pair<List<String>, List<Exception>> result =
      Pair.newPair(new ArrayList<>(), new ArrayList<>());
    final List<String> regionsList = result.getFirst();
    final List<Exception> errorList = result.getSecond();

    // Parse command-line
    CommandLine commandLine = getCommandLine(args, options);
    if (commandLine == null) {
      return result;
    }

    boolean outputFileFlag = commandLine.hasOption(outputFile.getOpt());
    boolean inputFileFlag = commandLine.hasOption(inputFile.getOpt());
    boolean numberOfLinesPerFileFlag = commandLine.hasOption(numberOfLinesPerFile.getOpt());

    final List<String> namespacesTables =
      getFromArgsOrFiles(commandLine.getArgList(), inputFileFlag);
    final List<Future<List<String>>> addedRegions =
      addMissingRegionsInMetaForTables(namespacesTables.toArray(new String[] {}));

    for (Future<List<String>> f : addedRegions) {
      try {
        regionsList.addAll(f.get());
      } catch (InterruptedException | ExecutionException e) {
        errorList.add(e);
      }
    }

    if (outputFileFlag) {
      String fileNameOrPrefix = commandLine.getOptionValue(outputFile.getOpt());
      if (numberOfLinesPerFileFlag) {
        int numberOfRegionsPerFile =
          Integer.parseInt(commandLine.getOptionValue(numberOfLinesPerFile.getOpt()));
        final List<List<String>> partition = Lists.partition(regionsList, numberOfRegionsPerFile);
        for (int i = 0; i < partition.size(); i++) {
          // Dump to file
          File file = new File(fileNameOrPrefix + "." + i);
          System.out.println("Dumping region names to file: " + file.getAbsolutePath());
          FileUtils.writeLines(file, partition.get(i));
        }
      } else {
        File file = new File(fileNameOrPrefix);
        System.out.println("Dumping region names to file: " + file.getAbsolutePath());
        FileUtils.writeLines(file, regionsList);
      }
    }

    return result;
  }

  List<Long> assigns(Hbck hbck, String[] args) throws IOException {
    // Init
    Options options = new Options();
    Option override = Option.builder("o").longOpt("override").build();
    Option inputFile = Option.builder("i").longOpt("inputFiles").build();
    Option batchOpt = Option.builder("b").longOpt("batchSize").hasArg().type(Integer.class).build();
    options.addOption(override);
    options.addOption(inputFile);
    options.addOption(batchOpt);

    // Parse command-line
    CommandLine commandLine = getCommandLine(args, options);
    if (commandLine == null) {
      return null;
    }

    int batchSize = getBatchSize(batchOpt, commandLine);
    boolean overrideFlag = commandLine.hasOption(override.getOpt());
    boolean inputFileFlag = commandLine.hasOption(inputFile.getOpt());

    List<String> regionList = getFromArgsOrFiles(commandLine.getArgList(), inputFileFlag);

    // Process here
    if (batchSize == NO_BATCH_SIZE) {
      return hbck.assigns(regionList, overrideFlag);
    } else {
      List<Long> pidList = new ArrayList<>(regionList.size());
      final List<List<String>> batch = Lists.partition(regionList, batchSize);
      for (int i = 0; i < batch.size(); i++) {
        LOG.info("Processing batch #" + i);
        pidList.addAll(hbck.assigns(batch.get(i), overrideFlag));
      }
      return pidList;
    }
  }

  List<Long> unassigns(Hbck hbck, String[] args) throws IOException {
    // Init
    Options options = new Options();
    Option override = Option.builder("o").longOpt("override").build();
    Option inputFile = Option.builder("i").longOpt("inputFiles").build();
    Option batchOpt = Option.builder("b").longOpt("batchSize").hasArg().type(Integer.class).build();
    options.addOption(override);
    options.addOption(inputFile);
    options.addOption(batchOpt);

    // Parse command-line
    CommandLine commandLine = getCommandLine(args, options);
    if (commandLine == null) {
      return null;
    }

    boolean overrideFlag = commandLine.hasOption(override.getOpt());
    boolean inputFileFlag = commandLine.hasOption(inputFile.getOpt());
    int batchSize = getBatchSize(batchOpt, commandLine);

    List<String> regionList = getFromArgsOrFiles(commandLine.getArgList(), inputFileFlag);

    // Process here
    if (batchSize == NO_BATCH_SIZE) {
      return hbck.unassigns(regionList, overrideFlag);
    } else {
      List<Long> pidList = new ArrayList<>(regionList.size());
      final List<List<String>> batch = Lists.partition(regionList, batchSize);
      for (int i = 0; i < batch.size(); i++) {
        LOG.info("Processing batch #" + i);
        pidList.addAll(hbck.unassigns(batch.get(i), overrideFlag));
      }
      return pidList;
    }
  }

  /** Returns List of results OR null if failed to run. */
  List<Boolean> bypass(String[] args) throws IOException {
    // Init
    Options options = new Options();
    // See usage for 'help' on these options.
    Option override = Option.builder("o").longOpt("override").build();
    options.addOption(override);
    Option recursive = Option.builder("r").longOpt("recursive").build();
    options.addOption(recursive);
    Option wait = Option.builder("w").longOpt("lockWait").hasArg().type(Integer.class).build();
    options.addOption(wait);
    Option inputFile = Option.builder("i").longOpt("inputFiles").build();
    options.addOption(inputFile);
    Option batchOpt = Option.builder("b").longOpt("batchSize").hasArg().type(Integer.class).build();
    options.addOption(batchOpt);

    // Parse command-line
    CommandLine commandLine = getCommandLine(args, options);
    if (commandLine == null) {
      return null;
    }
    long lockWait = DEFAULT_LOCK_WAIT;
    if (commandLine.hasOption(wait.getOpt())) {
      lockWait = Integer.parseInt(commandLine.getOptionValue(wait.getOpt()));
    }
    boolean overrideFlag = commandLine.hasOption(override.getOpt());
    boolean recursiveFlag = commandLine.hasOption(recursive.getOpt());
    boolean inputFileFlag = commandLine.hasOption(inputFile.getOpt());
    int batchSize = getBatchSize(batchOpt, commandLine);

    String[] pidStrs =
      getFromArgsOrFiles(commandLine.getArgList(), inputFileFlag).toArray(new String[0]);
    if (pidStrs == null || pidStrs.length <= 0) {
      showErrorMessage("No pids supplied.");
      return null;
    }
    List<Long> pids = Arrays.stream(pidStrs).map(Long::valueOf).collect(Collectors.toList());

    // Process here
    try (ClusterConnection connection = connect(); Hbck hbck = connection.getHbck()) {
      checkFunctionSupported(connection, BYPASS);
      if (batchSize == NO_BATCH_SIZE) {
        return hbck.bypassProcedure(pids, lockWait, overrideFlag, recursiveFlag);
      } else {
        List<Boolean> statusList = new ArrayList<>(pids.size());
        final List<List<Long>> batch = Lists.partition(pids, batchSize);
        for (int i = 0; i < batch.size(); i++) {
          LOG.info("Processing batch #" + i);
          statusList
            .addAll(hbck.bypassProcedure(batch.get(i), lockWait, overrideFlag, recursiveFlag));
        }
        return statusList;
      }
    }
  }

  List<Long> scheduleRecoveries(Hbck hbck, String[] args) throws IOException {
    List<HBaseProtos.ServerName> serverNames = new ArrayList<>();
    List<String> inputList = getInputList(args);
    if (inputList != null) {
      for (String serverName : inputList) {
        serverNames.add(parseServerName(serverName));
      }
    }
    return hbck.scheduleServerCrashProcedure(serverNames);
  }

  List<Long> recoverUnknown(Hbck hbck) throws IOException {
    return hbck.scheduleSCPsForUnknownServers();
  }

  /**
   * Runs the RegionInfoMismatchTool using CLI options.
   */
  void regionInfoMismatch(String[] args) throws Exception {
    // CLI Options
    Options options = new Options();
    Option dryRunOption = Option.builder("f").longOpt("fix").hasArg(false).build();
    options.addOption(dryRunOption);
    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(options, args, false);
    final boolean fix = commandLine.hasOption(dryRunOption.getOpt());
    try (ClusterConnection connection = connect()) {
      new RegionInfoMismatchTool(connection).run(fix);
    }
  }

  private HBaseProtos.ServerName parseServerName(String serverName) {
    ServerName sn = ServerName.parseServerName(serverName);
    return HBaseProtos.ServerName.newBuilder().setHostName(sn.getHostname()).setPort(sn.getPort())
      .setStartCode(sn.getStartcode()).build();
  }

  /**
   * Read property from hbck2.properties file.
   */
  private String readHBCK2BuildProperties(final String propertyKey) throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("hbck2.properties");
    final Properties properties = new Properties();
    properties.load(inputStream);
    return properties.getProperty(propertyKey);
  }

  private static String getCommandUsage() {
    // NOTE: List commands below alphabetically!
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    writer.println("Command:");
    usageAddFsRegionsMissingInMeta(writer);
    writer.println();
    usageAssigns(writer);
    writer.println();
    usageBypass(writer);
    writer.println();
    usageExtraRegionsInMeta(writer);
    writer.println();
    usageFilesystem(writer);
    writer.println();
    usageFixMeta(writer);
    writer.println();
    usageGenerateMissingTableInfo(writer);
    writer.println();
    usageRecoverUnknown(writer);
    writer.println();
    usageRegioninfoMismatch(writer);
    writer.println();
    usageReplication(writer);
    writer.println();
    usageReportMissingRegionsInMeta(writer);
    writer.println();
    usageSetRegionState(writer);
    writer.println();
    usageSetTableState(writer);
    writer.println();
    usageScheduleRecoveries(writer);
    writer.println();
    usageUnassigns(writer);
    writer.println();
    writer.close();
    return sw.toString();
  }

  private static void usageAddFsRegionsMissingInMeta(PrintWriter writer) {
    writer.println(" " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " [OPTIONS]");
    writer.println("      [<NAMESPACE|NAMESPACE:TABLENAME>...|-i <INPUTFILES>...]");
    writer.println("   Options:");
    writer.println("    -i,--inputFiles  take one or more files of namespace or table names");
    writer.println("    -o,--outputFile  name/prefix of the file(s) to dump region names");
    writer.println("    -n,--numLines  number of lines to be written to each output file");
    writer.println("   To be used when regions missing from hbase:meta but directories");
    writer.println("   are present still in HDFS. Can happen if user has run _hbck1_");
    writer.println("   'OfflineMetaRepair' against an hbase-2.x cluster. Needs hbase:meta");
    writer.println("   to be online. For each table name passed as parameter, performs diff");
    writer.println("   between regions available in hbase:meta and region dirs on HDFS.");
    writer.println("   Then for dirs with no hbase:meta matches, it reads the 'regioninfo'");
    writer.println("   metadata file and re-creates given region in hbase:meta. Regions are");
    writer.println("   re-created in 'CLOSED' state in the hbase:meta table, but not in the");
    writer.println("   Masters' cache, and they are not assigned either. To get these");
    writer.println("   regions online, run the HBCK2 'assigns'command printed when this");
    writer.println("   command-run completes.");
    writer.println("   NOTE: If using hbase releases older than 2.3.0, a rolling restart of");
    writer.println("   HMasters is needed prior to executing the set of 'assigns' output.");
    writer.println("   An example adding missing regions for tables 'tbl_1' in the default");
    writer.println("   namespace, 'tbl_2' in namespace 'n1' and for all tables from");
    writer.println("   namespace 'n2':");
    writer.println(
      "     $ HBCK2 " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " default:tbl_1 n1:tbl_2 n2");
    writer.println("   Returns HBCK2  an 'assigns' command with all re-inserted regions.");
    writer.println("   SEE ALSO: " + REPORT_MISSING_REGIONS_IN_META);
    writer.println("   SEE ALSO: " + FIX_META);
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains <NAMESPACE|NAMESPACE:TABLENAME>, one per line.");
    writer.println("   For example:");
    writer.println(
      "     $ HBCK2 " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " -i fileName1 fileName2");
    writer.println("   If -o or --outputFile is specified, the output file(s) can be passed as");
    writer.println("    input to assigns command via -i or -inputFiles option.");
    writer.println("   If -n or --numLines is specified, and say it is  set to 100, this will");
    writer.println("   create files with prefix as value passed by -o or --outputFile option.");
    writer.println("   Each file will have 100 region names (max.), one per line.");
    writer.println("   For example:");
    writer.println(
      "     $ HBCK2 " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " -o outputFilePrefix -n 100");
    writer.println("     -i fileName1 fileName2");
    writer.println("   But if -n is not specified, but -o is specified, it will dump all");
    writer.println("   region names in a single file, one per line.");
    writer.println("   NOTE: -n option is applicable only if -o option is specified.");
  }

  private static void usageAssigns(PrintWriter writer) {
    writer.println(" " + ASSIGNS + " [OPTIONS] [<ENCODED_REGIONNAME>...|-i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -o,--override  override ownership by another procedure");
    writer.println("    -i,--inputFiles  take one or more files of encoded region names");
    writer.println("    -b,--batchSize   number of regions to process in a batch");
    writer.println("   A 'raw' assign that can be used even during Master initialization (if");
    writer.println("   the -skip flag is specified). Skirts Coprocessors. Pass one or more");
    writer.println("   encoded region names. 1588230740 is the hard-coded name for the");
    writer.println("   hbase:meta region and de00010733901a05f5a2a3a382e27dd4 is an example of");
    writer.println("   what a user-space encoded region name looks like. For example:");
    writer.println("     $ HBCK2 " + ASSIGNS + " 1588230740 de00010733901a05f5a2a3a382e27dd4");
    writer.println("   Returns the pid(s) of the created AssignProcedure(s) or -1 if none.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains encoded region names, one per line. For example:");
    writer.println("     $ HBCK2 " + ASSIGNS + " -i fileName1 fileName2");
    writer.println("   If -b or --batchSize is specified, the command processes those many");
    writer.println("   regions at a time in a batch-ed manner; Consider using this option,");
    writer.println("   if the list of regions is huge, to avoid CallTimeoutException.");
    writer.println("   For example:");
    writer.println("     $ HBCK2 " + ASSIGNS + " -i fileName1 fileName2 -b 500");
    writer.println("   By default, batchSize is set to -1 i.e. no batching is done.");
  }

  private static void usageBypass(PrintWriter writer) {
    writer.println(" " + BYPASS + " [OPTIONS] [<PID>...|-i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -o,--override   override if procedure is running/stuck");
    writer.println("    -r,--recursive  bypass parent and its children. SLOW! EXPENSIVE!");
    writer.println("    -w,--lockWait   milliseconds to wait before giving up; default=1");
    writer.println("    -i,--inputFiles  take one or more input files of PID's");
    writer.println("    -b,--batchSize   number of procedures to process in a batch");
    writer.println("   Pass one (or more) procedure 'pid's to skip to procedure finish. Parent");
    writer.println("   of bypassed procedure will also be skipped to the finish. Entities will");
    writer.println("   be left in an inconsistent state and will require manual fixup. May");
    writer.println("   need Master restart to clear locks still held. Bypass fails if");
    writer.println("   procedure has children. Add 'recursive' if all you have is a parent pid");
    writer.println("   to finish parent and children. This is SLOW, and dangerous so use");
    writer.println("   selectively. Does not always work.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains PID's, one per line. For example:");
    writer.println("     $ HBCK2 " + BYPASS + " -i fileName1 fileName2");
    writer.println("   If -b or --batchSize is specified, the command processes those many");
    writer.println("   procedures at a time in a batch-ed manner; Consider using this option,");
    writer.println("   if the list of procedures is huge, to avoid CallTimeoutException.");
    writer.println("   For example:");
    writer.println("     $ HBCK2 " + BYPASS + " -i fileName1 fileName2 -b 500");
    writer.println("   By default, batchSize is set to -1 i.e. no batching is done.");
  }

  private static void usageFilesystem(PrintWriter writer) {
    writer.println(" " + FILESYSTEM + " [OPTIONS] [<TABLENAME>...|-i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -f, --fix    sideline corrupt hfiles, bad links, and references.");
    writer.println("    -i,--inputFiles  take one or more input files of table names");
    writer.println("   Report on corrupt hfiles, references, broken links, and integrity.");
    writer.println("   Pass '--fix' to sideline corrupt files and links. '--fix' does NOT");
    writer.println("   fix integrity issues; i.e. 'holes' or 'orphan' regions. Pass one or");
    writer.println("   more tablenames to narrow checkup. Default checks all tables and");
    writer.println("   restores 'hbase.version' if missing. Interacts with the filesystem");
    writer.println("   only! Modified regions need to be reopened to pick-up changes.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains table names, one per line. For example:");
    writer.println("     $ HBCK2 " + FILESYSTEM + " -i fileName1 fileName2");
  }

  private static void usageFixMeta(PrintWriter writer) {
    writer.println(" " + FIX_META);
    writer.println("   Do a server-side fix of bad or inconsistent state in hbase:meta.");
    writer.println("   Available in hbase 2.2.1/2.1.6 or newer versions. Master UI has");
    writer.println("   matching, new 'HBCK Report' tab that dumps reports generated by");
    writer.println("   most recent run of _catalogjanitor_ and a new 'HBCK Chore'. It");
    writer.println("   is critical that hbase:meta first be made healthy before making");
    writer.println("   any other repairs. Fixes 'holes', 'overlaps', etc., creating");
    writer.println("   (empty) region directories in HDFS to match regions added to");
    writer.println("   hbase:meta. Command is NOT the same as the old _hbck1_ command");
    writer.println("   named similarily. Works against the reports generated by the last");
    writer.println("   catalog_janitor and hbck chore runs. If nothing to fix, run is a");
    writer.println("   noop. Otherwise, if 'HBCK Report' UI reports problems, a run of");
    writer.println("   " + FIX_META + " will clear up hbase:meta issues. See 'HBase HBCK' UI");
    writer.println("   for how to generate new report.");
    writer.println("   SEE ALSO: " + REPORT_MISSING_REGIONS_IN_META);
  }

  private static void usageGenerateMissingTableInfo(PrintWriter writer) {
    writer.println(" " + GENERATE_TABLE_INFO + " [OPTIONS] [<TABLENAME>...]");
    writer.println("   Trying to fix an orphan table by generating a missing table descriptor");
    writer.println("   file. This command will have no effect if the table folder is missing");
    writer.println("   or if the .tableinfo is present (we don't override existing table");
    writer.println("   descriptors). This command will first check it the TableDescriptor is");
    writer.println("   cached in HBase Master in which case it will recover the .tableinfo");
    writer.println("   accordingly. If TableDescriptor is not cached in master then it will");
    writer.println("   create a default .tableinfo file with the following items:");
    writer.println("     - the table name");
    writer.println("     - the column family list determined based on the file system");
    writer.println("     - the default properties for both TableDescriptor and");
    writer.println("       ColumnFamilyDescriptors");
    writer.println("   If the .tableinfo file was generated using default parameters then");
    writer.println("   make sure you check the table / column family properties later (and");
    writer.println("   change them if needed).");
    writer.println("   This method does not change anything in HBase, only writes the new");
    writer.println("   .tableinfo file to the file system. Orphan tables can cause e.g.");
    writer.println("   ServerCrashProcedures to stuck, you might need to fix these still");
    writer.println("   after you generated the missing table info files. If no tables are ");
    writer.println("   specified, .tableinfo will be generated for all missing table ");
    writer.println("   descriptors.");
  }

  private static void usageReplication(PrintWriter writer) {
    writer.println(" " + REPLICATION + " [OPTIONS] [<TABLENAME>...|-i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -f, --fix    fix any replication issues found.");
    writer.println("    -i,--inputFiles  take one or more input files of table names");
    writer.println("   Looks for undeleted replication queues and deletes them if passed the");
    writer.println("   '--fix' option. Pass a table name to check for replication barrier and");
    writer.println("   purge if '--fix'.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains <TABLENAME>, one per line. For example:");
    writer.println("     $ HBCK2 " + REPLICATION + " -i fileName1 fileName2");
  }

  private static void usageExtraRegionsInMeta(PrintWriter writer) {
    writer.println(" " + EXTRA_REGIONS_IN_META + " [<NAMESPACE|NAMESPACE:TABLENAME>...|");
    writer.println("      -i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -f, --fix    fix meta by removing all extra regions found.");
    writer.println("    -i,--inputFiles  take one or more input files of namespace or");
    writer.println("   table names");
    writer.println("   Reports regions present on hbase:meta, but with no related ");
    writer.println("   directories on the file system. Needs hbase:meta to be online. ");
    writer.println("   For each table name passed as parameter, performs diff");
    writer.println("   between regions available in hbase:meta and region dirs on the given");
    writer.println("   file system. Extra regions would get deleted from Meta ");
    writer.println("   if passed the --fix option. ");
    writer.println("   NOTE: Before deciding on use the \"--fix\" option, it's worth check if");
    writer.println("   reported extra regions are overlapping with existing valid regions.");
    writer.println("   If so, then \"extraRegionsInMeta --fix\" is indeed the optimal solution. ");
    writer.println("   Otherwise, \"assigns\" command is the simpler solution, as it recreates ");
    writer.println("   regions dirs in the filesystem, if not existing.");
    writer.println("   An example triggering extra regions report for tables 'table_1'");
    writer.println("   and 'table_2', under default namespace:");
    writer.println("     $ HBCK2 " + EXTRA_REGIONS_IN_META + " default:table_1 default:table_2");
    writer.println("   An example triggering extra regions report for table 'table_1'");
    writer.println("   under default namespace, and for all tables from namespace 'ns1':");
    writer.println("     $ HBCK2 " + EXTRA_REGIONS_IN_META + " default:table_1 ns1");
    writer.println("   Returns list of extra regions for each table passed as parameter, or");
    writer.println("   for each table on namespaces specified as parameter.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains <NAMESPACE|NAMESPACE:TABLENAME>, one per line.");
    writer.println("   For example:");
    writer.println("     $ HBCK2 " + EXTRA_REGIONS_IN_META + " -i fileName1 fileName2");
  }

  private static void usageReportMissingRegionsInMeta(PrintWriter writer) {
    writer.println(" " + REPORT_MISSING_REGIONS_IN_META + " [<NAMESPACE|NAMESPACE:TABLENAME>...|");
    writer.println("      -i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -i,--inputFiles  take one or more files of namespace or table names");
    writer.println("   To be used when regions missing from hbase:meta but directories");
    writer.println("   are present still in HDFS. Can happen if user has run _hbck1_");
    writer.println("   'OfflineMetaRepair' against an hbase-2.x cluster. This is a CHECK only");
    writer.println("   method, designed for reporting purposes and doesn't perform any");
    writer.println("   fixes, providing a view of which regions (if any) would get re-added");
    writer.println("   to hbase:meta, grouped by respective table/namespace. To effectively");
    writer
      .println("   re-add regions in meta, run " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + ".");
    writer.println("   This command needs hbase:meta to be online. For each namespace/table");
    writer.println("   passed as parameter, it performs a diff between regions available in");
    writer.println("   hbase:meta against existing regions dirs on HDFS. Region dirs with no");
    writer.println("   matches are printed grouped under its related table name. Tables with");
    writer.println("   no missing regions will show a 'no missing regions' message. If no");
    writer.println("   namespace or table is specified, it will verify all existing regions.");
    writer.println("   It accepts a combination of multiple namespace and tables. Table names");
    writer.println("   should include the namespace portion, even for tables in the default");
    writer.println("   namespace, otherwise it will assume as a namespace value.");
    writer.println("   An example triggering missing regions report for tables 'table_1'");
    writer.println("   and 'table_2', under default namespace:");
    writer.println("     $ HBCK2 reportMissingRegionsInMeta default:table_1 default:table_2");
    writer.println("   An example triggering missing regions report for table 'table_1'");
    writer.println("   under default namespace, and for all tables from namespace 'ns1':");
    writer.println("     $ HBCK2 reportMissingRegionsInMeta default:table_1 ns1");
    writer.println("   Returns list of missing regions for each table passed as parameter, or");
    writer.println("   for each table on namespaces specified as parameter.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains <NAMESPACE|NAMESPACE:TABLENAME>, one per line.");
    writer.println("   For example:");
    writer.println("     $ HBCK2 " + REPORT_MISSING_REGIONS_IN_META + " -i fileName1 fileName2");
  }

  private static void usageSetRegionState(PrintWriter writer) {
    writer.println(" " + SET_REGION_STATE + " [<ENCODED_REGIONNAME> <STATE>|-i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -i,--inputFiles  take one or more input files of encoded region names ");
    writer.println("   and states.");
    writer.println("   To set the replica region's state, it needs the primary region's ");
    writer.println("   encoded regionname and replica id. The command will be ");
    writer.println("   " + SET_REGION_STATE + " <PRIMARY_ENCODED_REGIONNAME>,<replicaId> <STATE>");
    writer.println("   Possible region states:");
    writer.println("    OFFLINE, OPENING, OPEN, CLOSING, CLOSED, SPLITTING, SPLIT,");
    writer.println("    FAILED_OPEN, FAILED_CLOSE, MERGING, MERGED, SPLITTING_NEW,");
    writer.println("    MERGING_NEW, ABNORMALLY_CLOSED");
    writer.println("   WARNING: This is a very risky option intended for use as last resort.");
    writer.println("   Example scenarios include unassigns/assigns that can't move forward");
    writer.println("   because region is in an inconsistent state in 'hbase:meta'. For");
    writer.println("   example, the 'unassigns' command can only proceed if passed a region");
    writer.println("   in one of the following states: SPLITTING|SPLIT|MERGING|OPEN|CLOSING");
    writer.println("   Before manually setting a region state with this command, please");
    writer.println("   certify that this region is not being handled by a running procedure,");
    writer.println("   such as 'assign' or 'split'. You can get a view of running procedures");
    writer.println("   in the hbase shell using the 'list_procedures' command. An example");
    writer.println("   setting region 'de00010733901a05f5a2a3a382e27dd4' to CLOSING:");
    writer.println("     $ HBCK2 setRegionState de00010733901a05f5a2a3a382e27dd4 CLOSING");
    writer.println("   Returns \"0\" if region state changed and \"1\" otherwise.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains <ENCODED_REGIONNAME> <STATE>, one pair per line.");
    writer.println("   For example:");
    writer.println("     $ HBCK2 " + SET_REGION_STATE + " -i fileName1 fileName2");
  }

  private static void usageSetTableState(PrintWriter writer) {
    writer.println(" " + SET_TABLE_STATE + " [<TABLENAME> <STATE>|-i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -i,--inputFiles  take one or more files of table names and states");
    writer.println("   Possible table states: " + Arrays.stream(TableState.State.values())
      .map(Enum::toString).collect(Collectors.joining(", ")));
    writer.println("   To read current table state, in the hbase shell run:");
    writer.println("     hbase> get 'hbase:meta', '<TABLENAME>', 'table:state'");
    writer.println("   A value of \\x08\\x00 == ENABLED, \\x08\\x01 == DISABLED, etc.");
    writer.println("   Can also run a 'describe \"<TABLENAME>\"' at the shell prompt.");
    writer.println("   An example making table name 'user' ENABLED:");
    writer.println("     $ HBCK2 setTableState users ENABLED");
    writer.println("   Returns whatever the previous table state was.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains <TABLENAME> <STATE>, one pair per line.");
    writer.println("   For example:");
    writer.println("     $ HBCK2 " + SET_TABLE_STATE + " -i fileName1 fileName2");
  }

  private static void usageScheduleRecoveries(PrintWriter writer) {
    writer.println(" " + SCHEDULE_RECOVERIES + " [<SERVERNAME>...|-i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -i,--inputFiles  take one or more input files of server names");
    writer.println("   Schedule ServerCrashProcedure(SCP) for list of RegionServers. Format");
    writer.println("   server name as '<HOSTNAME>,<PORT>,<STARTCODE>' (See HBase UI/logs).");
    writer.println("   Example using RegionServer 'a.example.org,29100,1540348649479':");
    writer.println("     $ HBCK2 scheduleRecoveries a.example.org,29100,1540348649479");
    writer.println("   Returns the pid(s) of the created ServerCrashProcedure(s) or -1 if");
    writer.println("   no procedure created (see master logs for why not).");
    writer.println("   Command support added in hbase versions 2.0.3, 2.1.2, 2.2.0 or newer.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains <SERVERNAME>, one per line. For example:");
    writer.println("     $ HBCK2 " + SCHEDULE_RECOVERIES + " -i fileName1 fileName2");
  }

  private static void usageRecoverUnknown(PrintWriter writer) {
    writer.println(" " + RECOVER_UNKNOWN);
    writer.println("   Schedule ServerCrashProcedure(SCP) for RegionServers that are reported");
    writer.println("   as unknown.");
    writer.println("   Returns the pid(s) of the created ServerCrashProcedure(s) or -1 if");
    writer.println("   no procedure created (see master logs for why not).");
    writer.println("   Command support added in hbase versions 2.2.7, 2.3.5, 2.4.3,");
    writer.println("     2.5.0 or newer.");
  }

  private static void usageUnassigns(PrintWriter writer) {
    writer.println(" " + UNASSIGNS + " [OPTIONS] [<ENCODED_REGIONNAME>...|-i <INPUT_FILE>...]");
    writer.println("   Options:");
    writer.println("    -o,--override  override ownership by another procedure");
    writer.println("    -i,--inputFiles  take one or more input files of encoded region names");
    writer.println("    -b,--batchSize   number of regions to process in a batch");
    writer.println("   A 'raw' unassign that can be used even during Master initialization");
    writer.println("   (if the -skip flag is specified). Skirts Coprocessors. Pass one or");
    writer.println("   more encoded region names. 1588230740 is the hard-coded name for the");
    writer.println("   hbase:meta region and de00010733901a05f5a2a3a382e27dd4 is an example");
    writer.println("   of what a userspace encoded region name looks like. For example:");
    writer.println("     $ HBCK2 " + UNASSIGNS + " 1588230740 de00010733901a05f5a2a3a382e27dd4");
    writer.println("   Returns the pid(s) of the created UnassignProcedure(s) or -1 if none.");
    writer.println();
    writer.println("   SEE ALSO, org.apache.hbase.hbck1.OfflineMetaRepair, the offline");
    writer.println("   hbase:meta tool. See the HBCK2 README for how to use.");
    writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
    writer.println("   Each file contains encoded region names, one per line. For example:");
    writer.println("     $ HBCK2 " + UNASSIGNS + " -i fileName1 fileName2");
    writer.println("   If -b or --batchSize is specified, the tool processes those many");
    writer.println("   regions at a time in a batch-ed manner; Consider using this option,");
    writer.println("   if the list of regions is huge, to avoid CallTimeoutException.");
    writer.println("   For example:");
    writer.println("     $ HBCK2 " + UNASSIGNS + " -i fileName1 fileName2 -b 500");
    writer.println("   By default, batchSize is set to -1 i.e. no batching is done.");
  }

  private static void usageRegioninfoMismatch(PrintWriter writer) {
    writer.println(" " + REGIONINFO_MISMATCH);
    writer.println("   Options:");
    writer.println("   -f,--fix Update hbase:meta with the corrections");
    writer.println("   It is recommended to first run this utility without the fix");
    writer.println("   option to ensure that the utility is generating the correct");
    writer.println("   serialized RegionInfo data structures. Inspect the output to");
    writer.println("   confirm that the hbase:meta rowkey matches the new RegionInfo.");
    writer.println();
    writer.println("   This tool will read hbase:meta and report any regions whose rowkey");
    writer.println("   and cell value differ in their encoded region name. HBASE-23328 ");
    writer.println("   illustrates a problem for read-replica enabled tables in which ");
    writer.println("   the encoded region name (the MD5 hash) does not match between ");
    writer.println("   the rowkey and the value. This problem is generally harmless ");
    writer.println("   for normal operation, but can break other HBCK2 tools.");
    writer.println();
    writer.println("   Run this command to determine if any regions are affected by ");
    writer.println("   this bug and use the -f/--fix option to then correct any");
    writer.println("   affected regions.");
  }

  static void showErrorMessage(String error) {
    if (error != null) {
      System.out.println("ERROR: " + error);
      System.out.println("FOR USAGE, use the -h or --help option");
    }
  }

  static void showUsage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("HBCK2 [OPTIONS] COMMAND <ARGS>", "Options:", options, getCommandUsage());
  }

  @Override
  public void setConf(Configuration configuration) {
    this.conf = configuration;
  }

  @Override
  public Configuration getConf() {
    return this.conf;
  }

  /**
   * Process command line general options.
   */
  @Override
  public int run(String[] args) throws IOException {
    // Configure Options. The below article was more helpful than the commons-cli doc:
    // https://dzone.com/articles/java-command-line-interfaces-part-1-apache-commons
    Options options = new Options();
    Option help = Option.builder("h").longOpt("help").desc("output this help message").build();
    options.addOption(help);
    Option debug = Option.builder("d").longOpt("debug").desc("run with debug output").build();
    options.addOption(debug);
    Option quorum = Option.builder("q").longOpt(HConstants.ZOOKEEPER_QUORUM).hasArg()
      .desc("hbase ensemble").build();
    options.addOption(quorum);
    Option parent = Option.builder("z").longOpt(HConstants.ZOOKEEPER_ZNODE_PARENT).hasArg()
      .desc("parent znode of hbase ensemble").build();
    options.addOption(parent);
    Option peerPort = Option.builder("p").longOpt(HConstants.ZOOKEEPER_CLIENT_PORT).hasArg()
      .desc("port of hbase ensemble").type(Integer.class).build();
    options.addOption(peerPort);
    Option version = Option.builder("v").longOpt(VERSION).desc("this hbck2 version").build();
    options.addOption(version);
    Option skip = Option.builder("s").longOpt("skip")
      .desc("skip hbase version check (PleaseHoldException)").build();
    options.addOption(skip);

    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine;
    try {
      commandLine = parser.parse(options, args, true);
    } catch (ParseException e) {
      showErrorMessage(e.getMessage());
      return EXIT_FAILURE;
    }
    // Process general options.
    if (commandLine.hasOption(version.getOpt())) {
      System.out.println(readHBCK2BuildProperties(VERSION));
      return EXIT_SUCCESS;
    }
    if (commandLine.hasOption(help.getOpt()) || commandLine.getArgList().isEmpty()) {
      showUsage(options);
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
      String optionValue = commandLine.getOptionValue(peerPort.getOpt());
      if (optionValue.matches("[0-9]+")) {
        getConf().setInt(HConstants.ZOOKEEPER_CLIENT_PORT, Integer.parseInt(optionValue));
      } else {
        showErrorMessage(
          "Invalid client port. Please provide proper port for target hbase ensemble.");
        return EXIT_FAILURE;
      }
    }
    if (commandLine.hasOption(parent.getOpt())) {
      String optionValue = commandLine.getOptionValue(parent.getOpt());
      if (optionValue.startsWith("/")) {
        getConf().set(HConstants.ZOOKEEPER_ZNODE_PARENT, optionValue);
      } else {
        showErrorMessage("Invalid parent znode. Please provide proper parent znode of target hbase."
          + " Note that valid znodes must start with \"/\".");
        return EXIT_FAILURE;
      }
    }
    if (commandLine.hasOption(skip.getOpt())) {
      skipCheck = true;
    }
    return doCommandLine(commandLine, options);
  }

  /**
   * Create connection. Needs to be called before we go against remote server. Be sure to close when
   * done.
   */
  ClusterConnection connect() throws IOException {
    return (ClusterConnection) ConnectionFactory.createConnection(getConf());
  }

  /**
   * Process parsed command-line. General options have already been processed by caller.
   */
  @SuppressWarnings("checkstyle:methodlength")
  private int doCommandLine(CommandLine commandLine, Options options) throws IOException {
    // Now process command.
    String[] commands = commandLine.getArgs();
    String command = commands[0];
    switch (command) {
      // Case handlers all have same format. Check first that the server supports
      // the feature FIRST, then move to process the command.
      case SET_TABLE_STATE:
        if (commands.length < 2) {
          showErrorMessage(command
            + " takes tablename and state arguments: e.g. user ENABLED, or a list of input files");
          return EXIT_FAILURE;
        }
        try (ClusterConnection connection = connect(); Hbck hbck = connection.getHbck()) {
          checkFunctionSupported(connection, command);
          setTableState(hbck, purgeFirst(commands));
        }
        break;

      case ASSIGNS:
        if (commands.length < 2) {
          showErrorMessage(command + " takes one or more encoded region names");
          return EXIT_FAILURE;
        }
        try (ClusterConnection connection = connect(); Hbck hbck = connection.getHbck()) {
          checkFunctionSupported(connection, command);
          System.out.println(assigns(hbck, purgeFirst(commands)));
        }
        break;

      case BYPASS:
        if (commands.length < 2) {
          showErrorMessage(command + " takes one or more pids");
          return EXIT_FAILURE;
        }
        // bypass does the connection setup and the checkFunctionSupported down
        // inside in the bypass method delaying connection setup until last
        // moment. It does this because it has another set of command options
        // to process and wants to do that before setting up connection.
        // This is why it is not like the other command processings.
        List<Boolean> bs = bypass(purgeFirst(commands));
        if (bs == null) {
          // Something went wrong w/ the parse and command didn't run.
          return EXIT_FAILURE;
        }
        System.out.println(toString(bs));
        break;

      case UNASSIGNS:
        if (commands.length < 2) {
          showErrorMessage(command + " takes one or more encoded region names");
          return EXIT_FAILURE;
        }
        try (ClusterConnection connection = connect(); Hbck hbck = connection.getHbck()) {
          checkFunctionSupported(connection, command);
          System.out.println(toString(unassigns(hbck, purgeFirst(commands))));
        }
        break;

      case SET_REGION_STATE:
        if (commands.length < 2) {
          showErrorMessage(command + " takes region encoded name and state arguments: e.g. "
            + "35f30b0ce922c34bf5c284eff33ba8b3 CLOSING, or a list of input files");
          return EXIT_FAILURE;
        }

        try (ClusterConnection connection = connect()) {
          checkHBCKSupport(connection, command);
          return setRegionState(connection, purgeFirst(commands));
        }

      case FILESYSTEM:
        try (ClusterConnection connection = connect()) {
          checkHBCKSupport(connection, command);
          try (FileSystemFsck fsfsck = new FileSystemFsck(getConf())) {
            Pair<CommandLine, List<String>> pair =
              parseCommandWithFixAndInputOptions(purgeFirst(commands));
            return fsfsck.fsck(pair.getSecond(), pair.getFirst().hasOption("f")) != 0
              ? EXIT_FAILURE
              : EXIT_SUCCESS;
          }
        }

      case REPLICATION:
        try (ClusterConnection connection = connect()) {
          checkHBCKSupport(connection, command, "2.1.1", "2.2.0", "3.0.0");
          try (ReplicationFsck replicationFsck = new ReplicationFsck(getConf())) {
            Pair<CommandLine, List<String>> pair =
              parseCommandWithFixAndInputOptions(purgeFirst(commands));
            return replicationFsck.fsck(pair.getSecond(), pair.getFirst().hasOption("f")) != 0
              ? EXIT_FAILURE
              : EXIT_SUCCESS;
          }
        }

      case SCHEDULE_RECOVERIES:
        if (commands.length < 2) {
          showErrorMessage(command + " takes one or more serverNames");
          return EXIT_FAILURE;
        }
        try (ClusterConnection connection = connect(); Hbck hbck = connection.getHbck()) {
          checkFunctionSupported(connection, command);
          System.out.println(toString(scheduleRecoveries(hbck, purgeFirst(commands))));
        }
        break;

      case RECOVER_UNKNOWN:
        if (commands.length > 1) {
          showErrorMessage(command + " doesn't take any arguments");
          return EXIT_FAILURE;
        }
        try (ClusterConnection connection = connect(); Hbck hbck = connection.getHbck()) {
          checkFunctionSupported(connection, command);
          System.out.println(toString(recoverUnknown(hbck)));
        }
        break;

      case FIX_META:
        if (commands.length > 1) {
          showErrorMessage(command + " doesn't take any arguments");
          return EXIT_FAILURE;
        }
        try (ClusterConnection connection = connect(); Hbck hbck = connection.getHbck()) {
          checkFunctionSupported(connection, command);
          hbck.fixMeta();
          System.out.println("Server-side processing of fixMeta triggered.");
        }
        break;

      case ADD_MISSING_REGIONS_IN_META_FOR_TABLES:
        if (commands.length < 2) {
          showErrorMessage(command + " takes one or more table names.");
          return EXIT_FAILURE;
        }

        try {
          Pair<List<String>, List<Exception>> result =
            addMissingRegionsInMetaForTablesWrapper(purgeFirst(commands));
          System.out.println(formatReAddedRegionsMessage(result.getFirst(), result.getSecond()));
        } catch (Exception e) {
          return EXIT_FAILURE;
        }
        break;

      case REPORT_MISSING_REGIONS_IN_META:
        try {
          Map<TableName, List<Path>> report =
            reportTablesWithMissingRegionsInMeta(purgeFirst(commands));
          System.out.println(formatMissingRegionsInMetaReport(report));
        } catch (Exception e) {
          return EXIT_FAILURE;
        }
        break;

      case EXTRA_REGIONS_IN_META:
        try {
          Map<TableName, List<String>> report = extraRegionsInMeta(purgeFirst(commands));
          System.out.println(formatExtraRegionsReport(report));
        } catch (Exception e) {
          return EXIT_FAILURE;
        }
        break;

      case GENERATE_TABLE_INFO:
        List<String> tableNames = Arrays.asList(purgeFirst(commands));
        MissingTableDescriptorGenerator tableInfoGenerator =
          new MissingTableDescriptorGenerator(getConf());
        try (ClusterConnection connection = connect()) {
          tableInfoGenerator.generateTableDescriptorFileIfMissing(connection.getAdmin(),
            tableNames);
        } catch (IOException e) {
          showErrorMessage(e.getMessage());
          return EXIT_FAILURE;
        }
        break;

      case REGIONINFO_MISMATCH:
        // `commands` includes the `regionInfoMismatch` argument.
        if (commands.length > 2) {
          showErrorMessage(command + " takes one optional argument, got more than one.");
          return EXIT_FAILURE;
        }
        try {
          regionInfoMismatch(commands);
        } catch (Exception e) {
          e.printStackTrace();
          return EXIT_FAILURE;
        }
        break;

      default:
        showErrorMessage("Unsupported command: " + command);
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
  }

  private static String toString(List<?> things) {
    return things.stream().map(Object::toString).collect(Collectors.joining(", "));
  }

  private String formatMissingRegionsInMetaReport(Map<TableName, List<Path>> report) {
    Function<Path, String> resolver = r -> r.getName();
    String message = "Missing Regions for each table:\n\t";
    return formatReportMessage(message, (HashMap) report, resolver);
  }

  private String formatExtraRegionsReport(Map<TableName, List<String>> report) {
    String message = "Regions in Meta but having no equivalent dir, for each table:\n\t";
    return formatReportMessage(message, (HashMap) report, s -> s);
  }

  private String formatReportMessage(String reportMessage, Map<TableName, List<?>> report,
    Function resolver) {
    final StringBuilder builder = new StringBuilder();
    if (report.size() < 1) {
      builder.append("\nNo reports were found. You are likely passing non-existent "
        + "namespace or table. Note that table names should include the namespace "
        + "portion even for tables in the default namespace. See also the command usage.\n");
      return builder.toString();
    }
    builder.append(reportMessage);
    report.keySet().forEach(table -> {
      builder.append(table);
      if (!report.get(table).isEmpty()) {
        builder.append("->\n\t\t");
        report.get(table).forEach(region -> builder.append(resolver.apply(region)).append(" "));
      } else {
        builder.append(" -> No mismatching regions. This table is good!");
      }
      builder.append("\n\t");
    });
    return builder.toString();
  }

  private String formatReAddedRegionsMessage(List<String> readdedRegionNames,
    List<Exception> executionErrors) {
    final StringBuilder finalText = new StringBuilder();
    finalText.append("Regions re-added into Meta: ").append(readdedRegionNames.size());
    if (!readdedRegionNames.isEmpty()) {
      finalText.append("\n").append("WARNING: \n\t").append(readdedRegionNames.size())
        .append(" regions were added ")
        .append("to META, but these are not yet on Masters cache. \n")
        .append("You need to restart Masters, then run hbck2 'assigns' command below:\n\t\t")
        .append(buildHbck2AssignsCommand(readdedRegionNames));
    }
    if (!executionErrors.isEmpty()) {
      finalText.append("\n").append("ERROR: \n\t")
        .append("There were following errors on at least one table thread:\n");
      executionErrors.forEach(e -> finalText.append(e.getMessage()).append("\n"));
    }
    return finalText.toString();
  }

  private String formatRemovedRegionsMessage(int totalRemoved, List<Exception> executionErrors) {
    final StringBuilder finalText = new StringBuilder();
    finalText.append("Regions that had no dir on the FileSystem and got removed from Meta: ")
      .append(totalRemoved);
    if (!executionErrors.isEmpty()) {
      finalText.append("\n").append("ERROR: \n\t")
        .append("There were following errors on at least one table thread:\n");
      executionErrors.forEach(e -> finalText.append(e.getMessage()).append("\n"));
    }
    return finalText.toString();
  }

  private String buildHbck2AssignsCommand(List<String> regions) {
    final StringBuilder builder = new StringBuilder();
    builder.append("assigns ");
    regions.forEach(region -> builder.append(region).append(" "));
    return builder.toString();
  }

  /** Returns A new array with first element dropped. */
  private static String[] purgeFirst(String[] args) {
    int size = args.length;
    if (size <= 1) {
      return new String[] {};
    }
    size--;
    String[] result = new String[size];
    System.arraycopy(args, 1, result, 0, size);
    return result;
  }

  /** Returns arguements for SET_REGION_STATE command */
  private String[] formatSetRegionStateCommand(String[] commands) {
    if (commands.length < 2) {
      showErrorMessage("setRegionState takes region encoded name and state arguments: e.g. "
        + "35f30b0ce922c34bf5c284eff33ba8b3 CLOSING");
      return null;
    }
    RegionState.State state = RegionState.State.valueOf(commands[1]);
    Integer replicaId = 0;
    String region = commands[0];
    int separatorIndex = commands[0].indexOf(",");
    if (separatorIndex > 0) {
      region = commands[0].substring(0, separatorIndex);
      replicaId = Integer.getInteger(commands[0].substring(separatorIndex + 1));
    }
    if (replicaId > 0) {
      System.out
        .println("Change state for replica region " + replicaId + " for primary region " + region);
    }

    return new String[] { region, replicaId.toString(), state.name() };
  }

  HBCK2(Configuration conf) {
    super(conf);
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = HBaseConfiguration.create();
    int errCode = org.apache.hadoop.util.ToolRunner.run(new HBCK2(conf), args);
    if (errCode != 0) {
      System.exit(errCode);
    }
  }

  private List<String> stringArrayToList(String... nameSpaceOrTable) {
    return nameSpaceOrTable != null ? Arrays.asList(nameSpaceOrTable) : null;
  }

  /**
   * Get list of input if no other options
   * @param args Array of arguments
   * @return the list of input from arguments or parsed from input files
   */
  private List<String> getInputList(String[] args) throws IOException {
    CommandLine commandLine = parseCommandWithInputList(args, null);
    if (commandLine == null) {
      return null;
    }
    return getFromArgsOrFiles(commandLine.getArgList(), commandLine.hasOption("i"));
  }

  private CommandLine parseCommandWithInputList(String[] args, Options options) {
    if (args == null) {
      return null;
    }
    if (options == null) {
      options = new Options();
    }
    Option inputFile = Option.builder("i").longOpt("inputFiles").build();
    options.addOption(inputFile);
    return getCommandLine(args, options);
  }

  private Pair<CommandLine, List<String>> parseCommandWithFixAndInputOptions(String[] args)
    throws IOException {
    Options options = new Options();
    Option fixOption = Option.builder("f").longOpt("fix").build();
    options.addOption(fixOption);
    CommandLine commandLine = parseCommandWithInputList(args, options);
    List<String> params = getFromArgsOrFiles(commandLine.getArgList(), commandLine.hasOption("i"));
    return Pair.newPair(commandLine, params);
  }

  /**
   * Get a commandLine object with options and a arg list
   */
  private CommandLine getCommandLine(String[] args, Options options) {
    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine;
    try {
      commandLine = parser.parse(options, args, false);
    } catch (ParseException e) {
      showErrorMessage(e.getMessage());
      return null;
    }
    return commandLine;
  }

  /** Returns Read arguments from args or a list of input files */
  private List<String> getFromArgsOrFiles(List<String> args, boolean getFromFile)
    throws IOException {
    if (!getFromFile || args == null) {
      return args;
    }
    return getFromFiles(args);
  }

  /** Returns Read arguments from a list of input files */
  private List<String> getFromFiles(List<String> args) throws IOException {
    List<String> argList = new ArrayList<>();
    for (String filePath : args) {
      try (InputStream fileStream = new FileInputStream(filePath)) {
        LineIterator it = IOUtils.lineIterator(fileStream, "UTF-8");
        while (it.hasNext()) {
          argList.add(it.nextLine().trim());
        }
      }
    }
    return argList;
  }

  static int getBatchSize(Option batchOpt, CommandLine commandLine)
    throws IllegalArgumentException {
    int batchSize = DEFAULT_BATCH_SIZE;
    try {
      if (commandLine.hasOption(batchOpt.getOpt())) {
        batchSize = Integer.parseInt(commandLine.getOptionValue(batchOpt.getOpt()));
        if (batchSize <= 0) {
          throw new IllegalArgumentException("Batch size should be greater than 0!");
        }
      }
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Batch size should be an integer!");
    }
    LOG.info("Batch size set to: " + batchSize);
    return batchSize;
  }

}
