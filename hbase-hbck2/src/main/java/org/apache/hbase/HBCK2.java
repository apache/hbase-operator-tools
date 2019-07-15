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
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Hbck;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.hbase.filter.RowFilter;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.master.RegionState;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.util.VersionInfo;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hbase.hbck2.meta.MetaFixer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * HBase fixup tool version 2, for hbase-2.0.0+ clusters.
 * Supercedes hbck1.
 */
// TODO:
// + On assign, can we look to see if existing assign and if so fail until cancelled?
// + Add test of Master version to ensure it supports hbck functionality.
// ++ Hard. Server doesn't volunteer its version. Need to read the status? HBASE-20225
// + Doc how we just take pointer to zk ensemble... If want to do more exotic config. on client,
// then add a hbase-site.xml onto CLASSPATH for this tool to pick up.
// + Add --version
// + Add emitting what is supported against remote server?
public class HBCK2 extends Configured implements Tool {
  private static final Logger LOG = LogManager.getLogger(HBCK2.class);
  public static final int EXIT_SUCCESS = 0;
  public static final int EXIT_FAILURE = 1;
  // Commands
  private static final String SET_TABLE_STATE = "setTableState";
  private static final String ASSIGNS = "assigns";
  private static final String UNASSIGNS = "unassigns";
  private static final String BYPASS = "bypass";
  private static final String VERSION = "version";
  private static final String SET_REGION_STATE = "setRegionState";
  private static final String ADD_MISSING_REGIONS_IN_META_FOR_TABLES =
    "addMissingRegionsInMetaForTables";
  private static final String ADD_MISSING_REGIONS_IN_META = "addMissingRegionsInMeta";
  private static final String REPORT_MISSING_REGIONS_IN_META = "reportMissingRegionsInMeta";
  private Configuration conf;
  private static final String TWO_POINT_ONE = "2.1.0";
  private static final String MININUM_VERSION = "2.0.3";
  private boolean skipCheck = false;
  /**
   * Wait 1ms on lock by default.
   */
  private static final long DEFAULT_LOCK_WAIT = 1;

  /**
   * Check for HBCK support.
   */
  void checkHBCKSupport(Connection connection) throws IOException {
    if(skipCheck){
      LOG.info("hbck support check skipped");
      return;
    }
    try (Admin admin = connection.getAdmin()) {
      checkVersion(admin.getClusterMetrics(EnumSet.of(ClusterMetrics.Option.HBASE_VERSION)).
          getHBaseVersion());
    }
  }

  static void checkVersion(final String versionStr) {
    if (VersionInfo.compareVersion(MININUM_VERSION, versionStr) > 0) {
      throw new UnsupportedOperationException("Requires " + MININUM_VERSION + " at least.");
    }
    // except 2.1.0 didn't ship with support
    if (VersionInfo.compareVersion(TWO_POINT_ONE, versionStr) == 0) {
      throw new UnsupportedOperationException(TWO_POINT_ONE + " has no support for hbck2");
    }
  }

  TableState setTableState(TableName tableName, TableState.State state) throws IOException {
    try (ClusterConnection conn =
             (ClusterConnection) ConnectionFactory.createConnection(getConf())) {
      checkHBCKSupport(conn);
      try (Hbck hbck = conn.getHbck()) {
        return hbck.setTableStateInMeta(new TableState(tableName, state));
      }
    }
  }

  int setRegionState(String region, RegionState.State newState)
      throws IOException {
    if(newState==null){
      throw new IllegalArgumentException("State can't be null.");
    }
    try(Connection connection = ConnectionFactory.createConnection(getConf())){
      RegionState.State currentState = null;
      Table table = connection.getTable(TableName.valueOf("hbase:meta"));
      RowFilter filter = new RowFilter(CompareOperator.EQUAL, new SubstringComparator(region));
      Scan scan = new Scan();
      scan.setFilter(filter);
      Result result = table.getScanner(scan).next();
      if(result!=null){
        byte[] currentStateValue = result.getValue(HConstants.CATALOG_FAMILY,
          HConstants.STATE_QUALIFIER);
        if(currentStateValue==null){
          System.out.println("WARN: Region state info on meta was NULL");
        }else {
          currentState = RegionState.State.valueOf(Bytes.toString(currentStateValue));
        }
        Put put = new Put(result.getRow());
        put.addColumn(HConstants.CATALOG_FAMILY, HConstants.STATE_QUALIFIER,
          Bytes.toBytes(newState.name()));
        table.put(put);
        System.out.println("Changed region " + region + " STATE from "
          + currentState + " to " + newState);
        return EXIT_SUCCESS;
      } else {
        System.out.println("ERROR: Could not find region " + region + " in meta.");
      }
    }
    return EXIT_FAILURE;
  }

