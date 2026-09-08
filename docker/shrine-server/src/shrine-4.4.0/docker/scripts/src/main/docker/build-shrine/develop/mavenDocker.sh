#!/bin/sh -e -x
# pause mutagen
mutagen sync flush sourcecode # much faster after the first time - a few seconds
mutagen sync pause sourcecode
#
# build on the hosted OS
# mvn clean install #takes about 8 minutes the first time, 7 minutes after
docker exec -w /mnt/host/shrine buildShrine $@
#
# resume mutagen
mutagen sync resume sourcecode