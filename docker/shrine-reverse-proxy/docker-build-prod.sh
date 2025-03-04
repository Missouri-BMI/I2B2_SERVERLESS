#!/bin/bash

# docker build \
# --platform=linux/amd64 \
# -t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-reverse-proxy:mu \
# --build-arg ENVIRONMENT=prod \
# --build-arg PROJECT=mu \
# --build-arg BRANCH=feature/admin-mu \
# --no-cache  \
# .

docker build \
--platform=linux/amd64 \
-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-reverse-proxy:washu \
--build-arg ENVIRONMENT=prod \
--build-arg PROJECT=washu \
--build-arg BRANCH=feature/admin-washu \
--no-cache  \
.

