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

# Extra Tools for fixing Apache HBase inconsistencies

This _Operator Tools_ module provides extra tools for fixing different types of inconsistencies
in HBase. It differs from _HBCK2_ module by defining more complex operations than the commands
available in _HBCK2_. These tools often perform a set of steps to fix the underlying issues,
sometimes combining _HBCK2_ commands with other existing tools.


The current available tools in this
module are:

- RegionsMerger;
- MissingRegionDirsRepairTool;
- RegionsOnUnknownServersRecoverer;

## Setup
Make sure HBase tools jar is added to HBase classpath:

```
export HBASE_CLASSPATH=$HBASE_CLASSPATH:./hbase-tools-1.1.0-SNAPSHOT.jar
```

Each of these tools are detailed below.

## RegionsMerger - Tool for merging regions

_RegionsMerger_ is an utility tool for manually merging bunch of regions of
a given table. It's mainly useful on situations when an HBase cluster has too
many regions per RegionServers, and many of these regions are small enough that
it can be merged together, reducing the total number of regions in the cluster
and releasing RegionServers overall memory resources.

This may happen for mistakenly pre-splits, or after a purge in table
data, as regions would not be automatically merged.

### Usage

_RegionsMerger_ requires two arguments as parameters: 1) The name of the table
to have regions merged; 2) The desired total number of regions for the informed
table. For example, to merge all regions of table `my-table` until it gets to a
total of 5 regions, assuming the _setup_ step above has been performed:

```
$ hbase org.apache.hbase.RegionsMerger my-table 5
```

### Implementation Details

_RegionsMerger_ uses client API
_org.apache.hadoop.hbase.client.Admin.getRegions_ to fetch the list of regions
for the specified table, iterates through the resulting list, identifying pairs
of adjacent regions. For each pair found, it submits a merge request using
_org.apache.hadoop.hbase.client.Admin.mergeRegionsAsync_ client API method.
This means multiple merge requests had been sent once the whole list has been
iterated.

Assuming that all merges issued by the RegionsMerger are successful, the resulting number of 
regions will be no more than half the original number of regions. This resulting total
might not be equal to the target value passed as parameter, in which case
_RegionsMerger_ will perform another round of merge requests, this time over
the current existing regions (it fetches another list of regions from
  _org.apache.hadoop.hbase.client.Admin.getRegions_).

Merge requests are processed asynchronously. HBase may take a certain time to
complete some merge requests, so _RegionsMerger_ may perform some sleep between
rounds of regions iteration for sending requests. The specific amount of time is
configured by `hbase.tools.merge.sleep` property, in milliseconds, and it
defaults to `2000`(2 seconds).

While iterating through the list of regions, once a pair of adjacent regions is
detected, _RegionsMerger_ checks the current file system size of each region (excluding MOB data),
before deciding to submit the merge request for the given regions. If the sum of
both regions size exceeds a threshold, merge will not be attempted.
This threshold is a configurable percentage of `hbase.hregion.max.filesize`
value, and is applied to avoid merged regions from getting immediately split
after the merge completes, which would happen automatically if the resulting
region size reaches `hbase.hregion.max.filesize` value. The percentage of
`hbase.hregion.max.filesize` is a double value configurable via
`hbase.tools.merge.upper.mark` property and it defaults to `0.9`.

Given this `hbase.hregion.max.filesize` restriction for merge results, it may be
impossible to achieve the desired total number of regions.
_RegionsMerger_ keeps tracking the progress of regions merges, on each round.
If no progress is observed after a configurable amount of rounds,
_RegionsMerger_ aborts automatically. The limit of rounds without progress is an
integer value configured via `hbase.tools.max.iterations.blocked` property.

## MissingRegionDirsRepairTool - Tool for sideline regions dirs for regions not in meta table

_MissingRegionDirsRepairTool_ moves regions dirs existing under table's dir, but not in meta.
To be used in cases where the region is not present in meta, but still has a dir with hfiles on the
underlying file system, and no holes in the table region chain has been detected.

When no _region holes_ are reported, existing `HBCK2.addFsRegionsMissingInMeta`
command isn't appropriate, as it would bring the region back in meta and cause overlaps.

This tool performs the following actions:
1) Identifies regions in hdfs but not in meta;
2) For each of these regions, sidelines the related dir to a temp folder;
3) Load hfiles from each sidelined region to the related table;

Sidelined regions are never removed from temp folder. Operators should remove those manually,
after they certified on data integrity.

### Usage

This tool requires no parameters. Assuming classpath is properly set, can be run as follows:

```
$ hbase org.apache.hbase.MissingRegionDirsRepairTool
```


### Implementation Details

_MissingRegionDirsRepairTool_ uses `HBCK2.reportTablesWithMissingRegionsInMeta` to retrieve a
_Map<TableName,List<Path>>_ containing the list of affected regions grouped by table. For each of
the affected regions, it copies the entire region dir to a
`HBASE_ROOT_DIR/.missing_dirs_repair/TS/TBL_NAME/sidelined` directory. Then, it copies each of the
region hfiles to a `HBASE_ROOT_DIR/.missing_dirs_repair/TS/TBL_NAME/bulkload` dir, renaming these
files with the pattern `REGION_NAME-FILENAME`. For a given table, all affected regions would then
have all its files under same directory for bulkload. _MissingRegionDirsRepairTool_ then uses
_LoadIncrementalHFiles_ to load all files for a given table at once.

## RegionsOnUnknownServersRecoverer - Tool for recovering regions on "unknown servers."

_RegionsOnUnknownServersRecoverer_ parses the master log to identify `unknown servers`
holding regions. This condition may happen in the event of recovering previously destroyed clusters,
where new Master/RS names completely differ from the previous ones currently
stored in meta table (see HBASE-24286).

```
NOTE: This tool is useful for clusters runing hbase versions lower than 2.2.7, 2.3.5 and 2.4.7.
For any of these versions or higher, HBCK2 'recoverUnknown' option can be used as a much simpler solution.
```

### Usage

This tool requires the master logs path as parameter. Assuming classpath is properly set,
can be run as follows:

```
$ hbase org.apache.hbase.RegionsOnUnknownServersRecoverer PATH_TO_MASTER_LOGS [dryRun]
```

The `dryRun` optional parameter will just parse the logs and print the list of unknown servers,
without invoking `hbck2 scheduleRecoveries` command.


### Implementation Details

_RegionsOnUnknownServersRecoverer_ parses master log file searching for specific messages mentioning
 "unknown servers". Once "unknown servers" are found, it then uses `HBCK2.scheduleRecoveries` to
 submit SCPs for each of these "unknown servers". 