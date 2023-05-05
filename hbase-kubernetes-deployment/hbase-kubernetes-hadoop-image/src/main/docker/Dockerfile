# syntax=docker/dockerfile:1.4
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

# hadolint global ignore=DL3020

ARG BASE_IMG='apache/hadoop'
ARG BASE_TAG='3'
ARG BASE_IMG_ALPINE='alpine'
ARG BASE_IMG_TAG_ALPINE='latest'

FROM ${BASE_IMG_ALPINE}:${BASE_IMG_TAG_ALPINE} as alpine

## -- Stage installed_jmx_exporter --
# A stage that prepares the JMX Exporter installation directory. The jar must be in well-known
# location so can refer to it in command yaml value.
FROM alpine AS installed_jmx_exporter
ARG JMX_PROMETHEUS_JAR_URL
ADD --link ${JMX_PROMETHEUS_JAR_URL} /tmp/jmx_prometheus_javaagent.jar

FROM ${BASE_IMG}:${BASE_TAG} as final
ARG CORRETTO_KEY_URL
ARG CORRETTO_KEY
ARG CORRETTO_REPO_URL
ARG CORRETTO_REPO
USER root
ADD --link ${CORRETTO_KEY_URL} /tmp/
ADD --link ${CORRETTO_REPO_URL} /etc/yum.repos.d/

RUN rpm --import "/tmp/${CORRETTO_KEY}" \
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
