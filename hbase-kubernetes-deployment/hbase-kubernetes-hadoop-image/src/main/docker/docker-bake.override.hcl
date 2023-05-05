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
# Externalize default values of build parameters and document how to retrieve them.
#

function "basename" {
  params = [a]
  result = split("/", a)[length(split("/", a)) - 1]
}

variable CORRETTO_KEY_URL {
  default = "https://yum.corretto.aws/corretto.key"
}

variable CORRETTO_KEY {
  default = "${basename(CORRETTO_KEY_URL)}"
}

variable CORRETTO_REPO_URL {
  default = "https://yum.corretto.aws/corretto.repo"
}

variable CORRETTO_REPO {
  default = "${basename(CORRETTO_REPO_URL)}"
}

variable JMX_PROMETHEUS_JAR_URL {
  default = "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.16.1/jmx_prometheus_javaagent-0.16.1.jar"
}

variable JMX_PROMETHEUS_JAR {
  default = "${basename(JMX_PROMETHEUS_JAR_URL)}"
}
