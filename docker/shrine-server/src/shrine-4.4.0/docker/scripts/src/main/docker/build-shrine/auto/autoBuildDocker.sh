#!/bin/sh -e -x

#build the docker image
docker build -t build-shrine-base docker/scripts/src/main/docker/build-shrine/base
docker build -t build-shrine-auto docker/scripts/src/main/docker/build-shrine/auto

# create the machine and load it up with source code
# on an iTeam CentoOS7 build machine it may be as efficient to call just
# docker run -w /mnt/host/shrine -v ${pwd}:/mnt/host/shrine buildShrine mvn clean install

docker create -t --name buildShrine build-shrine-auto
docker cp . buildShrine:/mnt/host/shrine # takes about 3 minutes on the lap top

# start the machine and execute whatever command was passed in
docker start buildShrine
docker exec -w /mnt/host/shrine buildShrine $@

# clean up
docker stop buildShrine
docker rm buildShrine