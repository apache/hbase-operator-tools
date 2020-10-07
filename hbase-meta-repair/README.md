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

# Apache HBase Tool for repair metadata

_MetaRepair_ is an utility tool for repairing hbase metadata table from hdfs regioninfo file. 
When using the [Apache HBase&trade;](https://hbase.apache.org) 
versions before 2.0.3 and 2.1.1 (**Without HBCK2**), You can use it to fix the HBase metadata correctly.

## Build

Run:
```
$ mvn install
```
The built _hbase-meta-repair_ jar will be in the `target` sub-directory.

## Setup
Make sure HBase tools jar is added to HBase classpath:

```
export HBASE_CLASSPATH=$HBASE_CLASSPATH:./hbase-meta-repair-1.1.0-SNAPSHOT.jar
```

## Usage

_MetaRepair_ requires an arguments as parameters: The name of the table
to have metadata repaired.

For example, to repair metadata of table `my-table` , assuming the _setup_ step above has been performed:

```
$ hbase org.apache.hbase.repair.MetaRepair my-table
```
> The table with _namespace_ use `namespace:tablename`  form of parameter. 

## Implementation Details

HBase uses table `hbase:meta` to store metadata. The table structure is as follows:

|  Column |  Description | 
| ------ | ------ |
| info:state | region state | 
| info:sn | region server node, It consists of server and serverstartcode, such as `slave1,16020,1557998852385` | 
| info:serverstartcode | region server start timestamp | 
| info:server | region server address and port，such as `slave1:16020` | 
| info:seqnumDuringOpen | a binary string representing the online duration of the region | 
| info:regioninfo | same as the `.regioninfo` file | 

> `info:seqnumDuringOpen` and `info:serverstartcode` will be automatically generated after the region server is restarted.

