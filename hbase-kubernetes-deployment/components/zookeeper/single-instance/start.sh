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
# Derived from work Copyright 2017 The Kubernetes Authors.
# See https://github.com/kow3ns/kubernetes-zookeeper/blob/master/docker/scripts/start-zookeeper for more details
# and then https://github.com/cloudurable/kube-zookeeper-statefulsets/
# In the below we change the '--heap' argument to '--percentage' so
# could set the server heap as a percentage of the container resource
# limit rather than hard-code it.
# Currently zookeeper.root.logger is CONSOLE only. We do not write
# logs to files. Fix config. if you need it.
#
#
# Usage: start-zookeeper [OPTIONS]
# Starts a ZooKeeper server based on the supplied options.
#     --servers           The number of servers in the ensemble. The default
#                         value is 1.
#     --data_dir          The directory where the ZooKeeper process will store its
#                         snapshots. The default is /var/lib/zookeeper/data.
#     --data_log_dir      The directory where the ZooKeeper process will store its
#                         write ahead log. The default is
#                         /var/lib/zookeeper/data/log.
#     --conf_dir          The directory where the ZooKeeper process will store its
#                         configuration. The default is /opt/zookeeper/conf.
#     --client_port       The port on which the ZooKeeper process will listen for
#                         client requests. The default is 2181.

#     --election_port     The port on which the ZooKeeper process will perform
#                         leader election. The default is 3888.

#     --server_port       The port on which the ZooKeeper process will listen for
#                         requests from other servers in the ensemble. The
#                         default is 2888.

#     --tick_time         The length of a ZooKeeper tick in ms. The default is
#                         2000.

#     --init_limit        The number of Ticks that an ensemble member is allowed
#                         to perform leader election. The default is 10.

#     --sync_limit        The maximum session timeout that the ensemble will
#                         allows a client to request. The default is 5.

#     --percentage        The percentage of container memory to give to the JVM.

#     --max_client_cnxns  The maximum number of client connections that the
#                         ZooKeeper process will accept simultaneously. The
#                         default is 60.

#     --snap_retain_count The maximum number of snapshots the ZooKeeper process
#                         will retain if purge_interval is greater than 0. The
#                         default is 3.

#     --purge_interval    The number of hours the ZooKeeper process will wait
#                         between purging its old snapshots. If set to 0 old
#                         snapshots will never be purged. The default is 0.

#     --max_session_timeout The maximum time in milliseconds for a client session
#                         timeout. The default value is 2 * tick time.

#     --min_session_timeout The minimum time in milliseconds for a client session
#                         timeout. The default value is 20 * tick time.

#     --log_level         The log level for the zookeeeper server. Either FATAL,
#                         ERROR, WARN, INFO, DEBUG. The default is INFO.

#     --quorum_listen_on_all_ips
#                         When set to true the ZooKeeper server will listen for
#                         connections from its peers on all available IP addresses,
#                         and not only the address configured in the server list of
#                         the configuration file. It affects the connections handling
#                         the ZAB protocol and the Fast Leader Election protocol.
#                         Default value is false.
set -x

ZOOKEEPER_HOME="$( ls -d /apache-zookeeper*  )"
USER="$(whoami)"
HOST="$(hostname -s)"
DOMAIN="$(hostname -d)"
LOG_LEVEL=INFO
DATA_DIR="/var/lib/zookeeper/data"
DATA_LOG_DIR="/var/lib/zookeeper/log"
LOG_DIR="/var/log/zookeeper"
CONF_DIR="/opt/zookeeper/conf"
CLIENT_PORT=2181
SERVER_PORT=2888
ELECTION_PORT=3888
PROM_PORT=7001
TICK_TIME=2000
INIT_LIMIT=10
SYNC_LIMIT=5
JVM_HEAP_PERCENTAGE_OF_RESOURCE_LIMIT=50
MAX_CLIENT_CNXNS=1000
SNAP_RETAIN_COUNT=3
PURGE_INTERVAL=0
SERVERS=1
QUORUM_LISTEN_ON_ALL_IPS=false

