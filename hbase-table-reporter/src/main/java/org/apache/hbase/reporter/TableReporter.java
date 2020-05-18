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

import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.datasketches.quantiles.DoublesSketch;
import org.apache.datasketches.quantiles.DoublesUnion;
import org.apache.datasketches.quantiles.UpdateDoublesSketch;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.ExtendedCell;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseIOException;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.io.HeapSize;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * Run a scan against a table reporting on row size, column size and count.
 *
 * So can run against cdh5, uses loads of deprecated API and copies some Cell sizing methods local.
 */
public class TableReporter {
  private static String GNUPLOT_DATA_SUFFIX = ".gnuplotdata";

  /**
   * Quantile sketches. Has a print that dumps out sketches on stdout.
   * To accumlate Sketches instances, see {@link AccumlatingSketch}
   */
  static class Sketches {
    private static final DoubleSupplier IN_POINT_1_INC = new DoubleSupplier() {
      private BigDecimal accumulator = new BigDecimal(0);
      private final BigDecimal pointOhOne = new BigDecimal(0.01);

      @Override
      public double getAsDouble() {
        double d = this.accumulator.doubleValue();
        this.accumulator = this.accumulator.add(pointOhOne);
        return d;
      }
    };

    /**
     * Make an array of 100 increasing numbers from 0-1.
     */
    static double [] NORMALIZED_RANKS = DoubleStream.generate(IN_POINT_1_INC).limit(100).toArray();

    /**
     * Bins that sort of make sense for the data we're seeing here. After some trial and error.
     */
    static double [] BINS = new double [] {1, 5, 10, 15, 20, 25, 100, 1024, 5120, 10240, 20480, 51200, 102400, 1048576};

    /**
     * Size of row.
     */
    final UpdateDoublesSketch rowSizeSketch;

    /**
     * Count of columns in row.
     */
    final UpdateDoublesSketch columnCountSketch;

    Sketches() {
      this(DoublesSketch.builder().setK(256).build(), DoublesSketch.builder().setK(256).build());
    }

    Sketches(UpdateDoublesSketch rowSizeSketch, UpdateDoublesSketch columnCountSketch) {
      this.rowSizeSketch = rowSizeSketch;
      this.columnCountSketch = columnCountSketch;
    }

    void print(String preamble) {
      System.out.println(preamble);
      print();
    }

    void print() {
      print("rowSize", rowSizeSketch);
      print("columnCount", columnCountSketch);
    }

    private static void print(String label, final DoublesSketch sketch) {
      System.out.println(label + " quantiles " + Arrays.toString(sketch.getQuantiles(NORMALIZED_RANKS)));
      double [] pmfs = sketch.getPMF(BINS);
      // System.out.println(label + " pmfs " + Arrays.toString(pmfs));
      System.out.println(label + " histo " +
          (pmfs == null || pmfs.length == 0?
              "null": Arrays.toString(Arrays.stream(pmfs).map(d -> d * sketch.getN()).toArray())));
      System.out.println(label + "stats N=" + sketch.getN() + ", min=" + sketch.getMinValue() + ", max=" +
          sketch.getMaxValue());
    }
  }

  /**
   * For aggregating {@link Sketches}
   * To add sketches, need a DoublesUnion Sketch.
   */
  static class AccumlatingSketch {
    DoublesUnion rowSizeUnion = DoublesUnion.builder().build();
    DoublesUnion columnSizeUnion = DoublesUnion.builder().build();

    void add(Sketches other) {
      this.rowSizeUnion.update(other.rowSizeSketch);
      this.columnSizeUnion.update(other.columnCountSketch);
    }

    /**
     * @return A Sketches made of current state of aggregation.
     */
    Sketches get() {
      return new Sketches(rowSizeUnion.getResult(), columnSizeUnion.getResult());
    }
  }

  static void processRowResult(Result result, Sketches sketches) {
    // System.out.println(result.toString());
    long rowSize = 0;
    int columnCount = 0;
    for (Cell cell : result.rawCells()) {
      rowSize += estimatedSizeOfCell(cell);
      columnCount += 1;
    }
    sketches.rowSizeSketch.update(rowSize);
    sketches.columnCountSketch.update(columnCount);
  }

  /**
   * @return First <code>fraction</code> of Table's regions.
   */
  private static List<RegionInfo> getRegions(Connection connection, TableName tableName,
      double fraction, String encodedRegionName) throws IOException {
    try (Admin admin = connection.getAdmin()) {
      // Use deprecated API because running against old hbase.
      List<RegionInfo> regions = admin.getRegions(tableName);
      if (regions.size() <= 0) {
        throw new HBaseIOException("No regions found in " + tableName);
      }
      if (encodedRegionName != null) {
        return regions.stream().filter(ri -> ri.getEncodedName().equals(encodedRegionName)).
            collect(Collectors.toCollection(ArrayList::new));
      }
      return regions.subList(0, (int)(regions.size() * fraction)); // Rounds down.
    }
  }

