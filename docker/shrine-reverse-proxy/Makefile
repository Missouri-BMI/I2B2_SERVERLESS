# Variables
PLATFORM=linux/amd64
NO_CACHE=--no-cache
DEV_REPO=500206249851.dkr.ecr.us-east-2.amazonaws.com/i2b2-reverse-proxy
PROD_REPO=063312575449.dkr.ecr.us-east-2.amazonaws.com/i2b2-reverse-proxy
BRANCH_MU=feature/admin-mu
BRANCH_WASHU=feature/admin-washu

# Targets
build-dev-mu:
	docker build \
		--platform=$(PLATFORM) \
		-t $(DEV_REPO):mu \
		--build-arg ENVIRONMENT=dev \
		--build-arg PROJECT=mu \
		--build-arg BRANCH=$(BRANCH_MU) \
		$(NO_CACHE) \
		.

build-dev-washu:
	docker build \
		--platform=$(PLATFORM) \
		-t $(DEV_REPO):washu \
		--build-arg ENVIRONMENT=dev \
		--build-arg PROJECT=washu \
		--build-arg BRANCH=$(BRANCH_WASHU) \
		$(NO_CACHE) \
		.

build-prod-mu:
	docker build \
		--platform=$(PLATFORM) \
		-t $(PROD_REPO):mu \
		--build-arg ENVIRONMENT=prod \
		--build-arg PROJECT=mu \
		--build-arg BRANCH=$(BRANCH_MU) \
		$(NO_CACHE) \
		.

build-prod-washu:
	docker build \
		--platform=$(PLATFORM) \
		-t $(PROD_REPO):washu \
		--build-arg ENVIRONMENT=prod \
		--build-arg PROJECT=washu \
		--build-arg BRANCH=$(BRANCH_WASHU) \
		$(NO_CACHE) \
		.
