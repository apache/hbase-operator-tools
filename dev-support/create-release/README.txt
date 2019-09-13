Entrance script is _do-release-docker.sh_. We run the release
creation inside a Docker container to ensure consistent
build environment (See _hbase_rm_ dir for the _Dockerfile_
used). The script depends on their being a local docker;
for example, on mac os x, a _Docker for Desktop_ installed
and running. It does all steps building a release. See
head of _do-release-docker.sh_ for list of what this all
entails.

_do-release-docker.sh_ calls _do-release.sh_ which is the core
absent docker machinations that runs the release-making process.
Use it to avoid running release creation in a container.

If you just want to generate tarballs locally, run release-build.sh.
It will ask for passwords to use building or you can supply as
environment variables. For example:

 $ ASF_USERNAME="USERNAME" ASF_PASSWORD="PASSWORD" GPG_PASSPHRASE="PASSWORD" GPG_KEY="stack@duboce.net" GIT_REF=e3eb8ce83075cdfee7f7a9be3cb9de7686c4905f ./release-build.sh build

Ditto for running _do-release*.sh_.

Before starting the RC build, run a reconciliation of what is in
JIRA with what is in the commit log. Make sure they align and that
anomalies are explained up in JIRA (TODO, a script that can
do this: HBASE-22853).

See http://hbase.apache.org/book.html#maven.release

Running a build on GCE is easy enough. Here are some notes that
may help.

Create an instance. 4CPU/15G/10G disk seems to work well enough.
Once up, run the below to make your machine fit for RC building:

# Presuming debian-compatible OS
$ sudo apt-get install -y git openjdk-8-jdk maven gnupg gnupg-agent
# Install docker
$ sudo apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg2 \
    software-properties-common
$ curl -fsSL https://download.docker.com/linux/debian/gpg | sudo apt-key add -
$ sudo add-apt-repository -y \
   "deb [arch=amd64] https://download.docker.com/linux/debian \
   $(lsb_release -cs) \
   stable"
$ sudo apt-get update
$ sudo apt-get install -y docker-ce docker-ce-cli containerd.io
$ sudo usermod -a -G docker $USERID
# LOGOUT and then LOGIN again so $USERID shows as part of docker group
# Copy up private key for $USERID export from laptop and import on gce.
$ gpg --import stack.duboce.net.asc
$ export GPG_TTY=$(tty) # https://github.com/keybase/keybase-issues/issues/2798
$ eval $(gpg-agent --disable-scdaemon --daemon --no-grab  --allow-preset-passphrase --default-cache-ttl=86400 --max-cache-ttl=86400)
$ git clone https://github.com/apache/hbase-operator-tools.git
$ cd hbase-operator-tools
$ mkdir ~/build
$ ./dev-resources/create-release/do-release-docker.sh -d ~/build
# etc.
