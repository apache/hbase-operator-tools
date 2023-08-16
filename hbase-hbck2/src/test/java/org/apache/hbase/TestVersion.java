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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestVersion {
  @Test
  public void testThreshold() {
    assertFalse(Version.check("2.0.2", "10.0.0"));
  }

  @Test
  public void testCheckVersion202() {
    assertFalse(Version.check("2.0.2", HBCK2.MINIMUM_HBCK2_VERSION));
  }

  @Test
  public void testCheckVersion210() {
    assertFalse(Version.check("2.1.0", HBCK2.MINIMUM_HBCK2_VERSION));
  }

  @Test
  public void testCheckVersionSpecial210() {
    assertFalse(Version.check("2.1.0-patchedForHBCK2", HBCK2.MINIMUM_HBCK2_VERSION));
  }

  @Test
  public void testCheckVersion203() {
    assertTrue(Version.check("2.0.3", HBCK2.MINIMUM_HBCK2_VERSION));
  }

  @Test
  public void testCheckVersion211() {
    assertTrue(Version.check("2.1.1", HBCK2.MINIMUM_HBCK2_VERSION));
  }

  @Test
  public void testExcessiveMajor() {
    assertTrue(Version.check("5.0.1", HBCK2.MINIMUM_HBCK2_VERSION));
  }

  @Test
  public void testExcessiveMinor() {
    assertTrue(Version.check("2.10.1", HBCK2.MINIMUM_HBCK2_VERSION));
  }

  @Test
  public void testInferiorMinor() {
    assertFalse(Version.check("1.0.0", HBCK2.MINIMUM_HBCK2_VERSION));
  }

  @Test
  public void testABunch() {
    assertTrue(Version.check("2.1.1-patchedForHBCK2", HBCK2.MINIMUM_HBCK2_VERSION));
    assertTrue(Version.check("3.0.1", HBCK2.MINIMUM_HBCK2_VERSION));
    assertTrue(Version.check("4.0.0", HBCK2.MINIMUM_HBCK2_VERSION));
  }
}
