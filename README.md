# i2b2-snowflake as AWS ECS Service
Steps to run i2b2 containers as AWS ECS service.
1. Build docker images for i2b2-webclient and i2b2-core-server
2. Upload docker images in AWS ECR
2. Create ECS Task definition for i2b2.
3. Create ECS Cluster
4. Create i2b2 service using the task definition and deploy in the ECS cluster

# AWS ECR:
### Docker login to ECR
```
# login to aws-cli for role based sso accounts
$ aws sso login --profile <profile_name>

# login to aws ecr using docker-cli
$ aws ecr get-login-password --profile <profile_name> | docker login --username AWS --password-stdin <account_id>.dkr.ecr.<region>.amazonaws.com
```
### Create Repository in ECS
```
$ aws ecr create-repository \
    --repository-name <name> \
    --image-scanning-configuration scanOnPush=true \
    --profile <profile>
```

### Push Images in ECS
```
$ docker push <image-uri>
```

# Build Docker Image for i2b2-webclient

```
# change directory to i2b2-web
$ cd ./Docker/application/i2b2-web/

# Build docker image
$ docker build \
-t <image_uri> \
--build-arg CLIENT_TYPE=webclient \
--build-arg SERVER_NAME=<dns_name> \
--no-cache .
```

# Build Docker Image for i2b2-core-server
```
# change directory to i2b2-core-server
$ cd ./Docker/application/i2b2-serve/

# Build docker image
$ docker build --no-cache -t <image_uri> . 
```
