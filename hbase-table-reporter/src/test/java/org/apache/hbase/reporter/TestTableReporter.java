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
package org.apache.hbase.reporter;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellBuilderFactory;
import org.apache.hadoop.hbase.CellBuilderType;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

public class TestTableReporter {
  private static final byte[] CF = Bytes.toBytes("cf");
  private static final byte[] Q = Bytes.toBytes("q");

  private List<Cell> makeCells(byte[] row, int columns, int versions) {
    List<Cell> cells = new ArrayList<Cell>(columns);
    for (int j = 0; j < columns; j++) {
      for (int k = versions; k > 0; k--) {
        Cell cell = CellBuilderFactory.create(CellBuilderType.DEEP_COPY).setRow(row).setFamily(CF)
          .setQualifier(Bytes.toBytes(j)).setType(Cell.Type.Put).setTimestamp(k).setValue(row)
          .build();
        cells.add(cell);
      }
    }
    return cells;
  }

  @Test
  public void testSimpleSketching() {
    TableReporter.Sketches sketches = new TableReporter.Sketches();
    final int rows = 10;
    final int columns = 3;
    final int versions = 2;
    for (int i = 0; i < rows; i++) {
      TableReporter.processRowResult(Result.create(makeCells(Bytes.toBytes(i), columns, versions)),
        sketches);
    }
    sketches.print();
    // Just check the column counts. Should be 2.
    double[] columnCounts = sketches.columnCountSketch.getQuantiles(new double[] { 1 });
    assertEquals(columnCounts.length, 1);
    assertEquals((int) columnCounts[0], columns * versions);
  }

  @Test
  public void testAddSketches() {
    TableReporter.Sketches sketches = new TableReporter.Sketches();
    final int rows = 10;
    final int columns = 3;
    final int versions = 2;
    for (int i = 0; i < rows; i++) {
      TableReporter.processRowResult(Result.create(makeCells(Bytes.toBytes(i), columns, versions)),
        sketches);
    }
    sketches.print();
    TableReporter.Sketches sketches2 = new TableReporter.Sketches();
    for (int i = 0; i < rows; i++) {
      TableReporter.processRowResult(Result.create(makeCells(Bytes.toBytes(i), columns, versions)),
        sketches2);
    }
    sketches2.print();
    TableReporter.AccumlatingSketch accumlator = new TableReporter.AccumlatingSketch();
    accumlator.add(sketches);
    accumlator.add(sketches2);
    TableReporter.Sketches sum = accumlator.get();

    // Just check the column counts. Should be 2.
    double[] columnCounts = sum.columnCountSketch.getQuantiles(new double[] { 1 });
    assertEquals(columnCounts.length, 1);
    assertEquals((int) columnCounts[0], columns * versions);
    sum.print();
  }

  private List<Result> makeResults(int rows, int columns) {
    List<Result> results = new ArrayList<>(rows);
    for (int i = 0; i < rows; i++) {
      results.add(Result.create(makeCells(Bytes.toBytes(i), columns, 1)));
    }
    return results;
  }

  @Test
  public void testProcessResultsWithLimit() {
    TableReporter.Sketches sketches = new TableReporter.Sketches();
    assertEquals(3, TableReporter.processResults(makeResults(10, 2), sketches, 3));
    assertEquals(3, sketches.rowSizeSketch.getN());
  }

  @Test
  public void testProcessResultsWithoutLimit() {
    TableReporter.Sketches sketches = new TableReporter.Sketches();
    assertEquals(10, TableReporter.processResults(makeResults(10, 2), sketches, -1));
    assertEquals(10, sketches.rowSizeSketch.getN());
  }

  @Test
  public void testProcessResultsWithLimitCountsPartialRowsOnce() {
    // Each row arrives as two partial Results, as with Scan#setAllowPartialResults(true).
    List<Result> results = new ArrayList<>();
    for (Result row : makeResults(10, 2)) {
      List<Cell> cells = row.listCells();
      results.add(Result.create(cells.subList(0, 1), null, false, true));
      results.add(Result.create(cells.subList(1, 2), null, false, false));
    }
    TableReporter.Sketches sketches = new TableReporter.Sketches();
    assertEquals(3, TableReporter.processResults(results, sketches, 3));
    assertEquals(6, sketches.rowSizeSketch.getN());
  }
}
