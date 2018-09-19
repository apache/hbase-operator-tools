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

import junit.framework.TestCase;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.hbase.master.RegionState;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.logging.log4j.LogManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(os);
    PrintStream oldOut = System.out;
    System.setOut(stream);
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    hbck.run(new String[]{"-h"});
    stream.close();
    os.close();
    System.setOut(oldOut);
    String output = os.toString();
    assertTrue(output, output.startsWith("usage: HBCK2"));
  }

  @Test (expected=NumberFormatException.class)
  public void testCommandWithOptions() throws IOException {
    HBCK2 hbck = new HBCK2(TEST_UTIL.getConfiguration());
    // The 'x' below should cause the NumberFormatException. The Options should all be good.
    hbck.run(new String[]{"bypass", "--waitTime=3", "--force", "x"});
  }
}

