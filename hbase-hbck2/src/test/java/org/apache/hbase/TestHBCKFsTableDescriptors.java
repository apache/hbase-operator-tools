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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableInfoMissingException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * COPIED (partially) from org.apache.hadoop.hbase.util.TestFSTableDescriptors Tests for
 * {@link HBCKFsTableDescriptors}.
 */
@Category({ MiscTests.class, MediumTests.class })
public class TestHBCKFsTableDescriptors {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestHBCKFsTableDescriptors.class);

  private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();
  private static final Logger LOG = LoggerFactory.getLogger(TestHBCKFsTableDescriptors.class);

  @Rule
  public TestName name = new TestName();

  @Test(expected = IllegalArgumentException.class)
  public void testRegexAgainstOldStyleTableInfo() {
    Path p = new Path("/tmp", HBCKFsTableDescriptors.TABLEINFO_FILE_PREFIX);
    int i = HBCKFsTableDescriptors.getTableInfoSequenceId(p);
    assertEquals(0, i);
    // Assert it won't eat garbage -- that it fails
    p = new Path("/tmp", "abc");
    HBCKFsTableDescriptors.getTableInfoSequenceId(p);
  }

  @Test
  public void testFormatTableInfoSequenceId() {
    Path p0 = assertWriteAndReadSequenceId(0);
    // Assert p0 has format we expect.
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < HBCKFsTableDescriptors.WIDTH_OF_SEQUENCE_ID; i++) {
      sb.append("0");
    }
    assertEquals(HBCKFsTableDescriptors.TABLEINFO_FILE_PREFIX + "." + sb.toString(), p0.getName());
    // Check a few more.
    Path p2 = assertWriteAndReadSequenceId(2);
    Path p10000 = assertWriteAndReadSequenceId(10000);
    // Get a .tablinfo that has no sequenceid suffix.
    Path p = new Path(p0.getParent(), HBCKFsTableDescriptors.TABLEINFO_FILE_PREFIX);
    FileStatus fs = new FileStatus(0, false, 0, 0, 0, p);
    FileStatus fs0 = new FileStatus(0, false, 0, 0, 0, p0);
    FileStatus fs2 = new FileStatus(0, false, 0, 0, 0, p2);
    FileStatus fs10000 = new FileStatus(0, false, 0, 0, 0, p10000);
    Comparator<FileStatus> comparator = HBCKFsTableDescriptors.TABLEINFO_FILESTATUS_COMPARATOR;
    assertTrue(comparator.compare(fs, fs0) > 0);
    assertTrue(comparator.compare(fs0, fs2) > 0);
    assertTrue(comparator.compare(fs2, fs10000) > 0);
  }

  private Path assertWriteAndReadSequenceId(final int i) {
    Path p = new Path("/tmp", HBCKFsTableDescriptors.getTableInfoFileName(i));
    int ii = HBCKFsTableDescriptors.getTableInfoSequenceId(p);
    assertEquals(i, ii);
    return p;
  }

  @Test
  public void testReadingHTDFromFS() throws IOException {
    final String name = this.name.getMethodName();
    FileSystem fs = HBCKFsUtils.getRootDirFileSystem(UTIL.getConfiguration());
    TableDescriptor htd = TableDescriptorBuilder.newBuilder(TableName.valueOf(name)).build();
    Path rootdir = UTIL.getDataTestDir(name);
    HBCKFsTableDescriptors fstd = new HBCKFsTableDescriptors(fs, rootdir);
    fstd.createTableDescriptor(htd, false);
    TableDescriptor td2 =
      HBCKFsTableDescriptors.getTableDescriptorFromFs(fs, rootdir, htd.getTableName());
    assertTrue(htd.equals(td2));
  }

  @Test(expected = TableInfoMissingException.class)
  public void testNoSuchTable() throws IOException {
    final String name = "testNoSuchTable";
    FileSystem fs = HBCKFsUtils.getRootDirFileSystem(UTIL.getConfiguration());
    // Cleanup old tests if any detrius laying around.
    Path rootdir = new Path(UTIL.getDataTestDir(), name);
    HBCKFsTableDescriptors htds = new HBCKFsTableDescriptors(fs, rootdir);
    final TableName noSuchTable = TableName.valueOf("NoSuchTable");

    // should throw exception
    HBCKFsTableDescriptors.getTableDescriptorFromFs(fs, rootdir, noSuchTable);
  }

  @Test
  public void testTableInfoFileStatusComparator() {
    FileStatus bare = new FileStatus(0, false, 0, 0, -1,
      new Path("/tmp", HBCKFsTableDescriptors.TABLEINFO_FILE_PREFIX));
    FileStatus future =
      new FileStatus(0, false, 0, 0, -1, new Path("/tmp/tablinfo." + System.currentTimeMillis()));
    FileStatus farFuture = new FileStatus(0, false, 0, 0, -1,
      new Path("/tmp/tablinfo." + System.currentTimeMillis() + 1000));
    FileStatus[] alist = { bare, future, farFuture };
    FileStatus[] blist = { bare, farFuture, future };
    FileStatus[] clist = { farFuture, bare, future };
    Comparator<FileStatus> c = HBCKFsTableDescriptors.TABLEINFO_FILESTATUS_COMPARATOR;
    Arrays.sort(alist, c);
    Arrays.sort(blist, c);
    Arrays.sort(clist, c);
    // Now assert all sorted same in way we want.
    for (int i = 0; i < alist.length; i++) {
      assertTrue(alist[i].equals(blist[i]));
      assertTrue(blist[i].equals(clist[i]));
      assertTrue(clist[i].equals(i == 0 ? farFuture : i == 1 ? future : bare));
    }
  }

  @Test
  public void testCreateTableDescriptorUpdatesIfExistsAlready() throws IOException {
    Path testdir = UTIL.getDataTestDir(name.getMethodName());
    final TableName name = TableName.valueOf(this.name.getMethodName());
    TableDescriptor htd = TableDescriptorBuilder.newBuilder(name).build();
    FileSystem fs = HBCKFsUtils.getRootDirFileSystem(UTIL.getConfiguration());
    HBCKFsTableDescriptors fstd = new HBCKFsTableDescriptors(fs, testdir);
    assertTrue(fstd.createTableDescriptor(htd, false));
    assertFalse(fstd.createTableDescriptor(htd, false));
    htd = TableDescriptorBuilder.newBuilder(htd)
      .setValue(Bytes.toBytes("mykey"), Bytes.toBytes("myValue")).build();
    assertTrue(fstd.createTableDescriptor(htd, false)); // this will re-create
    Path tableDir = fstd.getTableDir(htd.getTableName());
    Path tmpTableDir = new Path(tableDir, HBCKFsTableDescriptors.TMP_DIR);
    FileStatus[] statuses = fs.listStatus(tmpTableDir);
    assertTrue(statuses.length == 0);

    assertEquals(htd, HBCKFsTableDescriptors.getTableDescriptorFromFs(fs, tableDir));
  }

}
