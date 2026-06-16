DOCKER_BUILD = docker build --platform=linux/amd64

IMAGE_NAME = i2b2-web
VERSION = v1.8.3

DEV_REGISTRY = 500206249851.dkr.ecr.us-east-2.amazonaws.com/$(IMAGE_NAME)
PROD_REGISTRY = 063312575449.dkr.ecr.us-east-2.amazonaws.com/$(IMAGE_NAME)

.PHONY: dev dev-mu-shrine dev-washu-shrine prod prod-mu-shrine prod-washu-shrine 

dev:
	$(DOCKER_BUILD) \
		-t $(DEV_REGISTRY):mu-$(VERSION) \
			--build-arg ENVIRONMENT=dev \
			--build-arg TARGET=mu .

dev-mu-shrine:
	$(DOCKER_BUILD) \
		-t $(DEV_REGISTRY):mu-shrine-$(VERSION) \
			--build-arg ENVIRONMENT=dev \
			--build-arg TARGET=mu-shrine .

dev-washu-shrine:
	$(DOCKER_BUILD) \
		-t $(DEV_REGISTRY):washu-shrine-$(VERSION) \
			--build-arg ENVIRONMENT=dev \
			--build-arg TARGET=washu-shrine .

prod:
	$(DOCKER_BUILD) \
		-t $(PROD_REGISTRY):mu-$(VERSION) \
		--build-arg ENVIRONMENT=prod \
		--build-arg TARGET=mu .

prod-mu-shrine:
	$(DOCKER_BUILD) \
		-t $(PROD_REGISTRY):mu-shrine-$(VERSION) \
		--build-arg ENVIRONMENT=prod \
		--build-arg TARGET=mu-shrine .

prod-washu-shrine:
	$(DOCKER_BUILD) \
		-t $(PROD_REGISTRY):washu-shrine-$(VERSION) \
			--build-arg ENVIRONMENT=prod \
			--build-arg TARGET=washu-shrine .

run:
	docker run --read-only --rm -it \
	--platform=linux/amd64 \
 	-p 80:80 \
	-p 443:443 \
  	063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-web:mu-v1.8.2