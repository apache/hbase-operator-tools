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

variable KUBECTL_VERSION {
  default = "1.24.10"
}

variable KUBECTL_SHA_AMD64_URL {
  default = "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl.sha512"
}

variable KUBECTL_SHA_AMD64 {
  default = "${basename(KUBECTL_SHA_AMD64_URL)}"
}

variable KUBECTL_BIN_AMD64_URL {
  default = "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
}

variable KUBECTL_BIN_AMD64 {
  default = "${basename(KUBECTL_BIN_AMD64_URL)}"
}

variable KUBECTL_SHA_ARM64_URL {
  default = "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/arm64/kubectl.sha512"
}

variable KUBECTL_SHA_ARM64 {
  default = "${basename(KUBECTL_SHA_ARM64_URL)}"
}

variable KUBECTL_BIN_ARM64_URL {
  default = "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/arm64/kubectl"
}

variable KUBECTL_BIN_ARM64 {
  default = "${basename(KUBECTL_BIN_ARM64_URL)}"
}

variable KUTTL_VERSION {
  default = "0.15.0"
}

variable KUTTL_CHECKSUMS_URL {
  default = "https://github.com/kudobuilder/kuttl/releases/download/v${KUTTL_VERSION}/checksums.txt"
}

variable KUTTL_CHECKSUMS {
  default = "${basename(KUTTL_CHECKSUMS_URL)}"
}

variable KUTTL_BIN_AMD64_URL {
  default = "https://github.com/kudobuilder/kuttl/releases/download/v${KUTTL_VERSION}/kubectl-kuttl_${KUTTL_VERSION}_linux_x86_64"
}

variable KUTTL_BIN_AMD64 {
  default = "${basename(KUTTL_BIN_AMD64_URL)}"
}

variable KUTTL_BIN_ARM64_URL {
  default = "https://github.com/kudobuilder/kuttl/releases/download/v${KUTTL_VERSION}/kubectl-kuttl_${KUTTL_VERSION}_linux_arm64"
}

variable KUTTL_BIN_ARM64 {
  default = "${basename(KUTTL_BIN_ARM64_URL)}"
}

variable KUSTOMIZE_VERSION {
  default = "4.5.4"
}

variable KUSTOMIZE_CHECKSUMS_URL {
  default = "https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2Fv${KUSTOMIZE_VERSION}/checksums.txt"
}

variable KUSTOMIZE_CHECKSUMS {
  default = "${basename(KUSTOMIZE_CHECKSUMS_URL)}"
}

variable KUSTOMIZE_BIN_AMD64_TGZ_URL {
  default = "https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2Fv${KUSTOMIZE_VERSION}/kustomize_v${KUSTOMIZE_VERSION}_linux_amd64.tar.gz"
}

variable KUSTOMIZE_BIN_AMD64_TGZ {
  default = "${basename(KUSTOMIZE_BIN_AMD64_TGZ_URL)}"
}

variable KUSTOMIZE_BIN_ARM64_TGZ_URL {
  default = "https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2Fv${KUSTOMIZE_VERSION}/kustomize_v${KUSTOMIZE_VERSION}_linux_arm64.tar.gz"
}

variable KUSTOMIZE_BIN_ARM64_TGZ {
  default = "${basename(KUSTOMIZE_BIN_ARM64_TGZ_URL)}"
}
