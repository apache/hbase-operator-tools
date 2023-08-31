#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

personality_plugins "all"

## @description  Globals specific to this personality
## @audience     private
## @stability    evolving
function personality_globals
{
  # shellcheck disable=SC2034
  BUILDTOOL=maven
  #shellcheck disable=SC2034
  PROJECT_NAME=hbase-operator-tools
  #shellcheck disable=SC2034
  PATCH_BRANCH_DEFAULT=master
  #shellcheck disable=SC2034
  JIRA_ISSUE_RE='^HBASE-[0-9]+$'
  #shellcheck disable=SC2034
  GITHUB_REPO="apache/hbase-operator-tools"
}

######################################
# Below plugin is copied from https://github.com/apache/hbase/blob/master/dev-support/hbase-personality.sh
######################################

add_test_type spotless

## @description  spotless file filter
## @audience     private
## @stability    evolving
## @param        filename
function spotless_filefilter
{
  # always add spotless check as it can format almost all types of files
  add_test spotless
}
## @description run spotless:check to check format issues
## @audience private
## @stability evolving
## @param repostatus
function spotless_rebuild
{
  local repostatus=$1
  local logfile="${PATCH_DIR}/${repostatus}-spotless.txt"

  if ! verify_needed_test spotless; then
    return 0
  fi

  big_console_header "Checking spotless on ${repostatus}"

  start_clock

  local -a maven_args=('spotless:check')

  # disabled because "maven_executor" needs to return both command and args
  # shellcheck disable=2046
  echo_and_redirect "${logfile}" $(maven_executor) "${maven_args[@]}"

  count=$(${GREP} -c '\[ERROR\]' "${logfile}")
  if [[ ${count} -gt 0 ]]; then
    add_vote_table -1 spotless "${repostatus} has ${count} errors when running spotless:check, run spotless:apply to fix."
    add_footer_table spotless "@@BASE@@/${repostatus}-spotless.txt"
    return 1
  fi

  add_vote_table +1 spotless "${repostatus} has no errors when running spotless:check."
  return 0
}

######################################
