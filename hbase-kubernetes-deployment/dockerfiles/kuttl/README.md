<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# dockerfiles/kuttl

This directory builds a docker image containing everything required to run `kubectl-kuttl` in
"mocked control plane" mode. This image is used as the basis for both dev and test environments.

## Build

Building the docker image locally is a little picky because there's lots of input arguments
(download of dependencies is externalized from the build) and because binary file names required
by the build do not necissarily match the distribution artifact names. Start by downloading all
the relevant binaries for your platform and naming them appropriately, see comments in
[docker-bake.override.hcl](./docker-bake.override.hcl) for details.

Next, create a buildx context that supports (optionally) multi-platform images. If you've created
this context previously, it's enough to ensure that it's active via `docker buildx ls`.

```shell
$ docker buildx create \
  --driver docker-container \
  --platform linux/amd64,linux/arm64 \
  --use \
  --bootstrap
```

Finally, build the image using,

```shell
$ docker buildx bake \
  --file dockerfiles/kuttl/docker-bake.hcl \
  --file dockerfiles/kuttl/docker-bake.override.hcl \
  --pull \
  --load
```

This exports an image to your local repository that is tagged as `${USER}/hbase/operator-tools/kuttl:latest`.

## Usage

The image is configured with `kuttle` as the entrypoint.

```shell
$ docker container run --rm -it ${USER}/hbase/operator-tools/kuttl:latest --help

```

Running tests in the image requires mounting the workspace into the container image and passing
appropriate parameters to `kuttl`. For example, run the "unit" tests like this:

```shell
$ docker container run \
  --mount type=bind,source=$(pwd),target=/workspace \
  --workdir /workspace \
  ${USER}/hbase/operator-tools/kuttl:latest \
  --config tests/kuttl-test-unit.yaml
```
