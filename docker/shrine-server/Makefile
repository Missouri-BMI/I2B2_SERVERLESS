# Variables
PLATFORM=linux/amd64
NO_CACHE=--no-cache
DEV_REPO=500206249851.dkr.ecr.us-east-2.amazonaws.com/shrine-server
PROD_REPO=063312575449.dkr.ecr.us-east-2.amazonaws.com/shrine-server

# Targets
.PHONY:  dev prod dev-mu dev-washu prod-mu prod-washu

dev-mu:
	docker build \
		--platform=$(PLATFORM) \
		-t $(DEV_REPO):mu \
		--build-arg ENVIRONMENT=dev \
		--build-arg PROJECT=mu \
		$(NO_CACHE) .

dev-washu:
	docker build \
		--platform=$(PLATFORM) \
		-t $(DEV_REPO):washu \
		--build-arg ENVIRONMENT=dev \
		--build-arg PROJECT=washu \
		$(NO_CACHE) .

prod-mu:
	docker build \
		--platform=$(PLATFORM) \
		-t $(PROD_REPO):mu \
		--build-arg ENVIRONMENT=prod \
		--build-arg PROJECT=mu \
		$(NO_CACHE) .

prod-washu:
	docker build \
		--platform=$(PLATFORM) \
		-t $(PROD_REPO):washu \
		--build-arg ENVIRONMENT=prod \
		--build-arg PROJECT=washu \
		$(NO_CACHE) .