function print_usage() {
echo "\
Usage: start-zookeeper [OPTIONS]
Starts a ZooKeeper server based on the supplied options.
    --servers           The number of servers in the ensemble. The default
                        value is 1.

    --data_dir          The directory where the ZooKeeper process will store its
                        snapshots. The default is /var/lib/zookeeper/data.

    --data_log_dir      The directory where the ZooKeeper process will store its
                        write ahead log. The default is
                        /var/lib/zookeeper/data/log.

    --conf_dir          The directoyr where the ZooKeeper process will store its
                        configuration. The default is /opt/zookeeper/conf.

    --client_port       The port on which the ZooKeeper process will listen for
                        client requests. The default is 2181.

    --election_port     The port on which the ZooKeeper process will perform
                        leader election. The default is 3888.

    --server_port       The port on which the ZooKeeper process will listen for
                        requests from other servers in the ensemble. The
                        default is 2888.

    --tick_time         The length of a ZooKeeper tick in ms. The default is
                        2000.

    --init_limit        The number of Ticks that an ensemble member is allowed
                        to perform leader election. The default is 10.

    --sync_limit        The maximum session timeout that the ensemble will
                        allows a client to request. The default is 5.

    --percentage        The percentage of container memory to give to the JVM.

    --max_client_cnxns  The maximum number of client connections that the
                        ZooKeeper process will accept simultaneously. The
                        default is 60.

    --snap_retain_count The maximum number of snapshots the ZooKeeper process
                        will retain if purge_interval is greater than 0. The
                        default is 3.

    --purge_interval    The number of hours the ZooKeeper process will wait
                        between purging its old snapshots. If set to 0 old
                        snapshots will never be purged. The default is 0.

    --max_session_timeout The maximum time in milliseconds for a client session
                        timeout. The default value is 2 * tick time.

    --min_session_timeout The minimum time in milliseconds for a client session
                        timeout. The default value is 20 * tick time.

    --log_level         The log level for the zookeeeper server. Either FATAL,
                        ERROR, WARN, INFO, DEBUG. The default is INFO.
"
}

function create_data_dirs() {
    if [ ! -d "$DATA_DIR"  ]; then
        mkdir -p "$DATA_DIR"
        chown -R "$USER":"$USER" "$DATA_DIR"
    fi

    if [ ! -d "$DATA_LOG_DIR"  ]; then
        mkdir -p "$DATA_LOG_DIR"
        chown -R "$USER":"$USER" "$DATA_LOG_DIR"
    fi

    if [ ! -d "$LOG_DIR"  ]; then
        mkdir -p "$LOG_DIR"
        chown -R "$USER":"$USER" "$LOG_DIR"
    fi
    if [ ! -f "$ID_FILE" ] && [ "$SERVERS" -gt 1 ]; then
        echo "$MY_ID" >> "$ID_FILE"
    fi
}

function print_servers() {
    for (( i=1; i<=SERVERS; i++ ))
    do
        echo "server.$i=$NAME-$((i-1)).$DOMAIN:$SERVER_PORT:$ELECTION_PORT"
    done
}

function create_config() {
    rm -f "$CONFIG_FILE"
    {
        echo "#This file was autogenerated DO NOT EDIT"
        echo "clientPort=$CLIENT_PORT"
        echo "dataDir=$DATA_DIR"
        echo "dataLogDir=$DATA_LOG_DIR"
        echo "tickTime=$TICK_TIME"
        echo "initLimit=$INIT_LIMIT"
        echo "syncLimit=$SYNC_LIMIT"
        echo "maxClientCnxns=$MAX_CLIENT_CNXNS"
        echo "minSessionTimeout=$MIN_SESSION_TIMEOUT"
        echo "maxSessionTimeout=$MAX_SESSION_TIMEOUT"
        echo "autopurge.snapRetainCount=$SNAP_RETAIN_COUNT"
        echo "autopurge.purgeInteval=$PURGE_INTERVAL"
        echo "quorumListenOnAllIPs=$QUORUM_LISTEN_ON_ALL_IPS"
        # Allow running all zk commands.
        echo "4lw.commands.whitelist=*"
        if [ "$SERVERS" -gt 1 ]; then
            print_servers
        fi
        echo "metricsProvider.className=org.apache.zookeeper.metrics.prometheus.PrometheusMetricsProvider"
        echo "metricsProvider.httpPort=$PROM_PORT"
    } >> "$CONFIG_FILE"
    cat "$CONFIG_FILE" >&2
}

function create_jvm_props() {
    rm -f "$JAVA_ENV_FILE"
    {
        echo "SERVER_JVMFLAGS=\"-XX:MaxRAMPercentage=${JVM_HEAP_PERCENTAGE_OF_RESOURCE_LIMIT} \
             -XX:InitialRAMPercentage=${JVM_HEAP_PERCENTAGE_OF_RESOURCE_LIMIT}\""
        echo "ZOO_LOG_DIR=$LOG_DIR"
        echo "JVMFLAGS="
    } >> "$JAVA_ENV_FILE"
}

