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
# Materialize a kustomize directory for a kuttl test.
#
# Kustomize is clunky for automated testing. It's pretty opinionated in that it will only evaluate
# a directory off of disk -- you cannot generate a kustomization and pass it in via stdin.
# In order to use kuttl generated namespaces within the kustomization, we have to modify the
# kustomization.yaml before applying it. If we modify that file in the source tree, we end up with
# the test namespace appended to the file under source control. So, this script creates a temp
# directory, copies all the resources into that directory, and modifies the kustomization.yaml as
# necessary. It then runs `kubectl apply -k` against that temporary directory.
#

declare DEBUG="${DEBUG:false}"
if [ "${DEBUG}" = 'true' ] ; then
  set -x
fi

set -eou pipefail

declare NAMESPACE
declare NEW_RESOURCES='[]'
declare NEW_COMPONENTS='[]'
declare kustomize_dir
declare -a rewritten_resources=()
declare -a rewritten_components=()

kustomize_dir="$(mktemp -d -p /tmp "${NAMESPACE}.XXXXXXXXXX")"
trap '[ -d "${kustomize_dir}" ] && rm -rf "${kustomize_dir}"' EXIT

cp -r ./* "${kustomize_dir}/"

for r in $(yq '.resources[]' kustomization.yaml) ; do
  if [[ "${r}" =~ ^\.\./.* ]] ; then
    # resolve the new relative location for any resource path that is not in the local directory
    canonized="$(cd "${r}" ; pwd)"
    r="../..${canonized}"
  fi
  rewritten_resources+=("'${r}'")
done
if [ "${#rewritten_resources[@]}" -gt 0 ] ; then
    NEW_RESOURCES="[ $(printf '%s,' "${rewritten_resources[@]}") ]"
fi

for r in $(yq '.components[]' kustomization.yaml) ; do
  if [[ "${r}" =~ ^\.\./.* ]] ; then
    # resolve the new relative location for any resource path that is not in the local directory
    canonized="$(cd "${r}" ; pwd)"
    r="../..${canonized}"
  fi
  rewritten_components+=("'${r}'")
done
if [ "${#rewritten_components[@]}" -gt 0 ] ; then
    NEW_COMPONENTS="[ $(printf '%s,' "${rewritten_components[@]}") ]"
fi

env NAMESPACE="${NAMESPACE}" \
    NEW_RESOURCES="${NEW_RESOURCES}" \
    NEW_COMPONENTS="${NEW_COMPONENTS}" \
    yq -i '
  .namespace = strenv(NAMESPACE) |
  .resources = env(NEW_RESOURCES) |
  .components = env(NEW_COMPONENTS)
' "${kustomize_dir}/kustomization.yaml"

if [ "${DEBUG}" = 'true' ] ; then
  cat "${kustomize_dir}/kustomization.yaml"
fi

kubectl apply -k "${kustomize_dir}"
