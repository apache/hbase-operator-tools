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
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.Properties;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
/**
 * Does command-line parsing tests. No clusters.
 * @see TestHBCK2 for cluster-tests.
 */
public class TestHBCKCommandLineParsing {
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  /**
   * A 'connected' hbck2 instance.
   */
  private HBCK2 hbck2;

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(3);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void before() throws Exception {
    this.hbck2 = new HBCK2(TEST_UTIL.getConfiguration());
  }

  @Test
  public void testHelp() throws IOException {
    // Passing no argument echoes out the usage info
    String output = retrieveOptionOutput(null);
    assertTrue(output, output.startsWith("usage: HBCK2"));

    // Passing -h/--help does the same
    output = retrieveOptionOutput(new String[]{"-h"});
    assertTrue(output, output.startsWith("usage: HBCK2"));
  }

  @Test
  public void testErrorMessage() throws IOException{
    // just chose some of the commands to test for
    String[] cmds = new String[]{"setTableState", "bypass", "scheduleRecoveries"};
    String output;
    for(String cmd: cmds){
      output = retrieveOptionOutput(new String[]{cmd});
      assertTrue(output, output.startsWith("ERROR: "));
      assertTrue(output, output.contains("FOR USAGE, use the -h or --help option"));
    }
  }

  @Test (expected=NumberFormatException.class)
  public void testCommandWithOptions() throws IOException {
    // The 'x' below should cause the NumberFormatException. The Options should all be good.
    this.hbck2.run(new String[]{"bypass", "--lockWait=3", "--override", "--recursive", "x"});
  }

  @Test (expected=FileNotFoundException.class)
  public void testInputFileOption() throws IOException {
    // The 'x' below should cause the io exception for file not found.
    // The Options should all be good.
    this.hbck2.run(new String[]{"bypass", "--override", "--inputFile", "x"});
  }

  @Test (expected=IllegalArgumentException.class)
  public void testSetRegionStateCommandInvalidState() throws IOException {
    // The 'INVALID_STATE' below should cause the IllegalArgumentException.
    this.hbck2.run(new String[]{"setRegionState", "region_encoded", "INVALID_STATE"});
  }

  @Test
  public void testVersionOption() throws IOException {
    // Read the hbck version from properties file.
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream("hbck2.properties");
    final Properties properties = new Properties();
    properties.load(inputStream);
    String expectedVersionOutput = properties.getProperty("version");
    // Get hbck version option output.
    String actualVersionOutput = retrieveOptionOutput(new String[]{"-v"}).trim();
    assertEquals(expectedVersionOutput, actualVersionOutput);
  }

  @Test
  public void testReplicationNoOption() throws IOException {
    String output = retrieveOptionOutput(new String[]{"replication"});
    assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
  }

  @Test
  public void testReplicationFixShortOption() throws IOException {
    String output = retrieveOptionOutput(new String[]{"replication",  "-f"});
    assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
  }

  @Test
  public void testReplicationFixShortOptionTable() throws IOException {
    String output = retrieveOptionOutput(new String[]{"replication",  "-f", "table"});
    assertTrue(
      output.indexOf("ERROR: No replication barrier(s) on table: table\n") >= 0);
    assertTrue(output.indexOf("ERROR: Unrecognized option: --fix") < 0);
  }

