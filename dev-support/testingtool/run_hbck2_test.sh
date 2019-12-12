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
./dev-support/testingtool/hbck2_test.sh --hbck2 "${SELF}/../../${hbase_hbck2_jar}" \
--single-process --working-dir /Users/sakthi/test/hbck2-testing-tool/output/hbck2 \
--hbase-client-install /Users/sakthi/test/hbck2-testing-tool/hbase-2.1.7 \
/Users/sakthi/test/hbck2-testing-tool/hbase-2.1.7 \
/Users/sakthi/test/hbck2-testing-tool/hadoop-3.1.2/bin/hadoop \
/Users/sakthi/test/hbck2-testing-tool/hadoop-3.1.2/share/hadoop/yarn/test/hadoop-yarn-server-tests-3.1.2-tests.jar \
/Users/sakthi/test/hbck2-testing-tool/hadoop-3.1.2/share/hadoop/mapreduce/hadoop-mapreduce-client-jobclient-3.1.2-tests.jar \
/Users/sakthi/test/hbck2-testing-tool/hadoop-3.1.2/bin/mapred
)