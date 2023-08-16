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

/**
 * Check versions.
 */
public final class Version {
  // Copied from hbase VersionInfo.
  private static final int VERY_LARGE_NUMBER = 100000;
  private static final int MAJOR = 0;
  private static final int MINOR = 1;
  private static final int PATCH = 2;

  private Version() {
  }

  /**
   * @param thresholdVersions List of versions from oldest to newest.
   * @return true if <code>version</code> is greater-than or equal to thresholdVersions. For
   *         example, if passed threshold list is <code>{"2.0.2", "2.1.3", "2.2.1"}</code> and the
   *         version is 2.1.2 then the result should be false since 2.1.2 is less than the matching
   *         passed-in 2.1.3 but if version is 2.1.5 then we return true.
   */
  static boolean check(final String version, String... thresholdVersions) {
    if (thresholdVersions == null) {
      return true;
    }
    boolean supported = false;
    // Components of the server version string.
    String[] versionComponents = getVersionComponents(version);
    boolean excessiveMajor = false;
    boolean excessiveMinor = false;
    for (String thresholdVersion : thresholdVersions) {
      // Get components of current threshold version.
      String[] thresholdVersionComponents = getVersionComponents(thresholdVersion);
      int serverMajor = Integer.parseInt(versionComponents[MAJOR]);
      int thresholdMajor = Integer.parseInt(thresholdVersionComponents[MAJOR]);
      if (serverMajor > thresholdMajor) {
        excessiveMajor = true;
        continue;
      }
      excessiveMajor = false;
      if (serverMajor < thresholdMajor) {
        continue;
      }
      int serverMinor = Integer.parseInt(versionComponents[MINOR]);
      int thresholdMinor = Integer.parseInt(thresholdVersionComponents[MINOR]);
      if (serverMinor > thresholdMinor) {
        excessiveMinor = true;
        continue;
      }
      excessiveMinor = false;
      if (serverMinor < thresholdMinor) {
        continue;
      }
      if (
        Integer.parseInt(versionComponents[PATCH])
            >= Integer.parseInt(thresholdVersionComponents[PATCH])
      ) {
        supported = true;
      }
      break;
    }
    return supported || excessiveMajor || excessiveMinor;
  }

  /**
   * Copied from hbase VersionInfo. Returns the version components as String objects Examples:
   * "1.2.3" returns ["1", "2", "3"], "4.5.6-SNAPSHOT" returns ["4", "5", "6", "-1"] "4.5.6-beta"
   * returns ["4", "5", "6", "-2"], "4.5.6-alpha" returns ["4", "5", "6", "-3"] "4.5.6-UNKNOW"
   * returns ["4", "5", "6", "-4"]
   * @return the components of the version string
   */
  private static String[] getVersionComponents(final String version) {
    assert (version != null);
    String[] strComps = version.split("[\\.-]");
    assert (strComps.length > 0);

    String[] comps = new String[strComps.length];
    for (int i = 0; i < strComps.length; ++i) {
      if (strComps[i].matches("\\d+")) {
        comps[i] = strComps[i];
      } else if (strComps[i] == null || strComps[i].isEmpty()) {
        comps[i] = String.valueOf(VERY_LARGE_NUMBER);
      } else {
        if ("SNAPSHOT".equals(strComps[i])) {
          comps[i] = "-1";
        } else if ("beta".equals(strComps[i])) {
          comps[i] = "-2";
        } else if ("alpha".equals(strComps[i])) {
          comps[i] = "-3";
        } else {
          comps[i] = "-4";
        }
      }
    }
    return comps;
  }
}
