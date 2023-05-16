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

ARG IMG_BASE='golang'
ARG IMG_BASE_TAG='1.19-alpine'
ARG BASE_IMG_ALPINE='alpine'
ARG BASE_IMG_TAG_ALPINE='latest'
ARG YQ_IMG='mikefarah/yq'
ARG YQ_IMG_TAG='latest'

FROM ${BASE_IMG_ALPINE}:${BASE_IMG_TAG_ALPINE} as alpine

FROM ${YQ_IMG}:${YQ_IMG_TAG} as yq

## -- Stages kubectl_${TARGETARCH} --
# Define stages that facilitate bringing in platform-specific binaries.
FROM alpine as kubectl_amd64
ARG KUBECTL_SHA_AMD64
ARG KUBECTL_BIN_AMD64
WORKDIR /tmp/
COPY ${KUBECTL_SHA_AMD64} /tmp/
COPY ${KUBECTL_BIN_AMD64} /tmp/
RUN echo "$(cat "${KUBECTL_SHA_AMD64}")  ${KUBECTL_BIN_AMD64}" | sha512sum -c

FROM alpine as kubectl_arm64
ARG KUBECTL_SHA_ARM64
ARG KUBECTL_BIN_ARM64
WORKDIR /tmp/
COPY ${KUBECTL_SHA_ARM64} /tmp/
COPY ${KUBECTL_BIN_ARM64} /tmp/
RUN echo "$(cat "${KUBECTL_SHA_ARM64}")  ${KUBECTL_BIN_ARM64}" | sha512sum -c

ARG TARGETARCH
FROM kubectl_${TARGETARCH} as kubectl
RUN mv /tmp/kubectl_* /tmp/kubectl \
    && chmod a+x /tmp/kubectl

## -- Stages kuttl_${TARGETARCH} --
# Define stages that facilitate bringing in platform-specific binaries.
FROM alpine as kuttl_amd64
ARG KUTTL_CHECKSUMS
ARG KUTTL_BIN_AMD64
WORKDIR /tmp/
COPY ${KUTTL_CHECKSUMS} /tmp/
COPY ${KUTTL_BIN_AMD64} /tmp/
RUN sha256sum -c <(grep "${KUTTL_BIN_AMD64}" "${KUTTL_CHECKSUMS}")

FROM alpine as kuttl_arm64
ARG KUTTL_CHECKSUMS
ARG KUTTL_BIN_ARM64
WORKDIR /tmp/
COPY ${KUTTL_CHECKSUMS} /tmp/
COPY ${KUTTL_BIN_ARM64} /tmp/
RUN sha256sum -c <(grep "${KUTTL_BIN_ARM64}" "${KUTTL_CHECKSUMS}")

ARG TARGETARCH
FROM kuttl_${TARGETARCH} as kuttl
RUN mv /tmp/kubectl-kuttl_* /tmp/kubectl-kuttl \
    && chmod a+x /tmp/kubectl-kuttl

## -- Stages kustomize_${TARGETARCH} --
# Define stages that facilitate bringing in platform-specific binaries.
FROM alpine as kustomize_amd64
ARG KUSTOMIZE_CHECKSUMS
ARG KUSTOMIZE_BIN_AMD64_TGZ
WORKDIR /tmp/
COPY ${KUSTOMIZE_CHECKSUMS} /tmp/
COPY ${KUSTOMIZE_BIN_AMD64_TGZ} /tmp/
RUN sha256sum -c <(grep "${KUSTOMIZE_BIN_AMD64_TGZ}" "${KUSTOMIZE_CHECKSUMS}") \
    && cd /tmp \
    && tar xzf "${KUSTOMIZE_BIN_AMD64_TGZ}"

FROM alpine as kustomize_arm64
ARG KUSTOMIZE_CHECKSUMS
ARG KUSTOMIZE_BIN_ARM64_TGZ
WORKDIR /tmp/
COPY ${KUSTOMIZE_CHECKSUMS} /tmp/
COPY ${KUSTOMIZE_BIN_ARM64_TGZ} /tmp/
RUN sha256sum -c <(grep "${KUSTOMIZE_BIN_ARM64_TGZ}" "${KUSTOMIZE_CHECKSUMS}")

ARG TARGETARCH
FROM kustomize_${TARGETARCH} as kustomize
RUN tar xzf /tmp/kustomize_*.tar.gz \
    && chmod a+x /tmp/kustomize

## -- Stage aws_cli_builder
FROM alpine as aws_cli_builder
ARG AWS_CLI_PUBLIC_KEY
ARG AWS_CLI_SOURCES_TGZ
ARG AWS_CLI_SOURCES_TGZ_SIG
COPY ${AWS_CLI_PUBLIC_KEY} /tmp/
COPY ${AWS_CLI_SOURCES_TGZ} /tmp/
COPY ${AWS_CLI_SOURCES_TGZ_SIG} /tmp/
WORKDIR /tmp
RUN apk add --no-cache \
        build-base \
        cmake \
        gnupg \
        groff \
        libffi-dev \
        python3 \
        python3-dev \
        tar \
    && gpg --import /tmp/aws_cli_public_key \
    && gpg --verify /tmp/${AWS_CLI_SOURCES_TGZ_SIG} /tmp/${AWS_CLI_SOURCES_TGZ} \
    && tar -xzf ${AWS_CLI_SOURCES_TGZ} \
    && cd "${AWS_CLI_SOURCES_TGZ%.tar.gz}" \
    && ./configure --with-install-type=portable-exe --with-download-deps \
    && make \
    && make install \
    && rm -rf \
        /usr/local/lib/aws-cli/aws_completer \
        /usr/local/lib/aws-cli/awscli/data/ac.index \
        /usr/local/lib/aws-cli/awscli/examples \
    && find /usr/local/lib/aws-cli/awscli/data -name completions-1*.json -delete \
    && find /usr/local/lib/aws-cli/awscli/botocore/data -name examples-1.json -delete \
    && cd /usr/local/lib/aws-cli \
    && for a in *.so* ; do test -f /lib/$a && rm $a; done

FROM ${IMG_BASE}:${IMG_BASE_TAG} as final
ARG IMG_BASE
ARG IMG_BASE_TAG

COPY --from=yq /usr/bin/yq /usr/bin/yq
COPY --from=kubectl /tmp/kubectl /usr/local/bin/
COPY --from=kuttl /tmp/kubectl-kuttl /usr/local/bin/
COPY --from=kustomize /tmp/kustomize /usr/local/bin/
COPY --from=aws_cli_builder /usr/local/lib/aws-cli/ /usr/local/lib/aws-cli/
COPY hbase-kubernetes-deployment/dockerfiles/kuttl/entrypoint.sh /bin/

# install python, needed by aws-cli
ENV LANG C.UTF-8
RUN apk add --update --no-cache \
        bash \
        docker-cli \
        groff \
        less \
        python3 \
        python3-dev \
    && ln -s /usr/local/lib/aws-cli/aws /usr/local/bin/aws \
    && /usr/local/bin/aws --version

# replicate the test-related bits generated by `kubebuilder` into its Makefile.
ENV GOBIN="${GOPATH}/bin"
ENV ENVTEST_K8S_VERSION='1.23.x'
RUN chmod a+x /bin/entrypoint.sh \
    && go install sigs.k8s.io/controller-runtime/tools/setup-envtest@latest \
    && "${GOBIN}/setup-envtest" use "${ENVTEST_K8S_VERSION}"
# disable downloading remote content henceforth
ENV ENVTEST_INSTALLED_ONLY=true

ENTRYPOINT ["/bin/entrypoint.sh"]
