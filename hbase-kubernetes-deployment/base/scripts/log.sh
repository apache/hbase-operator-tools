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
# when sourcing log, first argument should be the file within $HADOOP_LOG_DIR that will be written to

filename=${1}
LOG_FILEPATH="$HADOOP_LOG_DIR/$filename"

# logs provided message to whichever filepath is provided when sourcing log.sh
# Use -e for error logging, -w for warning logs
# log [-ew] MESSAGE
log(){
  prefix="" # No prefix with default INFO-level logging
  while getopts ":ew" arg; do
    case $arg in
      e) # change prefix to ERROR: in logs
        prefix="ERROR:"
        shift
        ;;
      w) # change prefix to WARNING: in logs
        prefix="WARNING:"
        shift
        ;;
      *) # what is this?
        ;;
    esac
  done
  message=${1}
  echo "$(date +"%F %T") $prefix $message" >> "$LOG_FILEPATH"
}
