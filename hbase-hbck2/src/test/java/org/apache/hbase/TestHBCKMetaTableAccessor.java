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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.master.RegionState;
import org.apache.hadoop.hbase.util.Bytes;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Test class for the MetaTableAccessor wrapper.
 */
public class TestHBCKMetaTableAccessor {
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();

  @Rule
  public TestName testName = new TestName();

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(3);
  }

  @Test
  public void testDeleteRegionInfo() throws Exception {
    assertFalse(listRegionsInMeta().contains(createTableAnddeleteFirstRegion()));
  }

  private RegionInfo createTableAnddeleteFirstRegion() throws Exception {
    TableName tableName = createTestTable(5);
    List<RegionInfo> regions = TEST_UTIL.getAdmin().getRegions(tableName);
    RegionInfo toBeDeleted = regions.get(0);
    HBCKMetaTableAccessor.deleteRegionInfo(TEST_UTIL.getConnection(), toBeDeleted);
    return toBeDeleted;
  }

  @Test
  public void testAddRegionToMeta() throws Exception {
    RegionInfo regionInfo = createTableAnddeleteFirstRegion();
    HBCKMetaTableAccessor.addRegionToMeta(TEST_UTIL.getConnection(), regionInfo);
    Connection connection = TEST_UTIL.getConnection();
    Table meta = connection.getTable(TableName.META_TABLE_NAME);
    Get get = new Get(regionInfo.getRegionName());
    Result r = meta.get(get);
    assertNotNull(r);
    assertFalse(r.isEmpty());
    RegionInfo returnedRI = RegionInfo.parseFrom(r.getValue(HConstants.CATALOG_FAMILY,
      HConstants.REGIONINFO_QUALIFIER));
    assertEquals(regionInfo, returnedRI);
    String state = Bytes.toString(r.getValue(HConstants.CATALOG_FAMILY,
      HConstants.STATE_QUALIFIER));
    assertEquals(RegionState.State.valueOf(state), RegionState.State.CLOSED);
  }

  private List<RegionInfo> listRegionsInMeta() throws Exception {
    Connection connection = TEST_UTIL.getConnection();
    Table table = connection.getTable(TableName.META_TABLE_NAME);
    Scan scan = new Scan();
    scan.addColumn(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER);
    ResultScanner scanner = table.getScanner(scan);
    final List<RegionInfo> regionInfos = new ArrayList<>();
    for(Result r : scanner) {
      regionInfos.add(RegionInfo.parseFrom(r.getValue(HConstants.CATALOG_FAMILY,
        HConstants.REGIONINFO_QUALIFIER)));
    }
    return regionInfos;
  }

  private TableName createTestTable(int totalRegions) throws IOException {
    TableName tableName = TableName.valueOf(testName.getMethodName());
    TEST_UTIL.createMultiRegionTable(tableName, Bytes.toBytes("family1"), totalRegions);
    return tableName;
  }
}
