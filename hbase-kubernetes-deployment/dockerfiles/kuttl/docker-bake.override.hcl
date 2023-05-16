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

variable KUBECTL_SHA_AMD64 {
  # wget -O sha512_kubectl_v1.24.10_linux_amd64 'https://dl.k8s.io/release/v1.24.10/bin/linux/amd64/kubectl.sha512'
  default = "sha512_kubectl_v1.24.10_linux_amd64"
}

variable KUBECTL_BIN_AMD64 {
  # wget -O kubectl_v1.24.10_linux_amd64 'https://dl.k8s.io/release/v1.24.10/bin/linux/amd64/kubectl'
  default = "kubectl_v1.24.10_linux_amd64"
}

variable KUBECTL_SHA_ARM64 {
  # wget -O sha512_kubectl_v1.24.10_linux_arm64 'https://dl.k8s.io/release/v1.24.10/bin/linux/arm64/kubectl.sha512'
  default = "sha512_kubectl_v1.24.10_linux_arm64"
}

variable KUBECTL_BIN_ARM64 {
  # wget -O kubectl_v1.24.10_linux_arm64 'https://dl.k8s.io/release/v1.24.10/bin/linux/arm64/kubectl'
  default = "kubectl_v1.24.10_linux_arm64"
}

variable KUTTL_CHECKSUMS {
  # wget -O kuttl_checksums.txt 'https://github.com/kudobuilder/kuttl/releases/download/v0.15.0/checksums.txt'
  default = "kuttl_checksums.txt"
}

variable KUTTL_BIN_AMD64 {
  # wget -O kubectl-kuttl_0.15.0_linux_x86_64 'https://github.com/kudobuilder/kuttl/releases/download/v0.15.0/kubectl-kuttl_0.15.0_linux_x86_64'
  default = "kubectl-kuttl_0.15.0_linux_x86_64"
}

variable KUTTL_BIN_ARM64 {
  # wget -O kubectl-kuttl_0.15.0_linux_arm64 'https://github.com/kudobuilder/kuttl/releases/download/v0.15.0/kubectl-kuttl_0.15.0_linux_arm64'
  default = "kubectl-kuttl_0.15.0_linux_arm64"
}

variable KUSTOMIZE_CHECKSUMS {
  # wget -O kustomize_checksums.txt 'https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2Fv4.5.4/checksums.txt'
  default = "kustomize_checksums.txt"
}

variable KUSTOMIZE_BIN_AMD64_TGZ {
  # wget -O kustomize_v4.5.4_linux_amd64.tar.gz 'https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2Fv4.5.4/kustomize_v4.5.4_linux_amd64.tar.gz'
  default = "kustomize_v4.5.4_linux_amd64.tar.gz"
}

variable KUSTOMIZE_BIN_ARM64_TGZ {
  # wget -O kustomize_v4.5.4_linux_arm64.tar.gz 'https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2Fv4.5.4/kustomize_v4.5.4_linux_arm64.tar.gz'
  default = "kustomize_v4.5.4_linux_arm64.tar.gz"
}

variable AWS_CLI_PUBLIC_KEY {
  # no download for this data ; copy it off of https://docs.aws.amazon.com/cli/latest/userguide/getting-started-source-install.html
  default = "aws_cli_public_key"
}

variable AWS_CLI_SOURCES_TGZ {
  # curl -LOSs 'https://awscli.amazonaws.com/awscli-2.10.4.tar.gz'
  default = "awscli-2.10.4.tar.gz"
}

variable AWS_CLI_SOURCES_TGZ_SIG {
  # curl -LOSs 'https://awscli.amazonaws.com/awscli-2.10.4.tar.gz.sig'
  default = "awscli-2.10.4.tar.gz.sig"
}
