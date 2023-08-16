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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestMissingRegionDirsRepairTool {

  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final TableName TABLE_NAME =
    TableName.valueOf(TestMissingRegionDirsRepairTool.class.getSimpleName());
  private static final byte[] family = Bytes.toBytes("f");
  private Table table;

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.getConfiguration().set(HConstants.HREGION_MAX_FILESIZE,
      Long.toString(1024 * 1024 * 3));
    TEST_UTIL.startMiniCluster(3);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void setup() throws Exception {
    table = TEST_UTIL.createMultiRegionTable(TABLE_NAME, family, 4);
    TEST_UTIL.waitUntilAllRegionsAssigned(TABLE_NAME);
  }

  @After
  public void tearDown() throws Exception {
    TEST_UTIL.deleteTable(TABLE_NAME);
  }

  @Test
  public void testMoveRegionDirAndBulkloadFiles() throws Exception {
    List<RegionInfo> regions = TEST_UTIL.getAdmin().getRegions(TABLE_NAME);
    // populate table
    regions.forEach(r -> {
      byte[] key = r.getStartKey().length == 0 ? new byte[] { 0 } : r.getStartKey();
      Put put = new Put(key);
      put.addColumn(family, Bytes.toBytes("c"), new byte[1024 * 1024]);
      try {
        table.put(put);
      } catch (IOException e) {
        throw new Error("Failed to put row");
      }
    });
    TEST_UTIL.getAdmin().flush(TABLE_NAME);
    FileSystem fileSystem = TEST_UTIL.getDFSCluster().getFileSystem();
    Path root = TEST_UTIL.getDefaultRootDirPath();
    Path tablePath = new Path((new Path(root, "data")), "default");
    tablePath = new Path(tablePath, TABLE_NAME.getNameAsString());
    Path regionPath = new Path(tablePath, regions.get(0).getEncodedName());
    Path tmpRegionPath = new Path(root, "test");
    tmpRegionPath = new Path(tmpRegionPath, regions.get(0).getEncodedName());
    // copy the first region entire dir to a temp folder
    HBCKFsUtils.copyFilesParallel(fileSystem, regionPath, fileSystem, tmpRegionPath,
      TEST_UTIL.getConfiguration(), 3);
    // truncates the table, so to delete all current existing hfiles
    TEST_UTIL.truncateTable(TABLE_NAME);
    // confirms the table is now empty
    Table table = TEST_UTIL.getConnection().getTable(TABLE_NAME);
    ResultScanner rs = table.getScanner(new Scan());
    assertTrue(rs.next() == null);
    // moves back the original region back to tables dir
    fileSystem.mkdirs(regionPath);
    HBCKFsUtils.copyFilesParallel(fileSystem, tmpRegionPath, fileSystem, regionPath,
      TEST_UTIL.getConfiguration(), 3);
    MissingRegionDirsRepairTool tool =
      new MissingRegionDirsRepairTool(TEST_UTIL.getConfiguration());
    tool.run(null);
    rs = table.getScanner(new Scan());
    assertNotNull(rs.next());
    // verifies the region dir has been moved away
    assertFalse(fileSystem.exists(regionPath));
  }

}
