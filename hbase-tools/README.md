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

# Apache HBase Tool for merging regions

_RegionsMerger_ is an utility tool for manually merging bunch of regions of
a given table. It's mainly useful on situations when an HBase cluster has too
many regions per RegionServers, and many of these regions are small enough that
it can be merged together, reducing the total number of regions in the cluster
and releasing RegionServers overall memory resources.

This may happen for mistakenly pre-splits, or after a purge in table
data, as regions would not be automatically merged.

## Setup
Make sure HBase tools jar is added to HBase classpath:

```
export HBASE_CLASSPATH=$HBASE_CLASSPATH:./hbase-tools-1.1.0-SNAPSHOT.jar
```

## Usage

_RegionsMerger_ requires two arguments as parameters: 1) The name of the table
to have regions merged; 2) The desired total number of regions for the informed
table. For example, to merge all regions of table `my-table` until it gets to a
total of 5 regions, assuming the _setup_ step above has been performed:

```
$ hbase org.apache.hbase.RegionsMerger my-table 5
```

## Implementation Details

_RegionsMerger_ uses client API
_org.apache.hadoop.hbase.client.Admin.getRegions_ to fetch the list of regions
for the specified table, iterates through the resulting list, identifying pairs
of adjacent regions. For each pair found, it submits a merge request using
_org.apache.hadoop.hbase.client.Admin.mergeRegionsAsync_ client API method.
This means multiple merge requests had been sent once the whole list has been
iterated.

Assuming all these requests complete, resulting total number of
regions will be ceil((original total number regions)/2). This resulting total
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
detected, _RegionsMerger_ checks the current file system size of each region,
before deciding to submit the merge request for the given regions. If the sum of
both regions size exceeds a threshold, merge will not be attempted.
This threshold is a configurable percentage of `hbase.hregion.max.filesize`
value, and is applied to avoid merged regions from getting immediately split
after the merge completes, which would happen automatically if the resulting
region size reaches `hbase.hregion.max.filesize` value. The percentage of
`hbase.hregion.max.filesize` is a double value configurable via
`hbase.tools.merge.upper.mark` property and it defaults to `0.9`.

Given this `hbase.hregion.max.filesize` restriction for merge results, it may be
impossible to achieve the desired total number of regions, in certain scenarios.
_RegionsMerger_ keeps tracking the progress of regions merges, on each round.
If no progress is observed after a configurable amount of rounds,
_RegionsMerger_ aborts automatically. The limit of rounds without progress is an
integer value configured via `hbase.tools.max.iterations.blocked` property.
