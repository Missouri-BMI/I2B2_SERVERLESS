Herein are Dockerfiles and scripts to build on docker-hosted operating systems.

Install Docker. Allocate four cores in docker.

# Build on a build server once

To build on an automated build server, from top-level, run 

> ./docker/scripts/src/main/docker/build-shrine/auto/autoBuildDocker.sh mvn install

That script will build a CentOS image with all the tools needed to build, create the machine, copy the source code, start the machine, build shrine, stop, and remove the machine.

# Set up a build service for multiple builds

To create a dev build server on your laptop install and start mutagen - a file sharing service based on ssh and rsync.

> brew install havoc-io/mutagen/mutagen
> mutagen daemon start

To start the build server 

> ./docker/scripts/src/main/docker/build-shrine/develop/setUpForDev.sh

To build shrine

> docker/scripts/src/main/docker/build-shrine/develop/mavenDocker.sh mvn clean install