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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellBuilderFactory;
import org.apache.hadoop.hbase.CellBuilderType;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableState;
import org.apache.hadoop.hbase.master.RegionState;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.apache.hadoop.hbase.shaded.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.shaded.protobuf.generated.HBaseProtos;

public class TestFsRegionsMetaRecoverer {
  private Connection mockedConnection;
  private FileSystem mockedFileSystem;
  private Table mockedTable;
  private FsRegionsMetaRecoverer fixer;
  private String testTblDir;

  @Before
  public void setup() throws Exception {
    this.mockedConnection = Mockito.mock(Connection.class);
    this.mockedFileSystem = Mockito.mock(FileSystem.class);
    this.mockedTable = Mockito.mock(Table.class);
    Configuration config = HBaseConfiguration.create();
    when(this.mockedConnection.getConfiguration()).thenReturn(config);
    when(this.mockedConnection.getTable(TableName.META_TABLE_NAME)).thenReturn(mockedTable);
    Admin mockedAdmin = Mockito.mock(Admin.class);
    when(this.mockedConnection.getAdmin()).thenReturn(mockedAdmin);
    when(mockedAdmin.tableExists(TableName.valueOf("test-tbl"))).thenReturn(true);
    this.testTblDir = config.get(HConstants.HBASE_DIR) + "/data/default/test-tbl";
    this.fixer = new FsRegionsMetaRecoverer(config, mockedConnection, mockedFileSystem);
  }

  private RegionInfo createRegionInfo(String table) {
    long regionTS = System.currentTimeMillis();
    RegionInfo info =
      RegionInfoBuilder.newBuilder(TableName.valueOf(table)).setRegionId(regionTS).build();
    return info;
  }

  private Cell createCellForRegionInfo(RegionInfo info) {
    byte[] regionInfoValue =
      ProtobufUtil.prependPBMagic(ProtobufUtil.toRegionInfo(info).toByteArray());
    Cell cell = CellBuilderFactory.create(CellBuilderType.SHALLOW_COPY).setRow(info.getRegionName())
      .setFamily(Bytes.toBytes("info")).setQualifier(Bytes.toBytes("regioninfo"))
      .setType(Cell.Type.Put).setValue(regionInfoValue).build();
    return cell;
  }

  private Cell createCellForTableState(TableName tableName) {
    Cell cell = CellBuilderFactory.create(CellBuilderType.SHALLOW_COPY).setRow(tableName.getName())
      .setFamily(Bytes.toBytes("table")).setQualifier(Bytes.toBytes("state")).setType(Cell.Type.Put)
      .setValue(HBaseProtos.TableState.newBuilder().setState(TableState.State.ENABLED.convert())
        .build().toByteArray())
      .build();
    return cell;
  }

  @Test
  public void testFindMissingRegionsInMETANoMissing() throws Exception {
    createRegionInMetaAndFileSystem();
    assertEquals("Should had returned 0 missing regions", 0,
      fixer.findMissingRegionsInMETA("test-tbl").size());
  }

  @Test
  public void testFindMissingRegionsInMETAOneMissing() throws Exception {
    ResultScanner mockedRS = Mockito.mock(ResultScanner.class);
    when(this.mockedTable.getScanner(any(Scan.class))).thenReturn(mockedRS);
    List<Cell> cells = new ArrayList<>();
    Result result = Result.create(cells);
    when(mockedRS.next()).thenReturn(result, (Result) null);
    Path p = new Path(this.testTblDir + "/182182182121");
    FileStatus status = new FileStatus(0, true, 0, 0, 0, p);
    when(mockedFileSystem.listStatus(new Path(this.testTblDir)))
      .thenReturn(new FileStatus[] { status });
    List<Path> missingRegions = fixer.findMissingRegionsInMETA("test-tbl");
    assertEquals("Should had returned 1 missing region", 1, missingRegions.size());
    assertEquals(p, missingRegions.get(0));
  }

  @Test
  public void testPutRegionInfoFromHdfsInMeta() throws Exception {
    RegionInfo info = this.createRegionInfo("test-tbl");
    Path regionPath = new Path("/hbase/data/default/test-tbl/" + info.getEncodedName());
    FSDataInputStream fis = new FSDataInputStream(new TestInputStreamSeekable(info));
    when(this.mockedFileSystem.open(new Path(regionPath, ".regioninfo"))).thenReturn(fis);
    fixer.putRegionInfoFromHdfsInMeta(regionPath);
    Mockito.verify(this.mockedConnection).getTable(TableName.META_TABLE_NAME);
    ArgumentCaptor<Put> captor = ArgumentCaptor.forClass(Put.class);
    Mockito.verify(this.mockedTable).put(captor.capture());
    Put capturedPut = captor.getValue();
    List<Cell> cells = capturedPut.get(HConstants.CATALOG_FAMILY, HConstants.STATE_QUALIFIER);
    assertEquals(1, cells.size());
    String state = Bytes.toString(cells.get(0).getValueArray(), cells.get(0).getValueOffset(),
      cells.get(0).getValueLength());
    assertEquals(RegionState.State.valueOf(state), RegionState.State.CLOSED);
    cells = capturedPut.get(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER);
    byte[] returnedInfo = Bytes.copy(cells.get(0).getValueArray(), cells.get(0).getValueOffset(),
      cells.get(0).getValueLength());
    assertEquals(info, RegionInfo.parseFrom(returnedInfo));
  }

