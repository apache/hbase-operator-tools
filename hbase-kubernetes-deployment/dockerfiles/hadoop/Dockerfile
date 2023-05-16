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

ARG BASE_IMG='apache/hadoop'
ARG BASE_TAG='3'

## -- Stage installed_jmx_exporter --
# A stage that prepares the JMX Exporter installation directory. The jar must be in well-known
# location so can refer to it in command yaml value.
FROM alpine AS installed_jmx_exporter
ARG JMX_PROMETHEUS_JAR
COPY ${JMX_PROMETHEUS_JAR} /tmp/jmx_prometheus_javaagent.jar

FROM ${BASE_IMG}:${BASE_TAG} as final
ARG CORRETTO_KEY
ARG CORRETTO_REPO
USER root
COPY ${CORRETTO_KEY} /tmp/
COPY ${CORRETTO_REPO} /etc/yum.repos.d/

RUN rpm --import /tmp/corretto.key \
    && yum -y remove java-1.8.0-* \
    && yum -y update \
    && yum -y install java-17-amazon-corretto-devel \
    && yum -y clean all \
    && rm -rf /var/cache

COPY --from=installed_jmx_exporter \
  --chown=hadoop:users \
  /tmp/jmx_prometheus_javaagent.jar /opt/hadoop/jmx_prometheus_javaagent.jar

ENV JMX_PROMETHEUS_JAR /opt/hadoop/jmx_prometheus_javaagent.jar
ENV USER='hadoop'
USER ${USER}
