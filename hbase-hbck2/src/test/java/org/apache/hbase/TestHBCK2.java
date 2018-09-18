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

import junit.framework.TestCase;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.hbase.master.RegionState;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.logging.log4j.LogManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestHBCK2 {
  private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger(TestHBCK2.class);
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final TableName TABLE_NAME = TableName.valueOf(TestHBCK2.class.getSimpleName());

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(3);
    TEST_UTIL.createMultiRegionTable(TABLE_NAME, Bytes.toBytes("family1"), 5);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Test
  public void testHelp() throws ParseException, IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(os);
    PrintStream oldOut = System.out;
    System.setOut(stream);
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    hbck.run(new String [] {"-h"});
    stream.close();
    os.close();
    System.setOut(oldOut);
    String output = os.toString();
    assertTrue(output, output.startsWith("usage: HBCK2"));
  }

  @Test
  public void testSetTableStateInMeta() throws IOException {
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    TableState state = hbck.setTableState(TABLE_NAME, TableState.State.DISABLED);
    TestCase.assertTrue("Found=" + state.getState(), state.isEnabled());
    // Restore the state.
    state = hbck.setTableState(TABLE_NAME, state.getState());
    TestCase.assertTrue("Found=" + state.getState(), state.isDisabled());
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
      HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
      List<Long> pids = hbck.unassigns(
          regions.stream().map(r -> r.getEncodedName()).collect(Collectors.toList()));
      waitOnPids(pids);
      for (RegionInfo ri: regions) {
        RegionState rs = TEST_UTIL.getHBaseCluster().getMaster().getAssignmentManager().
            getRegionStates().getRegionState(ri.getEncodedName());
        LOG.info("RS: {}", rs.toString());
        TestCase.assertTrue(rs.toString(), rs.isClosed());
      }
      pids = hbck.assigns(
          regions.stream().map(r -> r.getEncodedName()).collect(Collectors.toList()));
      waitOnPids(pids);
      for (RegionInfo ri: regions) {
        RegionState rs = TEST_UTIL.getHBaseCluster().getMaster().getAssignmentManager().
            getRegionStates().getRegionState(ri.getEncodedName());
        LOG.info("RS: {}", rs.toString());
        TestCase.assertTrue(rs.toString(), rs.isOpened());
      }
      // What happens if crappy region list passed?
      pids = hbck.assigns(
          Arrays.stream(new String [] {"a", "some rubbish name"}).collect(Collectors.toList()));
      for (long pid: pids) {
        assertEquals(org.apache.hadoop.hbase.procedure2.Procedure.NO_PROC_ID, pid);
      }
    }
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
