## Simplied installation process of i2b2-data, i2b2-web, i2b2-server, i2b2 shrine in docker containers

## Submodules
pull submodules (i2b2-data, i2b2-webclient, i2b2-core-server)
```sh
# clone the repository and initialize submodules
git clone --recurse-submodules <repository-url>
```

## i2b2 Platform in Docker

### Docker implementation: i2b2-webclient

```sh
# change directory to i2b2-web
$ cd ./Docker/i2b2-web/
```

#### provide your own configuration files for configuring httpd and shibd services
./Configuration/dev/apache/ajp.conf
> Note: Modify and revise Apache, Shibboleth and other configuration files  in `./Docker/i2b2-web/Configurations/` path

```
<VirtualHost 127.0.0.1:80>
  ProxyRequests Off 
  ProxyPreserveHost Off 
  <Location /i2b2/services/> 
        Require ip 127.0.0.1 
        ProxyPass ajp://127.0.0.1:8009/i2b2/services/ secret=<secret>
  </Location>
</VirtualHost>
```

./Configuration/dev/apache/i2b2.conf

```
ServerName https://i2b2-dev.nextgenbmi.umsystem.edu/

RedirectMatch ^/$ /webclient/
```


#### Configure Shibboleth
./Configuration/dev/shibboleth (attribute-map.xml, shibboleth2.xml sp-cert.pem and sp-key.pem. use certificate.cnf to generate keys)

#### build and run docker container
```
# build
make local
# run
make run
```

### Docker implementation: i2b2-core-server
> Note: create commands.cli in `./Docker/i2b2-server/configuration/` directory to configure the wildfly datasource for i2b2 cells.

```sh
# change directory to i2b2-server
$ cd ./Docker/i2b2-server/

# provide your own jdbc configuration files 
./configuration/dev/commands.cli

# build
make build-local

# run
make run-local

```

#### Example of configuring datasource in commands.cli
```sh

# system

batch

# Logs enabled
/subsystem=logging/console-handler=CONSOLE:write-attribute(name=level, value=INFO)

# create the logger for i2b2 package
/subsystem=logging/logger=edu.harvard.i2b2:add(level=INFO)

# AJP enable
/subsystem=undertow/server=default-server/ajp-listener=ajp:add(max-post-size=10485760000,socket-binding=ajp, scheme=http)

/subsystem=undertow/configuration=filter/expression-filter=secret-checker:add(expression="not equals(%{r,secret}, '<your-secret>') -> response-code(403)")

/subsystem=undertow/server=default-server/host=default-host/filter-ref=secret-checker:add(predicate="equals(%p, 8009)")

run-batch

# snowflake

batch
# Add Snowflake module
module add --name=net.snowflake --resources=/opt/jboss/customization/snowflake-jdbc-3.25.1.jar

# Add Snowflake driver
/subsystem=datasources/jdbc-driver=snowflake:add(driver-name="snowflake",driver-module-name="net.snowflake",driver-class-name=net.snowflake.client.jdbc.SnowflakeDriver)

data-source add \
--jndi-name=java:/QueryToolDemoDS \
--name=QueryToolDemoDS \
--connection-url=jdbc:snowflake://<your-account#TTT-YYDB>.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2DATA&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true&JDBC_QUERY_RESULT_FORMAT=JSON&private_key_file=/opt/jboss/customization/rsa_key.p8&private_key_file_pwd=<your-private-key> \
--driver-name=snowflake \
--user-name=I2B2_DEV_USER \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/OntologyDemoDS \
--name=OntologyDemoDS \
--connection-url=jdbc:snowflake://<your-account#TTT-YYDB>.snowflakecomputing.com/?db=I2B2_DEV&schema=I2B2METADATA&warehouse=I2B2_DEV_WH&role=I2B2_DEV_APP_ROLE&CLIENT_RESULT_COLUMN_CASE_INSENSITIVE=true&JDBC_QUERY_RESULT_FORMAT=JSON&private_key_file=/opt/jboss/customization/rsa_key.p8&private_key_file_pwd=<your-private-key> \
--driver-name=snowflake \
--user-name=I2B2_DEV_USER \
--max-pool-size=200 \
--enabled=true

run-batch

# postgresql

batch

# Add postgres module
module add --name=org.postgresql --resources=/opt/jboss/customization/postgresql-42.2.14.jar

# Add Postgres driver
/subsystem=datasources/jdbc-driver=postgresql:add(driver-name="postgresql",driver-module-name="org.postgresql",driver-class-name=org.postgresql.Driver)

# COMMON datasources
data-source add \
--jndi-name=java:/CRCBootStrapDS \
--name=CRCBootStrapDS \
--connection-url=jdbc:postgresql://<your-db>:5432/i2b2?currentSchema=i2b2hive \
--driver-name=postgresql \
--user-name=postgres \
--password=<your-pass> \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/OntologyBootStrapDS \
--name=OntologyBootStrapDS \
--connection-url=jdbc:postgresql://<your-db>:5432/i2b2?currentSchema=i2b2hive \
--driver-name=postgresql \
--user-name=postgres \
--password=<your-pass> \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/PMBootStrapDS \
--name=PMBootStrapDS \
--connection-url=jdbc:postgresql://<your-db>:5432/i2b2?currentSchema=i2b2pm \
--driver-name=postgresql \
--user-name=postgres \
--password=<your-pass> \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/WorkplaceBootStrapDS \
--name=WorkplaceBootStrapDS \
--connection-url=jdbc:postgresql://<your-db>:5432/i2b2?currentSchema=i2b2hive \
--driver-name=postgresql \
--user-name=postgres \
--password=<your-pass> \
--max-pool-size=200 \
--enabled=true

data-source add \
--jndi-name=java:/WorkplaceDemoDS \
--name=WorkplaceDemoDS \
--connection-url=jdbc:postgresql://<your-db>:5432/i2b2?currentSchema=i2b2workdata \
--driver-name=postgresql \
--user-name=postgres \
--password=<your-pass> \
--max-pool-size=200 \
--enabled=true

run-batch


```
### Docker implementation of i2b2 shrine
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

# build
make dev-mu

```

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
