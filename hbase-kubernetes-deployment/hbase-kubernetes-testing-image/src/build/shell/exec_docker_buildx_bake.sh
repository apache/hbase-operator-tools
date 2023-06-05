#!/usr/bin/env bash
#
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
# A work-around for INFRA-24621 establishing container user name and id in the jenkins environment.
# When `NON_ROOT_AS_JENKINS=true`, injects the values of `NON_ROOT_USER` and `NON_ROOT_UID` into
# the bake environment.

set -e
set -o pipefail
set -x

declare NON_ROOT_AS_JENKINS
declare NON_ROOT_USER
declare NON_ROOT_UID
declare -a bake_args=( "$@" )

NON_ROOT_AS_JENKINS="${NON_ROOT_AS_JENKINS:-false}"

if [ "${NON_ROOT_AS_JENKINS}" = 'true' ] ; then
    bake_args+=( "--set=*.args.NON_ROOT_USER=${NON_ROOT_USER}" )
    bake_args+=( "--set=*.args.NON_ROOT_USER_ID=${NON_ROOT_UID}" )
fi

docker buildx bake "${bake_args[@]}"
