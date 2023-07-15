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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.Cell.Type;
import org.apache.hadoop.hbase.CellBuilderFactory;
import org.apache.hadoop.hbase.CellBuilderType;
import org.apache.hadoop.hbase.CellScanner;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.exceptions.DeserializationException;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hbase.RegionInfoMismatchTool.MalformedRegion;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRegionInfoMismatchTool {
  private static final Logger LOG = LoggerFactory.getLogger(TestRegionInfoMismatchTool.class);
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private RegionInfoMismatchTool tool;
  private TableName tableName;
  private Connection connection;
  private Admin admin;

  @Rule
  public TestName testName = new TestName();

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(1);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void before() throws IOException {
    this.connection = TEST_UTIL.getConnection();
    this.admin = TEST_UTIL.getAdmin();
    this.tool = new RegionInfoMismatchTool(connection);
    this.tableName = TableName.valueOf(testName.getMethodName());
  }

  @After
  public void after() throws IOException {
    if (admin.tableExists(tableName)) {
      if (admin.isTableEnabled(tableName)) {
        admin.disableTable(tableName);
      }
      admin.deleteTable(tableName);
    }
  }

  @Test
  public void testNoReportOnHealthy() throws Exception {
    admin.createTable(TableDescriptorBuilder.newBuilder(tableName)
      .setColumnFamily(ColumnFamilyDescriptorBuilder.of("f")).setRegionReplication(2).build());
    List<RegionInfo> regions = HBCKMetaTableAccessor.getTableRegions(connection, tableName);

    assertEquals(1, regions.size());
    // Should find no malformed regions on a brand new table
    assertEquals(0, tool.getMalformedRegions().size());

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);

    // Verify that nothing would be printed to the console either.
    tool.run(out, false);
    out.close();
    String outputAsString = baos.toString();
    LOG.info("Output from tool: " + outputAsString);
    assertTrue("Expected no output to be printed",
      outputAsString.contains("Found 0 regions to fix"));
  }

  @Test
  public void testReportOneCorruptRegion() throws Exception {
    admin.createTable(
      TableDescriptorBuilder.newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of("f")).setRegionReplication(2).build(),
      new byte[][] { Bytes.toBytes("a"), Bytes.toBytes("b"), Bytes.toBytes("c") });
    List<RegionInfo> regions = HBCKMetaTableAccessor.getTableRegions(connection, tableName);

    // Log hbase:meta to be helpful
    printMeta(connection);

    assertEquals(4, regions.size());
    // Should find no malformed regions on a brand new table
    List<MalformedRegion> malformedRegions = tool.getMalformedRegions();
    assertEquals("Found malformed regions: " + malformedRegions, 0, malformedRegions.size());

    // Mess up info:regioninfo for the first region in this table.
    RegionInfo regionToCorrupt = regions.get(0);
    RegionInfo corruptedRegion = corruptRegionInfo(regionToCorrupt);
    try (Table meta = connection.getTable(TableName.META_TABLE_NAME)) {
      meta.put(makePutFromRegionInfo(regionToCorrupt, corruptedRegion));
    }

    // Log hbase:meta to be helpful
    printMeta(connection);

    // Run the tool and validate we get the expected number of regions back
    malformedRegions = tool.getMalformedRegions();
    assertEquals("Found malformed regions: " + malformedRegions, 1, malformedRegions.size());

    assertArrayEquals(regionToCorrupt.getEncodedNameAsBytes(),
      encodeRegionName(malformedRegions.get(0).getRegionName()));
    assertEquals(corruptedRegion, malformedRegions.get(0).getRegionInfo());
  }

  @Test
  public void testReportManyCorruptRegions() throws Exception {
    admin.createTable(
      TableDescriptorBuilder.newBuilder(tableName)
        .setColumnFamily(ColumnFamilyDescriptorBuilder.of("f")).setRegionReplication(2).build(),
      new byte[][] { Bytes.toBytes("a"), Bytes.toBytes("b"), Bytes.toBytes("c") });
    List<RegionInfo> regions = HBCKMetaTableAccessor.getTableRegions(connection, tableName);
    LOG.info("Created regions {}", regions);

    assertEquals(4, regions.size());
    // Should find no malformed regions on a brand new table
    List<MalformedRegion> malformedRegions = tool.getMalformedRegions();
    assertEquals("Found malformed regions: " + malformedRegions, 0, malformedRegions.size());

    // For each region in this table, mess up the info:regioninfo
    List<RegionInfo> corruptedRegions = new ArrayList<>();
    for (RegionInfo regionToCorrupt : regions) {
      RegionInfo corruptedRegion = corruptRegionInfo(regionToCorrupt);
      corruptedRegions.add(corruptedRegion);
      try (Table meta = connection.getTable(TableName.META_TABLE_NAME)) {
        meta.put(makePutFromRegionInfo(regionToCorrupt, corruptedRegion));
      }
    }

    // Log hbase:meta to be helpful
    printMeta(connection);

    // Run the tool
    malformedRegions = tool.getMalformedRegions();
    LOG.info("Found malformed regions {}", malformedRegions);
    // Make sure we got back the expected 4 regions
    assertEquals(4, malformedRegions.size());

    // Validate that the tool found the expected regions with the correct data.
    for (int i = 0; i < regions.size(); i++) {
      RegionInfo originalRegion = regions.get(i);
      RegionInfo corruptedRegion = corruptedRegions.get(i);
      assertArrayEquals(
        "Comparing " + Bytes.toStringBinary(originalRegion.getEncodedNameAsBytes()) + " and "
          + Bytes.toStringBinary(encodeRegionName(malformedRegions.get(i).getRegionName())),
        originalRegion.getEncodedNameAsBytes(),
        encodeRegionName(malformedRegions.get(i).getRegionName()));
      assertEquals(corruptedRegion, malformedRegions.get(i).getRegionInfo());
    }
  }

  @Test
  public void testFixOneCorruptRegion() throws Exception {
    // Validates that there is a corrupt region
    testReportOneCorruptRegion();

    // Fix meta (fix=true)
    tool.run(true);

    // Validate the we fixed the corrupt region
    List<MalformedRegion> malformedRegions = tool.getMalformedRegions();
    assertEquals("Found latent malformed regions: " + malformedRegions, 0, malformedRegions.size());
  }

  @Test
  public void testDryRunDoesntUpdateMeta() throws Exception {
    testReportOneCorruptRegion();

    // Do not actually fix meta (fix=false)
    tool.run(false);

    // Validate that the region should still be listed as corrupt
    List<MalformedRegion> malformedRegions = tool.getMalformedRegions();
    assertEquals("1 malformed region should still be present", 1, malformedRegions.size());
  }

  @Test
  public void testFixManyCorruptRegions() throws Exception {
    testReportManyCorruptRegions();

    // Fix meta (fix=true)
    tool.run(true);

    // Validate that we fixed all corrupt regions
    List<MalformedRegion> malformedRegions = tool.getMalformedRegions();
    assertEquals("Found latent malformed regions: " + malformedRegions, 0, malformedRegions.size());
  }

  // Copy from HBCKMetaTableAccessor so we can introduce the "bug" into the cell value
  Put makePutFromRegionInfo(RegionInfo originalRegionInfo, RegionInfo corruptRegionInfo)
    throws IOException {
    System.out.println("Changing " + originalRegionInfo + " to " + corruptRegionInfo);
    Put put = new Put(originalRegionInfo.getRegionName());
    // copied from MetaTableAccessor
    put.add(CellBuilderFactory.create(CellBuilderType.SHALLOW_COPY).setRow(put.getRow())
      .setFamily(HConstants.CATALOG_FAMILY).setQualifier(HConstants.REGIONINFO_QUALIFIER)
      .setType(Type.Put)
      // Hack in our own encoded name.
      .setValue(RegionInfo.toByteArray(corruptRegionInfo)).build());
    return put;
  }

  RegionInfo corruptRegionInfo(RegionInfo region) {
    if (region.getReplicaId() != 0) {
      throw new IllegalArgumentException("Passed in region should be default replica");
    }
    return RegionInfoBuilder.newBuilder(region).setReplicaId(1).build();
  }

  void printMeta(Connection conn) throws IOException, DeserializationException {
    try (Table meta = conn.getTable(TableName.META_TABLE_NAME)) {
      Scan s = new Scan();
      s.addFamily(HConstants.CATALOG_FAMILY).addFamily(HConstants.TABLE_FAMILY);
      try (ResultScanner scanner = meta.getScanner(s)) {
        Result r = null;
        while ((r = scanner.next()) != null) {
          CellScanner cells = r.cellScanner();
          while (cells.advance()) {
            printCell(cells.current());
          }
        }
      }
    }
  }

  void printCell(Cell cell) throws DeserializationException {
    LOG.info(CellUtil.toString(cell, true));
    if (
      Bytes.equals(CellUtil.cloneFamily(cell), HConstants.CATALOG_FAMILY)
        && Bytes.equals(CellUtil.cloneQualifier(cell), HConstants.REGIONINFO_QUALIFIER)
    ) {
      LOG.info("Deserialized RegionInfo=" + RegionInfo.parseFrom(CellUtil.cloneValue(cell)));
    }
  }

  /**
   * Encodes the given Region NAME (the rowkey) into the "encoded Region name" (the MD5 hash).
   */
  byte[] encodeRegionName(byte[] regionName) {
    return Bytes.toBytes(HBCKRegionInfo.encodeRegionName(regionName));
  }
}
