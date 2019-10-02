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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.yetus.audience.InterfaceAudience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * hbck's local version of the MetaTableAccessor from the hbase repo
 * A Utility class to facilitate hbck2's access to Meta table.
 */
@InterfaceAudience.Private
public final class HBCKMetaTableAccessor {

  /**
   * Private constructor to keep this class from being instantiated.
   */
  private HBCKMetaTableAccessor() {
  }

  private static final Logger LOG = LoggerFactory.getLogger(
      HBCKMetaTableAccessor.class);

  // Constants
  private static final byte[] MERGE_QUALIFIER_PREFIX = Bytes.toBytes("merge");

  public static List<RegionInfo> getMergeRegions(Cell[] cells) {
    if (cells == null) {
      return null;
    }
    List<RegionInfo> regionsToMerge = null;
    for (Cell cell : cells) {
      if (!isMergeQualifierPrefix(cell)) {
        continue;
      }
      // Ok. This cell is that of a info:merge* column.
      RegionInfo ri = RegionInfo
          .parseFromOrNull(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
      if (ri != null) {
        if (regionsToMerge == null) {
          regionsToMerge = new ArrayList<>();
        }
        regionsToMerge.add(ri);
      }
    }
    return regionsToMerge;
  }

  /**
   * Delete the passed <code>RegionInfo</code> from the <code>hbase:meta</code> table.
   *
   * @param connection connection we're using
   * @param regionInfo the regionInfo to delete from the meta table
   * @throws IOException if it's not able to delete the regionInfo
   */
  public static void deleteRegionInfo(Connection connection, RegionInfo regionInfo)
      throws IOException {
    Delete delete = new Delete(regionInfo.getRegionName());
    delete.addFamily(HConstants.CATALOG_FAMILY, HConstants.LATEST_TIMESTAMP);
    deleteFromMetaTable(connection, delete);
    LOG.info("Deleted {}", regionInfo.getRegionNameAsString());
  }

  // Private helper methods

  // COPIED from MetaTableAccessor.isMergeQualifierPrefix()
  private static boolean isMergeQualifierPrefix(Cell cell) {
    // Check to see if has family and that qualifier starts with the MERGE_QUALIFIER_PREFIX
    return CellUtil.matchingFamily(cell, HConstants.CATALOG_FAMILY) && qualifierStartsWith(cell,
        MERGE_QUALIFIER_PREFIX);
  }

  /**
   * Finds if the start of the qualifier part of the Cell matches 'startsWith'
   *
   * COPIED from PrivateCellUtil.qualifierStartsWith()
   * @param left       the cell with which we need to match the qualifier
   * @param startsWith the serialized keyvalue format byte[]
   * @return true if the qualifier have same staring characters, false otherwise
   */
  private static boolean qualifierStartsWith(final Cell left, final byte[] startsWith) {
    if (startsWith == null || startsWith.length == 0) {
      throw new IllegalArgumentException("Cannot pass an empty startsWith");
    }
    return Bytes
        .equals(left.getQualifierArray(), left.getQualifierOffset(), startsWith.length, startsWith,
            0, startsWith.length);
  }

  /**
   * Delete the passed <code>d</code> from the <code>hbase:meta</code> table.
   *
   * COPIED MetaTableAccessor.deleteFromMetaTable()
   * @param connection connection we're using
   * @param d          Delete to add to hbase:meta
   */
  private static void deleteFromMetaTable(final Connection connection, final Delete d)
      throws IOException {
    if (connection == null) {
      throw new NullPointerException("No connection");
    } else if (connection.isClosed()) {
      throw new IOException("connection is closed");
    }
    try (Table t = connection.getTable(TableName.META_TABLE_NAME)) {
      List<Delete> deletes = new ArrayList<>();
      deletes.add(d);
      LOG.debug("Add {} delete to meta table", deletes);
      t.delete(deletes);
    }
  }
}
