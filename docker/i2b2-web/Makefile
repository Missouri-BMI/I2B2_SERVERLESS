# Makefile for i2b2-web builds and run
# Variables
DOCKER_BUILD = docker build --platform=linux/amd64
DOCKER_RUN = docker run --platform=linux/amd64 --read-only=true -p 80:80

IMAGE_NAME = i2b2-web
VERSION = v1.8.2

DEV_REGISTRY = 500206249851.dkr.ecr.us-east-2.amazonaws.com
PROD_REGISTRY = 063312575449.dkr.ecr.us-east-2.amazonaws.com

DEV_TAG = $(DEV_REGISTRY)/$(IMAGE_NAME):$(VERSION)
PROD_TAG = $(PROD_REGISTRY)/$(IMAGE_NAME):$(VERSION)
LOCAL_TAG = $(IMAGE_NAME):latest

.PHONY: local dev prod run-local help

help:
	@echo "Available targets:"
	@echo "  local - Build local image"
	@echo "  dev   - Build dev image"
	@echo "  prod  - Build prod image"
	@echo "  run-local   - Run local image"


local:
	$(DOCKER_BUILD) \
		-t $(LOCAL_TAG) \
		--build-arg ENVIRONMENT=local .

dev:
	$(DOCKER_BUILD) \
		-t $(DEV_TAG) \
		--build-arg ENVIRONMENT=dev .

prod:
	$(DOCKER_BUILD) \
		-t $(PROD_TAG) \
		--build-arg ENVIRONMENT=prod .

run-local:
	$(DOCKER_RUN) --rm \
		$(LOCAL_TAG)
