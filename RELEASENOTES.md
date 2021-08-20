# RELEASENOTES

<!---
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Be careful doing manual edits in this file. Do not change format
# of release header or remove the below marker. This file is generated.
# DO NOT REMOVE THIS MARKER; FOR INTERPOLATING CHANGES!-->
# HBASE  hbase-operator-tools-1.1.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-24482](https://issues.apache.org/jira/browse/HBASE-24482) | *Major* | **[hbase-operator-tools] build of hbck2 fails with HBase branch-2.3, due to missing dependencies**

Make it so tests and compile works against hbase-2.3.x as well as hbase-2.1.x.


---

* [HBASE-23927](https://issues.apache.org/jira/browse/HBASE-23927) | *Major* | **hbck2 assigns command should accept one or more files containing a list of region names**

This adds a new option -i or --inputFile to assigns function to allow one or more files to read the region list from for region assignment. Each file contains encoded region names, one per line. White spaces will be trimmed. For example:
     $ HBCK2 assigns -i file1 file2
The files can be generated or piped from operator running grep/sed over log files.


---

* [HBASE-23180](https://issues.apache.org/jira/browse/HBASE-23180) | *Major* | **Add a hbck2 testing tool**

This adds a new tool that spins up hbase on hadoop minicluster and mimicks actions of hbck2 to verify it's functionalities. The tool has been designed for easy addition of more compound actions (i.e. collection of actions) to verify hbck2. deleteRegionInMeta.sh is one such example of a compound action script.


---

* [HBASE-23610](https://issues.apache.org/jira/browse/HBASE-23610) | *Trivial* | **Update Apache POM to version 21 for hbase-operator-tools**

Updated parent POM from version 18 to version 21 - https://github.com/apache/maven-apache-parent/compare/apache-18...apache-21


---

* [HBASE-23577](https://issues.apache.org/jira/browse/HBASE-23577) | *Minor* | **Bump Checkstyle from 8.11 to 8.18 in hbase-operator-tools**

Bumped the Checkstyle version from 8.11 to 8.18



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-23002](https://issues.apache.org/jira/browse/HBASE-23002) | *Major* | **[HBCK2/hbase-operator-tools] Create an assembly that builds an hbase-operator-tools tgz**

First cut at an assembly for hbase-operator-tools project.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


---

* [HBASE-22567](https://issues.apache.org/jira/browse/HBASE-22567) | *Major* | **[HBCK2] Add new methods for dealing with missing regions in META while Master is online**

Adds new, lightweight option for recovering missing regions in meta, "addFsRegionsMissingInMeta" command, as well "readonly" "reportMissingRegionsInMeta" to show the list of regions to be readded in meta. This is a less intrusive alternative to OfflineMetaRepair, can be used when master and meta are still capable of coming online.

Detailed description as provided by the command usage help:

{noformat}
 addFsRegionsMissingInMeta \<NAMESPACE\|NAMESPACE:TABLENAME\>...
   Options:
    -d,--force\_disable aborts fix for table if disable fails.
   To be used in scenarios where some regions may be missing in META,
   but there's still a valid 'regioninfo' metadata file on HDFS.
   This is a lighter version of 'OfflineMetaRepair tool commonly used for
   similar issues on 1.x release line.
   This command needs META to be online. For each table name passed as
   parameter, it performs a diff between regions available in META,
   against existing regions dirs on HDFS. Then, for region dirs with
   no matches in META, it reads regioninfo metadata file and
   re-creates given region in META. Regions are re-created in 'CLOSED'
   state at META table only, but not in Masters' cache, and are not
   assigned either. To get these regions online, run HBCK2
'assigns'command
   printed at the end of this command results for convenience.

   NOTE: If using hbase releases older than 2.3.0, a rolling restart of
   HMasters is needed prior to executing the provided 'assigns' command.

   An example adding missing regions for tables 'tbl\_1' on default
   namespace, 'tbl\_2' on namespace 'n1' and for all tables from
   namespace 'n2':
     $ HBCK2 addFsRegionsMissingInMeta default:tbl\_1 n1:tbl\_2 n2
   Returns HBCK2 'assigns' command with all re-inserted regions.
   SEE ALSO: reportMissingRegionsInMeta
...
 reportMissingRegionsInMeta \<NAMESPACE\|NAMESPACE:TABLENAME\>...
   To be used in scenarios where some regions may be missing in META,
   but there's still a valid 'regioninfo metadata file on HDFS.
   This is a checking only method, designed for reporting purposes and
   doesn't perform any fixes, providing a view of which regions (if any)
   would get re-added to meta, grouped by respective table/namespace.
   To effectively re-add regions in meta, addFsRegionsMissingInMeta should be executed.
   This command needs META to be online. For each namespace/table passed
   as parameter, it performs a diff between regions available in META,
   against existing regions dirs on HDFS. Region dirs with no matches
   are printed grouped under its related table name. Tables with no
   missing regions will show a 'no missing regions' message. If no
   namespace or table is specified, it will verify all existing regions.
   It accepts a combination of multiple namespace and tables. Table names
   should include the namespace portion, even for tables in the default
   namespace, otherwise it will assume as a namespace value.
   An example triggering missing regions report for tables 'table\_1'
   and 'table\_2', under default namespace:
     $ HBCK2 reportMissingRegionsInMeta default:table\_1 default:table\_2
   An example triggering missing regions report for table 'table\_1'
   under default namespace, and for all tables from namespace 'ns1':
     $ HBCK2 reportMissingRegionsInMeta default:table\_1 ns1
   Returns list of missing regions for each table passed as parameter, or
   for each table on namespaces specified as parameter.
...
{noformat}


---

* [HBASE-22717](https://issues.apache.org/jira/browse/HBASE-22717) | *Major* | **[HBCK2] Expose replication fixes from hbck1**

Adds 'replication' command to HBCK2. Will clear old deleted peer queues and if a table name is passed, can clear replication barrier flags.

Exposes the old hbck1 code that did this.


---

* [HBASE-21393](https://issues.apache.org/jira/browse/HBASE-21393) | *Major* | **Add an API  ScheduleSCP() to HBCK2**

Adds scheduleRecoveries verb to HBCK2 tool for scheduling ServerCrashProcedures.

Also adds a version checker and a refactor so we check version before trying a command (unless --skip is passed on command-line).


---

* [HBASE-22688](https://issues.apache.org/jira/browse/HBASE-22688) | *Major* | **[HBCK2] Add filesystem fixup to hbck2**

Adds a 'filesystem' command to HBCK2. Checks for bad references, links, and corrupt hfiles with the option of sidelining if pass --fix option.


---

* [HBASE-22680](https://issues.apache.org/jira/browse/HBASE-22680) | *Major* | **[HBCK2] OfflineMetaRepair for hbase2/hbck2**

Adds a version of the old OfflineMetaRepair tool from hbck1 updated to work against hbase2. Here is how you'd run it:

 $ HBASE\_CLASSPATH\_PREFIX=~/checkouts/hbase-operator-tools/hbase-hbck2/target/hbase-hbck2-1.0.0-SNAPSHOT.jar ./bin/hbase org.apache.hbase.hbck1.OfflineMetaRepair -details

See section "hbase:meta region/table restore/rebuild" in hbase-hbck README in operator tools for more detail on how to run it (https://github.com/apache/hbase-operator-tools/tree/master/hbase-hbck2)


---

* [HBASE-22143](https://issues.apache.org/jira/browse/HBASE-22143) | *Minor* | **HBCK2 setRegionState command**

Adds a new feature to HBCK2: setRegionState.

Given the encoded name of a Region, this command allows you to change the state of that Region recorded in Meta.


---

* [HBASE-21322](https://issues.apache.org/jira/browse/HBASE-21322) | *Critical* | **Add a scheduleServerCrashProcedure() API to HbckService**

Adds scheduleServerCrashProcedure to the HbckService.


---

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.


---

* [HBASE-21335](https://issues.apache.org/jira/browse/HBASE-21335) | *Critical* | **Change the default wait time of HBCK2 tool**

Changed waitTime parameter to lockWait on bypass. Changed default waitTime from 0 -- i.e. wait for ever -- to 1ms so if lock is held, we'll go past it and if override enforce bypass.


---

* [HBASE-21317](https://issues.apache.org/jira/browse/HBASE-21317) | *Major* | **[hbck2] Add version, version handling, and misc override to assigns/unassigns**

Adds --override to assigns and unassigns.
Adds --recursive to bypass
Adds dump of version info
Adds fail if remote cluster doesn't support hbck2


---

* [HBASE-21210](https://issues.apache.org/jira/browse/HBASE-21210) | *Major* | **Add bypassProcedure() API to HBCK2**

Added a bypass to hbck2:

{code}
$ HBASE\_CLASSPATH\_PREFIX=../hbase-operator-tools/hbase-hbck2/target/hbase-hbck2-1.0.0-SNAPSHOT.jar ./bin/hbase org.apache.hbase.HBCK2
usage: HBCK2 [OPTIONS] COMMAND \<ARGS\>

Options:
 -d,--debug                                 run with debug output
 -h,--help                                  output this help message
 -p,--hbase.zookeeper.property.clientPort   peerport of target hbase
                                            ensemble
 -q,--hbase.zookeeper.quorum \<arg\>          ensemble of target hbase
 -z,--zookeeper.znode.parent                parent znode of target hbase

Commands:
 setTableState \<TABLENAME\> \<STATE\>
   Possible table states: ENABLED, DISABLED, DISABLING, ENABLING
   To read current table state, in the hbase shell run:
     hbase\> get 'hbase:meta', '\<TABLENAME\>', 'table:state'
   A value of \\x08\\x00 == ENABLED, \\x08\\x01 == DISABLED, etc.
   An example making table name 'user' ENABLED:
     $ HBCK2 setTableState users ENABLED
   Returns whatever the previous table state was.

 assigns \<ENCODED\_REGIONNAME\>...
   A 'raw' assign that can be used even during Master initialization.
   Skirts Coprocessors. Pass one or more encoded RegionNames:
   e.g. 1588230740 is hard-coded encoding for hbase:meta region and
   de00010733901a05f5a2a3a382e27dd4 is an example of what a random
   user-space encoded Region name looks like. For example:
     $ HBCK2 assign 1588230740 de00010733901a05f5a2a3a382e27dd4
   Returns the pid of the created AssignProcedure or -1 if none.

 bypass [OPTIONS] \<PID\>...
   Pass one (or more) procedure 'pid's to skip to the procedure finish.
   Parent of this procedures will also skip to its finish. Entities will
   be left in an inconsistent state and will require manual fixup.
   Pass --force to break any outstanding locks.
   Pass --waitTime=\<seconds\> to wait on entity lock before giving up.
   Default: force=false and waitTime=0. Returns true if succeeded.

 unassigns \<ENCODED\_REGIONNAME\>...
   A 'raw' unassign that can be used even during Master initialization.
   Skirts Coprocessors. Pass one or more encoded RegionNames:
   Skirts Coprocessors. Pass one or more encoded RegionNames:
   de00010733901a05f5a2a3a382e27dd4 is an example of what a random
   user-space encoded Region name looks like. For example:
     $ HBCK2 unassign 1588230740 de00010733901a05f5a2a3a382e27dd4
   Returns the pid of the created UnassignProcedure or -1 if none.
{code}



