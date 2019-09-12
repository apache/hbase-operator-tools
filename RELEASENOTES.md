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
# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



# HBASE  hbase-operator-tools-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | *Major* | **Move to SLF4J**

Added SLF4J binding for LOG4J 2.


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

* [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | *Major* | **[hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized)**

Adds a general -s,--skip option to hbck2 so you can run the command it first checking it is version compatible.

Should not be needed going forward but in the spirt of our not knowing all the conditions under which we'll be trying to run hbck2, adding it.



