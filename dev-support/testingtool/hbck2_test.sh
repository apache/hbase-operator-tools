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

# This the main control script that takes care of following things:
#     1. Sets up a hadoop minicluster
#     2. Installs HBase on the minicluster
#     3. Creates a table & region data using LTT
#     4. Invokes all the action scripts present in dev-support/testingtool/scripts/

set -e
function usage {
  echo "Usage: ${0} [options] /path/to/component/bin-install /path/to/hadoop/executable /path/to/hadoop/hadoop-yarn-server-tests-tests.jar /path/to/hadoop/hadoop-mapreduce-client-jobclient-tests.jar /path/to/mapred/executable /path/to/hbase-hbck2.jar"
  echo ""
  echo "    --zookeeper-data /path/to/use                                     Where the embedded zookeeper instance should write its data."
  echo "                                                                      defaults to 'zk-data' in the working-dir."
  echo "    --working-dir /path/to/use                                        Path for writing configs and logs. must exist."
  echo "                                                                      defaults to making a directory via mktemp."
  echo "    --hadoop-client-classpath /path/to/some.jar:/path/to/another.jar  classpath for hadoop jars."
  echo "                                                                      defaults to 'hadoop classpath'"
  echo "    --hbase-client-install /path/to/unpacked/client/tarball           if given we'll look here for hbase client jars instead of the bin-install"
  echo "    --force-data-clean                                                Delete all data in HDFS and ZK prior to starting up hbase"
  echo "    --single-process                                                  Run as single process instead of pseudo-distributed"
  echo ""
  exit 1
}
# if no args specified, show usage
if [ $# -lt 6 ]; then
  usage
fi

# Get arguments
declare component_install
declare hadoop_exec
declare working_dir
declare zk_data_dir
declare clean
declare distributed="true"
declare hadoop_jars
declare hbase_client
declare hbck2_jar_path
while [ $# -gt 0 ]
do
  case "$1" in
    --working-dir) shift; working_dir=$1; shift;;
    --force-data-clean) shift; clean="true";;
    --zookeeper-data) shift; zk_data_dir=$1; shift;;
    --single-process) shift; distributed="false";;
    --hadoop-client-classpath) shift; hadoop_jars="$1"; shift;;
    --hbase-client-install) shift; hbase_client="$1"; shift;;
    --) shift; break;;
    -*) usage ;;
    *)  break;;  # terminate while loop
  esac
done

