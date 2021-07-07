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
package org.apache.hbase.repair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;

import org.apache.hadoop.hbase.HBaseTestingUtility;
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

public class TestMetaRepair {

  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final String NAMESPACE = "TEST";
  private static final TableName TABLE_NAME_WITH_NAMESPACE =
      TableName.valueOf(NAMESPACE, TestMetaRepair.class.getSimpleName());
  private static final TableName TABLE_NAME =
      TableName.valueOf(TestMetaRepair.class.getSimpleName());
  private static final byte[] family = Bytes.toBytes("test");
  private Table table;

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(3);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void setup() throws Exception {
    table = TEST_UTIL.createMultiRegionTable(TABLE_NAME, family, 5);
    TEST_UTIL.waitUntilAllRegionsAssigned(TABLE_NAME);
  }

  @After
  public void tearDown() throws Exception {
    TEST_UTIL.deleteTable(TABLE_NAME);
  }

  @Test
  public void testHbaseAndHdfsRegions() throws Exception {
    MetaRepair metaRepair = new MetaRepair(TEST_UTIL.getConfiguration());
    Map<String, byte[]> hbaseRegions = metaRepair.getMetaRegions(TABLE_NAME.getNameAsString());
    Map<String, RegionInfo> hdfsRegions = metaRepair.getHdfsRegions(TABLE_NAME.getNameAsString());
    assertEquals(5, hbaseRegions.size());
    assertEquals(5, hdfsRegions.size());
    assertTrue(hbaseRegions.keySet().containsAll(hdfsRegions.keySet()));
  }

  @Test
  public void testRepairMetadata() throws Exception {
    MetaRepair metaRepair = new MetaRepair(TEST_UTIL.getConfiguration());
    generateTableData(TABLE_NAME);
    final int originalCount = TEST_UTIL.countRows(table);
    metaRepair.repairMetadata(TABLE_NAME.getNameAsString());
    assertEquals("Row count before and after repair should be equal",
        originalCount, TEST_UTIL.countRows(table));
  }

  @Test
  public void testRepairMetadataWithNameSpace() throws Exception {
    try {
      TEST_UTIL.getAdmin().createNamespace(NamespaceDescriptor.create(NAMESPACE).build());
      Table tableWithNamespace = TEST_UTIL.createMultiRegionTable(TABLE_NAME_WITH_NAMESPACE,
          family, 6);
      MetaRepair metaRepair = new MetaRepair(TEST_UTIL.getConfiguration());
      final int originalCount = TEST_UTIL.countRows(tableWithNamespace);
      metaRepair.repairMetadata(TABLE_NAME_WITH_NAMESPACE.getNameAsString());
      assertEquals("Row count before and after repair should be equal",
          originalCount, TEST_UTIL.countRows(tableWithNamespace));
    } finally {
      TEST_UTIL.deleteTable(TABLE_NAME_WITH_NAMESPACE);
      TEST_UTIL.getAdmin().deleteNamespace(NAMESPACE);
    }
  }

  @Test
  public void testRepairMetadataInvalidParams() throws Exception {
    final int originalCount = TEST_UTIL.countRows(table);
    MetaRepair metaRepair = new MetaRepair(TEST_UTIL.getConfiguration());
    assertEquals(0, metaRepair.run(new String[]{TABLE_NAME.getNameAsString()}));
    assertEquals(1, metaRepair.run(new String[]{}));
    assertEquals(2, metaRepair.run(new String[]{"XXX"}));
    assertEquals("Row count before and after repair should be equal",
        originalCount, TEST_UTIL.countRows(table));
  }

  private void generateTableData(TableName tableName) throws Exception {
    TEST_UTIL.getAdmin().getRegions(tableName).forEach(r -> {
      byte[] key = r.getStartKey().length == 0 ? new byte[]{0} : r.getStartKey();
      Put put = new Put(key);
      put.addColumn(family, Bytes.toBytes("c"), new byte[1024 * 1024]);
      try {
        table.put(put);
      } catch (IOException e) {
        throw new Error("Failed to put row");
      }
    });
  }
}
