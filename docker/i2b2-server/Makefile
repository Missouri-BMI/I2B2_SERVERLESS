# Makefile

# Build variables (override on the command line, e.g. `make mu-dev VERSION=v1.8.3`)
PLATFORM    ?= linux/amd64
AWS_REGION  ?= us-east-2
DEV_ACCOUNT  ?= 500206249851
PROD_ACCOUNT ?= 063312575449
IMAGE_NAME  ?= i2b2-wildfly
VERSION     ?= v1.8.3

DEV_REGISTRY  = $(DEV_ACCOUNT).dkr.ecr.$(AWS_REGION).amazonaws.com
PROD_REGISTRY = $(PROD_ACCOUNT).dkr.ecr.$(AWS_REGION).amazonaws.com

DOCKER_BUILD = docker build --platform=$(PLATFORM)

# Local build targets
local:
	$(DOCKER_BUILD) \
		-t $(IMAGE_NAME):latest \
		--build-arg ENVIRONMENT=local .

# Dev build targets
mu-dev:
	$(DOCKER_BUILD) \
		-t $(DEV_REGISTRY)/$(IMAGE_NAME):mu-$(VERSION) \
		--build-arg ENVIRONMENT=dev .


shrine-dev:
	$(DOCKER_BUILD) \
		-t $(DEV_REGISTRY)/$(IMAGE_NAME):shrine-$(VERSION) \
		--build-arg ENVIRONMENT=shrine .

shrine-washu-dev:
	$(DOCKER_BUILD) \
		-t $(DEV_REGISTRY)/$(IMAGE_NAME):shrine-washu-$(VERSION) \
		--build-arg ENVIRONMENT=shrine-washu .


# Prod build targets
mu-prod:
	$(DOCKER_BUILD) \
		-t $(PROD_REGISTRY)/$(IMAGE_NAME):mu-$(VERSION) \
		--build-arg ENVIRONMENT=prod .

shrine-mu-prod:
	$(DOCKER_BUILD) \
		-t $(PROD_REGISTRY)/$(IMAGE_NAME):shrine-mu-$(VERSION) \
		--build-arg ENVIRONMENT=shrine-prod .

shrine-washu-prod:
	$(DOCKER_BUILD) \
		-t $(PROD_REGISTRY)/$(IMAGE_NAME):shrine-washu-$(VERSION) \
		--build-arg ENVIRONMENT=shrine-washu-prod .


# docker build --build-arg HEAP_SIZE="-Xms4500m -Xmx4500m" -t your-wildfly-prod .
run:
	docker run --rm -it \
	--read-only \
 	--platform=$(PLATFORM) \
	-p 8080:8080 \
	-p 9990:9990 \
  	$(PROD_REGISTRY)/$(IMAGE_NAME):mu-$(VERSION)
