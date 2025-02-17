## Simplied installation process of i2b2-data, i2b2-web, i2b2-server, i2b2 shrine in docker containers

## Submodules
```
# pull submodules (i2b2-data, i2b2-webclient, i2b2-core-server)
git submodule update --recursive
```
## Docker implementation of i2b2-data installer 
```sh
# change directory to data-installer
$ cd ./Docker/data-installer/

# create db configuration files 
./config/Crcdata/db.properties 
./config/Hivedata/db.properties
./config/Metadata/db.properties
./config/Imdata/db.properties 
./config/Pmdata/db.properties 
./config/Workdata/db.properties
# build and run docker container
./docker-build
./docker-run

```
## Docker implementation i2b2-webclient

```sh
# change directory to i2b2-web
$ cd ./Docker/i2b2-web/

# provide your own configuration files for configuring httpd and shibd services
./Configuration/dev/apache
./Configuration/dev/shibboleth (sp-cert.pem and sp-key.pem. use certificate.cnf to generate keys)

# build and run docker container
./docker-build
./docker-run

```
> Note: Modify and revise Apache, Shibboleth and other configuration files  in `./Docker/i2b2-web/Configurations/` path

## Docker implementation i2b2-core-server

```sh
# change directory to i2b2-server
$ cd ./Docker/i2b2-server/

# provide your own jdbc configuration files 
./configuration/dev/commands.cli

# build and run docker container
./build.dev.sh
./run-dev

```
> Note: create commands.cli in `./Docker/i2b2-server/configuration/` directory to configure the wildfly datasource for i2b2 cells.

### Example of configuring datasource in commands.cli
```sh
# Mark the commands below to be run as a batch
batch

# loggin
/subsystem=logging/logger=edu.harvard.i2b2:add ok
/subsystem=logging/logger=edu.harvard.i2b2:write-attribute(name="level", value="DEBUG")

# AJP enable
/subsystem=undertow/server=default-server/ajp-listener=ajp:add(max-post-size=10485760000,socket-binding=ajp, scheme=http)

/subsystem=undertow/configuration=filter/expression-filter=secret-checker:add(expression="not equals(%{r,secret}, 'YOUR_SECRET') -> response-code(403)")

/subsystem=undertow/server=default-server/host=default-host/filter-ref=secret-checker:add(predicate="equals(%p, 8009)")

# Add Snowflake module
module add --name=net.snowflake --resources=/opt/jboss/customization/snowflake-jdbc-3.13.30.jar

# Add Snowflake driver
/subsystem=datasources/jdbc-driver=snowflake:add(driver-name="snowflake",driver-module-name="net.snowflake",driver-class-name=net.snowflake.client.jdbc.SnowflakeDriver)

# COMMON datasources
data-source add \
--jndi-name=java:/CRCBootStrapDS \
--name=CRCBootStrapDS \
--connection-url=jdbc:snowflake://YOUR_ACCOUNT.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2HIVE&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true \
--driver-name=snowflake \
--user-name=YOUR_USER \
--password=YOUR_PASSWORD \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/QueryToolDemoDS \
--name=QueryToolDemoDS \
--connection-url=jdbc:snowflake://YOUR_ACCOUNT.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2DATA&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true \
--driver-name=snowflake \
--user-name=YOUR_USER \
--password=YOUR_PASSWORD \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/OntologyBootStrapDS \
--name=OntologyBootStrapDS \
--connection-url=jdbc:snowflake://YOUR_ACCOUNT.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2HIVE&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true \
--driver-name=snowflake \
--user-name=YOUR_USER \
--password=YOUR_PASSWORD \
--max-pool-size=200 \
--enabled=true


data-source add \
--jndi-name=java:/OntologyDemoDS \
--name=OntologyDemoDS \
--connection-url=jdbc:snowflake://YOUR_ACCOUNT.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2METADATA&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true \
--driver-name=snowflake \
--user-name=YOUR_USER \
--password=YOUR_PASSWORD \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/PMBootStrapDS \
--name=PMBootStrapDS \
--connection-url=jdbc:snowflake://YOUR_ACCOUNT.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2PM&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true \
--driver-name=snowflake \
--user-name=YOUR_USER \
--password=YOUR_PASSWORD \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/WorkplaceBootStrapDS \
--name=WorkplaceBootStrapDS \
--connection-url=jdbc:snowflake://YOUR_ACCOUNT.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2HIVE&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true \
--driver-name=snowflake \
--user-name=YOUR_USER \
--password=YOUR_PASSWORD \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/WorkplaceDemoDS \
--name=WorkplaceDemoDS \
--connection-url=jdbc:snowflake://YOUR_ACCOUNT.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2WORKDATA&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true \
--driver-name=snowflake \
--user-name=YOUR_USER \
--password=YOUR_PASSWORD \
--max-pool-size=200 \
--enabled=true

# Execute the batch

run-batch
```
## Docker implementation of i2b2 shrine
```sh
# change directory to shrine-server
$ cd ./Docker/shrine-server/

# copy config files 
./src/configs/mu/context.xml
./src/configs/mu/password.conf
./src/configs/mu/server.xml
./src/configs/mu/shrine.conf

# copy lucene_index, suggest_index, adaptermappings
./src/data/lucene_index
./src/data/suggest_index
./src/data/adapter_mapping.csv
./src/data/logo.png

# place keystore
src/keystore/${PROJECT}/output/ # eg .jks/.p12

# build and run docker container
./build

```

## i2b2 in AWS

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
```sh
# login to aws-cli for role based sso accounts
$ aws sso login --profile <profile_name>

# login to aws ecr using docker-cli
$ aws ecr get-login-password --profile <profile_name> | docker login --username AWS --password-stdin <account_id>.dkr.ecr.<region>.amazonaws.com
```

### Create Repository in ECS
```sh
$ aws ecr create-repository \
    --repository-name <name> \
    --image-scanning-configuration scanOnPush=true \
    --profile <profile>
```

### Push Images in ECS
```sh
$ docker push <image-uri>
```
