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
# Wrap up the complexities of launching `kubectl kuttl test` via docker container.

set -euox pipefail

declare default_run_args
default_run_args="--rm --mount type=bind,source=$(pwd),target=/workspace --workdir /workspace"

# from the calling environment
declare DOCKER_EXE="${DOCKER_EXE:-"$(command -v docker 2>/dev/null)"}"
declare DOCKER_CONTAINER_RUN_ADDITIONAL_ARGS="${DOCKER_CONTAINER_RUN_ADDITIONAL_ARGS:-"${default_run_args}"}"
declare USER="${USER:-apache}"
declare KUTTL_IMAGE="${KUTTL_IMAGE:-"${USER}/hbase/operator-tools/kuttl:latest"}"

declare run_args
read -r -a run_args <<< "$DOCKER_CONTAINER_RUN_ADDITIONAL_ARGS"

exec "${DOCKER_EXE}" container run \
    "${run_args[@]}" \
    "${KUTTL_IMAGE}" \
    "$@"
