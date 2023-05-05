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
# A convenience script for build the kuttl image.
# See hbase-kubernetes-deployment/dockerfiles/kuttl/README.md
#

variable BASE_IMG {
  default = "apache/hadoop"
}
variable BASE_TAG {
  default = "3"
}

variable USER {}
variable IMAGE_TAG {
  default = "latest"
}
variable IMAGE_NAME {
  default = "${USER}/hbase/operator-tools/hadoop"
}
variable CORRETTO_KEY {}
variable CORRETTO_REPO {}
variable JMX_PROMETHEUS_JAR {}

group default {
  targets = [ "hadoop" ]
}

target hadoop {
  dockerfile = "hbase-kubernetes-deployment/dockerfiles/hadoop/Dockerfile"
  args = {
    BASE_IMG = BASE_IMG
    BASE_TAG = BASE_TAG
    CORRETTO_KEY = CORRETTO_KEY
    CORRETTO_REPO = CORRETTO_REPO
    JMX_PROMETHEUS_JAR = JMX_PROMETHEUS_JAR
  }
  target = "final"
  platforms = [
    "linux/amd64"
  ]
  tags = [ "${IMAGE_NAME}:${IMAGE_TAG}" ]
}
