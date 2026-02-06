# Makefile

DOCKER_BUILD = docker build --platform=linux/amd64

# Local build targets
local:
	$(DOCKER_BUILD) \
		-t i2b2-wildfly:latest \
		--build-arg ENVIRONMENT=local .

# Dev build targets
mu-dev:
	$(DOCKER_BUILD) \
		-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:mu-v1.8.2 \
		--build-arg ENVIRONMENT=dev .


shrine-dev:
	$(DOCKER_BUILD) \
		-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:shrine-v1.8.2 \
		--build-arg ENVIRONMENT=shrine .

shrine-washu-dev:
	$(DOCKER_BUILD) \
		-t 500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:shrine-washu-v1.8.2 \
		--build-arg ENVIRONMENT=shrine-washu .


# Prod build targets
mu-prod:
	$(DOCKER_BUILD) \
		-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:mu-v1.8.2 \
		--build-arg ENVIRONMENT=prod .

shrine-mu-prod:
	$(DOCKER_BUILD) \
		-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:shrine-mu-v1.8.2 \
		--build-arg ENVIRONMENT=shrine-prod .

shrine-washu-prod:
	$(DOCKER_BUILD) \
		-t 063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:shrine-washu-v1.8.2 \
		--build-arg ENVIRONMENT=shrine-washu-prod .


# docker build --build-arg HEAP_SIZE="-Xms4500m -Xmx4500m" -t your-wildfly-prod .
run:
	docker run --rm -it \
	--read-only \
 	--platform=linux/amd64 \
	-p 8080:8080 \
	-p 9990:9990 \
  	063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-wildfly:mu-v1.8.2
