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

# dockerfiles/hbase

There is a contract that must be maintained between the container image run in the pod and the
infrastructure that launched the pod. Details like paths, users/groups, permissions, and
environment variables must align so that the deployment layer can pass runtime concerns down to
the container.

Ideally this image would start from some baseline image provided by the HBase project and extend
it as necissary according to the Kubernetes deployment needs. Such an image is not yet provided by
HBase, so this image serves dual purposes.

## Build

Start by downloading all the relevant binaries for your platform and naming them appropriately,
see comments in [docker-bake.override.hcl](./docker-bake.override.hcl) for details.

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

This exports an image to your local repository that is tagged as
`${USER}/hbase/operator-tools/hbase:latest`.
