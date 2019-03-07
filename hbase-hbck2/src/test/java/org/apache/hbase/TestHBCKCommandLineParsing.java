/**
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

import org.apache.commons.cli.ParseException;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.logging.log4j.LogManager;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import static org.junit.Assert.*;

/**
 * Does command-line parsing tests. No clusters.
 * @see TestHBCK2 for cluster-tests.
 */
public class TestHBCKCommandLineParsing {
  private static final org.apache.logging.log4j.Logger LOG =
      LogManager.getLogger(TestHBCKCommandLineParsing.class);
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();

  @Test
  public void testHelp() throws ParseException, IOException {
    String output = retrieveOptionOutput("-h");
    assertTrue(output, output.startsWith("usage: HBCK2"));
  }

  @Test (expected=NumberFormatException.class)
  public void testCommandWithOptions() throws IOException {
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    // The 'x' below should cause the NumberFormatException. The Options should all be good.
    hbck.run(new String[]{"bypass", "--lockWait=3", "--override", "--recursive", "x"});
  }

  @Test (expected=IllegalArgumentException.class)
  public void testSetRegionStateCommandInvalidState() throws IOException {
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    // The 'x' below should cause the NumberFormatException. The Options should all be good.
    hbck.run(new String[]{"setRegionState", "region_encoded", "INVALID_STATE"});
  }

  @Test
  public void testVersionOption() throws IOException {
    // Read the hbck version from properties file.
    String line;
    String expectedVersionOutput = "";
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream("hbck2.properties");
    BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
    while ((line = br.readLine()) != null) {
      expectedVersionOutput += line;
    }
    // Get hbck version option output.
    String actualVersionOutput = retrieveOptionOutput("-v").trim();
    assertEquals(expectedVersionOutput, actualVersionOutput);
  }

  private String retrieveOptionOutput(String option) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(os);
    PrintStream oldOut = System.out;
    System.setOut(stream);
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    hbck.run(new String[] { option });
    stream.close();
    os.close();
    System.setOut(oldOut);
    return os.toString();
  }
}

