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
# Get the name of the Kubernetes node with the provided hadoop pod IP
set -x
podIP="${1}" # this will be the IP of a datanode
outfile="$(mktemp "/tmp/$(basename "$0").XXXX")"
trap '{ rm -f -- "$outfile"; }' EXIT
script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
# shellcheck source=/dev/null
source "${script_dir}/apiserver_access.sh"
# Following model described here: https://chengdol.github.io/2019/11/06/k8s-api/
# http_code is the return status code
http_code=$(curl -w  "%{http_code}" -sS --cacert "$CACERT" -H "Content-Type: application/json" -H "Accept: application/json, */*" -H "Authorization: Bearer $TOKEN" "$APISERVER/api/v1/namespaces/hadoop/pods?fieldSelector=status.podIP%3D$podIP" -o "$outfile")
if [[ $http_code -ne 200 ]]; then
    echo "{\"Result\": \"Failure\", \"httpReturnCode\":$http_code}" | jq '.'
    exit 1
fi

# using jq, only return the name of the node containing this pod; jq will return null if no node is found
jq -r .items[0].spec.nodeName "$outfile"
