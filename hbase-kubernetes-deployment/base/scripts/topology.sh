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
# Using topology script notion for HDFS rack awareness: https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/RackAwareness.html

# This script takes in one or more datanode IPs as args and passes out rack name(s) for the pod(s) based on the EKS instance(s) they're running in.
# It will look for information about the EKS instance's partition placement group: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-groups.html#placement-groups-partition
# As well as information about the EKS instance's availability zone according to AWS: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-regions-availability-zones.html#concepts-availability-zones

# if partition placement group information is found (in the form of the $partition_group_label variable defined below),
# then the rack passed out will be "partition-group-<PARTITION NUMBER>".

# Otherwise, the script will take in availability zone information, pass out a
# rack label like "availability-zone-<AVAILABILITY ZONE NAME>".

# Supposition here is that when datanodes crash, the namenodes will provide the same rack when the pod comes back up.
# This is the behavior that's been observed when terminating datanodes manually and watching topology logs as they re-initialize.

script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

TOPOLOGY_LOG="topology.log" # filepath within $HADOOP_LOG_DIR wherein topology logs will be placed
export TOPOLOGY_LOG

# shellcheck disable=SC1091
source "${script_dir}/log.sh" "$TOPOLOGY_LOG"
partition_group_label="partition_number" # this is an assumption made based on the Siri cluster at the moment; modify this variable if the Kube node label signifying placement groups is named differently

log "argument(s) input to script: $*"
for dn_IP in "$@"
do
  log "datanode IP: $dn_IP"
  nodeLabels="$("${script_dir}/get_node_labels_from_pod_IP.sh" "$dn_IP")"
  nodePartitionGroup="$(echo "$nodeLabels" | jq -r ".$partition_group_label")"
  if [[ "$nodePartitionGroup" == "null" ]];
  then
    nodeAZ="$(echo "$nodeLabels" | jq -r '."topology.kubernetes.io/zone"')"
    if [[ "$nodeAZ" == "null" ]];
    then
      rack="/default-rack" # when no partition group or availability zone info is found for the datanode
      log "No partition groups or availability zones found; output default rack $rack for $dn_IP"
      echo "$rack"
    else
      rack="/availability-zone-$nodeAZ"
      log "output rack $rack for $dn_IP"
      echo "$rack"
    fi
  else
    rack="/partition-group-$nodePartitionGroup"
    log "output rack $rack for $dn_IP"
    echo "$rack"
  fi
done
