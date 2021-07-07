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

# hbase-operator-tools

Host for [Apache HBase&trade;](https://hbase.apache.org)
operator tools including:

 * [HBCK2](https://github.com/apache/hbase-operator-tools/tree/master/hbase-hbck2), the hbase-2.x fix-it tool, the successor to hbase-1's _hbck_ (A.K.A _hbck1_).
 * [TableReporter](https://github.com/apache/hbase-operator-tools/tree/master/hbase-table-reporter), a tool to generate a basic report on Table column counts and row sizes; use when no distributed execution available.
 * [MetadataRepair](https://github.com/apache/hbase-operator-tools/tree/master/hbase-meta-repair), a tool to repair hbase metadata for versions before 2.0.3 and 2.1.1 (without hbck2).
