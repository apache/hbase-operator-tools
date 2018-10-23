# HBCK2

HBCK2 is the successor to [hbck](https://hbase.apache.org/book.html#hbck.in.depth),
the hbase-1.x fixup tool (A.K.A _hbck1_). Use it in place of _hbck1_ making repairs
against hbase-2.x installs.

## _hbck1_
The _hbck_ that ships with hbase-1.x (A.K.A _hbck1_) should not be run against an
hbase-2.x cluster. It may do damage. While _hbck1_ is still bundled inside hbase-2.x
-- to minimize surprise (it has a fat pointer to _HBCK2_ at the head of its help
output) -- it's write-facility (`-fix`) has been removed. It can report on the state
of an hbase-2.x cluster but its assessments are likely inaccurate since it does not
understand the workings of an hbase-2.x.

_HBCK2_ does much less than _hbck1_ because many of the class of problems
_hbck1_ addressed are either no longer issues in hbase-2.x, or we've made
(or will make) a dedicated tool to do what _hbck1_ used do. _HBCK2_ also
works in a manner that differs from how _hbck1_ worked, asking the HBase
Master to do its bidding, rather than replicate functionality outside of the


## Running _HBCK2_
`org.apache.hbase.HBCK2` is the name of the main class. Running the below
will dump out the _HBCK2_ usage:

~~~~
 $ HBASE_CLASSPATH_PREFIX=/tmp/hbase-hbck2-1.0.0-SNAPSHOT.jar ./bin/hbase org.apache.hbase.HBCK2
~~~~

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
_HBCK2_ versions should be able to work across multiple hbase-2 releases; it will
fail with a message if it is unable to run. There is no `HbckService` in versions
of hbase before 2.0.3 and 2.1.1; _HBCK2_ will not work against these versions.

## Finding Problems

While _hbck1_ performed an analysis reporting your cluster good or bad, _HBCK2_
does no such thing (not currently). The operator figures what needs fixing and
then uses tools including _HBCK2_ to do fixup.

To figure if issues in assignment, check Master logs, the Master UI home
page _table_ tab at `https://YOUR_HOST:YOUR_PORT/master-status#tables`,
the current _Procedures & Locks_ tab at
`https://YOUR_HOST:YOUR_PORT/procedures.jsp` off the Master UI home page,
the HBase Canary tool, and reading Region state out of the `hbase:meta`
table. Lets look at each in turn. We'll follow this review with a set of
scenarios in which we use the below tooling to do various fixes.

### Master Logs

The Master runs all assignments, server crash handling, cluster start and
stop, etc. In hbase-2.x, all that the Master does has been cast as
Procedures run on a state machine engine. See
[http://hbase.apache.org/book.html#pv2](Procedure Framework) and
[http://hbase.apache.org/book.html#amv2](Assignment Manager) for
detail on how this infrastructure works. Each Procedure has a
Procedure `id`', it's `pid`. You can trace the lifecycle of a
Procedure as it logs each of its macro steps denoted by its
`pid`. Procedures start, step through states and finish. Some
Procedures spawn sub-procedures, wait on their Children, and then
themselves finish.

Generally all runs problem free but if some unforeseen circumstance
arises, the assignment framework may sustain damage requiring
operator intervention.  Below we will discuss some such scenarios
but they manifest in the Master log as a Region being _STUCK_ or
a Procedure transitioning an entity -- a Region of a Table --
may be blocked because another Procedure holds the exclusive lock
and is not letting go. More on these scenarios below.

### /master-status#tables

This tab shows a list of tables with columns showing whether a
table _ENABLED_, _ENABLING_, _DISABLING_, or _DISABLED_ as well
as other attributes of table. Also listed are columns with counts
of Regions in their various transition states: _OPEN_, _CLOSED_,
etc. A read of this table is good for figuring if the Regions of
this table have a proper disposition. For example if a table is
_ENABLED_ and there are Regions that are not in the _OPEN_ state
and the Master Log is silent about any ongoing assigns, then
something is amiss.

### Procedures & Locks

This page off the Master UI home page under the
_Procedures & Locks_ tab lists all ongoing Procedures and
Locks as well as the current set of Master Proc WALs (named
_pv2-0000000000000000###.log_ under the _MasterProcWALs_
directory in your hbase install). On startup, on a large
cluster when furious assigning is afoot, this page is
filled with lists of Procedures and Locks. The count of
MasterProcWALs will bloat too. If after the cluster settles,
there is a stuck Lock or Procedure or the count of WALs
doesn't ever come down but only grows, then operator intervention
is required.

### The [http://hbase.apache.org/book.html#_canary](HBase Canary Tool)

The Canary tool is useful verifying the state of assign.
It can be run with a table focus or against the whole cluster.

For example, to check cluster assigns:

```$ hbase canary -f false -t 6000000 &>/tmp/canary.log```

The _-f false_ tells the Canary to keep going across failed Region
fetches and the _-t 6000000_ tells the Canary run for ~two hours
maximum. When done, check out _/tmp/canary.log_. Grep for
_ERROR_ lines to find problematic Region assigns.

To do similar to what the Canary does against a single Region,
in the hbase shell, do something similar. In our example, the
Region belongs to the table _testtable_ and the Region
start row is _d1dddd0c_ (For overview on parsing a Region
name into its constituent parts, see
[https://hbase.apache.org/apidocs/org/apache/hadoop/hbase/client/RegionInfo.html](RegionInfo API)):

```hbase> scan 'testtable', {STARTROW => 'd1dddd0c', LIMIT => 10}```

### Other Tools

To figure the list of Regions that are not _OPEN_ on an
_ENABLED_ or _ENABLING_ table, read the _hbase:meta_ table _info:state_ column.
For example, to find the state of all Regions in the table
_IntegrationTestBigLinkedList_20180626064758_, do the following:

```$ echo " scan 'hbase:meta', {ROWPREFIXFILTER => 'IntegrationTestBigLinkedList_20180626064758,', COLUMN => 'info:state'}"| hbase shell > /tmp/t.txt```

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
Regions always move from _CLOSED_, to _OPENING_, to _OPEN_,
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

#### Start-over

If the Master is distraught and all attempts at fixup only
turn up undoable locks or Procedures that won't finish, and/or
the set of MasterProcWALs is growing without bound, it is
possible to have the Master start over. Just move aside the
_/hbase/MasterProcWALs/_ directory under your hbase install and
restart the Master. It will come back as a tabula rasa without
memory of the bad times past.

If at the time of the erasure, all Regions were happily
assigned or offlined, then on Master restart, all should be
like a brand new day. But if there were Regions-In-Transition
at the time, then the operator may have to intervene to bring outstanding
assigns/unassigns to their terminal point.



by table
file issues...
TODO: fix version file.
TODO: a rebuild of hbase:meta table by reading the fs content.
