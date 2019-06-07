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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import junit.framework.TestCase;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Hbck;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.hbase.master.RegionState;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.logging.log4j.LogManager;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * Tests commands. For command-line parsing, see adjacent test.
 * @see TestHBCKCommandLineParsing
 */
public class TestHBCK2 {
  private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger(TestHBCK2.class);
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final TableName TABLE_NAME = TableName.valueOf(TestHBCK2.class.getSimpleName());
  private static final TableName REGION_STATES_TABLE_NAME = TableName.
    valueOf(TestHBCK2.class.getSimpleName() + "-REGIONS_STATES");

  @Rule
  public TestName testName = new TestName();

  /**
   * A 'connected' hbck2 instance.
   */
  private HBCK2 hbck2;

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(3);
    TEST_UTIL.createMultiRegionTable(TABLE_NAME, Bytes.toBytes("family1"), 5);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void before() {
    this.hbck2 = new HBCK2(TEST_UTIL.getConfiguration());
  }

  @Test (expected = UnsupportedOperationException.class)
  public void testVersions() throws IOException {
    try (ClusterConnection connection = this.hbck2.connect()) {
      this.hbck2.checkHBCKSupport(connection, "test", "10.0.0");
    }
  }

  @Test
  public void testSetTableStateInMeta() throws IOException {
    try (ClusterConnection connection = this.hbck2.connect(); Hbck hbck = connection.getHbck()) {
      TableState state = this.hbck2.setTableState(hbck, TABLE_NAME, TableState.State.DISABLED);
      TestCase.assertTrue("Found=" + state.getState(), state.isEnabled());
      // Restore the state.
      state = this.hbck2.setTableState(hbck, TABLE_NAME, state.getState());
      TestCase.assertTrue("Found=" + state.getState(), state.isDisabled());
    }
  }

  @Test
  public void testAssigns() throws IOException {
    try (Admin admin = TEST_UTIL.getConnection().getAdmin()) {
      List<RegionInfo> regions = admin.getRegions(TABLE_NAME);
      for (RegionInfo ri: regions) {
        RegionState rs = TEST_UTIL.getHBaseCluster().getMaster().getAssignmentManager().
            getRegionStates().getRegionState(ri.getEncodedName());
        LOG.info("RS: {}", rs.toString());
      }
      List<String> regionStrs =
          regions.stream().map(RegionInfo::getEncodedName).collect(Collectors.toList());
      String [] regionStrsArray = regionStrs.toArray(new String[] {});
      try (ClusterConnection connection = this.hbck2.connect(); Hbck hbck = connection.getHbck()) {
        List<Long> pids = this.hbck2.unassigns(hbck, regionStrsArray);
        waitOnPids(pids);
        for (RegionInfo ri : regions) {
          RegionState rs = TEST_UTIL.getHBaseCluster().getMaster().getAssignmentManager().
              getRegionStates().getRegionState(ri.getEncodedName());
          LOG.info("RS: {}", rs.toString());
          TestCase.assertTrue(rs.toString(), rs.isClosed());
        }
        pids = this.hbck2.assigns(hbck, regionStrsArray);
        waitOnPids(pids);
        for (RegionInfo ri : regions) {
          RegionState rs = TEST_UTIL.getHBaseCluster().getMaster().getAssignmentManager().
              getRegionStates().getRegionState(ri.getEncodedName());
          LOG.info("RS: {}", rs.toString());
          TestCase.assertTrue(rs.toString(), rs.isOpened());
        }
        // What happens if crappy region list passed?
        pids = this.hbck2.assigns(hbck, Arrays.stream(new String[]{"a", "some rubbish name"}).
            collect(Collectors.toList()).toArray(new String[]{}));
        for (long pid : pids) {
          assertEquals(org.apache.hadoop.hbase.procedure2.Procedure.NO_PROC_ID, pid);
        }
      }
    }
  }

