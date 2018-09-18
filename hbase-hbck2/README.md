# HBCK2

HBCK2 is the successor to [hbck](https://hbase.apache.org/book.html#hbck.in.depth),
the hbase-1.x fixup tool (A.K.A _hbck1_). Use it in place of _hbck1_ making repairs
against hbase-2.x installs.

## _hbck1_
The _hbck_ that ships with hbase-1.x (A.K.A _hbck1_) should not be run against an
hbase-2.x cluster. It may do damage. While _hbck1_ is still bundled inside hbase-2.x
-- to minimize surprise (it has a fat pointer to _HBCK2_ at the head of its help
output) -- it's write-facility has been removed. It can report on the state of an
hbase-2.x cluster but its assessments may be inaccurate.

## Running _HBCK2_
`org.apache.hbase.HBCK2` is the name of the main class. Running the below
will dump out the HBCK2 usage:

~~~~
 $ HBASE_CLASSPATH_PREFIX=/tmp/hbase-hbck2-1.0.0-SNAPSHOT.jar ./bin/hbase org.apache.hbase.HBCK2
~~~~

