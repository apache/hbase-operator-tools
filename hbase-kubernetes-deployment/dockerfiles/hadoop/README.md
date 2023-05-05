# Hadoop Container Image

There is a contact that must be maintained between the container image run in the pod and the
infrastructure that launched the pod. Details like paths, users/groups, permissions, and
environment variables must align so that the deployment layer can pass runtime concerns down to
the container.

Start with the official hadoop image and extend it from there. Note that `apache/hadoop:3` is only
published for `linux/amd64` at this time.

## Build

Start by downloading all the relevant binaries for your platform and naming them appropriately,
see comments in [docker-bake.override.hcl](./docker-bake.override.hcl) for details.

Next, create a buildx context that supports at least `linux/amd64` images. If you've created
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
  --file dockerfiles/hadoop/docker-bake.hcl \
  --file dockerfiles/hadoop/docker-bake.override.hcl \
  --set '*.platform=linux/amd64' \
  --pull \
  --load
```

This exports an image to your local repository that is tagged as
`${USER}/hbase/operator-tools/hadoop:latest`.
