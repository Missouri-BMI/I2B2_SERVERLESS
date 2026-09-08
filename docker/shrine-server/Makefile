# shrine-server — PER-VARIANT images. The (env, project) config + the ENACT ontology are
# baked in at build (ENVIRONMENT + PROJECT build args); the tag encodes the PROJECT
# (mu | washu), the registry encodes the account.

PLATFORM     ?= linux/amd64
VERSION      ?= v1.8.3
IMAGE        ?= shrine-server
AWS_REGION   ?= us-east-2
DEV_REGISTRY  = 500206249851.dkr.ecr.$(AWS_REGION).amazonaws.com/$(IMAGE)
PROD_REGISTRY = 063312575449.dkr.ecr.$(AWS_REGION).amazonaws.com/$(IMAGE)

# $(1)=image tag  $(2)=build args  $(3)=target registry
define build_push
	docker build --platform=$(PLATFORM) $(2) -t $(IMAGE):$(1) .
	docker tag $(IMAGE):$(1) $(3):$(1)
	docker push $(3):$(1)
endef

.PHONY: mu-dev mu-prod washu-dev washu-prod

mu-dev:
	$(call build_push,mu-$(VERSION),--build-arg ENVIRONMENT=dev --build-arg PROJECT=mu,$(DEV_REGISTRY))
mu-prod:
	$(call build_push,mu-$(VERSION),--build-arg ENVIRONMENT=prod --build-arg PROJECT=mu,$(PROD_REGISTRY))
washu-dev:
	$(call build_push,washu-$(VERSION),--build-arg ENVIRONMENT=dev --build-arg PROJECT=washu,$(DEV_REGISTRY))
washu-prod:
	$(call build_push,washu-$(VERSION),--build-arg ENVIRONMENT=prod --build-arg PROJECT=washu,$(PROD_REGISTRY))