  @Test
  public void testReportTablesMissingRegionsOneMissing() throws Exception {
    ResultScanner mockedRS = Mockito.mock(ResultScanner.class);
    when(this.mockedTable.getScanner(any(Scan.class))).thenReturn(mockedRS);
    List<Cell> cells = new ArrayList<>();
    cells.add(createCellForTableState(TableName.valueOf("test-tbl")));
    Result result = Result.create(cells);
    when(mockedRS.next()).thenReturn(result, (Result) null);
    FileStatus status = new FileStatus();
    Path p = new Path(this.testTblDir + "/182182182121");
    status.setPath(p);
    when(mockedFileSystem.listStatus(new Path(this.testTblDir)))
      .thenReturn(new FileStatus[] { status });
    Map<TableName, List<Path>> report = fixer.reportTablesMissingRegions(null);
    assertEquals("Should had returned 1 missing region", 1, report.size());
  }

  @Test
  public void testFindExtraRegionsInMETANoExtra() throws Exception {
    createRegionInMetaAndFileSystem();
    assertEquals("Should had returned 0 extra regions", 0,
      fixer.findExtraRegionsInMETA("test-tbl").size());
  }

  private void createRegionInMetaAndFileSystem() throws Exception {
    RegionInfo info = createRegionInMeta(Mockito.mock(ResultScanner.class));
    FileStatus status = new FileStatus(0, true, 1, 128, System.currentTimeMillis(),
      new Path(this.testTblDir + "/" + info.getEncodedName()));
    when(mockedFileSystem.listStatus(new Path(this.testTblDir)))
      .thenReturn(new FileStatus[] { status });
  }

  private RegionInfo createRegionInMeta(ResultScanner mockedRS) throws Exception {
    when(this.mockedTable.getScanner(any(Scan.class))).thenReturn(mockedRS);
    RegionInfo info = createRegionInfo("test-tbl");
    List<Cell> cells = new ArrayList<>();
    cells.add(createCellForRegionInfo(info));
    Result result = Result.create(cells);
    when(mockedRS.next()).thenReturn(result, (Result) null);
    return info;
  }

  @Test
  public void testFindExtraRegionsInMETAOneExtra() throws Exception {
    RegionInfo info = createRegionInMeta(Mockito.mock(ResultScanner.class));
    List<HBCKMetaEntry> missingRegions = fixer.findExtraRegionsInMETA("test-tbl");
    assertEquals("Should had returned 1 extra region", 1, missingRegions.size());
    assertEquals(info.getEncodedName(), missingRegions.get(0).getEncodedRegionName());
  }

  @Test
  public void testRemoveRegionInfoFromMeta() throws Exception {
    ResultScanner mockedRsTables = Mockito.mock(ResultScanner.class);
    List<Cell> cells = new ArrayList<>();
    cells.add(createCellForTableState(TableName.valueOf("test-tbl")));
    Result result = Result.create(cells);
    ResultScanner mockedRsRegions = Mockito.mock(ResultScanner.class);
    createRegionInMeta(mockedRsRegions);
    when(this.mockedTable.getScanner(any(Scan.class))).thenReturn(mockedRsTables)
      .thenReturn(mockedRsRegions);
    when(mockedRsTables.next()).thenReturn(result, (Result) null);
    List<String> tableList = new ArrayList<>();
    tableList.add("default:test-tbl");
    fixer.removeExtraRegionsFromMetaForTables(tableList);
    Mockito.verify(this.mockedTable).delete(anyList());
  }

  @Test
  public void testReportTablesExtraRegionsOneExtra() throws Exception {
    ResultScanner mockedRS = Mockito.mock(ResultScanner.class);
    Mockito.when(this.mockedTable.getScanner(Mockito.any(Scan.class))).thenReturn(mockedRS);
    List<Cell> cells = new ArrayList<>();
    cells.add(createCellForTableState(TableName.valueOf("test-tbl")));
    Result result = Result.create(cells);
    Mockito.when(mockedRS.next()).thenReturn(result, (Result) null);
    Map<TableName, List<HBCKMetaEntry>> report = fixer.reportTablesExtraRegions(null);
    assertEquals("Should had returned 1 extra region.", 1, report.size());
  }

  private static final class TestInputStreamSeekable extends FSInputStream {
    private ByteArrayInputStream in;
    private long length;

    private TestInputStreamSeekable(RegionInfo info) throws Exception {
      byte[] bytes = RegionInfo.toDelimitedByteArray(info);
      this.length = bytes.length;
      this.in = new ByteArrayInputStream(bytes);
    }

    @Override
    public void seek(long l) {
      this.in.skip(l);
    }

    @Override
    public long getPos() {
      return this.length - in.available();
    }

    @Override
    public boolean seekToNewSource(long l) {
      this.in.skip(l);
      return true;
    }

    @Override
    public int read() {
      return in.read();
    }
  }
}
