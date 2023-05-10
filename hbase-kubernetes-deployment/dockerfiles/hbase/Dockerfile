# syntax=docker/dockerfile:1.2
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

ARG IMG_BASE='alpine'
ARG IMG_BASE_TAG='latest'

FROM ${IMG_BASE}:${IMG_BASE_TAG} as hbase_base
# define ARGs for identites we may need to share with the environment.
ARG HBASE_USER='hbase'
ARG HBASE_ID='1000'
ARG HADOOP_USER='hadoop'
ARG HADOOP_ID='1001'
ARG HDFS_USER='hdfs'
ARG HDFS_ID='1002'

ENV HBASE_USER="${HBASE_USER}"
ENV HBASE_ID="${HBASE_ID}"
ENV HADOOP_USER="${HADOOP_USER}"
ENV HADOOP_ID="${HADOOP_ID}"
ENV HDFS_USER="${HDFS_USER}"
ENV HDFS_ID="${HDFS_ID}"

# make a nonroot user kinda sorta like distroless. switch to using distroless in the future?
# https://github.com/GoogleContainerTools/distroless
ENV NON_ROOT_USER=nonroot
ENV NON_ROOT_USER_ID=65532

# Create `nonroot`, `hadoop`, and `hbase` groups ; `nonroot`, `hdfs`, and `hbase` users
RUN addgroup -g "${NON_ROOT_USER_ID}" "${NON_ROOT_USER}" \
    && addgroup -S -g "${HADOOP_ID}" "${HADOOP_USER}" \
    && addgroup -S -g "${HBASE_ID}" "${HBASE_USER}" \
    && adduser -S -D -H \
        -h /dev/null \
        -u "${NON_ROOT_USER_ID}" \
        -G "${NON_ROOT_USER}" \
        -g "${NON_ROOT_USER}" \
    "${NON_ROOT_USER}" \
    && adduser "${NON_ROOT_USER}" "${HBASE_USER}" \
    && adduser "${NON_ROOT_USER}" "${HADOOP_USER}" \
    && adduser -S -D -H \
        -h /dev/null \
        -u "${HDFS_ID}" \
        -G "${HADOOP_USER}" \
        -g "${HDFS_USER}" \
        "${HDFS_USER}" \
    && adduser -S -D -H \
        -h /dev/null \
        -u "${HBASE_ID}" \
        -G "${HADOOP_USER}" \
        -g "${HBASE_USER}" \
        "${HBASE_USER}"

FROM alpine AS verification_tools
RUN apk add --no-cache gpg

## -- Stage staged_hbase --
# A stage that prepares the HBase installation directory.
FROM verification_tools AS staged_hbase
ARG HBASE_TGZ
ARG HBASE_TGZ_SHA512
COPY "${HBASE_TGZ}" /tmp/
COPY "${HBASE_TGZ_SHA512}" /tmp/
RUN ash -c 'cd /tmp ; diff <(gpg --print-md SHA512 "${HBASE_TGZ}") "${HBASE_TGZ_SHA512}"' \
    && mkdir /tmp/hbase \
    && cd /tmp/hbase \
    && tar -xzf "/tmp/${HBASE_TGZ}" --strip-components=1 \
    && rm -rf docs/ lib/shaded-clients/

## -- Stage staged_hbase_operator_tools --
# A stage that prepares the HBase Operator Tools installation directory.
FROM verification_tools AS staged_hbase_operator_tools
ARG HBASE_OPERATOR_TOOLS_TGZ
ARG HBASE_OPERATOR_TOOLS_TGZ_SHA512
COPY ${HBASE_OPERATOR_TOOLS_TGZ} /tmp/
COPY ${HBASE_OPERATOR_TOOLS_TGZ_SHA512} /tmp/
RUN ash -c 'cd /tmp ; diff <(gpg --print-md SHA512 "${HBASE_OPERATOR_TOOLS_TGZ}") "${HBASE_OPERATOR_TOOLS_TGZ_SHA512}"' \
    && mkdir /tmp/hbase-operator-tools \
    && cd /tmp/hbase-operator-tools \
    && tar -xzf "/tmp/${HBASE_OPERATOR_TOOLS_TGZ}" --strip-components=1 \
    && mkdir /tmp/bin
COPY hbase-kubernetes-deployment/dockerfiles/hbase/hbck2 /tmp/bin/hbck2

## -- Stage installed_jmx_exporter --
# A stage that prepares the JMX Exporter installation directory. The jar must be in well-known location so can refer
# to it in command yaml value.
FROM alpine AS installed_jmx_exporter
ARG JMX_PROMETHEUS_JAR
COPY ${JMX_PROMETHEUS_JAR} /tmp/jmx_prometheus_javaagent.jar

## stage final
# copy all the bits out of prepared stages
FROM hbase_base as final
RUN apk add --no-cache \
    bash \
    curl \
    openjdk17-jre-headless \
    jq

ENV HBASE_HOME="/opt/hbase"
ENV HBASE_LOG_DIR="/var/log/hbase"
ENV HBASE_OPERATOR_TOOLS_HOME="/opt/hbase-operator-tools"
ENV JMX_PROMETHEUS_JAR="${HBASE_HOME}/jmx_prometheus_javaagent.jar"
ENV HADOOP_HOME="${HBASE_HOME}"
ENV PATH="${HBASE_HOME}/bin:${PATH}"
ENV JAVA_HOME="/usr/lib/jvm/default-jvm"

COPY --from=staged_hbase /tmp/hbase/ "${HBASE_HOME}/"
COPY --from=staged_hbase_operator_tools \
    /tmp/hbase-operator-tools/ "${HBASE_OPERATOR_TOOLS_HOME}/"
COPY --from=staged_hbase_operator_tools /tmp/bin/hbck2 "${HBASE_HOME}/bin/"
COPY --from=installed_jmx_exporter /tmp/jmx_prometheus_javaagent.jar "${JMX_PROMETHEUS_JAR}"

RUN mkdir -p "${HBASE_LOG_DIR}" \
    && chown "${HBASE_USER}":"${HBASE_USER}" "${HBASE_LOG_DIR}" \
    && chmod g+w "${HBASE_LOG_DIR}"

ENV USER="${NON_ROOT_USER}"
USER "${USER}"
CMD ["hbase", "version"]