function create_log_props() {
    rm -f "$LOGGER_PROPS_FILE"
    echo "Creating ZooKeeper log4j configuration"
    {
        echo "zookeeper.root.logger=CONSOLE"
        echo "zookeeper.console.threshold=$LOG_LEVEL"
        echo "log4j.rootLogger=\${zookeeper.root.logger}"
        echo "log4j.appender.CONSOLE=org.apache.log4j.ConsoleAppender"
        echo "log4j.appender.CONSOLE.Threshold=\${zookeeper.console.threshold}"
        echo "log4j.appender.CONSOLE.layout=org.apache.log4j.PatternLayout"
        echo "log4j.appender.CONSOLE.layout.ConversionPattern=%d{ISO8601} [myid:%X{myid}] - %-5p [%t:%C{1}@%L] - %m%n"
    } >> "$LOGGER_PROPS_FILE"
}

optspec=":hv-:"
while getopts "$optspec" optchar; do

    case "${optchar}" in
        -)
            case "${OPTARG}" in
                servers=*)
                    SERVERS=${OPTARG##*=}
                    ;;
                data_dir=*)
                    DATA_DIR=${OPTARG##*=}
                    ;;
                data_log_dir=*)
                    DATA_LOG_DIR=${OPTARG##*=}
                    ;;
                log_dir=*)
                    LOG_DIR=${OPTARG##*=}
                    ;;
                conf_dir=*)
                    CONF_DIR=${OPTARG##*=}
                    ;;
                client_port=*)
                    CLIENT_PORT=${OPTARG##*=}
                    ;;
                election_port=*)
                    ELECTION_PORT=${OPTARG##*=}
                    ;;
                server_port=*)
                    SERVER_PORT=${OPTARG##*=}
                    ;;
                tick_time=*)
                    TICK_TIME=${OPTARG##*=}
                    ;;
                init_limit=*)
                    INIT_LIMIT=${OPTARG##*=}
                    ;;
                sync_limit=*)
                    SYNC_LIMIT=${OPTARG##*=}
                    ;;
                percentage=*)
                    JVM_HEAP_PERCENTAGE_OF_RESOURCE_LIMIT=${OPTARG##*=}
                    ;;
                max_client_cnxns=*)
                    MAX_CLIENT_CNXNS=${OPTARG##*=}
                    ;;
                snap_retain_count=*)
                    SNAP_RETAIN_COUNT=${OPTARG##*=}
                    ;;
                purge_interval=*)
                    PURGE_INTERVAL=${OPTARG##*=}
                    ;;
                max_session_timeout=*)
                    MAX_SESSION_TIMEOUT=${OPTARG##*=}
                    ;;
                min_session_timeout=*)
                    MIN_SESSION_TIMEOUT=${OPTARG##*=}
                    ;;
                quorum_listen_on_all_ips=*)
                    QUORUM_LISTEN_ON_ALL_IPS=${OPTARG##*=}
                    ;;
                log_level=*)
                    LOG_LEVEL=${OPTARG##*=}
                    ;;
                *)
                    echo "Unknown option --${OPTARG}" >&2
                    exit 1
                    ;;
            esac;;
        h)
            print_usage
            exit
            ;;
        v)
            echo "Parsing option: '-${optchar}'" >&2
            ;;
        *)
            if [ "$OPTERR" != 1 ] || [ "${optspec:0:1}" = ":" ]; then
                echo "Non-option argument: '-${OPTARG}'" >&2
            fi
            ;;
    esac
done

MIN_SESSION_TIMEOUT=${MIN_SESSION_TIMEOUT:- $((TICK_TIME*2))}
MAX_SESSION_TIMEOUT=${MAX_SESSION_TIMEOUT:- $((TICK_TIME*20))}
ID_FILE="$DATA_DIR/myid"
if [ ! -d "$CONF_DIR"  ]; then
  mkdir -p "$CONF_DIR"
  chown -R "$USER":"$USER" "$CONF_DIR"
fi
CONFIG_FILE="$CONF_DIR/zoo.cfg"
LOGGER_PROPS_FILE="$CONF_DIR/log4j.properties"
JAVA_ENV_FILE="$CONF_DIR/java.env"

if [[ $HOST =~ (.*)-([0-9]+)$ ]]; then
    NAME=${BASH_REMATCH[1]}
    ORD=${BASH_REMATCH[2]}
else
    echo "Failed to parse name and ordinal of Pod"
    exit 1
fi

MY_ID=$((ORD+1))

export ZOOCFGDIR=${CONF_DIR}
create_config && create_jvm_props && create_log_props && create_data_dirs && exec "${ZOOKEEPER_HOME}/bin/zkServer.sh" start-foreground
