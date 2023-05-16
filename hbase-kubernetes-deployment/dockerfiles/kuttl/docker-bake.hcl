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

# input variables
variable KUBECTL_SHA_AMD64 {}
variable KUBECTL_BIN_AMD64 {}
variable KUBECTL_SHA_ARM64 {}
variable KUBECTL_BIN_ARM64 {}
variable KUTTL_CHECKSUMS {}
variable KUTTL_BIN_AMD64 {}
variable KUTTL_BIN_ARM64 {}
variable KUSTOMIZE_CHECKSUMS {}
variable KUSTOMIZE_BIN_AMD64_TGZ {}
variable KUSTOMIZE_BIN_ARM64_TGZ {}
variable AWS_CLI_PUBLIC_KEY {}
variable AWS_CLI_SOURCES_TGZ {}
variable AWS_CLI_SOURCES_TGZ_SIG {}

# output variables
variable USER {}
variable IMAGE_TAG {
  default = "latest"
}
variable IMAGE_NAME {
  default = "${USER}/hbase/operator-tools/kuttl"
}

group default {
  targets = [ "kuttl" ]
}

target kuttl {
  dockerfile = "hbase-kubernetes-deployment/dockerfiles/kuttl/Dockerfile"
  args = {
    KUBECTL_SHA_AMD64 = KUBECTL_SHA_AMD64
    KUBECTL_BIN_AMD64 = KUBECTL_BIN_AMD64
    KUBECTL_SHA_ARM64 = KUBECTL_SHA_ARM64
    KUBECTL_BIN_ARM64 = KUBECTL_BIN_ARM64
    KUTTL_CHECKSUMS = KUTTL_CHECKSUMS
    KUTTL_BIN_AMD64 = KUTTL_BIN_AMD64
    KUTTL_BIN_ARM64 = KUTTL_BIN_ARM64
    KUSTOMIZE_CHECKSUMS = KUSTOMIZE_CHECKSUMS
    KUSTOMIZE_BIN_AMD64_TGZ = KUSTOMIZE_BIN_AMD64_TGZ
    KUSTOMIZE_BIN_ARM64_TGZ = KUSTOMIZE_BIN_ARM64_TGZ
    AWS_CLI_PUBLIC_KEY = AWS_CLI_PUBLIC_KEY
    AWS_CLI_SOURCES_TGZ = AWS_CLI_SOURCES_TGZ
    AWS_CLI_SOURCES_TGZ_SIG = AWS_CLI_SOURCES_TGZ_SIG
  }
  target = "final"
  platforms = [
    "linux/amd64",
    "linux/arm64"
  ]
  tags = [ "${IMAGE_NAME}:${IMAGE_TAG}" ]
}
