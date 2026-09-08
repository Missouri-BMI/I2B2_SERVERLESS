#!/bin/sh -e -x

#build the docker image
docker build -t build-shrine-base docker/scripts/src/main/docker/build-shrine/base
docker build -t build-shrine-develop docker/scripts/src/main/docker/build-shrine/develop

#start the docker machine
#-v bind mounts for the .m2 and .npm from the host machine
docker run --name buildShrine -v ~/.m2:/root/.m2:cached -v ~/.npm:/root/.npm:cached -t -d build-shrine-develop

#start a mutagen file system sync for the source code
mutagen sync create --name sourcecode . docker://buildShrine/mnt/host/shrine

#Get the initial files up
mutagen sync flush sourcecode # takes 3 minutes the first time, a few seconds after