  Map<TableName,List<Path>> reportTablesWithMissingRegionsInMeta(String... nameSpaceOrTable)
      throws Exception {
    Map<TableName,List<Path>> report;
    try(final MetaFixer metaFixer = new MetaFixer(this.conf)){
      List<String> names = nameSpaceOrTable != null ? Arrays.asList(nameSpaceOrTable) : null;
      report = metaFixer.reportTablesMissingRegions(names);
    } catch (Exception e) {
      LOG.error("Error reporting missing regions: ", e);
      throw e;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug(formatMissingRegionsInMetaReport(report));
    }
    return report;
  }

  List<String> addMissingRegionsInMeta(List<Path> regionsPath) throws IOException {
    List<String> reAddedRegionsEncodedNames = new ArrayList<>();
    try(final MetaFixer metaFixer = new MetaFixer(this.conf)){
      for(Path regionPath : regionsPath){
        metaFixer.putRegionInfoFromHdfsInMeta(regionPath);
        reAddedRegionsEncodedNames.add(regionPath.getName());
      }
    }
    return reAddedRegionsEncodedNames;
  }

  Pair<List<String>, List<ExecutionException>> addMissingRegionsInMetaForTables(String...
      nameSpaceOrTable) {
    ExecutorService executorService = Executors.newFixedThreadPool(
      (nameSpaceOrTable == null ||
        nameSpaceOrTable.length > Runtime.getRuntime().availableProcessors()) ?
          Runtime.getRuntime().availableProcessors() :
          nameSpaceOrTable.length);
    List<Future<List<String>>> futures = new ArrayList<>( nameSpaceOrTable == null ? 1 :
      nameSpaceOrTable.length);
    final List<String> readdedRegionNames = new ArrayList<>();
    List<ExecutionException> executionErrors = new ArrayList<>();
    try {
      //reducing number of retries in case disable fails due to namespace table region also missing
      this.conf.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 1);
      try(Connection conn = ConnectionFactory.createConnection(this.conf);
        final Admin admin = conn.getAdmin()) {
        Map<TableName,List<Path>> report = reportTablesWithMissingRegionsInMeta(nameSpaceOrTable);
        for (TableName tableName : report.keySet()) {
          if(admin.tableExists(tableName)) {
            futures.add(executorService.submit(new Callable<List<String>>() {
              @Override
              public List<String> call() throws Exception {
                LOG.debug("running thread for {}", tableName.getNameWithNamespaceInclAsString());
                try {
                  admin.disableTable(tableName);
                } catch (IOException e) {
                  LOG.debug("Failed to disable table {}, "
                      + "is namespace table also missing regions? Continue anyway...",
                    tableName.getNameWithNamespaceInclAsString(), e);
                }
                List<String> reAddedRegions = addMissingRegionsInMeta(report.get(tableName));
                try {
                  admin.enableTable(tableName);
                } catch (IOException e) {
                  LOG.debug("Failed enabling table {}. It might be that namespace table "
                      + "region is also missing.\n"
                      + "After this command finishes, please make sure on this table state.",
                    tableName.getNameWithNamespaceInclAsString(), e);
                }
                return reAddedRegions;
              }
            }));
          } else {
            LOG.warn("Table {} does not exist! Skipping...",
              tableName.getNameWithNamespaceInclAsString());
          }
        }
        for(Future<List<String>> f : futures){
          try {
            readdedRegionNames.addAll(f.get());
          } catch (ExecutionException e){
            //we want to allow potential running threads to finish, so we collect execution
            //errors and show those later
            LOG.debug("Caught execution error: ", e);
            executionErrors.add(e);
          }
        }
      }
    } catch (Exception ie){
      LOG.error("ERROR executing thread: ", ie);
    } finally {
      executorService.shutdown();
    }
    Pair<List<String>, List<ExecutionException>> result = new Pair<>();
    result.setFirst(readdedRegionNames);
    result.setSecond(executionErrors);
    return result;
  }

  List<Long> assigns(String [] args) throws IOException {
    Options options = new Options();
    Option override = Option.builder("o").longOpt("override").build();
    options.addOption(override);
    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(options, args, false);
    } catch (ParseException e) {
      usage(options, e.getMessage());
      return null;
    }
    boolean overrideFlag = commandLine.hasOption(override.getOpt());
    try (ClusterConnection conn =
             (ClusterConnection) ConnectionFactory.createConnection(getConf())) {
      checkHBCKSupport(conn);
      try (Hbck hbck = conn.getHbck()) {
        return hbck.assigns(commandLine.getArgList(), overrideFlag);
      }
    }
  }

  List<Long> unassigns(String [] args) throws IOException {
    Options options = new Options();
    Option override = Option.builder("o").longOpt("override").build();
    options.addOption(override);
    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(options, args, false);
    } catch (ParseException e) {
      usage(options, e.getMessage());
      return null;
    }
    boolean overrideFlag = commandLine.hasOption(override.getOpt());
    try (ClusterConnection conn =
             (ClusterConnection) ConnectionFactory.createConnection(getConf())) {
      checkHBCKSupport(conn);
      try (Hbck hbck = conn.getHbck()) {
        return hbck.unassigns(commandLine.getArgList(), overrideFlag);
      }
    }
  }

  /**
   * @return List of results OR null if failed to run.
   */
  List<Boolean> bypass(String [] args)
      throws IOException {
    // Bypass has two options....
    Options options = new Options();
    // See usage for 'help' on these options.
    Option override = Option.builder("o").longOpt("override").build();
    options.addOption(override);
    Option recursive = Option.builder("r").longOpt("recursive").build();
    options.addOption(recursive);
    Option wait = Option.builder("w").longOpt("lockWait").hasArg().type(Integer.class).build();
    options.addOption(wait);
    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(options, args, false);
    } catch (ParseException e) {
      usage(options, e.getMessage());
      return null;
    }
    long lockWait = DEFAULT_LOCK_WAIT;
    if (commandLine.hasOption(wait.getOpt())) {
      lockWait = Integer.valueOf(commandLine.getOptionValue(wait.getOpt()));
    }
    String[] pidStrs = commandLine.getArgs();
    if (pidStrs == null || pidStrs.length <= 0) {
      usage(options, "No pids supplied.");
      return null;
    }
    boolean overrideFlag = commandLine.hasOption(override.getOpt());
    boolean recursiveFlag = commandLine.hasOption(override.getOpt());
    List<Long> pids = Arrays.stream(pidStrs).map(i -> Long.valueOf(i)).collect(Collectors.toList());
    try (ClusterConnection c = (ClusterConnection) ConnectionFactory.createConnection(getConf())) {
      checkHBCKSupport(c);
      try (Hbck hbck = c.getHbck()) {
        return hbck.bypassProcedure(pids, lockWait, overrideFlag, recursiveFlag);
      }
    }
  }

  private String readHBCK2BuildProperties() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("hbck2.properties");
    StringBuilder resultStringBuilder = new StringBuilder();
    BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
    String line;
    while ((line = br.readLine()) != null) {
      resultStringBuilder.append(line).append("\n");
    }
    return resultStringBuilder.toString();
  }

  private static final String getCommandUsage() {
    // NOTE: List commands belonw alphabetically!
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    writer.println();
    writer.println("Commands:");
    writer.println(" " + ASSIGNS + " [OPTIONS] <ENCODED_REGIONNAME>...");
    writer.println("   Options:");
    writer.println("    -o,--override  override ownership by another procedure");
    writer.println("   A 'raw' assign that can be used even during Master initialization.");
    writer.println("   Skirts Coprocessors. Pass one or more encoded RegionNames.");
    writer.println("   1588230740 is the hard-coded name for the hbase:meta region and");
    writer.println("   de00010733901a05f5a2a3a382e27dd4 is an example of what a user-space");
    writer.println("   encoded Region name looks like. For example:");
    writer.println("     $ HBCK2 assign 1588230740 de00010733901a05f5a2a3a382e27dd4");
    writer.println("   Returns the pid(s) of the created AssignProcedure(s) or -1 if none.");
    writer.println();
    writer.println(" " + BYPASS + " [OPTIONS] <PID>...");
    writer.println("   Options:");
    writer.println("    -o,--override   override if procedure is running/stuck");
    writer.println("    -r,--recursive  bypass parent and its children. SLOW! EXPENSIVE!");
    writer.println("    -w,--lockWait   milliseconds to wait on lock before giving up; default=1");
    writer.println("   Pass one (or more) procedure 'pid's to skip to procedure finish.");
    writer.println("   Parent of bypassed procedure will also be skipped to the finish.");
    writer.println("   Entities will be left in an inconsistent state and will require");
    writer.println("   manual fixup. May need Master restart to clear locks still held.");
    writer.println("   Bypass fails if procedure has children. Add 'recursive' if all");
    writer.println("   you have is a parent pid to finish parent and children. This");
    writer.println("   is SLOW, and dangerous so use selectively. Does not always work.");
    writer.println();
    writer.println(" " + UNASSIGNS + " <ENCODED_REGIONNAME>...");
    writer.println("   Options:");
    writer.println("    -o,--override  override ownership by another procedure");
    writer.println("   A 'raw' unassign that can be used even during Master initialization.");
    writer.println("   Skirts Coprocessors. Pass one or more encoded RegionNames:");
    writer.println("   1588230740 is the hard-coded name for the hbase:meta region and");
    writer.println("   de00010733901a05f5a2a3a382e27dd4 is an example of what a user-space");
    writer.println("   encoded Region name looks like. For example:");
    writer.println("     $ HBCK2 unassign 1588230740 de00010733901a05f5a2a3a382e27dd4");
    writer.println("   Returns the pid(s) of the created UnassignProcedure(s) or -1 if none.");
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
    writer.println(" " + SET_REGION_STATE + " <ENCODED_REGIONNAME> <STATE>");
    writer.println("   Possible region states: " + Arrays.stream(RegionState.State.values()).
      map(i -> i.toString()).collect(Collectors.joining(", ")));
    writer.println("   WARNING: This is a very risky option intended for use as last resort.");
    writer.println("    Example scenarios are when unassigns/assigns can't move forward ");
    writer.println("     due to region being in an inconsistent state in META. For example, ");
    writer.println("     'unassigns' command can only proceed ");
    writer.println("      if passed in region is in one of following states: ");
    writer.println("                [SPLITTING|SPLIT|MERGING|OPEN|CLOSING]");
    writer.println("   Before manually setting a region state with this command,");
    writer.println("   please certify that this region is not being handled by");
    writer.println("   by a running procedure, such as Assign or Split. You can get a view of ");
    writer.println("   running procedures from hbase shell, using 'list_procedures' command. ");
    writer.println("   An example setting region 'de00010733901a05f5a2a3a382e27dd4' to CLOSING:");
    writer.println("     $ HBCK2 setRegionState de00010733901a05f5a2a3a382e27dd4 CLOSING");
    writer.println("   Returns \"0\" SUCCESS code if it informed region state is changed, "
      + "\"1\" FAIL code otherwise.");
    writer.println();
    writer.println(" " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " <NAMESPACE|"
      + "NAMESPACE:TABLENAME>...");
    writer.println("   To be used in scenarios where some regions may be missing in META,");
    writer.println("   but there's still a valid 'regioninfo metadata file on HDFS. ");
    writer.println("   This is a lighter version of 'OfflineMetaRepair tool commonly used for ");
    writer.println("   similar issues on 1.x release line. ");
    writer.println("   This command needs META to be online. For each table name passed as");
    writer.println("   parameter, it performs a diff between regions available in META, ");
    writer.println("   against existing regions dirs on HDFS. Then, for region dirs with ");
    writer.println("   no matches in META, it reads regioninfo metadata file and ");
    writer.println("   re-creates given region in META. Regions are re-created in 'CLOSED' ");
    writer.println("   state at META table only, but not in Masters' cache, and are not ");
    writer.println("   assigned either. A rolling Masters restart, followed by a ");
    writer.println("   hbck2 'assigns' command with all re-inserted regions is required. ");
    writer.println("   This hbck2 'assigns' command is printed out after this command ");
    writer.println("   completes for users subsequent convenience.");
    writer.println("   WARNING: To avoid potential region overlapping problems due to ongoing ");
    writer.println("   splits, this command attempts to disable given tables ");
    writer.println("   (if it fails, disable is skipped) while re-inserting regions. ");
    writer.println("   An example adding missing regions for tables 'tbl_1' on default ");
    writer.println("   namespace, 'tbl_2' on namespace 'n1' and for all tables from ");
    writer.println("   namespace 'n2': ");
    writer.println("     $ HBCK2 addMissingRegionsInMeta default:tbl_1 n1:tbl_2 n2 ");
    writer.println("   Returns hbck2 'assigns' command with all re-inserted regions.");
    writer.println();
    writer.println(" " + REPORT_MISSING_REGIONS_IN_META + " <NAMESPACE|"
      + "NAMESPACE:TABLENAME>...");
    writer.println("   To be used in scenarios where some regions may be missing in META,");
    writer.println("   but there's still a valid 'regioninfo metadata file on HDFS. ");
    writer.println("   This is a checking only method, designed for reporting purposes and");
    writer.println("   doesn't perform any fixes, providing a view of which regions (if any) ");
    writer.println("   would get re-added to meta, grouped by respective table/namespace. ");
    writer.println("   To effectively re-add regions in meta, "
      + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " should be executed. ");
    writer.println("   This command needs META to be online. For each namespace/table passed");
    writer.println("   as parameter, it performs a diff between regions available in META, ");
    writer.println("   against existing regions dirs on HDFS. Region dirs with no matches");
    writer.println("   are printed grouped under its related table name. Tables with no");
    writer.println("   missing regions will show a 'no missing regions' message. If no");
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
    writer.println("   Returns list of missing regions for each table passed as parameter, or ");
    writer.println("   for each table on namespaces specified as parameter.");
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
    formatter.printHelp("HBCK2 [OPTIONS] COMMAND <ARGS>",
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
  public int run(String[] args) throws IOException {
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
    Option parent = Option.builder("z").longOpt(HConstants.ZOOKEEPER_ZNODE_PARENT).hasArg()
        .desc("parent znode of target hbase").build();
    options.addOption(parent);
    Option peerPort = Option.builder("p").longOpt(HConstants.ZOOKEEPER_CLIENT_PORT).hasArg()
        .desc("port of target hbase ensemble").type(Integer.class).build();
    options.addOption(peerPort);
    Option version = Option.builder("v").longOpt(VERSION).desc("this hbck2 version").build();
    options.addOption(version);
    Option skip = Option.builder("s").longOpt("skip").desc("skip hbase version check").build();
    options.addOption(skip);

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
    if (commandLine.hasOption(version.getOpt())) {
      System.out.println(readHBCK2BuildProperties());
      return EXIT_SUCCESS;
    }
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
      String optionValue = commandLine.getOptionValue(peerPort.getOpt());
      if (optionValue.matches("[0-9]+")) {
        getConf().setInt(HConstants.ZOOKEEPER_CLIENT_PORT, Integer.valueOf(optionValue));
      } else {
        usage(options,
          "Invalid client port. Please provide proper port for target hbase ensemble.");
        return EXIT_FAILURE;
      }
    }
    if (commandLine.hasOption(parent.getOpt())) {
      String optionValue = commandLine.getOptionValue(parent.getOpt());
      if (optionValue.startsWith("/")) {
        getConf().set(HConstants.ZOOKEEPER_ZNODE_PARENT, optionValue);
      } else {
        usage(options, "Invalid parent znode. Please provide proper parent znode of target hbase."
            + " Note that valid znodes must start with \"/\".");
        return EXIT_FAILURE;
      }
    }
    if(commandLine.hasOption(skip.getOpt())){
      skipCheck = true;
    }

    // Now process commands.
    String[] commands = commandLine.getArgs();
    String command = commands[0];
    switch (command) {
    case SET_TABLE_STATE:
      if (commands.length < 3) {
        usage(options, command + " takes tablename and state arguments: e.g. user ENABLED");
        return EXIT_FAILURE;
      }
      System.out.println(setTableState(TableName.valueOf(commands[1]), TableState.State.valueOf(commands[2])));
      break;

    case ASSIGNS:
      if (commands.length < 2) {
        usage(options, command + " takes one or more encoded region names");
        return EXIT_FAILURE;
      }
      System.out.println(assigns(purgeFirst(commands)));
      break;

    case BYPASS:
      if (commands.length < 2) {
        usage(options, command + " takes one or more pids");
        return EXIT_FAILURE;
      }
      List<Boolean> bs = bypass(purgeFirst(commands));
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
      System.out.println(toString(unassigns(purgeFirst(commands))));
      break;

    case SET_REGION_STATE:
      if (commands.length < 3) {
        usage(options, command + " takes region encoded name and state arguments: e.g. "
          + "35f30b0ce922c34bf5c284eff33ba8b3 CLOSING");
        return EXIT_FAILURE;
      }
      return setRegionState(commands[1], RegionState.State.valueOf(commands[2]));
    case ADD_MISSING_REGIONS_IN_META_FOR_TABLES:
      if(commands.length < 2){
        usage(options, command + " takes one or more table names.");
        return EXIT_FAILURE;
      }
      Pair<List<String>, List<ExecutionException>> result =
        addMissingRegionsInMetaForTables(purgeFirst(commands));
      System.out.println(formatReAddedRegionsMessage(result.getFirst(),result.getSecond()));
      break;
    case REPORT_MISSING_REGIONS_IN_META:
      try {
        Map<TableName,List<Path>> report =
          reportTablesWithMissingRegionsInMeta(purgeFirst(commands));
        System.out.println(formatMissingRegionsInMetaReport(report));
      } catch (Exception e) {
        return EXIT_FAILURE;
      }
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

  private String formatMissingRegionsInMetaReport(Map<TableName,List<Path>> report) {
    final StringBuilder builder = new StringBuilder();
    builder.append("Missing Regions for each table:\n\t");
    report.keySet().stream().forEach(table -> {
      builder.append(table);
      if (!report.get(table).isEmpty()){
        builder.append("->\n\t\t");
        report.get(table).stream().forEach(region -> builder.append(region.getName())
          .append(" "));
      } else {
        builder.append(" -> No missing regions");
      }
      builder.append("\n\t");
    });
    return builder.toString();
  }

  private String formatReAddedRegionsMessage(List<String> readdedRegionNames,
    List<ExecutionException> executionErrors) {
    final StringBuilder finalText = new StringBuilder();
    finalText.append("Regions re-added into Meta: ").append(readdedRegionNames.size());
    if(readdedRegionNames.isEmpty()){
      finalText.append("\n")
        .append("WARNING: \n\t")
        .append(readdedRegionNames.size()).append(" regions were added ")
        .append("to META, but these are not yet on Masters cache. \n")
        .append("You need to restart Masters, then run hbck2 'assigns' command below:\n\t\t")
        .append(buildHbck2AssignsCommand(readdedRegionNames));
    }
    if(executionErrors.isEmpty()){
      finalText.append("\n")
        .append("ERROR: \n\t")
        .append("There were following errors on at least one table thread:\n");
      executionErrors.stream().forEach(e -> finalText.append(e.getMessage()).append("\n"));
    }
    return finalText.toString();
  }

  private String buildHbck2AssignsCommand(List<String> regions) {
    final StringBuilder builder = new StringBuilder();
    builder.append("assigns ");
    regions.forEach(region -> builder.append(region).append(" "));
    return builder.toString();
  }

  /**
   * @return A new array with first element dropped.
   */
  private static String[] purgeFirst(String[] args) {
    int size = args.length;
    if (size <= 1) {
      return new String [] {};
    }
    size--;
    String [] result = new String [size];
    System.arraycopy(args, 1, result, 0, size);
    return result;
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
