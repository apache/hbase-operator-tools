<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Apache HBase HBCK2 Tool

HBCK2 is the successor to [hbck](https://hbase.apache.org/book.html#hbck.in.depth), the hbase-1.x fixup tool (A.K.A _hbck1_). Use it in place of _hbck1_ making repairs against hbase-2.x installs.

## _hbck1_
The _hbck_ tool that ships with hbase-1.x (A.K.A _hbck1_) should not be run against an
hbase-2.x cluster. It may do damage. While _hbck1_ is still bundled inside hbase-2.x
-- to minimize surprise (it has a fat pointer to _HBCK2_ at the head of its help
output) -- it's write-facility (`-fix`) has been removed. It can report on the state
of an hbase-2.x cluster but its assessments are likely inaccurate since it does not
understand the internal workings of an hbase-2.x.

_HBCK2_ does much less than _hbck1_ because many of the class of problems
_hbck1_ addressed are either no longer issues in hbase-2.x, or we've made
(or will make) a dedicated tool to do what _hbck1_ used incorporate. _HBCK2_ also
works in a manner that differs from how _hbck1_ operated, asking the HBase
Master to do its bidding, rather than replicate functionality outside of the
Master inside the _hbck1_ tool.

## Building _HBCK2_

Run:
```
mvn install
```
The built _HBCK2_ fat jar will be in the `target` sub-directory.

## Running _HBCK2_
`org.apache.hbase.HBCK2` is the name of the _HBCK2_ main class. After building
_HBCK2_ to generate the _HBCK2_ jar file, running the below will dump out the _HBCK2_ usage:

~~~~
 $ HBASE_CLASSPATH_PREFIX=./hbase-hbck2-1.0.0-SNAPSHOT.jar ./bin/hbase org.apache.hbase.HBCK2
~~~~

```
usage: HBCK2 [OPTIONS] COMMAND <ARGS>

Options:
 -d,--debug                                 run with debug output
 -h,--help                                  output this help message
 -p,--hbase.zookeeper.property.clientPort   port of target hbase ensemble
 -q,--hbase.zookeeper.quorum <arg>          ensemble of target hbase
 -v,--version                               this hbck2 version
 -z,--zookeeper.znode.parent                parent znode of target hbase

Commands:
 assigns [OPTIONS] <ENCODED_REGIONNAME>...
   Options:
    -o,--override  override ownership by another procedure
   A 'raw' assign that can be used even during Master initialization.
   Skirts Coprocessors. Pass one or more encoded RegionNames.
   1588230740 is the hard-coded name for the hbase:meta region and
   de00010733901a05f5a2a3a382e27dd4 is an example of what a user-space
   encoded Region name looks like. For example:
     $ HBCK2 assign 1588230740 de00010733901a05f5a2a3a382e27dd4
   Returns the pid(s) of the created AssignProcedure(s) or -1 if none.

 bypass [OPTIONS] <PID>...
   Options:
    -o,--override   override if procedure is running/stuck
    -r,--recursive  bypass parent and its children. SLOW! EXPENSIVE!
    -w,--lockWait   milliseconds to wait on lock before giving up;
default=1
   Pass one (or more) procedure 'pid's to skip to procedure finish.
   Parent of bypassed procedure will also be skipped to the finish.
   Entities will be left in an inconsistent state and will require
   manual fixup. May need Master restart to clear locks still held.
   Bypass fails if procedure has children. Add 'recursive' if all
   you have is a parent pid to finish parent and children. This
   is SLOW, and dangerous so use selectively. Does not always work.

 unassigns <ENCODED_REGIONNAME>...
   Options:
    -o,--override  override ownership by another procedure
   A 'raw' unassign that can be used even during Master initialization.
   Skirts Coprocessors. Pass one or more encoded RegionNames:
   1588230740 is the hard-coded name for the hbase:meta region and
   de00010733901a05f5a2a3a382e27dd4 is an example of what a user-space
   encoded Region name looks like. For example:
     $ HBCK2 unassign 1588230740 de00010733901a05f5a2a3a382e27dd4
   Returns the pid(s) of the created UnassignProcedure(s) or -1 if none.

 setTableState <TABLENAME> <STATE>
   Possible table states: ENABLED, DISABLED, DISABLING, ENABLING
   To read current table state, in the hbase shell run:
     hbase> get 'hbase:meta', '<TABLENAME>', 'table:state'
   A value of \x08\x00 == ENABLED, \x08\x01 == DISABLED, etc.
   An example making table name 'user' ENABLED:
     $ HBCK2 setTableState users ENABLED
   Returns whatever the previous table state was.
```

## _HBCK2_ Overview
_HBCK2_ is currently a simple tool that does one thing at a time only.

_HBCK2_ does not do diagnosis, leaving that function to other tooling,
described below.

In hbase-2.x, the Master is the final arbiter of all state, so a general principal of
_HBCK2_ is that it asks the Master to effect all repair. This means a Master must be
up before you can run an _HBCK2_ command.

_HBCK2_ works by making use of an intentionally obscured `HbckService` hosted on the
Master. The Service publishes a few methods for the _HBCK2_ tool to pull on. The
first thing _HBCK2_ does is poke the cluster to ensure the service is available.
It will fail if it is not or if the `HbckService` is lacking a wanted facility.
_HBCK2_ versions should be able to work across multiple hbase-2 releases. It will
fail with a complaint if it is unable to run. There is no `HbckService` in versions
of hbase before 2.0.3 and 2.1.1. _HBCK2_ will not work against these versions.

## Finding Problems

While _hbck1_ performed analysis reporting your cluster GOOD or BAD, _HBCK2_
is less presumptious. In hbase-2.x, the operator figures what needs fixing and
then uses tooling including _HBCK2_ to do fixup.

To figure issues in assignment, make use of the following utilities.

### Diagnosis Tooling

#### Master Logs

The Master runs all assignments, server crash handling, cluster start and
stop, etc. In hbase-2.x, all that the Master does has been cast as
Procedures run on a state machine engine. See
[Procedure Framework](https://hbase.apache.org/book.html#pv2)
and [Assignment Manager](https://hbase.apache.org/book.html#amv2)
for detail on how this new infrastructure works. Each Procedure has a
unique Procedure `id`, its `pid`, that it lists on each logging.
Following the _pid_, you can trace the lifecycle of a Procedure in the
Master logs as Procedures transition from start, through each of the
Procedure's various stages to finish. Some Procedures spawn sub-procedures,
wait on their Children, and then themselves finish. Each child logs
its _pid_ but also its _ppid_; its parent's _pid_.

Generally all runs problem free but if some unforeseen circumstance
arises, the assignment framework may sustain damage requiring
operator intervention.  Below we will discuss some such scenarios
but they can manifest in the Master log as a Region being _STUCK_ or
a Procedure transitioning an entity -- a Region or a Table --
may be blocked because another Procedure holds the exclusive lock
and is not letting go.

_STUCK_ Procedures look like this:

```
2018-09-12 15:29:06,558 WARN org.apache.hadoop.hbase.master.assignment.AssignmentManager: STUCK Region-In-Transition rit=OPENING, location=va1001.example.org,22101,1536173230599, table=IntegrationTestBigLinkedList_20180626110336, region=dbdb56242f17610c46ea044f7a42895b
```


#### /master-status#tables

This section about midway down in Master UI home-page shows a list of tables
with columns for whether the table is _ENABLED_, _ENABLING_, _DISABLING_, or
_DISABLED_ among other attributes. Also listed are columns with counts
of Regions in their various transition states: _OPEN_, _CLOSED_,
etc. A read of this table is good for figuring if the Regions of
this table have a proper disposition. For example if a table is
_ENABLED_ and there are Regions that are not in the _OPEN_ state
and the Master Log is silent about any ongoing assigns, then
something is amiss.

#### Procedures & Locks

This page off the Master UI home page under the
_Procedures & Locks_ menu item in the page heading lists all ongoing
Procedures and Locks as well as the current set of Master Procedure WALs
(named _pv2-0000000000000000###.log_ under the _MasterProcWALs_
directory in your hbase install). On startup, on a large
cluster when furious assigning is afoot, this page is
filled with lists of Procedures and Locks. The count of
MasterProcWALs will bloat too. If after the cluster settles,
there is a stuck Lock or Procedure or the count of WALs
doesn't ever come down but only grows, then operator intervention
is needed to alieve the blockage.

Lists of locks and procedures can also be obtained via the hbase shell:

```
$ echo "list_locks"| hbase shell &> /tmp/locks.txt
$ echo "list_procedures"| hbase shell &> /tmp/procedures.txt
```

#### The [HBase Canary Tool](http://hbase.apache.org/book.html#_canary)

The Canary tool is useful verifying the state of assign.
It can be run with a table focus or against the whole cluster.

For example, to check cluster assigns:

```
$ hbase canary -f false -t 6000000 &>/tmp/canary.log
```

The _-f false_ tells the Canary to keep going across failed Region
fetches and the _-t 6000000_ tells the Canary run for ~two hours
maximum. When done, check out _/tmp/canary.log_. Grep for
_ERROR_ lines to find problematic Region assigns.

You can do a probe like the Canary's in the hbase shell. 
For example, given a Region that has a start row of _d1dddd0c_
belonging to the table _testtable_, do as follows:


```
hbase> scan 'testtable', {STARTROW => 'd1dddd0c', LIMIT => 10}
```

For an overview on parsing a Region name into its constituent parts, see
[RegionInfo API](https://hbase.apache.org/apidocs/org/apache/hadoop/hbase/client/RegionInfo.html).

#### Other Tools

To figure the list of Regions that are not _OPEN_ on an
_ENABLED_ or _ENABLING_ table, read the _hbase:meta_ table _info:state_ column.
For example, to find the state of all Regions in the table
_IntegrationTestBigLinkedList_20180626064758_, do the following:

```
$ echo " scan 'hbase:meta', {ROWPREFIXFILTER => 'IntegrationTestBigLinkedList_20180626064758,', COLUMN => 'info:state'}"| hbase shell > /tmp/t.txt
```

...then grep for _OPENING_ or _CLOSING_ Regions.

To move an _OPENING_ issue to _OPEN_ so it agrees with a table's
_ENABLED_ state, use the `assign` command in the hbase shell to
queue a new Assign Procedure (watch the Master logs to see the
Assign run). If many Regions to assign, use the _HBCK2_ tool. It
can do bulk assigning.

## Fixing

General principals include a Region can not be assigned if
it is in _CLOSING_ state (or the inverse, unassigned if in
_OPENING_ state) without first transitioning via _CLOSED_:
Regions must always move from _CLOSED_, to _OPENING_, to _OPEN_,
and then to _CLOSING_, _CLOSED_.

When making repair, do fixup a table at a time.

Also, if a table is _DISABLED_, you cannot assign a Region.
In the Master logs, you will see that the Master will report
that the assign has been skipped because the table is
_DISABLED_. You may want to assign a Region because it is
currently in the _OPENING_ state and you want it in the
_CLOSED_ state so it agrees with the table's _DISABLED_
state. In this situation, you may have to temporarily set
the table status to _ENABLED_, just so you can do the
assign, and then set it back again after the unassign.
_HBCK2_ has facility to allow you do this. See the
_HBCK2_ usage output.

### Start-over

At an extreme, if the Master is distraught and all attempts at fixup only
turn up undoable locks or Procedures that won't finish, and/or
the set of MasterProcWALs is growing without bound, it is
possible to wipe the Master state clean. Just move aside the
_/hbase/MasterProcWALs/_ directory under your hbase install and
restart the Master process. It will come back as a `tabula rasa` without
memory of the bad times past.

If at the time of the erasure, all Regions were happily
assigned or offlined, then on Master restart, the Master should
pick up and continue as though nothing happened. But if there were Regions-In-Transition
at the time, then the operator may have to intervene to bring outstanding
assigns/unassigns to their terminal point. Read the _hbase:meta_
_info:state_ columns as described above to figure what needs
assigning/unassigning. Having erased all history moving aside
the _MasterProcWALs_, none of the entities should be locked so
you are free to bulk assign/unassign.

### Assigning/Unassigning

Generally, on assign, the Master will persist until successful.
An assign takes an exclusive lock on the Region. This precludes
a concurrent assign or unassign from running. An assign against
a locked Region will wait until the lock is released before
making progress. See the [Procedures & Locks] section above for
current list of outstanding Locks.

### _Master startup cannot progress, in holding-pattern until region onlined_

This should never happen. If it does, here is what it looks like:

```
2018-10-01 22:07:42,792 WARN org.apache.hadoop.hbase.master.HMaster: hbase:meta,,1.1588230740 is NOT online; state={1588230740 state=CLOSING, ts=1538456302300, server=ve1017.example.org,22101,1538449648131}; ServerCrashProcedures=true. Master startup cannot progress, in holding-pattern until region onlined.
```

The Master is unable to continue startup because there is no Procedure to assign
_hbase:meta_ (or _hbase:namespace_). To inject one, use the _HBCK2_ tool:

```
HBASE_CLASSPATH_PREFIX=./hbase-hbck2-1.0.0-SNAPSHOT.jar hbase org.apache.hbase.HBCK2 assigns 1588230740
```

...where 1588230740 is the encoded name of the _hbase:meta_ Region.

The same may happen to the _hbase:namespace_ system table. Look for the
encoded Region name of the _hbase:namespace_ Region and do similar to
what we did for _hbase:meta_.
