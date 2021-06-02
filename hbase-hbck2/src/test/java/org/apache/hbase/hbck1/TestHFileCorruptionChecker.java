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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
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
import org.mockito.Mockito;

@Category({MiscTests.class, SmallTests.class})
public class TestHFileCorruptionChecker {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestHFileCorruptionChecker.class);

  private static final HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();

  private String defaultRootDir;
  private String nonDefaultRootDir;
  private String testFsScheme;
  private String localFsScheme;
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

    FileSystem localFileSystem = new LocalFileSystem();
    testFsScheme = TEST_UTIL.getTestFileSystem().getUri().getScheme();
    localFsScheme = localFileSystem.getScheme();
    nonDefaultRootDir =
        TEST_UTIL.getRandomDir().makeQualified(localFileSystem.getUri(),
            localFileSystem.getWorkingDirectory()).toString();
  }

  @Test
  public void testCheckTableDir() throws IOException {
    checkFileSystemScheme(defaultRootDir, testFsScheme, testFsScheme);
  }

  @Test
  public void testCheckTableDirWithNonDefaultRootDir() throws IOException {
    checkFileSystemScheme(nonDefaultRootDir, testFsScheme, localFsScheme);
  }

  private void checkFileSystemScheme(String hbaseRootDir, String defaultFsScheme,
      String hbaseRootFsScheme) throws IOException {
    Configuration conf = TEST_UTIL.getConfiguration();
    conf.set(HConstants.HBASE_DIR, hbaseRootDir);

    // check default filesystem, should be always hdfs
    assertEquals(defaultFsScheme, TEST_UTIL.getTestFileSystem().getUri().getScheme());

    ExecutorService mockExecutor = Mockito.mock(ExecutorService.class);
    HFileCorruptionChecker corruptionChecker =
        new HFileCorruptionChecker(conf, mockExecutor, true);
    // if `FSUtils.listStatusWithStatusFilter` pass, then we're using the configured HBASE_DIR
    corruptionChecker.checkTableDir(HBCKFsUtils.getTableDir(new Path(hbaseRootDir),
        TableName.META_TABLE_NAME));

    assertEquals(hbaseRootFsScheme, corruptionChecker.fs.getScheme());
    if (!defaultFsScheme.equalsIgnoreCase(hbaseRootFsScheme)) {
      assertNotEquals(TEST_UTIL.getTestFileSystem().getUri().getScheme(),
          corruptionChecker.fs.getScheme());
    }
  }
}
