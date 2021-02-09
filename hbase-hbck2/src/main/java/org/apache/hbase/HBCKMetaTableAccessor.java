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

import static org.apache.hadoop.hbase.HConstants.CATALOG_FAMILY;
import static org.apache.hadoop.hbase.HConstants.REGIONINFO_QUALIFIER;
import static org.apache.hadoop.hbase.HConstants.TABLE_FAMILY;
import static org.apache.hadoop.hbase.HConstants.TABLE_STATE_QUALIFIER;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellBuilder;
import org.apache.hadoop.hbase.CellBuilderFactory;
import org.apache.hadoop.hbase.CellBuilderType;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.RegionLocations;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.RegionReplicaUtil;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.hbase.exceptions.DeserializationException;
import org.apache.hadoop.hbase.master.RegionState;
import org.apache.hadoop.hbase.shaded.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.shaded.protobuf.generated.HBaseProtos;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.util.PairOfSameType;

import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.annotations.VisibleForTesting;

/**
 * hbck's local version of the MetaTableAccessor from the hbase repo
 * A Utility class to facilitate hbck2's access to Meta table.
 */
@InterfaceAudience.Private
public final class HBCKMetaTableAccessor {

  /** The delimiter for meta columns for replicaIds &gt; 0 */
  static final char META_REPLICA_ID_DELIMITER = '_';

  /** A regex for parsing server columns from meta. See above javadoc for meta layout */
  private static final Pattern SERVER_COLUMN_PATTERN
    = Pattern.compile("^server(_[0-9a-fA-F]{4})?$");

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

