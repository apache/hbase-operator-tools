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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
    output = retrieveOptionOutput("-h");
    assertTrue(output, output.startsWith("usage: HBCK2"));
  }

  @Test
  public void testErrorMessage() throws IOException{
    // just chose some of the commands to test for
    String[] cmds = new String[]{"setTableState", "bypass", "scheduleRecoveries"};
    String output;
    for(String cmd: cmds){
      output = retrieveOptionOutput(cmd);
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
    String actualVersionOutput = retrieveOptionOutput("-v").trim();
    assertEquals(expectedVersionOutput, actualVersionOutput);
  }

  private String retrieveOptionOutput(String option) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(os);
    PrintStream oldOut = System.out;
    System.setOut(stream);
    if (option != null) {
      this.hbck2.run(new String[] { option });
    } else {
      this.hbck2.run(null);
    }
    stream.close();
    os.close();
    System.setOut(oldOut);
    return os.toString();
  }
}
