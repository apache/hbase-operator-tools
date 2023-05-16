#!/usr/bin/env bash
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
#
# Usage: jmxping.sh <ROLE> <HEADLESS-SERVICE> [<COUNT>]
# JMX ping that there are at least '<COUNT>' instances of '<ROLE>'
# running in the sub-domain specified by <HEADLESS-SERVICE>
# (See https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#stable-network-id).
# If no '<COUNT>' supplied, we read the replica count from passed
# in '<ROLE>' statefulset from apiserver.
set -x
role="${1}"
service="${2}"
count_param="${3}"
# Schema
schema=http
if [[ ${HTTP_POLICY} == HTTPS_* ]]; then
  schema=https
fi
# Jmxport to use
case "${role}" in
  datanode)
    jmxport=9864
    if [[ ${HTTP_POLICY} == HTTPS_* ]]; then
      # If HTTP policy is https, use https jmx port.
      jmxport=9865
    fi
    ;;
  namenode)
    jmxport=9870
    if [[ ${HTTP_POLICY} == HTTPS_* ]]; then
      # If HTTP policy is https, use https jmx port.
      jmxport=9871
    fi
    ;;
  journalnode)
    jmxport=8480
    if [[ ${HTTP_POLICY} == HTTPS_* ]]; then
      # If HTTP policy is https, use https jmx port.
      jmxport=8481
    fi
    ;;
  master)
    jmxport=16010
    ;;
  regionserver)
    jmxport=16030
    ;;
  *)
    exit 1
    ;;
esac

interval=5
timeout=$((60 * 60))
while ((timeout > 0))
do
  # The statefulset we depend on may not have deployed yet... so the first
  # attempts at getting replicas may fail.
  # https://stackoverflow.com/questions/3601515/how-to-check-if-a-variable-is-set-in-bash
  replicas="$(/tmp/scripts/get_statefulset_replica_count.sh "$role")"
  count=${count_param}
  if [ "x" = "${count_param}x" ]; then
    count=${replicas}
  else
    count=$((replicas < count_param? replicas : count_param ))
  fi
  seq_end=$(( count - 1 ))
  total=0
  for i in $( seq 0 $seq_end ); do
    # Url is http://journalnode-1:8480/jmx?qry=java.lang:type=OperatingSystem
    url="${schema}://${role}-${i}.${service}:${jmxport}/jmx?qry=java.lang:type=OperatingSystem"
    # Returns 1 if success, zero otherwise.
    result=$(curl --cacert /tmp/scratch/ca.crt -v "$url" | grep -c SystemLoadAverage)
    ((total+=result))
    ((total != count)) || exit 0
  done
  timeout=$((timeout - interval))
  echo "Failed; sleeping $interval, then retrying for $timeout more seconds"
  sleep $interval
done
echo "Timedout!"
exit 1