  /**
   * Returns all regions in meta for the given table.
   * @param conn a valid, open connection.
   * @param table the table to list regions in meta.
   * @return a list of <code>RegionInfo</code> for all table regions present in meta.
   * @throws IOException on any issues related with scanning meta table
   */
  public static List<RegionInfo> getTableRegions(final Connection conn, final TableName table)
      throws IOException {
    final MetaScanner<RegionInfo> scanner = new MetaScanner<>();
    final String startRow = Bytes.toString(table.getName()) + ",,";
    final String stopRow = Bytes.toString(table.getName()) + " ,,";
    return scanner.scanMeta(conn,
      scan -> {
        scan.withStartRow(Bytes.toBytes(startRow));
        scan.withStopRow(Bytes.toBytes(stopRow));
      },
      r -> {
        Cell cell = r.getColumnLatestCell(CATALOG_FAMILY, REGIONINFO_QUALIFIER);
        if(cell != null) {
          RegionInfo info = RegionInfo
            .parseFromOrNull(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
          return info;
        }
        return null;
      });
  }

  /**
   * Returns a list of all tables having related entries on meta.
   * @param conn a valid, open connection.
   * @return a list of <code>TableName</code> for each table having at least one entry in meta.
   * @throws IOException on any issues related with scanning meta table
   */
  public static List<TableName> getTables(final Connection conn) throws IOException {
    MetaScanner<TableName> scanner = new MetaScanner<>();
    return scanner.scanMeta(conn,
      scan -> scan.addColumn(TABLE_FAMILY, TABLE_STATE_QUALIFIER),
      r -> {
        final byte[] rowBytes = r.getRow();
        String table = Bytes.toString(rowBytes);
        if(table.lastIndexOf(HConstants.DELIMITER)>0) {
          table = table.substring(0, table.lastIndexOf(HConstants.DELIMITER));
        }
        return TableName.valueOf(table);
      });
  }

  /**
   * Converts and adds the passed <code>RegionInfo</code> parameter into a valid 'info:regioninfo'
   * cell value in 'hbase:meta'.
   * @param conn a valid, open connection.
   * @param region the region to be inserted in meta.
   * @throws IOException on any issues related with scanning meta table
   */
  public static void addRegionToMeta(Connection conn, RegionInfo region) throws IOException {
    Put put = makePutFromRegionInfo(region,
      System.currentTimeMillis());
    addRegionStateToPut(put, RegionState.State.CLOSED);
    conn.getTable(TableName.META_TABLE_NAME).put(put);
  }

  /**
   * List all valid regions currently in META, excluding parent whoese been completely split.
   * @param conn a valid, open connection.
   * @return a list of all regions in META, excluding split parents.
   * @throws IOException on any issues related with scanning meta table
   */
  public static List<RegionInfo> getAllRegions(Connection conn) throws IOException {
    MetaScanner<RegionInfo> scanner = new MetaScanner<>();
    return scanner.scanMeta(conn,
      scan -> scan.addColumn(HConstants.CATALOG_FAMILY, HConstants.STATE_QUALIFIER),
      r -> {
        Cell cell = r.getColumnLatestCell(HConstants.CATALOG_FAMILY,
          HConstants.REGIONINFO_QUALIFIER);
        RegionInfo info = RegionInfo.parseFromOrNull(cell.getValueArray(),
          cell.getValueOffset(), cell.getValueLength());
        return info.isSplit() ? null : info;
      });
  }


  /**
   * List all dirty metadata currently in META.
   * @param conn a valid, open connection.
   * @return a Map of all dirty metadata in META.
   * @throws IOException on any issues related with scanning meta table
   */
  public static Map<TableName, List<byte[]>> getDirtyMetadata(Connection conn) throws IOException {
    Map<TableName, List<byte[]>> dirtyTableRegions = new HashMap<>();
    Map<String, TableName> tableNameMap = new HashMap<>();
    getTables(conn).forEach(tableName -> tableNameMap.put(tableName.getNameAsString(), tableName));
    Table metaTable = conn.getTable(TableName.META_TABLE_NAME);
    Scan scan = new Scan();
    ResultScanner resultScanner = metaTable.getScanner(scan);
    for (Result result : resultScanner) {
      result.listCells().forEach(cell -> {
        byte[] rowBytes = CellUtil.cloneRow(cell);
        String row = Bytes.toString(rowBytes);
        String tableName = row.split(",")[0];
        if (!tableNameMap.containsKey(tableName)) {
          if (dirtyTableRegions.containsKey(tableNameMap.get(tableName))) {
            dirtyTableRegions.get(tableNameMap.get(tableName)).add(rowBytes);
          } else {
            List<byte[]> list = new ArrayList<>();
            list.add(rowBytes);
            dirtyTableRegions.put(tableNameMap.get(tableName), list);
          }
        }
      });
    }
    return dirtyTableRegions;
  }

  /**
   * Scans all "table:state" cell values existing in meta and returns as a map of
   * <code>TableName</code> as key and <code>TableState</code> as the value.
   * @param conn a valid, open connection.
   * @return a Map of <code>TableName</code> as key and <code>TableState</code> as the value.
   * @throws IOException on any issues related with scanning meta table
   */
  public static Map<TableName, TableState> getAllTablesStates(Connection conn) throws IOException {
    final MetaScanner<Pair<TableName, TableState>> scanner = new MetaScanner<>();
    final Map<TableName, TableState> resultMap = new HashMap<>();
    scanner.scanMeta(conn,
      scan -> scan.addColumn(TABLE_FAMILY, TABLE_STATE_QUALIFIER),
      r -> {
        try {
          TableState state = getTableState(r);
          TableName name = TableName.valueOf(r.getRow());
          resultMap.put(name, state);
        } catch (IOException e) {
          LOG.error(e.getMessage());
        }
        return null;
      });
    return resultMap;
  }


  /**
   * (Copied partially from MetaTableAccessor)
   * @param tableName table we're working with
   * @return start row for scanning META according to query type
   */
  public static byte[] getTableStartRowForMeta(TableName tableName) {
    if (tableName == null) {
      return null;
    }
    byte[] startRow = new byte[tableName.getName().length + 2];
    System.arraycopy(tableName.getName(), 0, startRow, 0,
      tableName.getName().length);
    startRow[startRow.length - 2] = HConstants.DELIMITER;
    startRow[startRow.length - 1] = HConstants.DELIMITER;
    return startRow;
  }

  /**
   * @param tableName table we're working with
   * @return stop row for scanning META according to query type
   */
  public static byte[] getTableStopRowForMeta(TableName tableName) {
    if (tableName == null) {
      return null;
    }
    final byte[] stopRow;
    stopRow = new byte[tableName.getName().length + 3];
    System.arraycopy(tableName.getName(), 0, stopRow, 0,
      tableName.getName().length);
    stopRow[stopRow.length - 3] = ' ';
    stopRow[stopRow.length - 2] = HConstants.DELIMITER;
    stopRow[stopRow.length - 1] = HConstants.DELIMITER;
    return stopRow;
  }

  /** Returns the row key to use for this regionInfo */
  public static byte[] getMetaKeyForRegion(RegionInfo regionInfo) {
    if (regionInfo.isMetaRegion()) {
      return RegionInfoBuilder.newBuilder(regionInfo.getTable())
        .setRegionId(regionInfo.getRegionId())
        .setReplicaId(0)
        .setOffline(regionInfo.isOffline())
        .build().getRegionName();
    } else {
      return RegionInfoBuilder.newBuilder(regionInfo.getTable())
        .setStartKey(regionInfo.getStartKey())
        .setEndKey(regionInfo.getEndKey())
        .setSplit(regionInfo.isSplit())
        .setRegionId(regionInfo.getRegionId())
        .setReplicaId(0)
        .setOffline(regionInfo.isOffline())
        .build().getRegionName();
    }
  }

  public static Put addLocation(Put p, ServerName sn, long openSeqNum, int replicaId)
    throws IOException {
    CellBuilder builder = CellBuilderFactory.create(CellBuilderType.SHALLOW_COPY);
    return p.add(builder.clear()
      .setRow(p.getRow())
      .setFamily(CATALOG_FAMILY)
      .setQualifier(getServerColumn(replicaId))
      .setTimestamp(p.getTimestamp())
      .setType(Cell.Type.Put)
      .setValue(Bytes.toBytes(sn.getAddress().toString()))
      .build())
      .add(builder.clear()
        .setRow(p.getRow())
        .setFamily(CATALOG_FAMILY)
        .setQualifier(getStartCodeColumn(replicaId))
        .setTimestamp(p.getTimestamp())
        .setType(Cell.Type.Put)
        .setValue(Bytes.toBytes(sn.getStartcode()))
        .build())
      .add(builder.clear()
        .setRow(p.getRow())
        .setFamily(CATALOG_FAMILY)
        .setQualifier(getSeqNumColumn(replicaId))
        .setTimestamp(p.getTimestamp())
        .setType(Cell.Type.Put)
        .setValue(Bytes.toBytes(openSeqNum))
        .build());
  }

  /**
   * Count regions in <code>hbase:meta</code> for passed table.
   * @param connection Connection object
   * @param tableName table name to count regions for
   * @return Count or regions in table <code>tableName</code>
   */
  public static int getRegionCount(final Connection connection, final TableName tableName)
    throws IOException {
    try (RegionLocator locator = connection.getRegionLocator(tableName)) {
      List<HRegionLocation> locations = locator.getAllRegionLocations();
      return locations == null ? 0 : locations.size();
    }
  }

  /**
   * Decode table state from META Result.
   * Should contain cell from HConstants.TABLE_FAMILY
   * (Copied from MetaTableAccessor)
   * @return null if not found
   */
  public static TableState getTableState(Result r) throws IOException {
    Cell cell = r.getColumnLatestCell(TABLE_FAMILY, TABLE_STATE_QUALIFIER);
    if (cell == null) {
      return null;
    }
    try {
      return TableState.parseFrom(TableName.valueOf(r.getRow()),
        Arrays.copyOfRange(cell.getValueArray(), cell.getValueOffset(),
          cell.getValueOffset() + cell.getValueLength()));
    } catch (DeserializationException e) {
      throw new IOException(e);
    }
  }

  /**
   * Fetch table state for given table from META table
   * @param conn connection to use
   * @param tableName table to fetch state for
   */
  public static TableState getTableState(Connection conn, TableName tableName)
    throws IOException {
    if (tableName.equals(TableName.META_TABLE_NAME)) {
      return new TableState(tableName, TableState.State.ENABLED);
    }
    Table metaHTable = conn.getTable(TableName.META_TABLE_NAME);
    Get get = new Get(tableName.getName()).addColumn(TABLE_FAMILY, TABLE_STATE_QUALIFIER);
    Result result = metaHTable.get(get);
    return getTableState(result);
  }

  /**
   * Construct PUT for given state
   * @param state new state
   */
  public static Put makePutFromTableState(TableState state, long ts) {
    Put put = new Put(state.getTableName().getName(), ts);
    put.addColumn(TABLE_FAMILY, TABLE_STATE_QUALIFIER,
      state.convert().toByteArray());
    return put;
  }

  /**
   * Generates and returns a Put containing the region into for the catalog table
   */
  public static Put makePutFromRegionInfo(RegionInfo region, long ts) throws IOException {
    Put put = new Put(region.getRegionName(), ts);
    //copied from MetaTableAccessor
    put.add(CellBuilderFactory.create(CellBuilderType.SHALLOW_COPY)
      .setRow(put.getRow())
      .setFamily(HConstants.CATALOG_FAMILY)
      .setQualifier(HConstants.REGIONINFO_QUALIFIER)
      .setTimestamp(put.getTimestamp())
      .setType(Cell.Type.Put)
      // Serialize the Default Replica HRI otherwise scan of hbase:meta
      // shows an info:regioninfo value with encoded name and region
      // name that differs from that of the hbase;meta row.
      .setValue(RegionInfo.toByteArray(RegionReplicaUtil.getRegionInfoForDefaultReplica(region)))
      .build());
    return put;
  }

  private static void addRegionStateToPut(Put put, RegionState.State state) throws IOException {
    put.add(CellBuilderFactory.create(CellBuilderType.SHALLOW_COPY)
      .setRow(put.getRow())
      .setFamily(HConstants.CATALOG_FAMILY)
      .setQualifier(HConstants.STATE_QUALIFIER)
      .setTimestamp(put.getTimestamp())
      .setType(Cell.Type.Put)
      .setValue(Bytes.toBytes(state.name()))
      .build());
  }

  /**
   * Remove state for table from meta
   * (Copied from MetaTableAccessor)
   * @param connection to use for deletion
   * @param table to delete state for
   */
  public static void deleteTableState(Connection connection, TableName table)
    throws IOException {
    long time = EnvironmentEdgeManager.currentTime();
    Delete delete = new Delete(table.getName());
    delete.addColumns(TABLE_FAMILY, HConstants.TABLE_STATE_QUALIFIER, time);
    deleteFromMetaTable(connection, delete);
    LOG.info("Deleted table " + table + " state from META");
  }

  /**
   * Updates state in META
   * @param conn connection to use
   * @param tableName table to look for
   */
  public static void updateTableState(Connection conn, TableName tableName,
    TableState.State actual) throws IOException {
    Put put = makePutFromTableState(new TableState(tableName, actual),
      EnvironmentEdgeManager.currentTime());
    conn.getTable(TableName.META_TABLE_NAME).put(put);
  }

  /**
   * Returns the RegionInfo object from the column {@link HConstants#CATALOG_FAMILY} and
   * <code>qualifier</code> of the catalog table result.
   * (Copied from MetaTableAccessor)
   * @param r a Result object from the catalog table scan
   * @param qualifier Column family qualifier
   * @return An RegionInfo instance or null.
   */
  public static RegionInfo getRegionInfo(final Result r, byte [] qualifier) {
    Cell cell = r.getColumnLatestCell(CATALOG_FAMILY, qualifier);
    if (cell == null) {
      return null;
    }
    return RegionInfo.parseFromOrNull(cell.getValueArray(),
      cell.getValueOffset(), cell.getValueLength());
  }

  /**
   * Returns the HRegionLocation parsed from the given meta row Result
   * for the given regionInfo and replicaId. The regionInfo can be the default region info
   * for the replica.
   * (Copied from MetaTableAccessor)
   * @param r the meta row result
   * @param regionInfo RegionInfo for default replica
   * @param replicaId the replicaId for the HRegionLocation
   * @return HRegionLocation parsed from the given meta row Result for the given replicaId
   */
  private static HRegionLocation getRegionLocation(final Result r, final RegionInfo regionInfo,
    final int replicaId) {
    ServerName serverName = getServerName(r, replicaId);
    long seqNum = getSeqNumDuringOpen(r, replicaId);
    RegionInfo replicaInfo = RegionReplicaUtil.getRegionInfoForReplica(regionInfo, replicaId);
    return new HRegionLocation(replicaInfo, serverName, seqNum);
  }

  /**
   * The latest seqnum that the server writing to meta observed when opening the region.
   * E.g. the seqNum when the result of {@link #getServerName(Result, int)} was written.
   * (Copied from MetaTableAccessor)
   * @param r Result to pull the seqNum from
   * @return SeqNum, or HConstants.NO_SEQNUM if there's no value written.
   */
  private static long getSeqNumDuringOpen(final Result r, final int replicaId) {
    Cell cell = r.getColumnLatestCell(CATALOG_FAMILY, getSeqNumColumn(replicaId));
    if (cell == null || cell.getValueLength() == 0) {
      return HConstants.NO_SEQNUM;
    }
    return Bytes.toLong(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
  }

  /**
   * Returns a {@link ServerName} from catalog table {@link Result}.
   * (Copied from MetaTableAccessor)
   * @param r Result to pull from
   * @return A ServerName instance or null if necessary fields not found or empty.
   */
  @InterfaceAudience.Private // for use by HMaster#getTableRegionRow which is used for testing only
  public static ServerName getServerName(final Result r, final int replicaId) {
    byte[] serverColumn = getServerColumn(replicaId);
    Cell cell = r.getColumnLatestCell(CATALOG_FAMILY, serverColumn);
    if (cell == null || cell.getValueLength() == 0) {
      return null;
    }
    String hostAndPort = Bytes.toString(
      cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
    byte[] startcodeColumn = getStartCodeColumn(replicaId);
    cell = r.getColumnLatestCell(CATALOG_FAMILY, startcodeColumn);
    if (cell == null || cell.getValueLength() == 0) {
      return null;
    }
    try {
      return ServerName.valueOf(hostAndPort,
        Bytes.toLong(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()));
    } catch (IllegalArgumentException e) {
      LOG.error("Ignoring invalid region for server " + hostAndPort + "; cell=" + cell, e);
      return null;
    }
  }

  /**
   * Returns an HRegionLocationList extracted from the result.
   * (Copied from MetaTableAccessor)
   * @return an HRegionLocationList containing all locations for the region range or null if
   *   we can't deserialize the result.
   */
  public static RegionLocations getRegionLocations(final Result r) {
    if (r == null) {
      return null;
    }
    RegionInfo regionInfo = getRegionInfo(r, REGIONINFO_QUALIFIER);
    if (regionInfo == null) {
      return null;
    }

    List<HRegionLocation> locations = new ArrayList<>(1);
    NavigableMap<byte[], NavigableMap<byte[],byte[]>> familyMap = r.getNoVersionMap();

    locations.add(getRegionLocation(r, regionInfo, 0));

    NavigableMap<byte[], byte[]> infoMap = familyMap.get(CATALOG_FAMILY);
    if (infoMap == null) {
      return new RegionLocations(locations);
    }

    // iterate until all serverName columns are seen
    int replicaId = 0;
    byte[] serverColumn = getServerColumn(replicaId);
    SortedMap<byte[], byte[]> serverMap;
    serverMap = infoMap.tailMap(serverColumn, false);

    if (serverMap.isEmpty()) {
      return new RegionLocations(locations);
    }

    for (Map.Entry<byte[], byte[]> entry : serverMap.entrySet()) {
      replicaId = parseReplicaIdFromServerColumn(entry.getKey());
      if (replicaId < 0) {
        break;
      }
      HRegionLocation location = getRegionLocation(r, regionInfo, replicaId);
      // In case the region replica is newly created, it's location might be null. We usually do not
      // have HRL's in RegionLocations object with null ServerName. They are handled as null HRLs.
      if (location.getServerName() == null) {
        locations.add(null);
      } else {
        locations.add(location);
      }
    }

    return new RegionLocations(locations);
  }

  /**
   * Returns the daughter regions by reading the corresponding columns of the catalog table
   * Result.
   * (Copied from MetaTableAccessor)
   * @param data a Result object from the catalog table scan
   * @return pair of RegionInfo or PairOfSameType(null, null) if region is not a split parent
   */
  public static PairOfSameType<RegionInfo> getDaughterRegions(Result data) {
    RegionInfo splitA = getRegionInfo(data, HConstants.SPLITA_QUALIFIER);
    RegionInfo splitB = getRegionInfo(data, HConstants.SPLITB_QUALIFIER);
    return new PairOfSameType<>(splitA, splitB);
  }

  /**
   * Returns the column qualifier for serialized region state
   * (Copied from MetaTableAccessor)
   * @param replicaId the replicaId of the region
   * @return a byte[] for sn column qualifier
   */
  @VisibleForTesting
  static byte[] getServerNameColumn(int replicaId) {
    return replicaId == 0 ? HConstants.SERVERNAME_QUALIFIER
      : Bytes.toBytes(HConstants.SERVERNAME_QUALIFIER_STR + META_REPLICA_ID_DELIMITER
      + String.format(RegionInfo.REPLICA_ID_FORMAT, replicaId));
  }

  /**
   * Returns the column qualifier for server column for replicaId
   * (Copied from MetaTableAccessor)
   * @param replicaId the replicaId of the region
   * @return a byte[] for server column qualifier
   */
  @VisibleForTesting
  public static byte[] getServerColumn(int replicaId) {
    return replicaId == 0
      ? HConstants.SERVER_QUALIFIER
      : Bytes.toBytes(HConstants.SERVER_QUALIFIER_STR + META_REPLICA_ID_DELIMITER
      + String.format(RegionInfo.REPLICA_ID_FORMAT, replicaId));
  }

  /**
   * Returns the column qualifier for server start code column for replicaId
   * (Copied from MetaTableAccessor)
   * @param replicaId the replicaId of the region
   * @return a byte[] for server start code column qualifier
   */
  @VisibleForTesting
  public static byte[] getStartCodeColumn(int replicaId) {
    return replicaId == 0
      ? HConstants.STARTCODE_QUALIFIER
      : Bytes.toBytes(HConstants.STARTCODE_QUALIFIER_STR + META_REPLICA_ID_DELIMITER
      + String.format(RegionInfo.REPLICA_ID_FORMAT, replicaId));
  }

  /**
   * Returns the column qualifier for seqNum column for replicaId
   * (Copied from MetaTableAccessor)
   * @param replicaId the replicaId of the region
   * @return a byte[] for seqNum column qualifier
   */
  @VisibleForTesting
  public static byte[] getSeqNumColumn(int replicaId) {
    return replicaId == 0
      ? HConstants.SEQNUM_QUALIFIER
      : Bytes.toBytes(HConstants.SEQNUM_QUALIFIER_STR + META_REPLICA_ID_DELIMITER
      + String.format(RegionInfo.REPLICA_ID_FORMAT, replicaId));
  }

  /**
   * Parses the replicaId from the server column qualifier. See top of the class javadoc
   * for the actual meta layout
   * (Copied from MetaTableAccessor)
   * @param serverColumn the column qualifier
   * @return an int for the replicaId
   */
  @VisibleForTesting
  static int parseReplicaIdFromServerColumn(byte[] serverColumn) {
    String serverStr = Bytes.toString(serverColumn);

    Matcher matcher = SERVER_COLUMN_PATTERN.matcher(serverStr);
    if (matcher.matches() && matcher.groupCount() > 0) {
      String group = matcher.group(1);
      if (group != null && group.length() > 0) {
        return Integer.parseInt(group.substring(1), 16);
      } else {
        return 0;
      }
    }
    return -1;
  }

  public static class MetaScanner<R> {

    public List<R> scanMeta(final Connection conn, Consumer<Scan> scanDecorator,
        Function<Result, R> scanProcessor) throws IOException {
      final Scan metaScan = new Scan();
      scanDecorator.accept(metaScan);
      final Table metaTable = conn.getTable(TableName.META_TABLE_NAME);
      final ResultScanner rs = metaTable.getScanner(metaScan);
      final List<R> results = new ArrayList<>();
      Result result;
      while((result = rs.next())!=null){
        R processedResult = scanProcessor.apply(result);
        if(processedResult != null) {
          results.add(processedResult);
        }
      }
      return results;
    }
  }
}