# should still have where component checkout is.
if [ $# -lt 6 ]; then
  usage
fi
component_install="$(cd "$(dirname "$1")"; pwd)/$(basename "$1")"
hadoop_exec="$(cd "$(dirname "$2")"; pwd)/$(basename "$2")"
yarn_server_tests_test_jar="$(cd "$(dirname "$3")"; pwd)/$(basename "$3")"
mapred_jobclient_test_jar="$(cd "$(dirname "$4")"; pwd)/$(basename "$4")"
mapred_exec="$(cd "$(dirname "$5")"; pwd)/$(basename "$5")"
hbck2_jar_path="$(cd "$(dirname "$6")"; pwd)/$(basename "$6")"

if [ ! -x "${hadoop_exec}" ]; then
  echo "hadoop cli does not appear to be executable." >&2
  exit 1
fi

if [ ! -x "${mapred_exec}" ]; then
  echo "mapred cli does not appear to be executable." >&2
  exit 1
fi

if [ ! -d "${component_install}" ]; then
  echo "Path to HBase binary install should be a directory." >&2
  exit 1
fi

if [ ! -f "${yarn_server_tests_test_jar}" ]; then
  echo "Specified YARN server tests test jar is not a file." >&2
  exit 1
fi

if [ ! -f "${mapred_jobclient_test_jar}" ]; then
  echo "Specified MapReduce jobclient test jar is not a file." >&2
  exit 1
fi

if [ ! -f "${hbck2_jar_path}" ]; then
  echo "Specified hbck2 jar is not a file." >&2
  exit 1
fi

if [ -z "${working_dir}" ]; then
  if ! working_dir="$(mktemp -d -t hbck2-test)" ; then
    echo "Failed to create temporary working directory. Please specify via --working-dir" >&2
    exit 1
  fi
else
  # absolutes please
  working_dir="$(cd "$(dirname "${working_dir}")"; pwd)/$(basename "${working_dir}")"
  if [ ! -d "${working_dir}" ]; then
    echo "passed working directory '${working_dir}' must already exist." >&2
    exit 1
  fi
  # clean the working directory
  rm -rf "${working_dir:?}"/*
fi

if [ -z "${zk_data_dir}" ]; then
  zk_data_dir="${working_dir}/zk-data"
  mkdir "${zk_data_dir}"
else
  # absolutes please
  zk_data_dir="$(cd "$(dirname "${zk_data_dir}")"; pwd)/$(basename "${zk_data_dir}")"
  if [ ! -d "${zk_data_dir}" ]; then
    echo "passed directory for zk '${zk_data_dir}' must already exist."
    exit 1
  fi
fi

if [ -z "${hbase_client}" ]; then
  hbase_client="${component_install}"
else
  echo "Using HBase client-side artifact"
  # absolutes please
  hbase_client="$(cd "$(dirname "${hbase_client}")"; pwd)/$(basename "${hbase_client}")"
  if [ ! -d "${hbase_client}" ]; then
    echo "If given hbase client install should be a directory with contents of the client tarball." >&2
    exit 1
  fi
fi

if [ -n "${hadoop_jars}" ]; then
  declare -a tmp_jars
  for entry in $(echo "${hadoop_jars}" | tr ':' '\n'); do
    tmp_jars=("${tmp_jars[@]}" "$(cd "$(dirname "${entry}")"; pwd)/$(basename "${entry}")")
  done
  hadoop_jars="$(IFS=:; echo "${tmp_jars[*]}")"
fi


echo "You'll find logs and temp files in ${working_dir}"

function redirect_and_run {
  log_base=$1
  shift
  echo "$*" >"${log_base}.err"
  "$@" >"${log_base}.out" 2>>"${log_base}.err"
}

# TODO (can put in dev-support. maybe release scripts.)
SELF=$(cd "$(dirname "$0")" && pwd)
echo "SELF is at: ${SELF}"

(cd "${working_dir}"
echo "Hadoop version information:"
"${hadoop_exec}" version
hadoop_version=$("${hadoop_exec}" version | head -n 1)
hadoop_version="${hadoop_version#Hadoop }"
if [ "${hadoop_version%.*.*}" -gt 2 ]; then
  "${hadoop_exec}" envvars
else
  echo "JAVA_HOME: ${JAVA_HOME}"
fi

# Ensure that if some other Hadoop install happens to be present in the environment we ignore it.
HBASE_DISABLE_HADOOP_CLASSPATH_LOOKUP="true"
export HBASE_DISABLE_HADOOP_CLASSPATH_LOOKUP

if [ -n "${clean}" ]; then
  echo "Cleaning out ZooKeeper..."
  rm -rf "${zk_data_dir:?}/*"
fi

echo "HBase version information:"
"${component_install}/bin/hbase" version 2>/dev/null
hbase_version=$("${component_install}/bin/hbase" version | head -n 1 2>/dev/null)
hbase_version="${hbase_version#HBase }"

if [ ! -s "${hbase_client}/lib/shaded-clients/hbase-shaded-mapreduce-${hbase_version}.jar" ]; then
  echo "HBase binary install doesn't appear to include a shaded mapreduce artifact." >&2
  exit 1
fi

if [ ! -s "${hbase_client}/lib/shaded-clients/hbase-shaded-client-${hbase_version}.jar" ]; then
  echo "HBase binary install doesn't appear to include a shaded client artifact." >&2
  exit 1
fi

if [ ! -s "${hbase_client}/lib/shaded-clients/hbase-shaded-client-byo-hadoop-${hbase_version}.jar" ]; then
  echo "HBase binary install doesn't appear to include a shaded client artifact." >&2
  exit 1
fi

echo "Writing out configuration for HBase."
rm -rf "${working_dir}/hbase-conf"
mkdir "${working_dir}/hbase-conf"

if [ -f "${component_install}/conf/log4j.properties" ]; then
  cp "${component_install}/conf/log4j.properties" "${working_dir}/hbase-conf/log4j.properties"
else
  # shellcheck disable=SC2154
  cat >"${working_dir}/hbase-conf/log4j.properties" <<EOF
# Define some default values that can be overridden by system properties
hbase.root.logger=INFO,console

# Define the root logger to the system property "hbase.root.logger".
log4j.rootLogger=${hbase.root.logger}

# Logging Threshold
log4j.threshold=ALL
# console
log4j.appender.console=org.apache.log4j.ConsoleAppender
log4j.appender.console.target=System.err
log4j.appender.console.layout=org.apache.log4j.PatternLayout
log4j.appender.console.layout.ConversionPattern=%d{ISO8601} %-5p [%t] %c{2}: %.1000m%n
EOF
fi

cat >"${working_dir}/hbase-conf/hbase-site.xml" <<EOF
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<!--
/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
-->
<configuration>
  <property>
    <name>hbase.rootdir</name>
    <!-- We rely on the defaultFS being set in our hadoop confs -->
    <value>/hbase</value>
  </property>
  <property>
    <name>hbase.zookeeper.property.dataDir</name>
    <value>${zk_data_dir}</value>
  </property>
  <property>
    <name>hbase.cluster.distributed</name>
    <value>${distributed}</value>
  </property>
</configuration>
EOF

if [ "true" = "${distributed}" ]; then
  cat >"${working_dir}/hbase-conf/regionservers" <<EOF
localhost
EOF
fi

function cleanup {

  echo "Shutting down HBase"
  HBASE_CONF_DIR="${working_dir}/hbase-conf/" "${component_install}/bin/stop-hbase.sh"

  if [ -f "${working_dir}/hadoop.pid" ]; then
    echo "Shutdown: listing HDFS contents"
    redirect_and_run "${working_dir}/hadoop_listing_at_end" \
    "${hadoop_exec}" --config "${working_dir}/hbase-conf/" fs -ls -R /

    echo "Shutting down Hadoop"
    kill -6 "$(cat "${working_dir}/hadoop.pid")"
  fi
}

trap cleanup EXIT SIGQUIT

echo "Starting up Hadoop"

if [ "${hadoop_version%.*.*}" -gt 2 ]; then
  "${mapred_exec}" minicluster -format -writeConfig "${working_dir}/hbase-conf/core-site.xml" -writeDetails "${working_dir}/hadoop_cluster_info.json" >"${working_dir}/hadoop_cluster_command.out" 2>"${working_dir}/hadoop_cluster_command.err" &
else
  HADOOP_CLASSPATH="${yarn_server_tests_test_jar}" "${hadoop_exec}" jar "${mapred_jobclient_test_jar}" minicluster -format -writeConfig "${working_dir}/hbase-conf/core-site.xml" -writeDetails "${working_dir}/hadoop_cluster_info.json" >"${working_dir}/hadoop_cluster_command.out" 2>"${working_dir}/hadoop_cluster_command.err" &
fi

echo "$!" > "${working_dir}/hadoop.pid"

sleep_time=2
until [ -s "${working_dir}/hbase-conf/core-site.xml" ]; do
  printf '\twaiting for Hadoop to finish starting up.\n'
  sleep "${sleep_time}"
  sleep_time="$((sleep_time*2))"
done

if [ "${hadoop_version%.*.*}" -gt 2 ]; then
  echo "Verifying configs"
  "${hadoop_exec}" --config "${working_dir}/hbase-conf/" conftest
fi

if [ -n "${clean}" ]; then
  echo "Cleaning out HDFS..."
  "${hadoop_exec}" --config "${working_dir}/hbase-conf/" fs -rm -r /hbase || true
fi

echo "Listing HDFS contents"
redirect_and_run "${working_dir}/hadoop_cluster_smoke" \
    "${hadoop_exec}" --config "${working_dir}/hbase-conf/" fs -ls -R /

echo "Starting up HBase"
HBASE_CONF_DIR="${working_dir}/hbase-conf/" "${component_install}/bin/start-hbase.sh"

sleep_time=2
until "${component_install}/bin/hbase" --config "${working_dir}/hbase-conf/" shell --noninteractive >"${working_dir}/waiting_hbase_startup.log" 2>&1 <<EOF
  count 'hbase:meta'
EOF
do
  printf '\tretry waiting for hbase to come up.\n'
  sleep "${sleep_time}"
  sleep_time="$((sleep_time*2))"
done

echo "Running LoadTestTool to create a table with 5 regions with 1000 rows"
# Changing the number of rows here might effect the post-test verification in scripts like scripts/deleteRegionInMeta.sh
"${hbase_client}/bin/hbase" --config "${working_dir}/hbase-conf/" org.apache.hadoop.hbase.util.LoadTestTool  -write 3:1024 -tn testtable -num_keys 1000 >"${working_dir}/ltt.log" 2>&1

if [ -z "${hadoop_jars}" ]; then
  echo "Hadoop client jars not given; getting them from 'hadoop classpath'."
  hadoop_jars=$("${hadoop_exec}" --config "${working_dir}/hbase-conf/" classpath)
fi

echo "Running all the action scripts from ${SELF}/scripts/..."

echo "Invoking deleteRegionFromMeta.sh..."
"$SELF/scripts/deleteRegionInMeta.sh" "${hbase_client}" "${working_dir}" "${component_install}" "${hbck2_jar_path}" "${hadoop_jars}" "${hbase_version}" default:testtable

echo "SUCCESS: hbck2 testing succeeded."
)