  /**
   * Class that scans a Region to produce a Sketch.
   */
  static class SketchRegion implements Callable<SketchRegion> {
    private final RegionInfo ri;
    private final Connection connection;
    private final TableName tableName;
    private final int limit;
    private Sketches sketches = new Sketches();
    private volatile long duration;

    SketchRegion(Connection connection, TableName tableName, RegionInfo ri, int limit) {
      this.ri = ri;
      this.connection = connection;
      this.tableName = tableName;
      this.limit = limit;
    }

    @Override
    public SketchRegion call() {
      try (Table table = this.connection.getTable(this.tableName)) {
        Scan scan = new Scan();
        scan.setStartRow(this.ri.getStartKey());
        scan.setStopRow(this.ri.getEndKey());
        scan.setAllowPartialResults(true);
        long startTime = System.currentTimeMillis();
        long count = 0;
        try (ResultScanner resultScanner = table.getScanner(scan)) {
          for (Result result : resultScanner) {
            processRowResult(result, sketches);
            count++;
            if (this.limit >= 0 && count <= this.limit) {
              break;
            }
          }
        }
        this.duration = System.currentTimeMillis() - startTime;
      } catch (IOException e) {
        e.printStackTrace();
      }
      return this;
    }

    Sketches getSketches() {
      return this.sketches;
    }

    RegionInfo getRegionInfo() {
      return this.ri;
    }

    long getDuration() {
      return this.duration;
    }
  }