  @Test
  public void testReplicationFixShortOptionInputFile() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "replication", "-f", "-i", input.getPath() });
      assertTrue(
        output.indexOf("ERROR: No replication barrier(s) on table: table\n") >= 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: -i") < 0);
    } finally {
      input.delete();
    }
  }

  @Test
  public void testReplicationFixLongOption() throws IOException {
    String output = retrieveOptionOutput(new String[]{"replication",  "--fix"});
    assertTrue(output.indexOf("ERROR: Unrecognized option: --fix") < 0);
  }

  @Test
  public void testReplicationFixLongOptionTable() throws IOException {
    String output = retrieveOptionOutput(new String[]{"replication",  "--fix", "table"});
    assertTrue(
      output.indexOf("ERROR: No replication barrier(s) on table: table\n") >= 0);
    assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
  }

  @Test
  public void testReplicationFixLongOptionInputFile() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "replication", "--fix", "-i", input.getPath() });
      assertTrue(
        output.indexOf("ERROR: No replication barrier(s) on table: table\n") >= 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: --fix") < 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: -i") < 0);
    } finally {
      input.delete();
    }
  }

  @Test
  public void testReplicationFixShortOptionInputFileLong() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "replication", "-f", "--inputFiles", input.getPath() });
      assertTrue(
        output.indexOf("ERROR: No replication barrier(s) on table: table\n") >= 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: --inputFiles") < 0);
    } finally {
      input.delete();
    }
  }

  @Test
  public void testReplicationFixLongOptionInputFileLong() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "replication", "--fix", "--inputFiles",
          input.getPath() });
      assertTrue(
        output.indexOf("ERROR: No replication barrier(s) on table: table\n") >= 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: --fix") < 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: --inoutFiles") < 0);
    } finally {
      input.delete();
    }
  }

  @Test
  public void testReplicationInputFileLong() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "replication", "--inputFiles", input.getPath() });
      assertTrue(output.indexOf("ERROR: Unrecognized option: --inputFiles") < 0);

    } finally {
      input.delete();
    }
  }

  @Test
  public void testReplicationInputFile() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "replication", "-i", input.getPath() });
      assertTrue(output.indexOf("ERROR: Unrecognized option: -i") < 0);
    } finally {
      input.delete();
    }
  }

  private File createInputFile() throws Exception {
    File f = new File("input");
    try(BufferedWriter writer = new BufferedWriter(
      new OutputStreamWriter(new FileOutputStream(f)))) {
      writer.write("table");
      writer.flush();
    }
    return f;
  }

  @Test
  public void testFilesystemFixShortOption() throws IOException {
    String output = retrieveOptionOutput(new String[]{"filesystem",  "-f"});
    assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
  }

  @Test
  public void testFilesystemFixShortOptionTable() throws IOException {
    String output = retrieveOptionOutput(new String[]{"filesystem",  "-f", "table"});
    assertTrue(output.indexOf("ERROR: Unrecognized option: --fix") < 0);
  }

  @Test
  public void testFilesystemFixShortOptionInputFile() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "filesystem", "-f", "-i", input.getPath() });
      assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: -i") < 0);
    } finally {
      input.delete();
    }
  }

  @Test
  public void testFilesystemFixLongOption() throws IOException {
    String output = retrieveOptionOutput(new String[]{"filesystem",  "--fix"});
    assertTrue(output.indexOf("ERROR: Unrecognized option: --fix") < 0);
  }

  @Test
  public void testFilesystemFixLongOptionTable() throws IOException {
    String output = retrieveOptionOutput(new String[]{"filesystem",  "--fix", "table"});
    assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
  }

  @Test
  public void testFilesystemFixLongOptionInputFile() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "filesystem", "--fix", "-i", input.getPath() });
      assertTrue(output.indexOf("ERROR: Unrecognized option: --fix") < 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: -i") < 0);
    } finally {
      input.delete();
    }
  }

  @Test
  public void testFilesystemFixShortOptionInputFileLong() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "filesystem", "-f", "--inputFiles", input.getPath() });
      assertTrue(output.indexOf("ERROR: Unrecognized option: -f") < 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: --inputFiles") < 0);
    } finally {
      input.delete();
    }
  }

  @Test
  public void testFilesystemFixLongOptionInputFileLong() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "filesystem", "--fix", "--inputFiles",
          input.getPath() });
      assertTrue(output.indexOf("ERROR: Unrecognized option: --fix") < 0);
      assertTrue(output.indexOf("ERROR: Unrecognized option: --inoutFiles") < 0);
    } finally {
      input.delete();
    }
  }

  @Test
  public void testFilesystemInputFileLong() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "filesystem", "--inputFiles", input.getPath() });
      assertTrue(output.indexOf("ERROR: Unrecognized option: --inputFiles") < 0);

    } finally {
      input.delete();
    }
  }

  @Test
  public void testFilesystemInputFile() throws Exception {
    File input = null;
    try {
      input = createInputFile();
      String output =
        retrieveOptionOutput(new String[] { "filesystem", "-i", input.getPath() });
      assertTrue(output.indexOf("ERROR: Unrecognized option: -i") < 0);
    } finally {
      input.delete();
    }
  }

  private String retrieveOptionOutput(String[] options) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(os);
    PrintStream oldOut = System.out;
    System.setOut(stream);
    if (options != null) {
      this.hbck2.run(options);
    } else {
      this.hbck2.run(null);
    }
    stream.close();
    os.close();
    System.setOut(oldOut);
    return os.toString();
  }
}
