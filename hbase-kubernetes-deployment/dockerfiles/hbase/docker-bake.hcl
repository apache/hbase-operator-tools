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
# A convenience script for build the hbase image.
# See hbase-kubernetes-deployment/dockerfiles/hbase/README.md
#

# input variables
variable HBASE_TGZ {}
variable HBASE_TGZ_SHA512 {}
variable HBASE_OPERATOR_TOOLS_TGZ {}
variable HBASE_OPERATOR_TOOLS_TGZ_SHA512 {}
variable JMX_PROMETHEUS_JAR {}

# output variables
variable USER {}
variable IMAGE_TAG {
  default = "latest"
}
variable IMAGE_NAME {
  default = "${USER}/hbase/operator-tools/hbase"
}

group default {
  targets = [ "hbase" ]
}

target hbase {
  dockerfile = "hbase-kubernetes-deployment/dockerfiles/hbase/Dockerfile"
  args = {
    HBASE_TGZ = HBASE_TGZ
    HBASE_TGZ_SHA512 = HBASE_TGZ_SHA512
    HBASE_OPERATOR_TOOLS_TGZ = HBASE_OPERATOR_TOOLS_TGZ
    HBASE_OPERATOR_TOOLS_TGZ_SHA512 = HBASE_OPERATOR_TOOLS_TGZ_SHA512
    JMX_PROMETHEUS_JAR = JMX_PROMETHEUS_JAR
  }
  platforms = [
    "linux/amd64",
    "linux/arm64"
  ]
  tags = [ "${IMAGE_NAME}:${IMAGE_TAG}" ]
}
