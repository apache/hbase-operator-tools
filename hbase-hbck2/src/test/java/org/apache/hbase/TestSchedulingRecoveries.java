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

import java.io.IOException;
import java.util.List;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.Hbck;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestSchedulingRecoveries {
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private HBCK2 hbck2;

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(2);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void before() {
    this.hbck2 = new HBCK2(TEST_UTIL.getConfiguration());
  }

  @Test
  public void testSchedulingSCPWithTwoGoodHosts() throws IOException {
    String sn1 = TEST_UTIL.getHBaseCluster().getRegionServer(0).toString();
    String sn2 = TEST_UTIL.getHBaseCluster().getRegionServer(1).toString();
    try (ClusterConnection connection = this.hbck2.connect(); Hbck hbck = connection.getHbck()) {
      List<Long> pids = this.hbck2.scheduleRecoveries(hbck, new String[] { sn1, sn2 });
      assertEquals(2, pids.size());
      assertTrue(pids.get(0) > 0);
      assertTrue(pids.get(1) > 0);
    }
  }
}