  @Test
  public void testSetRegionState() throws IOException {
    TEST_UTIL.createTable(REGION_STATES_TABLE_NAME, Bytes.toBytes("family1"));
    try (Admin admin = TEST_UTIL.getConnection().getAdmin()) {
      List<RegionInfo> regions = admin.getRegions(REGION_STATES_TABLE_NAME);
      RegionInfo info = regions.get(0);
      assertEquals(RegionState.State.OPEN, getCurrentRegionState(info));
      String region = info.getEncodedName();
      try (ClusterConnection connection = this.hbck2.connect()) {
        this.hbck2.setRegionState(connection, region, RegionState.State.CLOSING);
      }
      assertEquals(RegionState.State.CLOSING, getCurrentRegionState(info));
    } finally {
      TEST_UTIL.deleteTable(REGION_STATES_TABLE_NAME);
    }
  }

  @Test
  public void testSetRegionStateInvalidRegion() throws IOException {
    try (ClusterConnection connection = this.hbck2.connect()) {
      assertEquals(HBCK2.EXIT_FAILURE, this.hbck2.setRegionState(connection, "NO_REGION",
          RegionState.State.CLOSING));
    }
  }

  @Test (expected = IllegalArgumentException.class)
  public void testSetRegionStateInvalidState() throws IOException {
    TEST_UTIL.createTable(REGION_STATES_TABLE_NAME, Bytes.toBytes("family1"));
    try (Admin admin = TEST_UTIL.getConnection().getAdmin()) {
      List<RegionInfo> regions = admin.getRegions(REGION_STATES_TABLE_NAME);
      RegionInfo info = regions.get(0);
      assertEquals(RegionState.State.OPEN, getCurrentRegionState(info));
      String region = info.getEncodedName();
      try (ClusterConnection connection = this.hbck2.connect()) {
        this.hbck2.setRegionState(connection, region, null);
      }
    } finally {
      TEST_UTIL.deleteTable(REGION_STATES_TABLE_NAME);
    }
  }

  @Test
  public void testAddMissingRegionsInMetaAllRegionsMissing() throws Exception {
    this.testAddMissingRegionsInMetaForTables(5,5);
  }

  @Test
  public void testAddMissingRegionsInMetaTwoMissingOnly() throws Exception {
    this.testAddMissingRegionsInMetaForTables(2,5);
  }

  @Test
  public void testReportMissingRegionsInMetaAllNsTbls() throws Exception {
    this.testReportMissingRegionsInMeta(5, 5,null);
  }

  @Test
  public void testReportMissingRegionsInMetaSpecificTbl() throws Exception {
    this.testReportMissingRegionsInMeta(5, 5,
      TABLE_NAME.getNameWithNamespaceInclAsString());
  }

  @Test
  public void testReportMissingRegionsInMetaSpecificTblAndNsTbl() throws Exception {
    this.testReportMissingRegionsInMeta(5, 5,
      TABLE_NAME.getNameWithNamespaceInclAsString(), "hbase:namespace");
  }

  @Test
  public void testReportMissingRegionsInMetaSpecificTblAndNsTblAlsoMissing() throws Exception {
    List<RegionInfo> regions = MetaTableAccessor
      .getTableRegions(TEST_UTIL.getConnection(), TableName.valueOf("hbase:namespace"));
    MetaTableAccessor.deleteRegions(TEST_UTIL.getConnection(),
      regions.subList(0,1));
    this.testReportMissingRegionsInMeta(5, 6,
      TABLE_NAME.getNameWithNamespaceInclAsString(), "hbase:namespace");
  }

  @Test
  public void testFormatReportMissingRegionsInMetaNoMissing() throws IOException {
    final String expectedResult = "Missing Regions for each table:\n"
      + "\tTestHBCK2 -> No missing regions\n\thbase:namespace -> No missing regions\n\t\n";
    String result = testFormatMissingRegionsInMetaReport();
    assertTrue(result.contains(expectedResult));
  }

  @Test
  public void testFormatReportMissingInMetaOneMissing() throws IOException {
    TableName tableName = createTestTable(5);
    List<RegionInfo> regions = MetaTableAccessor
      .getTableRegions(TEST_UTIL.getConnection(), tableName);
    MetaTableAccessor.deleteRegions(TEST_UTIL.getConnection(), regions.subList(0,1));
    String expectedResult = "Missing Regions for each table:\n";
    String result = testFormatMissingRegionsInMetaReport();
    //validates initial report message
    assertTrue(result.contains(expectedResult));
    //validates our test table region is reported missing
    expectedResult = "\t" + tableName.getNameAsString() + "->\n\t\t"
      + regions.get(0).getEncodedName();
    assertTrue(result.contains(expectedResult));
    //validates namespace region is not reported missing
    expectedResult = "\n\thbase:namespace -> No missing regions\n\t";
    assertTrue(result.contains(expectedResult));
  }

