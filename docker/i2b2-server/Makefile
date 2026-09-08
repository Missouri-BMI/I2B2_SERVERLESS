# i2b2-wildfly (i2b2 core server) — PER-VARIANT images. The datasource config for one
# variant is baked in at build (ENVIRONMENT build arg); the tag encodes the app variant
# (mu | shrine | shrine-washu), the registry encodes the account.
#   mu           -> plain i2b2   (ENVIRONMENT=dev / prod)
#   shrine       -> SHRINE mu    (ENVIRONMENT=shrine / shrine-prod)
#   shrine-washu -> SHRINE washu (ENVIRONMENT=shrine-washu / shrine-washu-prod)

PLATFORM     ?= linux/amd64
VERSION      ?= v1.8.3
IMAGE        ?= i2b2-wildfly
AWS_REGION   ?= us-east-2
DEV_REGISTRY  = 500206249851.dkr.ecr.$(AWS_REGION).amazonaws.com/$(IMAGE)
PROD_REGISTRY = 063312575449.dkr.ecr.$(AWS_REGION).amazonaws.com/$(IMAGE)

# $(1)=image tag  $(2)=build args  $(3)=target registry
define build_push
	docker build --platform=$(PLATFORM) $(2) -t $(IMAGE):$(1) .
	docker tag $(IMAGE):$(1) $(3):$(1)
	docker push $(3):$(1)
endef

.PHONY: mu-dev mu-prod shrine-dev shrine-prod washu-dev washu-prod

mu-dev:
	$(call build_push,mu-$(VERSION),--build-arg ENVIRONMENT=dev,$(DEV_REGISTRY))
mu-prod:
	$(call build_push,mu-$(VERSION),--build-arg ENVIRONMENT=prod,$(PROD_REGISTRY))
shrine-dev:
	$(call build_push,shrine-$(VERSION),--build-arg ENVIRONMENT=shrine,$(DEV_REGISTRY))
shrine-prod:
	$(call build_push,shrine-$(VERSION),--build-arg ENVIRONMENT=shrine-prod,$(PROD_REGISTRY))
washu-dev:
	$(call build_push,shrine-washu-$(VERSION),--build-arg ENVIRONMENT=shrine-washu,$(DEV_REGISTRY))
washu-prod:
	$(call build_push,shrine-washu-$(VERSION),--build-arg ENVIRONMENT=shrine-washu-prod,$(PROD_REGISTRY))
