# Variables
AWS_PROFILE_DEV := mhmcb
AWS_PROFILE_PROD := mhmcb-prod
AWS_REGION := us-east-2
ECR_REPO_DEV :=  500206249851.dkr.ecr.us-east-2.amazonaws.com
ECR_REPO_PROD := 063312575449.dkr.ecr.us-east-2.amazonaws.com


TAG := latest

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
