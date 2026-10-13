# i2b2-wildfly (i2b2 core server) — PER-VARIANT images. The datasource config for one
# variant is baked in at build (ENVIRONMENT build arg); the tag encodes the app variant
# (mu | shrine | shrine-washu), the registry encodes the account.
#   mu           -> plain i2b2   (ENVIRONMENT=dev / prod)
#   shrine       -> SHRINE mu    (ENVIRONMENT=shrine / shrine-prod)
#   shrine-washu -> SHRINE washu (ENVIRONMENT=shrine-washu / shrine-washu-prod)
#   local        -> local only   (ENVIRONMENT=local; tagged $(IMAGE):local, never pushed;
#                                 same image docker/docker-compose.yml uses)

PLATFORM     ?= linux/amd64
VERSION      ?= v1.8.3
IMAGE        ?= i2b2-wildfly
AWS_REGION   ?= us-east-2
DEV_REGISTRY  = 500206249851.dkr.ecr.$(AWS_REGION).amazonaws.com/$(IMAGE)
PROD_REGISTRY = 063312575449.dkr.ecr.$(AWS_REGION).amazonaws.com/$(IMAGE)

# Local run settings (make run-local). Override: make run-local HTTP_PORT=8081
LOCAL_TAG    ?= local
CONTAINER    ?= $(IMAGE)
HTTP_PORT    ?= 8080
AJP_PORT     ?= 8009

# $(1)=image tag  $(2)=build args  $(3)=target registry
define build_push
	docker build --platform=$(PLATFORM) $(2) -t $(IMAGE):$(1) .
	docker tag $(IMAGE):$(1) $(3):$(1)
	docker push $(3):$(1)
endef

.PHONY: mu-dev mu-prod shrine-dev shrine-prod washu-dev washu-prod \
	local run-local stop-local logs-local

# ---------------------------------------------------------------------------
# Local: build the image with the "local" datasource config, no registry, no push.
# Requires configuration/local/commands.cli (+ rsa_key.p8); see README.md / template/.
# ---------------------------------------------------------------------------
local:
	@test -f configuration/local/commands.cli || \
	  { echo "configuration/local/commands.cli missing: copy configuration/template/commands-temp.cli to configuration/local/commands.cli and fill in the DB creds"; exit 1; }
	docker build --platform=$(PLATFORM) --build-arg ENVIRONMENT=local -t $(IMAGE):$(LOCAL_TAG) .

# Build + run standalone on http://localhost:$(HTTP_PORT)/i2b2/ (replaces an existing container).
run-local: local stop-local
	docker run -d --name $(CONTAINER) --platform=$(PLATFORM) \
	  -p $(HTTP_PORT):8080 -p $(AJP_PORT):8009 $(IMAGE):$(LOCAL_TAG)
	@echo "i2b2 core server starting -> http://localhost:$(HTTP_PORT)/i2b2/  (make logs-local to follow)"

stop-local:
	-docker rm -f $(CONTAINER) >/dev/null 2>&1 || true

logs-local:
	docker logs -f $(CONTAINER)

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
