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
package org.apache.hbase.hbck1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.apache.hbase.HBCKFsUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;

@Category({MiscTests.class, SmallTests.class})
public class TestHBaseFsck {
  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestHBaseFsck.class);

  private static final HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();

  private String defaultRootDir;
  private String nonDefaultRootDir;
  private FileSystem testFileSystem;
  private LocalFileSystem localFileSystem;
  private Configuration conf;

  @Rule
  public TestName testName = new TestName();

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(3);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void setup() throws IOException {
    conf = TEST_UTIL.getConfiguration();
    // the default is a hdfs directory
    defaultRootDir = TEST_UTIL.getDataTestDirOnTestFS().toString();
    localFileSystem = new LocalFileSystem();
    testFileSystem = TEST_UTIL.getTestFileSystem();
    nonDefaultRootDir =
        TEST_UTIL.getRandomDir().makeQualified(localFileSystem.getUri(),
            localFileSystem.getWorkingDirectory()).toString();
  }

  @Test
  public void testHBaseRootDirWithSameFileSystemScheme() throws IOException,
      ClassNotFoundException {
    checkFileSystemScheme(defaultRootDir, testFileSystem.getUri().getScheme());
  }

  @Test
  public void testHBaseRootDirWithDifferentFileSystemScheme() throws IOException,
      ClassNotFoundException {
    checkFileSystemScheme(nonDefaultRootDir, localFileSystem.getUri().getScheme());
  }

  private void checkFileSystemScheme(String hbaseRootDir, String expectedFsScheme)
      throws IOException, ClassNotFoundException {
    conf.set(HConstants.HBASE_DIR, hbaseRootDir);
    HBaseFsck fsck = new HBaseFsck(conf);
    String actualFsScheme = fsck.getRootFs().getScheme();
    assertEquals(expectedFsScheme, actualFsScheme);
  }

  @Test
  public void testFileLockCallableWithSetHBaseRootDir() throws IOException {
    FileSystem fs = new Path(nonDefaultRootDir).getFileSystem(conf);
    try {
      assertNotEquals(TEST_UTIL.getTestFileSystem().getUri().getScheme(),
          fs.getScheme());

      conf.set(HConstants.HBASE_DIR, nonDefaultRootDir);
      Path expectedLockFilePath = new Path(HBaseFsck.getTmpDir(conf), HBaseFsck.HBCK2_LOCK_FILE);
      HBaseFsck.FileLockCallable fileLockCallable = new HBaseFsck.FileLockCallable(conf,
          HBaseFsck.createLockRetryCounterFactory(conf).create());

      assertTrue(!fs.exists(expectedLockFilePath));
      // make a call and generate the hbck2 lock file to the non default file system
      fileLockCallable.call();
      assertTrue(fs.exists(expectedLockFilePath));
    } finally {
      HBCKFsUtils.delete(fs, new Path(nonDefaultRootDir), true);
    }
  }

}
