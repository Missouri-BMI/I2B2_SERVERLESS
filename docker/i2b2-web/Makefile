# i2b2-web — PER-VARIANT images. The (env, target) Apache/Shibboleth config + served
# webclient are baked in at build (ENVIRONMENT + TARGET build args); the tag encodes the
# TARGET (mu | mu-shrine | washu-shrine), the registry encodes the account.

PLATFORM     ?= linux/amd64
VERSION      ?= v1.8.3
IMAGE        ?= i2b2-web
AWS_REGION   ?= us-east-2
DEV_REGISTRY  = 500206249851.dkr.ecr.$(AWS_REGION).amazonaws.com/$(IMAGE)
PROD_REGISTRY = 063312575449.dkr.ecr.$(AWS_REGION).amazonaws.com/$(IMAGE)

# $(1)=image tag  $(2)=build args  $(3)=target registry
define build_push
	docker build --platform=$(PLATFORM) $(2) -t $(IMAGE):$(1) .
	docker tag $(IMAGE):$(1) $(3):$(1)
	docker push $(3):$(1)
endef

.PHONY: mu-dev mu-prod mu-shrine-dev mu-shrine-prod washu-shrine-dev washu-shrine-prod

mu-dev:
	$(call build_push,mu-$(VERSION),--build-arg ENVIRONMENT=dev --build-arg TARGET=mu,$(DEV_REGISTRY))
mu-prod:
	$(call build_push,mu-$(VERSION),--build-arg ENVIRONMENT=prod --build-arg TARGET=mu,$(PROD_REGISTRY))
mu-shrine-dev:
	$(call build_push,mu-shrine-$(VERSION),--build-arg ENVIRONMENT=dev --build-arg TARGET=mu-shrine,$(DEV_REGISTRY))
mu-shrine-prod:
	$(call build_push,mu-shrine-$(VERSION),--build-arg ENVIRONMENT=prod --build-arg TARGET=mu-shrine,$(PROD_REGISTRY))
washu-shrine-dev:
	$(call build_push,washu-shrine-$(VERSION),--build-arg ENVIRONMENT=dev --build-arg TARGET=washu-shrine,$(DEV_REGISTRY))
washu-shrine-prod:
	$(call build_push,washu-shrine-$(VERSION),--build-arg ENVIRONMENT=prod --build-arg TARGET=washu-shrine,$(PROD_REGISTRY))
