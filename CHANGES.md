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
## Release hbase-operator-tools-1.0.0 - Unreleased (as of 2019-09-11)



### NEW FEATURES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-22183](https://issues.apache.org/jira/browse/HBASE-22183) | [hbck2] Update hbck2 README to explain new "setRegionState" method. |  Minor | documentation, hbck2 |
| [HBASE-22143](https://issues.apache.org/jira/browse/HBASE-22143) | HBCK2 setRegionState command |  Minor | hbase-operator-tools, hbck2 |


### IMPROVEMENTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-22999](https://issues.apache.org/jira/browse/HBASE-22999) | Fix non-varargs compile warnings |  Minor | hbase-operator-tools |


### BUG FIXES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-22951](https://issues.apache.org/jira/browse/HBASE-22951) | [HBCK2] hbase hbck throws IOE "No FileSystem for scheme: hdfs" |  Major | documentation, hbck2 |
| [HBASE-22952](https://issues.apache.org/jira/browse/HBASE-22952) | HBCK2 replication command is incompatible with 2.0.x |  Critical | hbase-operator-tools |
| [HBASE-22949](https://issues.apache.org/jira/browse/HBASE-22949) | [HBCK2] Add lang3 as explicit dependency |  Major | . |
| [HBASE-22687](https://issues.apache.org/jira/browse/HBASE-22687) | [hbase-operator-tools] Add checkstyle plugin and configs from hbase |  Major | . |
| [HBASE-22674](https://issues.apache.org/jira/browse/HBASE-22674) | precommit docker image installs JRE over JDK (multiple repos) |  Critical | build, hbase-connectors |
| [HBASE-21763](https://issues.apache.org/jira/browse/HBASE-21763) | [HBCK2] hbck2 options does not work and throws exceptions |  Minor | hbck2 |
| [HBASE-21484](https://issues.apache.org/jira/browse/HBASE-21484) | [HBCK2] hbck2 should default to a released hbase version |  Major | hbck2 |
| [HBASE-21483](https://issues.apache.org/jira/browse/HBASE-21483) | [HBCK2] version string checking should look for exactly the version we know doesn't work |  Major | hbck2 |
| [HBASE-21378](https://issues.apache.org/jira/browse/HBASE-21378) | [hbck2] add --skip version check to hbck2 tool (checkHBCKSupport blocks assigning hbase:meta or hbase:namespace when master is not initialized) |  Major | hbase-operator-tools, hbck2 |


### TESTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-21353](https://issues.apache.org/jira/browse/HBASE-21353) | TestHBCKCommandLineParsing#testCommandWithOptions hangs on call to HBCK2#checkHBCKSupport |  Major | hbase-operator-tools, hbck2 |


### SUB-TASKS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-22997](https://issues.apache.org/jira/browse/HBASE-22997) | Move to SLF4J |  Major | hbase-operator-tools |
| [HBASE-22998](https://issues.apache.org/jira/browse/HBASE-22998) | Fix NOTICE and LICENSE |  Blocker | hbase-operator-tools |
| [HBASE-22825](https://issues.apache.org/jira/browse/HBASE-22825) | [HBCK2] Add a client-side to hbase-operator-tools that can exploit fixMeta added in server side |  Major | hbck2 |
| [HBASE-22957](https://issues.apache.org/jira/browse/HBASE-22957) | [HBCK2] reference file check fails if compiled with old version but check against new |  Major | . |
| [HBASE-22865](https://issues.apache.org/jira/browse/HBASE-22865) | [HBCK2] shows the whole help/usage message after the error message |  Minor | hbck2 |
| [HBASE-22843](https://issues.apache.org/jira/browse/HBASE-22843) | [HBCK2] Fix HBCK2 after HBASE-22777 & HBASE-22758 |  Blocker | hbase-operator-tools |
| [HBASE-22717](https://issues.apache.org/jira/browse/HBASE-22717) | [HBCK2] Expose replication fixes from hbck1 |  Major | . |
| [HBASE-22713](https://issues.apache.org/jira/browse/HBASE-22713) | [HBCK2] Add hdfs integrity report to 'filesystem' command |  Major | hbck2 |
| [HBASE-21393](https://issues.apache.org/jira/browse/HBASE-21393) | Add an API  ScheduleSCP() to HBCK2 |  Major | hbase-operator-tools, hbck2 |
| [HBASE-22688](https://issues.apache.org/jira/browse/HBASE-22688) | [HBCK2] Add filesystem fixup to hbck2 |  Major | hbck2 |
| [HBASE-22680](https://issues.apache.org/jira/browse/HBASE-22680) | [HBCK2] OfflineMetaRepair for hbase2/hbck2 |  Major | hbck2 |


### OTHER:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-19121](https://issues.apache.org/jira/browse/HBASE-19121) | HBCK for AMv2 (A.K.A HBCK2) |  Major | hbck, hbck2 |
| [HBASE-22906](https://issues.apache.org/jira/browse/HBASE-22906) | Clean up checkstyle complaints in hbase-operator-tools |  Trivial | hbase-operator-tools |
| [HBASE-22675](https://issues.apache.org/jira/browse/HBASE-22675) | Use commons-cli from hbase-thirdparty |  Major | hbase-operator-tools |
| [HBASE-21433](https://issues.apache.org/jira/browse/HBASE-21433) | [hbase-operator-tools] Add Apache Yetus integration for hbase-operator-tools repository |  Major | build, hbase-operator-tools |


