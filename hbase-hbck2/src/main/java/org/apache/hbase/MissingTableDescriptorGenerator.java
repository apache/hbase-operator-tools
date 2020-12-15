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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.FSTableDescriptors;
import org.apache.hadoop.hbase.util.FSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class can be used to generate missing table descriptor file based on the in-memory cache
 * of the active master or based on the file system.
 */
public class MissingTableDescriptorGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(MissingTableDescriptorGenerator.class);

  private final Configuration configuration;
  private FileSystem fs;
  private Path rootDir;

  public MissingTableDescriptorGenerator(Configuration configuration) throws IOException {
    this.configuration = configuration;
    this.rootDir = HBCKFsUtils.getRootDir(this.configuration);
    this.fs = rootDir.getFileSystem(this.configuration);
  }

  /**
   * Trying to generate missing table descriptor. If anything goes wrong, then the method throws
   * IllegalStateException without changing anything. The method follows these steps:
   *
   * - if the table folder is missing, then we return
   * - if the .tableinfo file is not missing, then we return (we don't overwrite it)
   * - if TableDescriptor is cached in master then recover the .tableinfo accordingly
   * - if TableDescriptor is not cached in master, then we create a default .tableinfo file
   *   with the following items:
   *      - the table name
   *      - the column family list (determined based on the file system)
   *      - the default properties for both {@link TableDescriptor} and
   *        {@link ColumnFamilyDescriptor}
   *
   * This method does not change anything in HBase, only writes the new .tableinfo file
   * to the file system.
   *
   * @param tableNameAsString the table name in standard 'table' or 'ns:table' format
   */
  public void generateTableDescriptorFileIfMissing(String tableNameAsString) {
    TableName tableName = TableName.valueOf(tableNameAsString);
    assertTableFolderIsPresent(tableName);
    if (checkIfTableInfoPresent(tableName)) {
      LOG.info("Table descriptor already exists, exiting without changing anything.");
      return;
    }

    FSTableDescriptors fstd;
    try {
      fstd = new FSTableDescriptors(configuration);
    } catch (IOException e) {
      LOG.error("Unable to initialize FSTableDescriptors, exiting without changing anything.", e);
      return;
    }

    Optional<TableDescriptor> tableDescriptorFromMaster = getTableDescriptorFromMaster(tableName);
    try {
      if (tableDescriptorFromMaster.isPresent()) {
        LOG.info("Table descriptor found in the cache of HBase Master, " +
                 "writing it to the file system.");
        fstd.createTableDescriptor(tableDescriptorFromMaster.get(), false);
        LOG.info("Table descriptor written successfully. Orphan table {} fixed.", tableName);
      } else {
        generateDefaultTableInfo(fstd, tableName);
        LOG.info("Table descriptor written successfully.");
        LOG.warn("Orphan table {} fixed with a default .tableinfo file. It is strongly " +
                 "recommended to review the TableDescriptor and modify if necessary.", tableName);
      }
    } catch (IOException e) {
      LOG.error("Exception while writing the table descriptor to the file system for table {}",
                tableName, e);
    }

  }

  private void assertTableFolderIsPresent(TableName tableName) {
    final Path tableDir = HBCKFsUtils.getTableDir(rootDir, tableName);
    try {
      if (!fs.exists(tableDir)) {
        throw new IllegalStateException("Exiting without changing anything. " +
                                        "Table folder not exists: " + tableDir);
      }
      if (!fs.getFileStatus(tableDir).isDirectory()) {
        throw new IllegalStateException("Exiting without changing anything. " +
                                        "Table folder is not a directory: " + tableDir);
      }
    } catch (IOException e) {
      LOG.error("Exception while trying to find table folder for table {}", tableName, e);
      throw new IllegalStateException("Exiting without changing anything. " +
                                      "Can not validate if table folder exists.");
    }
  }

  private boolean checkIfTableInfoPresent(TableName tableName) {
    final Path tableDir = HBCKFsUtils.getTableDir(rootDir, tableName);
    try {
      FileStatus tableInfoFile = FSTableDescriptors.getTableInfoPath(fs, tableDir);
      if (tableInfoFile != null) {
        LOG.info("Table descriptor found for table {} in: {}", tableName, tableInfoFile.getPath());
        return true;
      }
    } catch (IOException e) {
      LOG.error("Exception while trying to find the table descriptor for table {}", tableName, e);
      throw new IllegalStateException("Can not validate if table descriptor exists. " +
                                      "Exiting without changing anything.");
    }
    return false;
  }

  private Optional<TableDescriptor> getTableDescriptorFromMaster(TableName tableName) {
    LOG.info("Trying to fetch table descriptor for orphan table: {}", tableName);
    try (Connection conn = ConnectionFactory.createConnection(configuration);
         Admin admin = conn.getAdmin()) {
      TableDescriptor tds = admin.getDescriptor(tableName);
      return Optional.of(tds);
    } catch (TableNotFoundException e) {
      LOG.info("Table Descriptor not found in HBase Master: {}", tableName);
    } catch (IOException e) {
      LOG.warn("Exception while fetching table descriptor. Is master offline?", e);
    }
    return Optional.empty();
  }

  private void generateDefaultTableInfo(FSTableDescriptors fstd, TableName tableName)
    throws IOException {
    Set<String> columnFamilies = getColumnFamilies(tableName);
    if(columnFamilies.isEmpty()) {
      LOG.warn("No column family found in HDFS for table {}.", tableName);
    } else {
      LOG.info("Column families to be listed in the new table info: {}", columnFamilies);
    }

    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(tableName);
    for (String columnFamily : columnFamilies) {
      final ColumnFamilyDescriptor family = ColumnFamilyDescriptorBuilder.of(columnFamily);
      tableBuilder.setColumnFamily(family);
    }
    fstd.createTableDescriptor(tableBuilder.build(), false);
  }

  private Set<String> getColumnFamilies(TableName tableName) {
    try {
      final Path tableDir = HBCKFsUtils.getTableDir(rootDir, tableName);
      final List<Path> regionDirs = FSUtils.getRegionDirs(fs, tableDir);
      Set<String> columnFamilies = new HashSet<>();
      for (Path regionDir : regionDirs) {
        FileStatus[] familyDirs = fs.listStatus(regionDir, new FSUtils.FamilyDirFilter(fs));
        for (FileStatus familyDir : familyDirs) {
          String columnFamily = familyDir.getPath().getName();
          columnFamilies.add(columnFamily);
        }
      }
      return columnFamilies;
    } catch (IOException e) {
      LOG.error("Exception while trying to find in HDFS the column families for table {}",
                tableName, e);
      throw new IllegalStateException("Unable to determine the list of column families. " +
                                      "Exiting without changing anything.");
    }
  }
}
