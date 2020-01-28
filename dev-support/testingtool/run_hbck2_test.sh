#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# This script does following things:
#     1. Do a mvn package to generate the hbck2 jar
#     2. Invoke dev-support/testingtool/hbck2_test.sh with the above generated jar and other necessary parameters

# HOW TO RUN:
#     1. Replace the passed parameters at hbck2_test.sh invocation line with appropriate values
#     2. ./dev-support/testingtool/run_hbck2_test.sh

set -e

SELF=$(cd "$(dirname "$0")" && pwd)
echo "SELF is at: ${SELF}"

(cd "${SELF}/../../"
mvn clean package assembly:single -DskipTests
hbase_hbck2_jar=$(ls hbase-hbck2/*target/hbase-hbck2-*)

# Replace the passed parameters in here [starting from --working-dir] with appropriate values
./dev-support/testingtool/hbck2_test.sh \
--single-process --working-dir /path/to/working_dir \
--hbase-client-install /path/to/hbase-x.x.x \
/path/to/hbase-x.x.x \
/path/to/hadoop-x.x.x/bin/hadoop \
/path/to/hadoop-x.x.x/share/hadoop/yarn/test/hadoop-yarn-server-tests-x.x.x-tests.jar \
/path/to/hadoop-x.x.x/share/hadoop/mapreduce/hadoop-mapreduce-client-jobclient-x.x.x-tests.jar \
/path/to/hadoop-x.x.x/bin/mapred \
"${SELF}/../../${hbase_hbck2_jar}"
)