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

import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.util.ByteArrayHashKey;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.HashKey;
import org.apache.hadoop.hbase.util.JenkinsHash;

/**
 * A copy of utilities from {@link org.apache.hadoop.hbase.client.RegionInfo} to copy internal
 * methods into HBCK2 for stability to avoid using Private methods.
 */
public final class HBCKRegionInfo {

  /**
   * Separator used to demarcate the encodedName in a region name in the new format. See description
   * on new format above.
   */
  static final int ENC_SEPARATOR = '.';

  static final int MD5_HEX_LENGTH = 32;

  static final int DEFAULT_REPLICA_ID = 0;

  static final byte REPLICA_ID_DELIMITER = (byte) '_';

  private HBCKRegionInfo() {
  }

  /**
   * Does region name contain its encoded name?
   * @param regionName region name
   * @return boolean indicating if this a new format region name which contains its encoded name.
   */
  public static boolean hasEncodedName(final byte[] regionName) {
    // check if region name ends in ENC_SEPARATOR
    return (regionName.length >= 1)
      && (regionName[regionName.length - 1] == RegionInfo.ENC_SEPARATOR);
  }

  /** Returns the encodedName */
  public static String encodeRegionName(final byte[] regionName) {
    String encodedName;
    if (hasEncodedName(regionName)) {
      // region is in new format:
      // <tableName>,<startKey>,<regionIdTimeStamp>/encodedName/
      encodedName =
        Bytes.toString(regionName, regionName.length - MD5_HEX_LENGTH - 1, MD5_HEX_LENGTH);
    } else {
      // old format region name. First hbase:meta region also
      // use this format.EncodedName is the JenkinsHash value.
      HashKey<byte[]> key = new ByteArrayHashKey(regionName, 0, regionName.length);
      int hashVal = Math.abs(JenkinsHash.getInstance().hash(key, 0));
      encodedName = String.valueOf(hashVal);
    }
    return encodedName;
  }

  /**
   * Separate elements of a regionName. Region name is of the format:
   * <code>tablename,startkey,regionIdTimestamp[_replicaId][.encodedName.]</code>. Startkey can
   * contain the delimiter (',') so we parse from the start and then parse from the end.
   * @return Array of byte[] containing tableName, startKey and id OR null if not parseable as a
   *         region name.
   */
  public static byte[][] parseRegionNameOrReturnNull(final byte[] regionName) {
    int offset = -1;
    for (int i = 0; i < regionName.length; i++) {
      if (regionName[i] == HConstants.DELIMITER) {
        offset = i;
        break;
      }
    }
    if (offset == -1) {
      return null;
    }
    byte[] tableName = new byte[offset];
    System.arraycopy(regionName, 0, tableName, 0, offset);
    offset = -1;

    int endOffset = regionName.length;
    // check whether regionName contains encodedName
    if (
      regionName.length > MD5_HEX_LENGTH + 2 && regionName[regionName.length - 1] == ENC_SEPARATOR
        && regionName[regionName.length - MD5_HEX_LENGTH - 2] == ENC_SEPARATOR
    ) {
      endOffset = endOffset - MD5_HEX_LENGTH - 2;
    }

    // parse from end
    byte[] replicaId = null;
    int idEndOffset = endOffset;
    for (int i = endOffset - 1; i > 0; i--) {
      if (regionName[i] == REPLICA_ID_DELIMITER) { // replicaId may or may not be present
        replicaId = new byte[endOffset - i - 1];
        System.arraycopy(regionName, i + 1, replicaId, 0, endOffset - i - 1);
        idEndOffset = i;
        // do not break, continue to search for id
      }
      if (regionName[i] == HConstants.DELIMITER) {
        offset = i;
        break;
      }
    }
    if (offset == -1) {
      return null;
    }
    byte[] startKey = HConstants.EMPTY_BYTE_ARRAY;
    if (offset != tableName.length + 1) {
      startKey = new byte[offset - tableName.length - 1];
      System.arraycopy(regionName, tableName.length + 1, startKey, 0,
        offset - tableName.length - 1);
    }
    byte[] id = new byte[idEndOffset - offset - 1];
    System.arraycopy(regionName, offset + 1, id, 0, idEndOffset - offset - 1);
    byte[][] elements = new byte[replicaId == null ? 3 : 4][];
    elements[0] = tableName;
    elements[1] = startKey;
    elements[2] = id;
    if (replicaId != null) {
      elements[3] = replicaId;
    }
    return elements;
  }
}
