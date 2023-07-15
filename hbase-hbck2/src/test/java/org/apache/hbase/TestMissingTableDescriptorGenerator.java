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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestMissingTableDescriptorGenerator {

  private static final HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final String TABLE_NAME_AS_STRING = "test-1";
  private static final String TABLE_NAME_2_AS_STRING = "test-2";
  private static final String TABLE_NAME_3_AS_STRING = "test-3";
  private static final TableName TABLE_NAME = TableName.valueOf(TABLE_NAME_AS_STRING);
  private static final TableName TABLE_NAME_2 = TableName.valueOf(TABLE_NAME_2_AS_STRING);
  private static final TableName TABLE_NAME_3 = TableName.valueOf(TABLE_NAME_3_AS_STRING);
  private static final byte[] FAMILY_A = Bytes.toBytes("familyA");
  private static final byte[] FAMILY_B = Bytes.toBytes("familyB");
  private static final List<ColumnFamilyDescriptor> COLUMN_FAMILIES =
    asList(ColumnFamilyDescriptorBuilder.of(FAMILY_A), ColumnFamilyDescriptorBuilder.of(FAMILY_B));
  private static final int CUSTOM_MAX_FILE_SIZE = 99 * 1024 * 1024;

  private static final TableDescriptor TABLE_INFO_WITH_DEFAULT_PARAMS =
    TableDescriptorBuilder.newBuilder(TABLE_NAME).setColumnFamilies(COLUMN_FAMILIES).build();

  private static final TableDescriptor TABLE_INFO_2_WITH_DEFAULT_PARAMS =
    TableDescriptorBuilder.newBuilder(TABLE_NAME_2).setColumnFamilies(COLUMN_FAMILIES).build();

  private static final TableDescriptor TABLE_INFO_3_WITH_DEFAULT_PARAMS =
    TableDescriptorBuilder.newBuilder(TABLE_NAME_3).setColumnFamilies(COLUMN_FAMILIES).build();

  private static final TableDescriptor TABLE_INFO_WITH_CUSTOM_MAX_FILE_SIZE =
    TableDescriptorBuilder.newBuilder(TABLE_NAME).setColumnFamilies(COLUMN_FAMILIES)
      .setMaxFileSize(CUSTOM_MAX_FILE_SIZE).build();

  private MissingTableDescriptorGenerator missingTableDescriptorGenerator;
  private HBCKFsTableDescriptors tableDescriptorUtil;
  private Path rootDir;
  private FileSystem fs;

  @Before
  public void before() throws Exception {
    TEST_UTIL.startMiniCluster(1);
    final Configuration conf = TEST_UTIL.getConfiguration();
    missingTableDescriptorGenerator = new MissingTableDescriptorGenerator(conf);

    // creating FSTableDescriptors helper class, with usecache=false, so it will
    // always fetch the table descriptors from the filesystem
    rootDir = TEST_UTIL.getDefaultRootDirPath();
    fs = TEST_UTIL.getTestFileSystem();
    tableDescriptorUtil = new HBCKFsTableDescriptors(fs, rootDir);
  }

  @After
  public void after() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Test
  public void shouldGenerateTableInfoBasedOnCachedTableDescriptor() throws Exception {
    TEST_UTIL.createTable(TABLE_INFO_WITH_CUSTOM_MAX_FILE_SIZE, null);

    // remove the .tableinfo file
    tableDescriptorUtil.deleteTableDescriptorIfExists(TABLE_NAME);

    List<String> tableNames = new ArrayList<>();
    tableNames.add(TABLE_NAME_AS_STRING);
    generateAndVerifyTableDescriptor(tableNames, CUSTOM_MAX_FILE_SIZE);

  }

  @Test
  public void shouldGenerateTableInfoBasedOnFileSystem() throws Exception {
    TEST_UTIL.createTable(TABLE_INFO_WITH_CUSTOM_MAX_FILE_SIZE, null);

    // remove the .tableinfo file
    tableDescriptorUtil.deleteTableDescriptorIfExists(TABLE_NAME);

    // restart HBase (so the table descriptor cache should be cleaned in HBase Master)
    // In this case actually the region belongs to the test table shouldn't be online
    // after the restart. You should find in the logs a warning similar to:
    // "Failed opening region test-1,,1608040700497.5d72e524ae11c5c72c6f3d365f190349.
    // java.io.IOException: Missing table descriptor for 5d72e524ae11c5c72c6f3d365f190349"
    TEST_UTIL.shutdownMiniHBaseCluster();
    Thread.sleep(2000);
    TEST_UTIL.restartHBaseCluster(1);

    List<String> tableNames = new ArrayList<>();
    tableNames.add(TABLE_NAME_AS_STRING);

    // regenerate the .tableinfo file
    generateAndVerifyTableDescriptor(tableNames, TABLE_INFO_WITH_DEFAULT_PARAMS.getMaxFileSize());
  }

  @Test
  public void testTableinfoGeneratedWhenNoTableSpecified() throws Exception {
    TEST_UTIL.createTable(TABLE_INFO_WITH_DEFAULT_PARAMS, null);
    TEST_UTIL.createTable(TABLE_INFO_2_WITH_DEFAULT_PARAMS, null);
    TEST_UTIL.createTable(TABLE_INFO_3_WITH_DEFAULT_PARAMS, null);

    // remove the .tableinfo files
    tableDescriptorUtil.deleteTableDescriptorIfExists(TABLE_NAME);
    tableDescriptorUtil.deleteTableDescriptorIfExists(TABLE_NAME_2);
    tableDescriptorUtil.deleteTableDescriptorIfExists(TABLE_NAME_3);

    List<String> tableNames = new ArrayList<>();
    // pass empty list and check if all the tables repaired
    generateAndVerifyTableDescriptor(tableNames, TABLE_INFO_WITH_DEFAULT_PARAMS.getMaxFileSize());

  }

  private void generateAndVerifyTableDescriptor(List<String> tableNames, long customMaxFileSize)
    throws IOException, InterruptedException {
    // regenerate the .tableinfo file
    missingTableDescriptorGenerator.generateTableDescriptorFileIfMissing(TEST_UTIL.getAdmin(),
      tableNames);

    // list all the tables
    TableName[] tables = TEST_UTIL.getAdmin().listTableNames();

    // verify .tableinfo for all tables
    for (TableName table : tables) {
      // verify table info file content (as the table descriptor should be restored based on the
      // cache in HBase Master, we expect the maxFileSize to be set to the non-default value)
      TableDescriptor descriptor =
        HBCKFsTableDescriptors.getTableDescriptorFromFs(fs, rootDir, table);
      assertEquals(table.getNameAsString(), descriptor.getTableName().getNameAsString());
      assertTrue(descriptor.hasColumnFamily(FAMILY_A));
      assertTrue(descriptor.hasColumnFamily(FAMILY_B));
      assertEquals(customMaxFileSize, descriptor.getMaxFileSize());

      // restart the cluster (the table descriptor cache should be reinitialized in the HBase
      // Master)
      TEST_UTIL.shutdownMiniHBaseCluster();
      Thread.sleep(2000);
      TEST_UTIL.restartHBaseCluster(1);

      // verify the table is working
      try (Table htable = TEST_UTIL.getConnection().getTable(table)) {
        TEST_UTIL.loadRandomRows(htable, FAMILY_A, 10, 10);
      }
    }
  }
}
