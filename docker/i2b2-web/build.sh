#!/bin/bash
docker build \
--platform=linux/amd64 \
-t i2b2-web \
--build-arg ENVIRONMENT=local \
--no-cache  \
.