  private static void sketch(Configuration configuration, String tableNameAsStr, int limit,
    double fraction, int threads, String isoNow, String encodedRegionName)
  throws IOException, InterruptedException, ExecutionException {
    TableName tableName = TableName.valueOf(tableNameAsStr);
    AccumlatingSketch totalSketches = new AccumlatingSketch();
    long startTime = System.currentTimeMillis();
    int count = 0;
    try (Connection connection = ConnectionFactory.createConnection(configuration)) {
      // Get list of Regions. If 'fraction', get this fraction of all Regions. If
      // encodedRegionName, then set fraction to 1.0 in case the returned set does not
      // include the encodedRegionName we're looking for.
      List<RegionInfo> regions = getRegions(connection, tableName, fraction, encodedRegionName);
      count = regions.size();
      if (count <= 0) {
        throw new HBaseIOException("Empty regions list; fraction " + fraction +
            " too severe or communication problems?");
      } else {
        System.out.println(Instant.now().toString() + " Scanning " + tableNameAsStr +
            " regions=" + count + ", " + regions);
      }
      ExecutorService es =
          Executors.newFixedThreadPool(threads, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r);
              t.setDaemon(true);
              return t;
            }
          });
      try {
        List<SketchRegion> srs = regions.stream().map(ri -> new SketchRegion(connection, tableName, ri, limit)).
            collect(Collectors.toList());
        List<Future<SketchRegion>> futures = new ArrayList<>(srs.size());
        for (SketchRegion sr: srs) {
          // Do submit rather than inokeall; invokeall blocks until all done. This way I get control back
          // after all submitted.
          futures.add(es.submit(sr));
        }
        // Avoid java.util.ConcurrentModificationException
        List<Future<SketchRegion>> removals = new ArrayList<>();
        while (!futures.isEmpty()) {
          for (Future<SketchRegion> future: futures) {
            if (future.isDone()) {
              SketchRegion sr = future.get();
              sr.getSketches().print(Instant.now().toString() +
                  " region=" + sr.getRegionInfo().getRegionNameAsString() + ", duration=" +
                  (Duration.ofMillis(sr.getDuration()).toString()));
              totalSketches.add(sr.getSketches());
              removals.add(future);
            }
          }
          if (!removals.isEmpty()) {
            futures.removeAll(removals);
            removals.clear();
          }
          Thread.sleep(1000);
        }
      } finally {
        es.shutdown();
      }
    }
    Sketches sketches = totalSketches.get();
    String isoDuration = Duration.ofMillis(System.currentTimeMillis() - startTime).toString();
    sketches.print(Instant.now().toString() + " Totals for " + tableNameAsStr + " regions=" + count +
        ", limit=" + limit + ", fraction=" + fraction + ", took=" + isoDuration);
    // Dump out the gnuplot files. Saves time generating graphs.
    dumpGnuplotDataFiles(isoNow, sketches, tableNameAsStr, count, isoDuration);
  }

  /**
   * This is an estimate of the heap space occupied by a cell. When the cell is of type
   * {@link HeapSize} we call {@link HeapSize#heapSize()} so cell can give a correct value. In other
   * cases we just consider the bytes occupied by the cell components ie. row, CF, qualifier,
   * timestamp, type, value and tags.
   * Note that this can be the JVM heap space (on-heap) or the OS heap (off-heap)
   * @return estimate of the heap space
   */
  public static long estimatedSizeOfCell(final Cell cell) {
    if (cell instanceof HeapSize) {
      return ((HeapSize) cell).heapSize();
    }
    // TODO: Add sizing of references that hold the row, family, etc., arrays.
    return estimatedSerializedSizeOf(cell);
  }

  /**
   * Estimate based on keyvalue's serialization format in the RPC layer. Note that there is an extra
   * SIZEOF_INT added to the size here that indicates the actual length of the cell for cases where
   * cell's are serialized in a contiguous format (For eg in RPCs).
   * @return Estimate of the <code>cell</code> size in bytes plus an extra SIZEOF_INT indicating the
   *         actual cell length.
   */
  public static int estimatedSerializedSizeOf(final Cell cell) {
    if (cell instanceof ExtendedCell) {
      return ((ExtendedCell) cell).getSerializedSize(true) + Bytes.SIZEOF_INT;
    }

    return getSumOfCellElementLengths(cell) +
        // Use the KeyValue's infrastructure size presuming that another implementation would have
        // same basic cost.
        KeyValue.ROW_LENGTH_SIZE + KeyValue.FAMILY_LENGTH_SIZE +
        // Serialization is probably preceded by a length (it is in the KeyValueCodec at least).
        Bytes.SIZEOF_INT;
  }

  /**
   * @return Sum of the lengths of all the elements in a Cell; does not count in any infrastructure
   */
  private static int getSumOfCellElementLengths(final Cell cell) {
    return getSumOfCellKeyElementLengths(cell) + cell.getValueLength() + cell.getTagsLength();
  }

  /**
   * @return Sum of all elements that make up a key; does not include infrastructure, tags or
   *         values.
   */
  private static int getSumOfCellKeyElementLengths(final Cell cell) {
    return cell.getRowLength() + cell.getFamilyLength() + cell.getQualifierLength()
        + KeyValue.TIMESTAMP_TYPE_SIZE;
  }

  private static String getFileNamePrefix(String isoNow, String tableName, String sketchName) {
    return "reporter." + isoNow + "." + tableName + "." + sketchName;
  }

  private static String getFileFirstLine(String tableName, int regions, String isoDuration, UpdateDoublesSketch sketch) {
    return "# " + tableName + " regions=" + regions + ", duration=" + isoDuration + ", N=" + sketch.getN() +
        ", min=" + sketch.getMinValue() + ", max=" + sketch.getMaxValue();
  }

  private static void dumpPercentilesFile(String prefix, String firstLine, UpdateDoublesSketch sketch)
      throws IOException {
    dumpFile(File.createTempFile(prefix + ".percentiles.", GNUPLOT_DATA_SUFFIX),
        firstLine, sketch.getQuantiles(Sketches.NORMALIZED_RANKS));
  }

  private static void dumpHistogramFile(String prefix, String firstLine, UpdateDoublesSketch sketch)
      throws IOException {
    double [] pmfs = sketch.getPMF(Sketches.BINS);
    double [] ds = Arrays.stream(pmfs).map(d -> d * sketch.getN()).toArray();
    dumpFile(File.createTempFile(prefix + ".histograms.", GNUPLOT_DATA_SUFFIX),
        firstLine, ds);
  }

  private static void dumpFile(File file, String firstLine, double [] ds) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write(firstLine);
      writer.newLine();
      for (double d : ds) {
        writer.write(Double.toString(d));
        writer.newLine();
      }
    }
    System.out.println(Instant.now().toString() + " wrote " + file.toString());
  }

  private static void dumpFiles(String prefix, String firstLine, UpdateDoublesSketch sketch) throws IOException {
    dumpPercentilesFile(prefix, firstLine, sketch);
    dumpHistogramFile(prefix, firstLine, sketch);
  }

  /**
   * Write four files, a histogram and percentiles, one each for each of the row size and column count sketches.
   * Tie the four files with isoNow time.
   */
  private static void dumpGnuplotDataFiles(String isoNow, Sketches sketches, String tableName, int regions,
      String isoDuration) throws IOException {
    UpdateDoublesSketch sketch = sketches.columnCountSketch;
    dumpFiles(getFileNamePrefix(isoNow, tableName, "columnCount"),
        getFileFirstLine(tableName, regions, isoDuration, sketch), sketch);
    sketch = sketches.rowSizeSketch;
    dumpFiles(getFileNamePrefix(isoNow, tableName, "rowSize"),
        getFileFirstLine(tableName, regions, isoDuration, sketch), sketch);
  }

  static void usage(Options options) {
    usage(options, null);
  }

  static void usage(Options options, String error) {
    if (error != null) {
      System.out.println("ERROR: " + error);
    }
    // HelpFormatter can't output -Dproperty=value.
    // Options doesn't know how to process -D one=two...i.e.
    // with a space between -D and the property-value... so
    // take control of the usage output and output what
    // Options can parse.
    System.out.println("Usage: reporter <OPTIONS> TABLENAME");
    System.out.println("OPTIONS:");
    System.out.println(" -h,--help        Output this help message");
    System.out.println(" -l,--limit       Scan row limit (per thread): default none");
    System.out.println(" -f,--fraction    Fraction of table Regions to read; between 0 and 1: default 1.0 (all)");
    System.out.println(" -r,--region      Scan this Region only; pass encoded name; 'fraction' is ignored.");
    System.out.println(" -t,--threads     Concurrent thread count (thread per Region); default 1");
    System.out.println(" -Dproperty=value Properties such as the zookeeper to connect to; e.g:");
    System.out.println("                  -Dhbase.zookeeper.quorum=ZK0.remote.cluster.example.org");
  }

  public static void main(String [] args)
  throws ParseException, IOException, ExecutionException, InterruptedException {
    Options options = new Options();
    Option help = Option.builder("h").longOpt("help").
        desc("output this help message").build();
    options.addOption(help);
    Option limitOption = Option.builder("l").longOpt("limit").hasArg().build();
    options.addOption(limitOption);
    Option fractionOption = Option.builder("f").longOpt("fraction").hasArg().build();
    options.addOption(fractionOption);
    Option regionOption = Option.builder("r").longOpt("region").hasArg().build();
    options.addOption(regionOption);
    Option threadsOption = Option.builder("t").longOpt("threads").hasArg().build();
    options.addOption(threadsOption);
    Option configOption = Option.builder("D").valueSeparator().argName("property=value").
        hasArgs().build();
    options.addOption(configOption);
    // Parse command-line.
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(options, args);

    // Process general options.
    if (commandLine.hasOption(help.getOpt()) || commandLine.getArgList().isEmpty()) {
      usage(options);
      System.exit(0);
    }

    int limit = -1;
    String opt = limitOption.getOpt();
    if (commandLine.hasOption(opt)) {
      limit = Integer.parseInt(commandLine.getOptionValue(opt));
    }
    double fraction = 1.0;
    opt = fractionOption.getOpt();
    if (commandLine.hasOption(opt)) {
      fraction = Double.parseDouble(commandLine.getOptionValue(opt));
      if (fraction > 1 || fraction <= 0) {
        usage(options, "Bad fraction: " + fraction + "; fraction must be > 0 and < 1");
        System.exit(0);
      }
    }
    int threads = 1;
    opt = threadsOption.getOpt();
    if (commandLine.hasOption(opt)) {
      threads = Integer.parseInt(commandLine.getOptionValue(opt));
      if (threads > 1000 || threads <= 0) {
        usage(options, "Bad thread count: " + threads + "; must be > 0 and < 1000");
        System.exit(0);
      }
    }

    String encodedRegionName = null;
    opt = regionOption.getOpt();
    if (commandLine.hasOption(opt)) {
      encodedRegionName = commandLine.getOptionValue(opt);
    }

    Configuration configuration = HBaseConfiguration.create();
    opt = configOption.getOpt();
    if (commandLine.hasOption(opt)) {
      // If many options, they all show up here in the keyValues
      // array, one after the other.
      String [] keyValues = commandLine.getOptionValues(opt);
      for (int i = 0; i < keyValues.length;) {
        configuration.set(keyValues[i], keyValues[i + 1]);
        i += 2; // Skip over this key and value to next one.
      }
    }

    // Now process commands.
    String [] commands = commandLine.getArgs();
    if (commands.length < 1) {
      usage(options, "No TABLENAME: " + Arrays.toString(commands));
      System.exit(1);
    }

    String now = Instant.now().toString();
    for (String command : commands) {
      sketch(configuration, command, limit, fraction, threads, now, encodedRegionName);
    }
  }
}
