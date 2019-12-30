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
import java.util.List;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


public class TestRegionsMerger {
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final TableName TABLE_NAME =
    TableName.valueOf(TestRegionsMerger.class.getSimpleName());
  private static final byte[] family = Bytes.toBytes("f");
  private Table table;

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.getConfiguration().set(HConstants.HREGION_MAX_FILESIZE,
      Long.toString(1024*1024*3));
    TEST_UTIL.getConfiguration().setInt(RegionsMerger.MAX_ROUNDS_IDDLE, 3);
    TEST_UTIL.startMiniCluster(3);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void setup() throws Exception {
    table = TEST_UTIL.createMultiRegionTable(TABLE_NAME, family, 15);
    TEST_UTIL.waitUntilAllRegionsAssigned(TABLE_NAME);
  }

  @After
  public void tearDown() throws Exception {
    TEST_UTIL.deleteTable(TABLE_NAME);
  }

  @Test
  public void testMergeRegionsCanMergeToTarget() throws Exception {
    RegionsMerger merger = new RegionsMerger(TEST_UTIL.getConfiguration());
    merger.mergeRegions(TABLE_NAME.getNameWithNamespaceInclAsString(), 3);
    List<RegionInfo> result = TEST_UTIL.getAdmin().getRegions(TABLE_NAME);
    assertEquals(3, result.size());
  }

  @Test
  public void testMergeRegionsCanMergeSomeButNotToTarget() throws Exception {
    RegionsMerger merger = new RegionsMerger(TEST_UTIL.getConfiguration());
    generateTableData();
    merger.mergeRegions(TABLE_NAME.getNameWithNamespaceInclAsString(), 3);
    List<RegionInfo> result = TEST_UTIL.getAdmin().getRegions(TABLE_NAME);
    assertEquals(8, result.size());
  }

  @Test
  public void testMergeRegionsCannotMergeAny() throws Exception {
    TEST_UTIL.getConfiguration().setDouble(RegionsMerger.RESULTING_REGION_UPPER_MARK, 0.5);
    RegionsMerger merger = new RegionsMerger(TEST_UTIL.getConfiguration());
    generateTableData();
    TEST_UTIL.getAdmin().flush(TABLE_NAME);
    merger.mergeRegions(TABLE_NAME.getNameWithNamespaceInclAsString(), 3);
    List<RegionInfo> result = TEST_UTIL.getAdmin().getRegions(TABLE_NAME);
    assertEquals(15, result.size());
  }

  @Test
  public void testMergeRegionsInvalidParams() throws Exception {
    RegionsMerger merger = new RegionsMerger(TEST_UTIL.getConfiguration());
    assertEquals(-1, merger.run(new String[]{}));
  }

  private void generateTableData() throws Exception {
    TEST_UTIL.getAdmin().getRegions(TABLE_NAME).forEach(r -> {
      byte[] key = r.getStartKey().length == 0 ? new byte[]{0} : r.getStartKey();
      Put put = new Put(key);
      put.addColumn(family, Bytes.toBytes("c"), new byte[1024*1024]);
      try {
        table.put(put);
      } catch (IOException e) {
        throw new Error("Failed to put row");
      }
    });
  }
}
