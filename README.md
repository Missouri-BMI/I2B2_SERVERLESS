## Simplied installation process of i2b2-data, i2b2-web, i2b2-server, i2b2 shrine in docker containers

## Submodules
pull submodules (i2b2-data, i2b2-webclient, i2b2-core-server)
```sh
# clone the repository and initialize submodules
git clone --recurse-submodules <repository-url>
```
### How to
see docker/i2b2-server, i2b2-web, shrine-server README.md file for building and configuring instances

### Branches Overview

- **i2b2-core-server:**  
  branch: `release/mu-1.8.3`

- **i2b2-webclient:**  
  branch: `release/mu-1.8.3`

- **i2b2-webclient-classic:**  
  branch: `release/mu`

- **Admin consoles for Shrine:**  
  - General admin: `release/mu-admin`
  - WashU-specific admin: `release/washu-admin`

- i2b2-data
  - branch: `snowflake/release-1.8.3`


## i2b2 dockers in AWS

> Note: CloudFormation template for creating network infrastructure in AWS VPC is on progress. It will be available under  `./infrastructure/` directory.

> Note: CloudFormation template for creating CI/CD pipeline in AWS ECS is on progress. It will be available under  `./pipeline/` directory.

### i2b2 as AWS ECS Service
Steps to run i2b2 containers as AWS ECS service.
1. Build docker images for i2b2-webclient and i2b2-core-server
2. Upload docker images in AWS ECR
2. Create ECS Task definition for i2b2.
3. Create ECS Cluster
4. Create i2b2 service using the task definition and deploy in the ECS cluster

### AWS ECR:

### Docker login to ECR
Use make file to login to your configured aws sso credential and docker ECR