  private String testFormatMissingRegionsInMetaReport()
      throws IOException {
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    final StringBuilder builder = new StringBuilder();
    PrintStream originalOS = System.out;
    OutputStream testOS = new OutputStream() {
      @Override public void write(int b) throws IOException {
        builder.append((char)b);
      }
    };
    System.setOut(new PrintStream(testOS));

    hbck.run(new String[]{"reportMissingRegionsInMeta"});
    System.setOut(originalOS);
    return builder.toString();
  }

  private TableName createTestTable(int totalRegions) throws IOException {
    TableName tableName = TableName.valueOf(testName.getMethodName());
    TEST_UTIL.createMultiRegionTable(tableName, Bytes.toBytes("family1"), totalRegions);
    return tableName;
  }

  private void testAddMissingRegionsInMetaForTables(int missingRegions, int totalRegions)
    throws Exception {
    TableName tableName = createTestTable(totalRegions);
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    List<RegionInfo> regions = MetaTableAccessor
      .getTableRegions(TEST_UTIL.getConnection(), tableName);
    MetaTableAccessor.deleteRegions(TEST_UTIL.getConnection(), regions.subList(0,missingRegions));
    int remaining = totalRegions - missingRegions;
    assertEquals("Table should have " + remaining + " regions in META.", remaining,
      MetaTableAccessor.getRegionCount(TEST_UTIL.getConnection(), tableName));
    assertEquals(missingRegions,hbck.addMissingRegionsInMetaForTables("default:"
      + tableName.getNameAsString()).getFirst().size());
    assertEquals("Table regions should had been re-added in META.", totalRegions,
      MetaTableAccessor.getRegionCount(TEST_UTIL.getConnection(), tableName));
    //compare the added regions to make sure those are the same
    List<RegionInfo> newRegions = MetaTableAccessor
      .getTableRegions(TEST_UTIL.getConnection(), tableName);
    assertEquals("All re-added regions should be the same", regions, newRegions);
  }

  private void testReportMissingRegionsInMeta(int missingRegionsInTestTbl,
      int expectedTotalMissingRegions, String... namespaceOrTable) throws Exception {
    List<RegionInfo> regions = MetaTableAccessor
      .getTableRegions(TEST_UTIL.getConnection(), TABLE_NAME);
    MetaTableAccessor.deleteRegions(TEST_UTIL.getConnection(),
      regions.subList(0,missingRegionsInTestTbl));
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    final Map<TableName,List<Path>> report =
      hbck.reportTablesWithMissingRegionsInMeta(namespaceOrTable);
    long resultingMissingRegions = report.keySet().stream().mapToLong( nsTbl ->
      report.get(nsTbl).size()).sum();
    assertEquals(expectedTotalMissingRegions, resultingMissingRegions);
    hbck.addMissingRegionsInMetaForTables(null);
  }

  @Test (expected = IllegalArgumentException.class)
  public void testSetRegionStateInvalidRegionAndInvalidState() throws IOException {
    try (ClusterConnection connection = this.hbck2.connect()) {
      this.hbck2.setRegionState(connection, "NO_REGION", null);
    }
  }

  private RegionState.State getCurrentRegionState(RegionInfo regionInfo) throws IOException{
    Table metaTable = TEST_UTIL.getConnection().getTable(TableName.valueOf("hbase:meta"));
    Get get = new Get(regionInfo.getRegionName());
    get.addColumn(HConstants.CATALOG_FAMILY, HConstants.STATE_QUALIFIER);
    Result result = metaTable.get(get);
    byte[] currentStateValue = result.getValue(HConstants.CATALOG_FAMILY,
      HConstants.STATE_QUALIFIER);
    return currentStateValue != null ?
      RegionState.State.valueOf(Bytes.toString(currentStateValue))
      : null;
  }

  private void waitOnPids(List<Long> pids) {
    for (Long pid: pids) {
      while (!TEST_UTIL.getHBaseCluster().getMaster().getMasterProcedureExecutor().
          isFinished(pid)) {
        Threads.sleep(100);
      }
    }
  }
}
