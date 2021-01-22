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

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellBuilderFactory;
import org.apache.hadoop.hbase.CellBuilderType;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.NamespaceDescriptor;
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
  private static final String NAMESPACE = "TEST";
  private static final TableName TABLE_NAME_WITH_NAMESPACE =
    TableName.valueOf(NAMESPACE, TestRegionsMerger.class.getSimpleName());
  private static final TableName TABLE_NAME =
    TableName.valueOf(TestRegionsMerger.class.getSimpleName());
  private static final byte[] family = Bytes.toBytes("f");
  private Table table;

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.getConfiguration().set(HConstants.HREGION_MAX_FILESIZE,
      Long.toString(1024*1024*3));
    TEST_UTIL.startMiniCluster(3);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void setup() throws Exception {
    table = TEST_UTIL.createMultiRegionTable(TABLE_NAME, family, 7);
    TEST_UTIL.waitUntilAllRegionsAssigned(TABLE_NAME);
  }

  @After
  public void tearDown() throws Exception {
    TEST_UTIL.deleteTable(TABLE_NAME);
  }

  @Test
  public void testMergeRegionsCanMergeToTarget() throws Exception {
    final int originalCount = TEST_UTIL.countRows(table);
    TEST_UTIL.getConfiguration().setInt(RegionsMerger.MAX_ROUNDS_IDLE, 10);
    // hbase-2.3 and hbase-2.1 merge's work differently; 2.3 won't merge if a merge candidate is a parent.
    // The below used to merge until only 3 regions. Made it less aggressive. Originally there are 15 regions.
    // Merge till 10.
    final int target = 5;
    List<RegionInfo> result = mergeRegionsToTarget(TABLE_NAME, target);
    assertEquals(target, result.size());
    assertEquals("Row count before and after merge should be equal",
        originalCount, TEST_UTIL.countRows(table));
  }

  @Test
  public void testMergeRegionsForNonDefaultNamespaceTable() throws Exception {
    try {
      TEST_UTIL.getConfiguration().setInt(RegionsMerger.MAX_ROUNDS_IDLE, 10);
      TEST_UTIL.getAdmin().createNamespace(NamespaceDescriptor.create(NAMESPACE).build());
      Table tableWithNamespace = TEST_UTIL.createMultiRegionTable(TABLE_NAME_WITH_NAMESPACE,
        family, 7);
      final int originalCount = TEST_UTIL.countRows(tableWithNamespace);
      final int target = 5;
      List<RegionInfo> result = mergeRegionsToTarget(TABLE_NAME_WITH_NAMESPACE, target);
      assertEquals(target, result.size());
      assertEquals("Row count before and after merge should be equal",
        originalCount, TEST_UTIL.countRows(tableWithNamespace));
    } finally {
      TEST_UTIL.deleteTable(TABLE_NAME_WITH_NAMESPACE);
      TEST_UTIL.getAdmin().deleteNamespace(NAMESPACE);
    }
  }


  @Test
  public void testMergeRegionsCanMergeSomeButNotToTarget() throws Exception {
    TEST_UTIL.getConfiguration().setInt(RegionsMerger.MAX_ROUNDS_IDLE, 3);
    generateTableData();
    final int originalCount = TEST_UTIL.countRows(table);
    List<RegionInfo> result = mergeRegionsToTarget(TABLE_NAME, 3);
    assertEquals(4, result.size());
    assertEquals("Row count before and after merge should be equal",
        originalCount, TEST_UTIL.countRows(table));
  }

  @Test
  public void testMergeRegionsCannotMergeAny() throws Exception {
    TEST_UTIL.getConfiguration().setDouble(RegionsMerger.RESULTING_REGION_UPPER_MARK, 0.5);
    TEST_UTIL.getConfiguration().setInt(RegionsMerger.MAX_ROUNDS_IDLE, 2);
    generateTableData();
    TEST_UTIL.getAdmin().flush(TABLE_NAME);
    final int originalCount = TEST_UTIL.countRows(table);
    List<RegionInfo> result = mergeRegionsToTarget(TABLE_NAME, 3);
    assertEquals(7, result.size());
    assertEquals("Row count before and after merge should be equal",
        originalCount, TEST_UTIL.countRows(table));
  }

  @Test
  public void testMergeRegionsInvalidParams() throws Exception {
    final int originalCount = TEST_UTIL.countRows(table);
    RegionsMerger merger = new RegionsMerger(TEST_UTIL.getConfiguration());
    assertEquals(1, merger.run(new String[]{}));
    assertEquals("Row count before and after merge should be equal",
        originalCount, TEST_UTIL.countRows(table));
  }

  @Test
  public void testRegionHasNoState() throws Exception {
    TEST_UTIL.getConfiguration().setInt(RegionsMerger.MAX_ROUNDS_IDLE, 3);
    generateTableData();
    // Turn on the replication of the table, and then merge two regions, the parent regions will
    // not have the column info:state in the meta table, only the column
    // rep_barrier:seqnumDuringOpen is left. And we should skip the parent regions.
    // Here we manually put a region with no state defined.
    String noStateRegion = TABLE_NAME.getNameAsString()+",0";
    Put put = new Put(Bytes.toBytes(noStateRegion));
    put.add(CellBuilderFactory.create(CellBuilderType.SHALLOW_COPY)
      .setRow(put.getRow())
      .setFamily(HConstants.REPLICATION_BARRIER_FAMILY)
      .setQualifier(HConstants.SEQNUM_QUALIFIER)
      .setTimestamp(put.getTimestamp())
      .setType(Cell.Type.Put)
      .setValue(Bytes.toBytes(1))
      .build());
    MetaTableAccessor.getMetaHTable(TEST_UTIL.getConnection()).put(put);

    TEST_UTIL.getAdmin().flush(TABLE_NAME);
    final int originalCount = TEST_UTIL.countRows(table);
    List<RegionInfo> result = mergeRegionsToTarget(TABLE_NAME, 4);
    assertEquals(4, result.size());
    assertEquals("Row count before and after merge should be equal",
      originalCount, TEST_UTIL.countRows(table));
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

  private List<RegionInfo> mergeRegionsToTarget(TableName tableName, int target) throws Exception {
    RegionsMerger merger = new RegionsMerger(TEST_UTIL.getConfiguration());
    merger.mergeRegions(tableName.getNameWithNamespaceInclAsString(), target);
    return TEST_UTIL.getAdmin().getRegions(tableName);
  }
}
