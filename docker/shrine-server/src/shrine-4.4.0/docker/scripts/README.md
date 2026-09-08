# Local Development Environment Setup

## Prerequisites
1. [Docker](https://docs.docker.com/get-docker/) or [Docker Desktop](https://docs.docker.com/get-docker/)
    1. Make sure to allocate enough memory to Docker. In Docker Desktop, you can navigate to Preferences->Resources and allocate at least 8GB of memory 
2. Local versions of SHRINE search index, suggest index, and adapter mappings file
   1. ```wget https://shrine-ontologies.s3.amazonaws.com/demo/releases/lucene_index-DEMO-SHRINE-4.1.0.zip```
   Unzip .
   2. ```wget https://shrine-ontologies.s3.amazonaws.com/demo/releases/suggest_index-DEMO-SHRINE-4.1.0.zip```
   Unzip .
   3. ```wget https://shrine-ontologies.s3.amazonaws.com/demo/releases/AdapterMappings-DEMO-DEMO-SHRINE-4.1.0.csv```

## Minimum Docker Resources
10GB of memory (Can be set in the Docker Dashboard)

<br/>

## Setup environment variables
1. Create a file named `${SHRINE_BASE}/docker/scripts/src/main/docker/.env` and enter the following (replacing `/PATH/TO` with your local path and replacing any other placeholders that you want to change E.g. passwords):
   ```bash
   LUCENE_INDEX=/PATH/TO/lucene_index/
   SUGGEST_INDEX=/PATH/TO/suggest_index/
   ADAPTER_MAPPINGS=/PATH/TO/AdapterMappings-DEMO-SHRINE-x.y.0.csv  # match your copy of AdapterMappings.csv
   COMPOSE_PROJECT_NAME=shrine
   I2B2_WILDFLY_TAG=release-v1.7.13

   #Following fields only needs to be set if not using maven cmds
   #SHRINE_VERSION=specify.version.number.here
   #DOCKER_REGISTRY_URL=localhost:5000/

   #The following are optional i2b2 environment variables
   I2B2_WEB_TAG=release-v1.7.13
   I2B2_MSSQL_TAG=release-v1.7.13
   I2B2_DS_TYPE=mssql
   I2B2_DS_IP=i2b2-mssql
   I2B2_DS_PORT=1433
   I2B2_DS_PM_USER=SA
   I2B2_DS_ONT_USER=SA
   I2B2_DS_CRC_USER=SA
   I2B2_DS_WD_USER=SA
   I2B2_DS_HIVE_USER=SA

   I2B2_DS_PM_PASS="<YourStrong@Passw0rd>"
   I2B2_DS_WD_PASS="<YourStrong@Passw0rd>"
   I2B2_DS_HIVE_PASS="<YourStrong@Passw0rd>"

   I2B2_MSSQL_SA_PASSWORD="<YourStrong@Passw0rd>"

   I2B2_DS_CRC_IP=i2b2-mssql
   I2B2_DS_CRC_PASS="<YourStrong@Passw0rd>"
   I2B2_DS_CRC_PORT=1433
   I2B2_DS_CRC_DB=i2b2demodata

   I2B2_DS_ONT_IP=i2b2-mssql
   I2B2_DS_ONT_USERI2B2_DS_ONT_PASS="<YourStrong@Passw0rd>"
   I2B2_DS_ONT_PORT=1433
   I2B2_DS_ONT_DB=i2b2metadata
   ```

<br/>

# Maven cmds to start SHRINE Network

First run `mvn clean install` in your `shrine` directory

Also, make sure your Docker Desktop does not show `shrine-images-registry` 
or `shrine` in the Containers/Apps screen

Then run these in `shrine/docker/custom-lifecycle`
```mvn clean install```


Then run these in `shrine/docker/scripts`
```mvn clean install```

## mvn cmd to start new SHRINE network and docker registry
```bash
mvn createRegistry buildAllImages startNetwork
```


## create a new local docker registry
```bash
mvn createRegistry 
```

## start an existing docker registry
```bash
mvn startRegistry
```

## build the SHRINE MySQL image
```bash
mvn buildShrineMySQL
```

## build the SHRINE certificate image
```bash
mvn buildShrineCerts
```

## build the i2b2 wildfly image
```bash
mvn buildShrineI2b2Wildfly
```

## build Kafka
```bash
mvn buildKafka
```

## build the SHRINE node image
```bash
mvn buildShrineNode
```

## builds the shrine MySQL, certificate, i2b2 wildfly, Kafka, and node images
```bash
mvn buildAllImages
```

## pushes the SHRINE MySQL image to the docker local (this can be ignored for local development)
```bash
mvn pushShrineMySQL
```

## pushes the SHRINE certificate image to the docker registry (this can be ignored for local development)
```bash
mvn pushShrineCerts
```

## pushes the i2b2 wildfly image to the docker registry (this can be ignored for local development)
```bash
mvn pushShrineI2b2Wildfly
```

## pushes the SHRINE node image to the docker registry (this can be ignored for local development)
```bash
mvn pushShrineNode
```

## pushes the SHRINE Kafka image to the docker registry (this can be ignored for local development)
```bash
mvn pushKafka

## pushes all the built images to the docker registry (this can be ignored for local development)
```bash
mvn pushAllImages  ### Optional? broken?
```

## starts the SHRINE network (assumes images have already been built)
```bash
mvn startNetwork
```

## stop an existing docker registry
```bash
mvn stopRegistry
```


## stop the SHRINE network
```bash
mvn stopNetwork
```

## convenience cmd to update only the shrine nodes in the network once a network is running
```bash
mvn refreshShrineNode
```

## View the default local docker registry at 
http://localhost:5000/v2/_catalog

<br/>

# Start SHRINE Network Using Docker Cmds (Alternative to maven cmds above)

## Build the docker images
1. Build your SHRINE code as normal using Maven from the root of the source tree, ${SHRINE_BASE}
    ```bash
    mvn clean install
    ```
2. cd docker/scripts
    
3. From docker/scripts build the shrine-mysql docker image
    ```bash
    docker build --no-cache -f localhost:5000/src/main/docker/images/mysql/Dockerfile -t shrine-mysql:0.1 ../../
    ```
4. Build the shrine certs image
    ```bash
    docker build --no-cache --tag localhost:5000/shrine-certs:0.1 --progress plain -f src/main/docker/images/certs/Dockerfile ../../                                                                                                                                                                                       
    ```
5. Build the shrine docker image
    ```bash
    docker build --no-cache --tag localhost:5000/shrine-node:0.1 -f src/main/docker/images/shrine-node/Dockerfile ../../
    ```
6. Build the shrine i2b2 wildfly
    ```bash
    docker build --no-cache --build-arg I2B2_WILDFLY_TAG=release-v1.7.12a.0002 --tag shrine-i2b2-wildfly:0.1 -f src/main/docker/images/i2b2/Dockerfile ../../
    ```
7. From build the kafka image
    ```bash
    docker build -f kafka/src/main/docker/images/kafka/Dockerfile --no-cache -t kafka:3.0.0 .
 

## Run SHRINE Local Dev From Command Line
1. cd docker/scripts/

2. From ${SHRINE_BASE} run
    ```bash
   docker-compose --env-file src/main/docker/.env -f src/main/docker/dev-environments/certs/docker-compose.yml -f src/main/docker/dev-environments/kafka/docker-compose.yml -f src/main/docker/dev-environments/shrine-hub/docker-compose.yml -f src/main/docker/dev-environments/shrine-node1/docker-compose.yml -f src/main/docker/dev-environments/shrine-node2/docker-compose.yml -f src/main/docker/dev-environments/docker-compose.yml up --detach
    ```
3. To stop your SHRINE Local Dev from command line
    ```bash
   docker-compose --env-file docker/scripts/src/main/docker/.env -f docker/scripts/src/main/docker/dev-environments/certs/docker-compose.yml -f src/main/docker/dev-environments/kafka/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/shrine-hub/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/shrine-node1/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/shrine-node2/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/docker-compose.yml down
    ```
    
Note that you can also specify all of the properties directly in your environment via command line instead of using a .env file.
To do so, `export` each of the above properties. If you choose to specify your properties this way, you do not need to specify the .env file in your docker-compose command:
```bash
   docker-compose -f src/main/docker/dev-environments/certs/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/shrine-hub/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/shrine-node1/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/shrine-node2/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/kafka/docker-compose.yml -f docker/scripts/src/main/docker/dev-environments/docker-compose.yml up --detach
```


### Run SHRINE Local Dev From Intellij IDEA (Optional)
1. Make sure you have the Docker plugin installed 
2. Choose from the menu Run->Edit Configurations
3. In the upper left of the dialog, press the `+` button
4. Choose Docker -> Docker Compose
5. Give the configuration a relevant name (E.g. `compose shrine`)
6. Select your Docker server (You may have to `create new` and select your existing Docker install)
7. Choose your docker-compose file `./docker/scripts/src/main/docker/docker-compose.yml` using the file browser
8. Set your environment variables
    ```bash
    LUCENE_INDEX=PATH/TO/lucene_index/;SUGGEST_INDEX=PATH/TO/suggest_index/;ADAPTER_MAPPINGS=PATH/TO/AdapterMappings.csv
    ```
9. `Apply` 
10. In your Run Configuration tool, select `compose shrine` and click the run button
12. To stop the SHRINE Local Dev use your `Services` tool window, select the running composition, and click the `Down` button


<br/>

# Use your Local Dev instance
1. Navigate to `https://localhost:6443/shrine-api/shrine-webclient/`
2. Log in with username `demo` and password `demouser` (the demo i2b2 user)

**To push new front-end code:**
1. in shrine/qep/frontend, run ```mvn clean install```
2. in shrine/apps/shrine-api-war, run ```mvn clean install```
3. in shrine/docker/scripts, run ``` mvn refreshShrineNode```

**To push new back-end code:**
1. in the modified module(s), run ```mvn clean install```
2. in shrine/apps/shrine-api-app, run ```mvn clean install```
3. in shrine/apps/shrine-api-war, run ```mvn clean install```
4. in shrine/docker/scripts, run ``` mvn refreshShrineNode```

<br/>

# Useful/Troubleshooting commands:

## Get a command line shell for the shrine server

```bash
docker exec -it shrine-shrine-node1-1 bash
```
or, see the output of `docker container ls` for the correct node name

## Get a command line shell for the i2b2 server

```bash
docker exec -it i2b2-wildfly bash
```

## Install some database tools on the i2b2 wildfly server

```bash
rpm --import https://packages.microsoft.com/keys/microsoft.asc
curl https://packages.microsoft.com/config/centos/7/prod.repo > /etc/yum.repos.d/msprod.repo
yum install libunwind
yum install mssql-cli
```

## list running containers
```bash
docker container ls
```
## show logs for a container
```bash
docker logs --tail 200 --follow --timestamps <docker_container>
```

```bash
docker system prune
```

```bash
docker volume prune
```

```bash
docker image prune
```

#Kafka Help

## Log onto kafka-1 container
```bash
docker exec -it dev-environment_kafka-1_1 bash
```

## Create Topic (queue)
```bash
./bin/kafka-topics.sh --create --bootstrap-server kafka-1:19092 --topic shrine-node1-events --command-config /tmp/user-config.properties --replication-factor 1 --partitions 1 
```

## View information about created topic
```bash
bin/kafka-topics.sh --describe --topic test-topic --command-config /tmp/user-config.properties --bootstrap-server kafka-1:19092
```

## Write events to created topic
```bash
bin/kafka-console-producer.sh --topic test-topic --producer.config /tmp/user-config.properties --bootstrap-server kafka-1:19092
```

## Add user read access permissions for topic
```bash
bin/kafka-acls.sh --add --allow-principal User:shrine --command-config /tmp/admin-config.properties --group shrine-group --operation Read  --topic test-topic --bootstrap-server kafka-1:19092
```

## Read all events written to created topic
```bash
bin/kafka-console-consumer.sh --from-beginning --topic test-topic --consumer.config  /tmp/user-config.properties --group shrine-group --bootstrap-server kafka-1:19092
```
