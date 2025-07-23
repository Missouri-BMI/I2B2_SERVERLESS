# Makefile for i2b2-web builds and run

DOCKER_BUILD = docker build --platform=linux/amd64 --no-cache
DOCKER_RUN = docker run --platform=linux/amd64 --read-only=true -p 80:80

local:
	$(DOCKER_BUILD) \
		-t i2b2-web:latest \
		--build-arg ENVIRONMENT=local .

dev:
	$(DOCKER_BUILD) --rm \
		-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-web \
		--build-arg ENVIRONMENT=dev .

prod:
	$(DOCKER_BUILD) --rm \
		-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-web \
		--build-arg ENVIRONMENT=prod .

run:
	$(DOCKER_RUN) \
		i2b2-web:latest
