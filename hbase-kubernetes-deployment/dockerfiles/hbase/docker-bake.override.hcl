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

variable HBASE_TGZ {
  # wget 'https://downloads.apache.org/hbase/2.5.4/hbase-2.5.4-hadoop3-bin.tar.gz'
  default = "hbase-2.5.4-hadoop3-bin.tar.gz"
}

variable HBASE_TGZ_SHA512 {
  # wget 'https://downloads.apache.org/hbase/2.5.4/hbase-2.5.4-hadoop3-bin.tar.gz.sha512'
  default = "hbase-2.5.4-hadoop3-bin.tar.gz.sha512"
}

variable HBASE_OPERATOR_TOOLS_TGZ {
  # wget 'https://downloads.apache.org/hbase/hbase-operator-tools-1.2.0/hbase-operator-tools-1.2.0-bin.tar.gz'
  default = "hbase-operator-tools-1.2.0-bin.tar.gz"
}

variable HBASE_OPERATOR_TOOLS_TGZ_SHA512 {
  # wget 'https://downloads.apache.org/hbase/hbase-operator-tools-1.2.0/hbase-operator-tools-1.2.0-bin.tar.gz.sha512'
  default = "hbase-operator-tools-1.2.0-bin.tar.gz.sha512"
}

variable JMX_PROMETHEUS_JAR {
  # wget 'https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.16.1/jmx_prometheus_javaagent-0.16.1.jar'
  default = "jmx_prometheus_javaagent-0.16.1.jar"
}
