#!/bin/bash

docker run \
--platform=linux/amd64 \
--read-only=true \
-p 8080:8080 -p 8009:8009 \
500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly