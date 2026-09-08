# Variables
AWS_PROFILE_DEV := app-web-dev
AWS_PROFILE_PROD := app-web-dev
AWS_REGION := us-east-2
ECR_REPO_DEV :=  500206249851.dkr.ecr.us-east-2.amazonaws.com
ECR_REPO_PROD := 063312575449.dkr.ecr.us-east-2.amazonaws.com

# Single version tag applied to every per-variant image (i2b2-wildfly, i2b2-web,
# shrine-server). Override: make images-dev VERSION=v1.8.4
VERSION := v1.8.3

# AWS Login
aws-login-dev:
	aws sso login --profile $(AWS_PROFILE_DEV)

aws-login-prod:
	aws sso login --profile $(AWS_PROFILE_PROD)

# Docker Login
docker-login-dev:
	aws ecr get-login-password --region $(AWS_REGION) --profile $(AWS_PROFILE_DEV) | docker login --username AWS --password-stdin $(ECR_REPO_DEV)

docker-login-prod:
	aws ecr get-login-password --region $(AWS_REGION) --profile $(AWS_PROFILE_PROD) | docker login --username AWS --password-stdin $(ECR_REPO_PROD)

# ---------------------------------------------------------------------------
# Build & push the PER-VARIANT images to ECR. Each image bakes ONE variant's configuration
# (with real creds) straight from the component folders, selected by the ENVIRONMENT /
# TARGET / PROJECT build args; the tag encodes the variant. Edit config in the folders.
# ---------------------------------------------------------------------------
images-dev: docker-login-dev
	$(MAKE) -C docker/i2b2-server  mu-dev shrine-dev washu-dev            VERSION=$(VERSION)
	$(MAKE) -C docker/i2b2-web     mu-dev mu-shrine-dev washu-shrine-dev  VERSION=$(VERSION)
	$(MAKE) -C docker/shrine-server mu-dev washu-dev                      VERSION=$(VERSION)

images-prod: docker-login-prod
	$(MAKE) -C docker/i2b2-server  mu-prod shrine-prod washu-prod             VERSION=$(VERSION)
	$(MAKE) -C docker/i2b2-web     mu-prod mu-shrine-prod washu-shrine-prod   VERSION=$(VERSION)
	$(MAKE) -C docker/shrine-server mu-prod washu-prod                        VERSION=$(VERSION)

# ---------------------------------------------------------------------------
# CloudFormation stacks. EXISTING resources are passed as parameters (reused, never
# recreated); only NEW resources are created.
#   network  (OPTIONAL, greenfield) : VPC + subnets + NAT + cluster
#                                      deployments/infrastructure/cloudformation.yaml
#   net-*    (per app)              : that app's ALB + listener + target group + SGs
#                                      deployments/infrastructure/{i2b2,shrine-mu,shrine-washu}.yaml
#   platform (per env)             : ECR + exec role + log group (no secret store)
#                                      deployments/platform.yaml
#   app-*    (per app)             : that app's ECS task definition + service; the target
#                                      group + service SG are resolved from the net stack and
#                                      passed as parameters. deployments/app/app-*.yaml
#
# Fill these in (or override on the CLI), e.g.
#   make deploy-nets-dev VPC_ID=vpc-123 PUBLIC_SUBNETS=subnet-a,subnet-b
#   make deploy-apps-dev CLUSTER_NAME=my-cluster PRIVATE_SUBNETS=subnet-c,subnet-d
# ---------------------------------------------------------------------------
NETWORK_STACK        := i2b2-infra
PLATFORM_STACK       := i2b2-platform
I2B2_NET_STACK       := i2b2-net
SHRINE_MU_NET_STACK  := shrine-mu-net
SHRINE_WASHU_NET_STACK := shrine-washu-net
CFN_CAPS             := CAPABILITY_NAMED_IAM
OUTBACK_DEV          := outback=application-i2b2-dev
OUTBACK_PROD         := outback=application-i2b2-prod

# Existing AWS resource identifiers (comma-separated lists must have NO spaces).
VPC_ID          ?=
PUBLIC_SUBNETS  ?=
PRIVATE_SUBNETS ?=
CLUSTER_NAME    ?=

# App compute stacks take the network edge as PARAMETERS. They only need the cluster +
# private subnets (+ platform); the target group / service SG / ALB DNS are resolved from
# the app's network-edge stack at deploy time by the deploy_app macro below.
APP_PARAMS = ClusterName=$(CLUSTER_NAME) PrivateSubnetIds=$(PRIVATE_SUBNETS) PlatformStackName=$(PLATFORM_STACK)

# Resolve a network-edge stack's outputs and deploy an app stack with them as parameters.
# $(1)=net stack  $(2)=template  $(3)=app stack  $(4)=extra params  $(5)=profile  $(6)=registry  $(7)=tags
define deploy_app
	out() { aws cloudformation describe-stacks --stack-name $(1) --profile $(5) --region $(AWS_REGION) \
	          --query "Stacks[0].Outputs[?OutputKey=='$$1'].OutputValue" --output text; }; \
	aws cloudformation deploy --template-file $(2) --stack-name $(3) --capabilities $(CFN_CAPS) \
	  --parameter-overrides $(4) Registry=$(6) $(APP_PARAMS) \
	    ServiceSecurityGroupId=$$(out ServiceSecurityGroupId) \
	    TargetGroupArn=$$(out TargetGroupArn) \
	    AlbDnsName=$$(out AlbDnsName) \
	  --tags $(7) --profile $(5) --region $(AWS_REGION)
endef

# ======================= DEV =======================
# OPTIONAL greenfield network (skip if you already have a VPC + cluster).
deploy-network-dev:
	aws cloudformation deploy --template-file deployments/infrastructure/cloudformation.yaml \
	  --stack-name $(NETWORK_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides EnvironmentName=i2b2-dev --tags $(OUTBACK_DEV) \
	  --profile $(AWS_PROFILE_DEV) --region $(AWS_REGION)

# Per-app network edge (ALB + target group + security groups).
deploy-net-i2b2-dev:
	aws cloudformation deploy --template-file deployments/infrastructure/i2b2.yaml \
	  --stack-name $(I2B2_NET_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides Environment=dev VpcId=$(VPC_ID) PublicSubnetIds=$(PUBLIC_SUBNETS) \
	  --tags $(OUTBACK_DEV) --profile $(AWS_PROFILE_DEV) --region $(AWS_REGION)

deploy-net-shrine-mu-dev:
	aws cloudformation deploy --template-file deployments/infrastructure/shrine-mu.yaml \
	  --stack-name $(SHRINE_MU_NET_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides Environment=dev VpcId=$(VPC_ID) PublicSubnetIds=$(PUBLIC_SUBNETS) \
	  --tags $(OUTBACK_DEV) --profile $(AWS_PROFILE_DEV) --region $(AWS_REGION)

deploy-net-shrine-washu-dev:
	aws cloudformation deploy --template-file deployments/infrastructure/shrine-washu.yaml \
	  --stack-name $(SHRINE_WASHU_NET_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides Environment=dev VpcId=$(VPC_ID) PublicSubnetIds=$(PUBLIC_SUBNETS) \
	  --tags $(OUTBACK_DEV) --profile $(AWS_PROFILE_DEV) --region $(AWS_REGION)

deploy-nets-dev: deploy-net-i2b2-dev deploy-net-shrine-mu-dev deploy-net-shrine-washu-dev

deploy-platform-dev:
	aws cloudformation deploy --template-file deployments/platform.yaml \
	  --stack-name $(PLATFORM_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides Environment=dev \
	  --tags $(OUTBACK_DEV) --profile $(AWS_PROFILE_DEV) --region $(AWS_REGION)

deploy-i2b2-dev:
	$(call deploy_app,$(I2B2_NET_STACK),deployments/app/app-i2b2.yaml,i2b2-app,Environment=dev,$(AWS_PROFILE_DEV),$(ECR_REPO_DEV),$(OUTBACK_DEV))

deploy-shrine-mu-dev:
	$(call deploy_app,$(SHRINE_MU_NET_STACK),deployments/app/app-shrine.yaml,shrine-mu-app,Environment=dev Project=mu,$(AWS_PROFILE_DEV),$(ECR_REPO_DEV),$(OUTBACK_DEV))

deploy-shrine-washu-dev:
	$(call deploy_app,$(SHRINE_WASHU_NET_STACK),deployments/app/app-shrine.yaml,shrine-washu-app,Environment=dev Project=washu,$(AWS_PROFILE_DEV),$(ECR_REPO_DEV),$(OUTBACK_DEV))

deploy-apps-dev: deploy-i2b2-dev deploy-shrine-mu-dev deploy-shrine-washu-dev

deploy-dev:
	aws ecs update-service --cluster $(CLUSTER_NAME) --service i2b2-dev         --force-new-deployment --profile $(AWS_PROFILE_DEV) --region $(AWS_REGION)
	aws ecs update-service --cluster $(CLUSTER_NAME) --service shrine-mu-dev     --force-new-deployment --profile $(AWS_PROFILE_DEV) --region $(AWS_REGION)
	aws ecs update-service --cluster $(CLUSTER_NAME) --service shrine-washu-dev  --force-new-deployment --profile $(AWS_PROFILE_DEV) --region $(AWS_REGION)

# ======================= PROD =======================
deploy-network-prod:
	aws cloudformation deploy --template-file deployments/infrastructure/cloudformation.yaml \
	  --stack-name $(NETWORK_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides EnvironmentName=i2b2-prod --tags $(OUTBACK_PROD) \
	  --profile $(AWS_PROFILE_PROD) --region $(AWS_REGION)

deploy-net-i2b2-prod:
	aws cloudformation deploy --template-file deployments/infrastructure/i2b2.yaml \
	  --stack-name $(I2B2_NET_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides Environment=prod VpcId=$(VPC_ID) PublicSubnetIds=$(PUBLIC_SUBNETS) \
	  --tags $(OUTBACK_PROD) --profile $(AWS_PROFILE_PROD) --region $(AWS_REGION)

deploy-net-shrine-mu-prod:
	aws cloudformation deploy --template-file deployments/infrastructure/shrine-mu.yaml \
	  --stack-name $(SHRINE_MU_NET_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides Environment=prod VpcId=$(VPC_ID) PublicSubnetIds=$(PUBLIC_SUBNETS) \
	  --tags $(OUTBACK_PROD) --profile $(AWS_PROFILE_PROD) --region $(AWS_REGION)

deploy-net-shrine-washu-prod:
	aws cloudformation deploy --template-file deployments/infrastructure/shrine-washu.yaml \
	  --stack-name $(SHRINE_WASHU_NET_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides Environment=prod VpcId=$(VPC_ID) PublicSubnetIds=$(PUBLIC_SUBNETS) \
	  --tags $(OUTBACK_PROD) --profile $(AWS_PROFILE_PROD) --region $(AWS_REGION)

deploy-nets-prod: deploy-net-i2b2-prod deploy-net-shrine-mu-prod deploy-net-shrine-washu-prod

deploy-platform-prod:
	aws cloudformation deploy --template-file deployments/platform.yaml \
	  --stack-name $(PLATFORM_STACK) --capabilities $(CFN_CAPS) \
	  --parameter-overrides Environment=prod \
	  --tags $(OUTBACK_PROD) --profile $(AWS_PROFILE_PROD) --region $(AWS_REGION)

deploy-i2b2-prod:
	$(call deploy_app,$(I2B2_NET_STACK),deployments/app/app-i2b2.yaml,i2b2-app,Environment=prod,$(AWS_PROFILE_PROD),$(ECR_REPO_PROD),$(OUTBACK_PROD))

deploy-shrine-mu-prod:
	$(call deploy_app,$(SHRINE_MU_NET_STACK),deployments/app/app-shrine.yaml,shrine-mu-app,Environment=prod Project=mu,$(AWS_PROFILE_PROD),$(ECR_REPO_PROD),$(OUTBACK_PROD))

deploy-shrine-washu-prod:
	$(call deploy_app,$(SHRINE_WASHU_NET_STACK),deployments/app/app-shrine.yaml,shrine-washu-app,Environment=prod Project=washu,$(AWS_PROFILE_PROD),$(ECR_REPO_PROD),$(OUTBACK_PROD))

deploy-apps-prod: deploy-i2b2-prod deploy-shrine-mu-prod deploy-shrine-washu-prod

deploy-prod:
	aws ecs update-service --cluster $(CLUSTER_NAME) --service i2b2-prod         --force-new-deployment --profile $(AWS_PROFILE_PROD) --region $(AWS_REGION)
	aws ecs update-service --cluster $(CLUSTER_NAME) --service shrine-mu-prod     --force-new-deployment --profile $(AWS_PROFILE_PROD) --region $(AWS_REGION)
	aws ecs update-service --cluster $(CLUSTER_NAME) --service shrine-washu-prod  --force-new-deployment --profile $(AWS_PROFILE_PROD) --region $(AWS_REGION)

.PHONY: aws-login-dev aws-login-prod docker-login-dev docker-login-prod \
	images-dev images-prod \
	deploy-network-dev deploy-net-i2b2-dev deploy-net-shrine-mu-dev deploy-net-shrine-washu-dev deploy-nets-dev \
	deploy-platform-dev deploy-i2b2-dev deploy-shrine-mu-dev deploy-shrine-washu-dev deploy-apps-dev deploy-dev \
	deploy-network-prod deploy-net-i2b2-prod deploy-net-shrine-mu-prod deploy-net-shrine-washu-prod deploy-nets-prod \
	deploy-platform-prod deploy-i2b2-prod deploy-shrine-mu-prod deploy-shrine-washu-prod deploy-apps-prod deploy-prod
