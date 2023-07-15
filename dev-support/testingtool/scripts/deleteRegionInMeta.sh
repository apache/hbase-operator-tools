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

# This action script does the following:
#     1. Delete one of the regions of the given table from hbase:meta using HBCKActions
#     2. Runs hbck2's 'reportMissingRegionsInMeta' to make sure the deleted regions is reported missing
#     3. Attemps to add the missing region in 'hbase:meta' using hbck2's 'addFsRegionsMissingInMeta'
#     4. Restarts the master as the above commands requires a master restart for cache refresh
#     5. Runs hbck2's assigns command to finally assign the missing region
#     6. Verifies the assignment using row count

set -e
function usage {
  echo "Usage: ${0} hbase_client working_dir component_install hbck2_jar_path hadoop_jars hbase_version table_name"
}

function redirect_and_run {
  log_base=$1
  shift
  echo "$*" >"${log_base}.err"
  "$@" >"${log_base}.out" 2>>"${log_base}.err"
}

# if no args specified, show usage
if [ $# -lt 7 ]; then
  usage
  exit 1
fi

# Carry forward the arguments from the control script
declare hbase_client=$1
declare working_dir=$2
declare component_install=$3
declare hbck2_jar_path=$4
declare hadoop_jars=$5
declare hbase_version=$6
declare table_name=$7;


echo "========= Running hbckActions. It'll delete the region from meta table"
redirect_and_run "${working_dir}/hbckActions_deleteRegionFromMeta" \
java -cp "${working_dir}/hbase-conf/:${hbase_client}/lib/shaded-clients/hbase-shaded-client-byo-hadoop-${hbase_version}.jar:${working_dir}:${hadoop_jars}:${hbck2_jar_path}" org.apache.hbase.HBCKActions deleteRegionFromMeta "${table_name}"

echo "========= Running hbck2's reportMissingRegionsInMeta."
HBASE_CLASSPATH_PREFIX="${hadoop_jars}" HBASE_CONF_DIR="${working_dir}/hbase-conf/" redirect_and_run \
    "${working_dir}/hbck2_reportMissingRegions" "${component_install}/bin/hbase" hbck -j "${hbck2_jar_path}" reportMissingRegionsInMeta "${table_name}"

if [ ! -f "${working_dir}/hbck2_reportMissingRegions.out" ] ; then
    echo "ERROR: Logfile not found to grep/verify reportMissingRegions command"
    exit 2
elif grep -q "\-\> No missing regions" "${working_dir}/hbck2_reportMissingRegions.out"  ; then
    echo "ERROR: No missing region. hbck2 failed. Exiting.."
    exit 2
else
    echo "A region is missing as expected"
    grep "Missing Regions for each table" -A2 "${working_dir}/hbck2_reportMissingRegions.out"
fi

echo "========= Running hbck2's addFsRegionsMissingInMeta."
HBASE_CLASSPATH_PREFIX="${hadoop_jars}" HBASE_CONF_DIR="${working_dir}/hbase-conf/" redirect_and_run \
    "${working_dir}/hbck2_addMissingRegions" "${component_install}/bin/hbase" hbck -j "${hbck2_jar_path}" addFsRegionsMissingInMeta "${table_name}"

if [ ! -f "${working_dir}/hbck2_addMissingRegions.out" ] ; then
    echo "ERROR: Logfile not found to grep/verify addMissingRegions command"
    exit 2
elif grep -q "regions were added to META" "${working_dir}/hbck2_addMissingRegions.out"; then
    echo "Region/s were added in META as expected"
    assigns_output=$(grep -Eo 'assigns [0-9a-z]+' "${working_dir}/hbck2_addMissingRegions.out")
    grep "Regions re-added into Meta" -A4 "${working_dir}/hbck2_addMissingRegions.out"
    echo "The assigns command extraced from output is: $assigns_output"
else
    echo "ERROR: No region added in META. hbck2 failed. Exiting.."
    exit 2
fi

echo "========= Restarting HBase master..."
HBASE_CONF_DIR="${working_dir}/hbase-conf/" "${component_install}/bin/rolling-restart.sh"

sleep_time=2
until "${component_install}/bin/hbase" --config "${working_dir}/hbase-conf/" shell --noninteractive >"${working_dir}/waiting_hbase_restart.log" 2>&1 <<EOF
  count 'hbase:meta'
EOF
do
  printf '\tretry waiting for hbase to restart.\n'
  sleep "${sleep_time}"
  sleep_time="$((sleep_time*2))"
done

region_id=$(echo "$assigns_output" | cut -d ' ' -f 2)

# Please note that even a normal hbase assign using the shell would also work fine here
# This doesn't check for the case where hbase would not be able to assign a region but
# hbck2 would be able to.
echo "========= Running hbck2's assigns command."
HBASE_CLASSPATH_PREFIX="${hadoop_jars}" HBASE_CONF_DIR="${working_dir}/hbase-conf/" redirect_and_run \
    "${working_dir}/hbck2_assignsRegion" "${component_install}/bin/hbase" hbck -j "${hbck2_jar_path}" assigns "$region_id"

sleep 5
echo "========= Verifying row count after assign."
assign_rowcount=$(echo "count '${table_name}'" | "${hbase_client}/bin/hbase" --config "${working_dir}/hbase-conf/" shell --noninteractive 2>/dev/null | tail -n 1)
echo "Found ${assign_rowcount} rows..."
if [ ! "${assign_rowcount}" -eq 1000 ]; then
  echo "ERROR: Instead of finding 1000 rows, we found ${assign_rowcount}."
  exit 2
else
  echo "SUCCESS: deleteRegionInMeta recovery process succeeded"
fi
