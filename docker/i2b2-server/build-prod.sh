#!/bin/bash

docker build  \
--platform=linux/amd64 \
-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:prod \
--build-arg ENVIRONMENT=prod \
--no-cache .