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
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.exceptions.DeserializationException;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hbase.HBCKMetaTableAccessor.MetaScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone utility to correct the bug corrected by HBASE-23328 in which
 * the region name in the rowkey of a row in meta does not match the name
 * which is stored in the value of the info:regioninfo column of the same row.
 */
public class RegionInfoMismatchTool {
  private static final Logger LOG = LoggerFactory.getLogger(RegionInfoMismatchTool.class);

  private final Connection connection;

  public RegionInfoMismatchTool(Connection connection) {
    this.connection = connection;
  }

  static class MalformedRegion {
    byte[] regionName;
    RegionInfo regionInfo;

    MalformedRegion(byte[] regionName, RegionInfo regionInfo) {
      this.regionName = regionName;
      this.regionInfo = regionInfo;
    }

    byte[] getRegionName() {
      return regionName;
    }

    RegionInfo getRegionInfo() {
      return regionInfo;
    }

    @Override
    public String toString() {
      return "regionName=" + Bytes.toStringBinary(regionName) + ", regioninfo="
        + regionInfo.toString();
    }
  }

  /**
   * Returns a list of {@link MalformedRegion}'s which exist in meta. If there are
   * no malformed regions, the returned list will be empty.
   */
  List<MalformedRegion> getMalformedRegions() throws IOException {
    try (Table meta = connection.getTable(TableName.META_TABLE_NAME)) {
      MetaScanner<MalformedRegion> scanner = new MetaScanner<>();
      return scanner.scanMeta(connection,
        scan -> scan.addFamily(HConstants.CATALOG_FAMILY),
        r -> {
          Cell riCell = r.getColumnLatestCell(HConstants.CATALOG_FAMILY,
              HConstants.REGIONINFO_QUALIFIER);
          RegionInfo info = RegionInfo.parseFromOrNull(riCell.getValueArray(),
              riCell.getValueOffset(), riCell.getValueLength());
          // Get the expected value from the RegionInfo in the cell value
          byte[] valueEncodedRegionName = info.getEncodedNameAsBytes();
          // Compare that to what is actually in the rowkey
          HBCKMetaTableAccessor.getMetaKeyForRegion(info);
          byte[] rowKeyRegionName = CellUtil.cloneRow(riCell);
          byte[] rowkeyEncodedRegionName = Bytes.toBytes(
              HBCKRegionInfo.encodeRegionName(rowKeyRegionName));
          // If they are equal, we are good.
          if (Arrays.equals(rowkeyEncodedRegionName, valueEncodedRegionName)) {
            // Returning null will cause `scanMeta` to ignore this row
            LOG.debug("Ignoring region {} because rowkey aligns with value", info);
            return null;
          }

          LOG.debug("Found mismatched region {} and {}", Bytes.toStringBinary(rowKeyRegionName),
              Bytes.toStringBinary(valueEncodedRegionName));
          // Only return row/regioninfo pairs that are wrong
          return new MalformedRegion(rowKeyRegionName, info);
        });
    }
  }

  /**
   * Run the RegionInfoMistmatchTool. Use the {@code fix} argument to control whether this method
   * will report problems or fix problems.
   *
   * @param fix True if hbase:meta should be updated. False to report on any problems.
   */
  public void run(boolean fix) throws IOException, DeserializationException {
    run(System.out, fix);
  }

  void run(PrintStream out, boolean fix) throws IOException, DeserializationException {
    List<MalformedRegion> regionsToFix = getMalformedRegions();
    if (!fix) {
      out.println("Fix mode is disabled, printing all malformed regions detected:");
      for (MalformedRegion r : regionsToFix) {
        out.println("Rowkey " + HBCKRegionInfo.encodeRegionName(r.getRegionName())
            + " does not match " + r.getRegionInfo());
      }
    }
    out.println("Found " + regionsToFix.size() + " regions to fix.");
    try (Table meta = connection.getTable(TableName.META_TABLE_NAME)) {
      for (MalformedRegion regionToFix : regionsToFix) {
        final byte[] regionName = regionToFix.getRegionName();
        final RegionInfo wrongRegionInfo = regionToFix.getRegionInfo();

        // The encoded region name is an MD5 hash, but the regionID is what is actually
        // broken by HBASE-23328
        byte[][] regionNameParts = HBCKRegionInfo.parseRegionNameOrReturnNull(regionName);
        if (regionNameParts == null) {
          throw new RuntimeException("Couldn't parse parts from "
              + Bytes.toStringBinary(regionName));
        }
        int i = 0;
        for (byte[] part : regionNameParts) {
          LOG.debug("Region name part[{}]: {}", i++, Bytes.toStringBinary(part));
        }
        // Third component of a region name is just a literal numeric (not a binary-encoded long)
        long regionId = Long.parseLong(Bytes.toString(regionNameParts[2]));
        // HBASE-24500: We cannot use newBuilder(RegionInfo) because it will copy the NAME and encodedName
        // from the original RegionInfo instead of re-computing it. Copy all of the fields by hand
        // which will force the new RegionInfo to recompute the NAME/encodedName fields.
        RegionInfo correctedRegionInfo = RegionInfoBuilder.newBuilder(wrongRegionInfo.getTable())
            // regionId shouldn't need to be re-set
            .setRegionId(regionId)
            .setStartKey(wrongRegionInfo.getStartKey())
            .setEndKey(wrongRegionInfo.getEndKey())
            .setReplicaId(0)
            .setOffline(wrongRegionInfo.isOffline())
            .setSplit(wrongRegionInfo.isSplit())
            .build();

        String rowkeyEncodedRegionName = HBCKRegionInfo.encodeRegionName(regionName);
        String updatedValueEncodedRegionName = correctedRegionInfo.getEncodedName();
        if (!rowkeyEncodedRegionName.equals(updatedValueEncodedRegionName)) {
          out.println("Aborting: sanity-check failed on updated RegionInfo. Expected encoded "
              + "region name " +rowkeyEncodedRegionName + " but got "
              + updatedValueEncodedRegionName + ".");
          out.println("Incorrectly created RegionInfo was: " + correctedRegionInfo);
          throw new RuntimeException("Failed sanity-check on corrected RegionInfo");
        }

        out.println("Updating RegionInfo for " + Bytes.toStringBinary(regionName) + " to "
            + correctedRegionInfo);

        // Write the update back to meta.
        if (fix) {
          meta.put(HBCKMetaTableAccessor.makePutFromRegionInfo(correctedRegionInfo,
                System.currentTimeMillis()));
        }
      }
      if (!fix) {
        out.println("Fix mode is not enabled, hbase:meta was not updated. See the tool output for"
            + " a list of detected problematic regions. Re-run the tool without the dry run option"
            + " to persist updates to hbase:meta.");
      }
    }
  }
}
