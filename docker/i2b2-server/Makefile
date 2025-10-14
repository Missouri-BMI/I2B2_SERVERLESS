# Makefile

DOCKER_BUILD = docker build --platform=linux/amd64 --no-cache

# Local build targets
local:
	$(DOCKER_BUILD) \
		-t i2b2-wildfly:latest \
		--build-arg ENVIRONMENT=local .

# Dev build targets
mu-dev:
	$(DOCKER_BUILD) \
		-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:dev \
		--build-arg ENVIRONMENT=dev .


shrine-dev:
	$(DOCKER_BUILD) \
		-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:shrine \
		--build-arg ENVIRONMENT=shrine .

shrine-washu-dev:
	$(DOCKER_BUILD) \
		-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:shrine-washu \
		--build-arg ENVIRONMENT=shrine-washu .


# Prod build targets
mu-prod:
	$(DOCKER_BUILD) \
		-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:prod \
		--build-arg ENVIRONMENT=prod .

shrine-mu-prod:
	$(DOCKER_BUILD) \
		-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:shrine-mu \
		--build-arg ENVIRONMENT=shrine-prod .

shrine-washu-prod:
	$(DOCKER_BUILD) \
		-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:shrine-washu \
		--build-arg ENVIRONMENT=shrine-washu-prod .

run-local:
	docker run \
	--platform=linux/amd64 \
	--read-only=true \
	-p 8080:8080 -p 8009:8009 -p 9990:9990 \
	i2b2-wildfly:latest


# docker build --build-arg HEAP_SIZE="-Xms4500m -Xmx4500m" -t your-wildfly-prod .
