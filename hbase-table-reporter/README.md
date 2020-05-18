# hbase-table-reporter
Basic report on Table column counts and row sizes. Used when no distributed
execution engine available... Runs a Scan of all data to size. Allows
specifying subset of Regions-in-a-Table and even specifying a
Single Region only.

Here is how you run it to count over the first 20% of the
Regions of the Table using an executor pool of 8 threads (to
make it so we are scanning 8 regions at a time):

```HBASE_CLASSPATH_PREFIX=./hbase-table-reporter-1.0-SNAPSHOT.jar hbase com.apple.hbase.Reporter -t 8 -f 0.2 GENIE2_modality_session```

The output is a report per Region with a totals for all Regions output at the end. Here is
what a single Region report looks like:

```
2020-03-06T20:50:42.473Z region=prod:example,\x0B\x89\xE8\x18Z\xE7RRg/\x05\x81\xE5\xF0.\xB0,1583452813195.d383921761f3ad7b9b1303ee672ff806., duration=PT0.023S
rowSize quantiles [6424.0, 6424.0, 6424.0, 6424.0, 6424.0, 6424.0, 6424.0, 6424.0, 70000.0, 70000.0, 70000.0, 70000.0, 70000.0, 70000.0, 70000.0, 70000.0, 71720.0, 71720.0, 71720.0, 71720.0, 71720.0, 71720.0, 71720.0, 71720.0, 79712.0, 79712.0, 79712.0, 79712.0, 79712.0, 79712.0, 79712.0, 79840.0, 79840.0, 79840.0, 79840.0, 79840.0, 79840.0, 79840.0, 79840.0, 103248.0, 103248.0, 103248.0, 103248.0, 103248.0, 103248.0, 103248.0, 103248.0, 115464.0, 115464.0, 115464.0, 115464.0, 115464.0, 115464.0, 115464.0, 131880.0, 131880.0, 131880.0, 131880.0, 131880.0, 131880.0, 131880.0, 131880.0, 142280.0, 142280.0, 142280.0, 142280.0, 142280.0, 142280.0, 142280.0, 142280.0, 144208.0, 144208.0, 144208.0, 144208.0, 144208.0, 144208.0, 144208.0, 158088.0, 158088.0, 158088.0, 158088.0, 158088.0, 158088.0, 158088.0, 158088.0, 190152.0, 190152.0, 190152.0, 190152.0, 190152.0, 190152.0, 190152.0, 190152.0, 237408.0, 237408.0, 237408.0, 237408.0, 237408.0, 237408.0, 237408.0]
rowSize histo [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 4.0, 8.0, 0.0]
rowSizestats N=13, min=6424.0, max=237408.0
columnCount quantiles [2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0]
columnCount histo [0.0, 13.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
columnCountstats N=13, min=2.0, max=3.0
```
It starts w/ the timestamp for when we wrote this Region report followed by the name of the
Region this report is about and how long it took to generate this report. Then we report 100
quantiles to show size distribution in the Region. The next line is an histogram of sizes
reported and then a count of rows seen and smallest and largest sizes encountered.

## Making Plots

When the above reporter runs, on the end, it dumps the data into files that can be used as src plotting diagrams in gnuplot.
See the tail of the output made by the hbase-table-reporter. Per table, we generate files into the tmp dir with a '''hbase-table-reporter'' prefix.
There'll be one for the table's rowSize histogram, rowSize percentiles, and ditto for column count. There are gnuplot *.p files in
this repo at `src/main/gnuplot` that you can can use plotting. Edit the '.p' files to reference the generated files using
appropriate `histo.p` or `freq.p`.

The first line of the generated files is commented out. It lists vitals like table name, min and max, counts, etc.

Here's a bit of script that might help generating the `.p` files:
```
$ for i in $(ls histogram); do  echo -n \"pwd/$i\"; x=$(head -1 $i | sed -e 's/^# //' | sed -e 's//\\\\/g'); echo " title \"$x\" with lines, \\";  done
```

Or if all the reporter files are in the current directory, something like this to put the `.p` file references into files in the tmp dir:
```
$ for z in rowSize.histo rowSize.per columnCount.histo columnCount.per; do for i in $(ls *$z*); do  echo -n \"`pwd`/$i\"; x=$(head -1 $i | sed -e 's/^# //' | sed -e 's/_/\\\\_/g'); echo " title \"$x\" with lines, \\";  done > /tmp/$z.txt; done 
```
