# HBASE Operator Tools Changelog

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
## Release hbase-operator-tools-1.2.0 - Unreleased (as of 2021-12-14)

### NEW FEATURES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-25874](https://issues.apache.org/jira/browse/HBASE-25874) | [hbase-operator-tools]Add tool for identifying "unknown servers" from master logs, then submit SCPs for each of those. |  Major |  |


### IMPROVEMENTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-26257](https://issues.apache.org/jira/browse/HBASE-26257) | Improve Performance of HBCK when specifying a subset of tables |  Major | hbase-operator-tools,hbck2 |


### BUG FIXES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-26338](https://issues.apache.org/jira/browse/HBASE-26338) | hbck2 setRegionState cannot set replica region state |  Major | hbck2 |
| [HBASE-26072](https://issues.apache.org/jira/browse/HBASE-26072) | Upgrade hbase version in hbase-operator-tools to 2.4.4 |  Trivial | hbase-operator-tools |
| [HBASE-26054](https://issues.apache.org/jira/browse/HBASE-26054) | Fix hbase-operator-tools build with HBase 2.4.4 |  Minor | hbase-operator-tools |
| [HBASE-25965](https://issues.apache.org/jira/browse/HBASE-25965) | Move delete of WAL directory out of LOG.info and avoid left over directory |  Minor | hbase-operator-tools |
| [HBASE-25921](https://issues.apache.org/jira/browse/HBASE-25921) | Fix Wrong FileSystem when running `filesystem` on non-HDFS storage |  Major | hbase-operator-tools |
| [HBASE-25885](https://issues.apache.org/jira/browse/HBASE-25885) | [hbase-operator-tools] Extra checks on RegionsMerger to avoid submitting merge requests for regions with merge qualifiers |  Major | hbase-operator-tools |


### SUB-TASKS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-26571](https://issues.apache.org/jira/browse/HBASE-26571) | [hbase-operator-tools] Upgrade to log4j 2.16.0 |  Major | hbase-operator-tools |
| [HBASE-25659](https://issues.apache.org/jira/browse/HBASE-25659) | [hbck2] Add hbase-operator-tools command for scheduleSCPsForUnknownServers |  Major | hbase-operator-tools |
HBASE-26571



### OTHER:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-26561](https://issues.apache.org/jira/browse/HBASE-26561) | [hbase-operator-tools] Upgrade log4j2 to 2.15.0 to address CVE-2021-44228 |  Major | hbase-operator-tools,logging,security |
| [HBASE-25794](https://issues.apache.org/jira/browse/HBASE-25794) | Fix checkstyle violations in hbase-table-reporter module |  Trivial | hbase-operator-tools |
| [HBASE-25577](https://issues.apache.org/jira/browse/HBASE-25577) | HBase operator tools pom should include nexus staging repo management |  Major | hbase-operator-tools,community |


## Release hbase-operator-tools-1.1.0 - Unreleased (as of 2021-02-13)



### NEW FEATURES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-25266](https://issues.apache.org/jira/browse/HBASE-25266) | [hbase-operator-tools] Add a repair tool for moving stale regions dir not present in meta away from table dir |  Major | hbase-operator-tools |
| [HBASE-23562](https://issues.apache.org/jira/browse/HBASE-23562) | [operator tools] Add a RegionsMerge tool that allows for merging multiple adjacent regions until a desired number of regions is reached. |  Minor | hbase-operator-tools |
| [HBASE-23371](https://issues.apache.org/jira/browse/HBASE-23371) | [HBCK2] Provide client side method for removing "ghost" regions in meta. |  Major | hbase-operator-tools |


### IMPROVEMENTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-25297](https://issues.apache.org/jira/browse/HBASE-25297) | [HBCK2] Regenerate missing table descriptors by hbck2 |  Major | hbase-operator-tools, hbck2 |
| [HBASE-24626](https://issues.apache.org/jira/browse/HBASE-24626) | [HBCK2] Remove reference to hase I.A. private class CommonFsUtils from FsRegionsMetaRecoverer |  Major | hbase-operator-tools, hbck2 |
| [HBASE-23927](https://issues.apache.org/jira/browse/HBASE-23927) | hbck2 assigns command should accept one or more files containing a list of region names |  Major | hbase-operator-tools, hbck2, Operability, Usability |
| [HBASE-24039](https://issues.apache.org/jira/browse/HBASE-24039) | HBCK2 feature negotiation to check what commands are supported |  Critical | hbck2, Operability |
| [HBASE-24116](https://issues.apache.org/jira/browse/HBASE-24116) | Update Apache POM to version 23 for hbase-operator-tools |  Minor | hbase-operator-tools |
| [HBASE-23934](https://issues.apache.org/jira/browse/HBASE-23934) | [operator tools] Add forbiddennapis plugin to pom.xml so that we can permanently ban references to hbase I.A. private classes from hbck2 |  Major | hbase-operator-tools |
| [HBASE-23791](https://issues.apache.org/jira/browse/HBASE-23791) | [operator tools] Remove reference to I.A. Private interface MetaTableAccessor |  Major | hbase-operator-tools |
| [HBASE-23610](https://issues.apache.org/jira/browse/HBASE-23610) | Update Apache POM to version 21 for hbase-operator-tools |  Trivial | hbck2 |
| [HBASE-23609](https://issues.apache.org/jira/browse/HBASE-23609) | Clean up tests in hbase-operator-tools |  Minor | hbck2 |
| [HBASE-23611](https://issues.apache.org/jira/browse/HBASE-23611) | Enforcer plugin does not use configured version in hbase-operator-tools |  Minor | hbck2 |
| [HBASE-23577](https://issues.apache.org/jira/browse/HBASE-23577) | Bump Checkstyle from 8.11 to 8.18 in hbase-operator-tools |  Minor | hbase-operator-tools |
| [HBASE-23256](https://issues.apache.org/jira/browse/HBASE-23256) | fix hbck2 assigns/unassigns usage |  Minor | hbck2 |
| [HBASE-23031](https://issues.apache.org/jira/browse/HBASE-23031) | Upgrade Yetus version in RM scripts |  Minor | hbase-operator-tools |
| [HBASE-23109](https://issues.apache.org/jira/browse/HBASE-23109) | [hbase-operator-tools] Fix checkstyle issues |  Minor | hbase-operator-tools |


### BUG FIXES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-25529](https://issues.apache.org/jira/browse/HBASE-25529) | [hbase-operator-tools]Fix OOME "unable to create new native thread" on UTs |  Major | hbase-operator-tools |
| [HBASE-24997](https://issues.apache.org/jira/browse/HBASE-24997) | [hbase-operator-tools] NPE in RegionsMerger#mergeRegions |  Major | hbase-operator-tools |
| [HBASE-24889](https://issues.apache.org/jira/browse/HBASE-24889) | [hbase-operator-tools] Add missing ASF headers |  Major | hbase-operator-tools |
| [HBASE-24778](https://issues.apache.org/jira/browse/HBASE-24778) | [hbase-operator-tools] Merging regions failed when the table is not default namespace |  Major | hbase-operator-tools |
| [HBASE-24571](https://issues.apache.org/jira/browse/HBASE-24571) | HBCK2 fix addFsRegionsMissingInMeta to add regions in CLOSED state again |  Major | hbase-operator-tools |
| [HBASE-24482](https://issues.apache.org/jira/browse/HBASE-24482) | [hbase-operator-tools] build of hbck2 fails with HBase branch-2.3, due to missing dependencies |  Major | hbase-operator-tools |
| [HBASE-24398](https://issues.apache.org/jira/browse/HBASE-24398) | [hbase-operator-tools] RegionsMerger ConcurrentModificationException |  Major | hbase-operator-tools |
| [HBASE-24239](https://issues.apache.org/jira/browse/HBASE-24239) | [HBCK2] Remove removeExtraRegionsFromMeta from HBCK2 doc |  Minor | documentation, hbase-operator-tools |
| [HBASE-23112](https://issues.apache.org/jira/browse/HBASE-23112) | [hbase-operator-tools] fixMeta in hbck2 is porcelain, in hbck1 it was plumbing; fix |  Major | hbase-operator-tools |


### SUB-TASKS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-25137](https://issues.apache.org/jira/browse/HBASE-25137) | Migrate HBase-Operator-Tools-PreCommit jenkins job from Hadoop to hbase |  Major | hbase-operator-tools, jenkins |
| [HBASE-24397](https://issues.apache.org/jira/browse/HBASE-24397) | [hbase-operator-tools] Tool to Report on row sizes and column counts |  Major | hbase-operator-tools, tooling |


### OTHER:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-24882](https://issues.apache.org/jira/browse/HBASE-24882) | Migrate hbase-operator-tools testing to ci-hadoop |  Major | build, hbase-operator-tools |
| [HBASE-23180](https://issues.apache.org/jira/browse/HBASE-23180) | Add a hbck2 testing tool |  Major | hbase-operator-tools |
| [HBASE-23714](https://issues.apache.org/jira/browse/HBASE-23714) | Move to Apache parent POM version 22 for operator-tools |  Minor | dependencies, hbase-operator-tools |
| [HBASE-23641](https://issues.apache.org/jira/browse/HBASE-23641) | Use ReplicationPeerConfig.needToReplicate in HBaseFsck |  Major | hbase-operator-tools |
| [HBASE-23124](https://issues.apache.org/jira/browse/HBASE-23124) | [hbase-operator-tools] Remove commons-lang3 dependency |  Minor | hbase-operator-tools |


## Release hbase-operator-tools-1.0.0 - Unreleased (as of 2019-09-20)



### NEW FEATURES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-22567](https://issues.apache.org/jira/browse/HBASE-22567) | [HBCK2] Add new methods for dealing with missing regions in META while Master is online |  Major | hbck2 |
| [HBASE-22183](https://issues.apache.org/jira/browse/HBASE-22183) | [hbck2] Update hbck2 README to explain new "setRegionState" method. |  Minor | documentation, hbck2 |
| [HBASE-22143](https://issues.apache.org/jira/browse/HBASE-22143) | HBCK2 setRegionState command |  Minor | hbase-operator-tools, hbck2 |


### IMPROVEMENTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-22691](https://issues.apache.org/jira/browse/HBASE-22691) | [hbase-operator-tools] Move Checkstyle suppression file to different location |  Trivial | hbck2 |
| [HBASE-23018](https://issues.apache.org/jira/browse/HBASE-23018) | [HBCK2] Add useful messages when report/fixing missing regions in meta |  Minor | hbase-operator-tools, hbck2, Operability |
| [HBASE-22999](https://issues.apache.org/jira/browse/HBASE-22999) | Fix non-varargs compile warnings |  Minor | hbase-operator-tools |


### BUG FIXES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-23057](https://issues.apache.org/jira/browse/HBASE-23057) | Add commons-lang3 dependency to HBCK2 |  Critical | hbase-operator-tools |
| [HBASE-23039](https://issues.apache.org/jira/browse/HBASE-23039) | HBCK2 bypass -r command does not work |  Major | hbase-operator-tools |
| [HBASE-23033](https://issues.apache.org/jira/browse/HBASE-23033) | Do not run git-commit-id-plugin when .git is missing |  Blocker | hbase-operator-tools |
| [HBASE-23029](https://issues.apache.org/jira/browse/HBASE-23029) | Handle hbase-operator-tools releasenotes in release making script |  Minor | hbase-operator-tools |
| [HBASE-23026](https://issues.apache.org/jira/browse/HBASE-23026) | docker run command should not quote JAVA\_VOL |  Major | hbase-operator-tools |
| [HBASE-23025](https://issues.apache.org/jira/browse/HBASE-23025) | Do not quote GPG command |  Major | hbase-operator-tools |
| [HBASE-22984](https://issues.apache.org/jira/browse/HBASE-22984) | [HBCK2] HBCKMetaTableAccessor.deleteFromMetaTable throwing java.lang.UnsupportedOperationException at runtime |  Major | hbck2 |
| [HBASE-22951](https://issues.apache.org/jira/browse/HBASE-22951) | [HBCK2] hbase hbck throws IOE "No FileSystem for scheme: hdfs" |  Major | documentation, hbck2 |
| [HBASE-22952](https://issues.apache.org/jira/browse/HBASE-22952) | HBCK2 replication command is incompatible with 2.0.x |  Critical | hbase-operator-tools |
| [HBASE-22949](https://issues.apache.org/jira/browse/HBASE-22949) | [HBCK2] Add lang3 as explicit dependency |  Major | . |
| [HBASE-22687](https://issues.apache.org/jira/browse/HBASE-22687) | [hbase-operator-tools] Add checkstyle plugin and configs from hbase |  Major | . |
| [HBASE-22674](https://issues.apache.org/jira/browse/HBASE-22674) | precommit docker image installs JRE over JDK (multiple repos) |  Critical | build, hbase-connectors |
| [HBASE-21763](https://issues.apache.org/jira/browse/HBASE-21763) | [HBCK2] hbck2 options does not work and throws exceptions |  Minor | hbck2 |
| [HBASE-21484](https://issues.apache.org/jira/browse/HBASE-21484) | [HBCK2] hbck2 should default to a released hbase version |  Major | hbck2 |
| [HBASE-21483](https://issues.apache.org/jira/browse/HBASE-21483) | [HBCK2] version string checking should look for exactly the version we know doesn't work |  Major | hbck2 |
| [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | [hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized) |  Major | hbase-operator-tools, hbck2 |
| [HBASE-21335](https://issues.apache.org/jira/browse/HBASE-21335) | Change the default wait time of HBCK2 tool |  Critical | . |
| [HBASE-21317](https://issues.apache.org/jira/browse/HBASE-21317) | [hbck2] Add version, version handling, and misc override to assigns/unassigns |  Major | hbase-operator-tools, hbck2 |


### TESTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-21353](https://issues.apache.org/jira/browse/HBASE-21353) | TestHBCKCommandLineParsing#testCommandWithOptions hangs on call to HBCK2#checkHBCKSupport |  Major | hbase-operator-tools, hbck2 |


### SUB-TASKS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-23021](https://issues.apache.org/jira/browse/HBASE-23021) | [hbase-operator-tools] README edits in prep for release |  Major | . |
| [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | Move to SLF4J |  Major | hbase-operator-tools |
| [HBASE-22998](https://issues.apache.org/jira/browse/HBASE-22998) | Fix NOTICE and LICENSE |  Blocker | hbase-operator-tools |
| [HBASE-22859](https://issues.apache.org/jira/browse/HBASE-22859) | [HBCK2] Fix the orphan regions on filesystem |  Major | documentation, hbck2 |
| [HBASE-22825](https://issues.apache.org/jira/browse/HBASE-22825) | [HBCK2] Add a client-side to hbase-operator-tools that can exploit fixMeta added in server side |  Major | hbck2 |
| [HBASE-22957](https://issues.apache.org/jira/browse/HBASE-22957) | [HBCK2] reference file check fails if compiled with old version but check against new |  Major | . |
| [HBASE-22865](https://issues.apache.org/jira/browse/HBASE-22865) | [HBCK2] shows the whole help/usage message after the error message |  Minor | hbck2 |
| [HBASE-22843](https://issues.apache.org/jira/browse/HBASE-22843) | [HBCK2] Fix HBCK2 after HBASE-22777 & HBASE-22758 |  Blocker | hbase-operator-tools |
| [HBASE-22717](https://issues.apache.org/jira/browse/HBASE-22717) | [HBCK2] Expose replication fixes from hbck1 |  Major | . |
| [HBASE-22713](https://issues.apache.org/jira/browse/HBASE-22713) | [HBCK2] Add hdfs integrity report to 'filesystem' command |  Major | hbck2 |
| [HBASE-21393](https://issues.apache.org/jira/browse/HBASE-21393) | Add an API  ScheduleSCP() to HBCK2 |  Major | hbase-operator-tools, hbck2 |
| [HBASE-22688](https://issues.apache.org/jira/browse/HBASE-22688) | [HBCK2] Add filesystem fixup to hbck2 |  Major | hbck2 |
| [HBASE-22680](https://issues.apache.org/jira/browse/HBASE-22680) | [HBCK2] OfflineMetaRepair for hbase2/hbck2 |  Major | hbck2 |
| [HBASE-21322](https://issues.apache.org/jira/browse/HBASE-21322) | Add a scheduleServerCrashProcedure() API to HbckService |  Critical | hbck2 |
| [HBASE-21210](https://issues.apache.org/jira/browse/HBASE-21210) | Add bypassProcedure() API to HBCK2 |  Major | hbase-operator-tools, hbck2 |


### OTHER:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-19121](https://issues.apache.org/jira/browse/HBASE-19121) | HBCK for AMv2 (A.K.A HBCK2) |  Major | hbck, hbck2 |
| [HBASE-23003](https://issues.apache.org/jira/browse/HBASE-23003) | [HBCK2/hbase-operator-tools] Release-making scripts |  Major | . |
| [HBASE-23002](https://issues.apache.org/jira/browse/HBASE-23002) | [HBCK2/hbase-operator-tools] Create an assembly that builds an hbase-operator-tools tgz |  Major | . |
| [HBASE-22906](https://issues.apache.org/jira/browse/HBASE-22906) | Clean up checkstyle complaints in hbase-operator-tools |  Trivial | hbase-operator-tools |
| [HBASE-22675](https://issues.apache.org/jira/browse/HBASE-22675) | Use commons-cli from hbase-thirdparty |  Major | hbase-operator-tools |
| [HBASE-21433](https://issues.apache.org/jira/browse/HBASE-21433) | [hbase-operator-tools] Add Apache Yetus integration for hbase-operator-tools repository |  Major | build, hbase-operator-tools |


