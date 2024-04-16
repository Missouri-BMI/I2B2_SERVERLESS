#!/bin/bash

docker build  \
--platform=linux/amd64 \
-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly-shrine-washu \
--build-arg ENVIRONMENT=shrine-washu \
--no-cache .

