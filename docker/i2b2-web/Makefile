# Makefile for i2b2-web builds and run

DOCKER_BUILD = docker build --platform=linux/amd64
DOCKER_RUN = docker run --platform=linux/amd64 --read-only=true -p 80:80

local:
	$(DOCKER_BUILD) \
		-t i2b2-web:latest \
		--build-arg ENVIRONMENT=local .

dev:
	$(DOCKER_BUILD) \
		-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-web:v1.8.2 \
		--build-arg ENVIRONMENT=dev .

prod:
	$(DOCKER_BUILD) \
		-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-web:v1.8.2 \
		--build-arg ENVIRONMENT=prod .

run:
	$(DOCKER_RUN) --rm \
		i2b2-web:latest
