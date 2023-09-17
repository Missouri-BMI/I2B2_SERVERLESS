#!/bin/bash
# Usage: execute.sh [WildFly mode] [configuration file]
#
# The default mode is 'standalone' and default configuration is based on the
# mode. It can be 'standalone.xml' or 'domain.xml'.

JBOSS_HOME=/opt/jboss/wildfly
JBOSS_CLI=$JBOSS_HOME/bin/jboss-cli.sh
JBOSS_MODE=${1:-"standalone"}
JBOSS_CONFIG=${2:-"$JBOSS_MODE.xml"}

function wait_for_server() {
  until `$JBOSS_CLI -c ":read-attribute(name=server-state)" 2> /dev/null | grep -q running`; do
    sleep 1
  done
}

# function enable_debug() {
#   $JBOSS_CLI -c << EOF
#     batch
#     /subsystem=logging/logger=edu.harvard.i2b2:add(level=DEBUG) 
#     /subsystem=logging/console-handler=CONSOLE:write-attribute(name=level, value=DEBUG)
#     run-batch
# EOF
# }

# function enable_info() {
#   $JBOSS_CLI -c << EOF
#     batch
#     /subsystem=logging/logger=edu.harvard.i2b2:add(level=INFO) 
#     /subsystem=logging/console-handler=CONSOLE:write-attribute(name=level, value=INFO)
#     run-batch
# EOF
# }

function cofigure_wildfly() {
  $JBOSS_CLI -c << EOF
    batch

    # AJP enable
    /subsystem=undertow/server=default-server/ajp-listener=ajp:add(max-post-size=10485760000,socket-binding=ajp, scheme=http)

    /subsystem=undertow/configuration=filter/expression-filter=secret-checker:add(expression="not equals(%{r,secret}, '${AJP_SECRET}') -> response-code(403)")

    /subsystem=undertow/server=default-server/host=default-host/filter-ref=secret-checker:add(predicate="equals(%p, 8009)")
  
    # Add Snowflake module
    module add --name=net.snowflake --resources=/opt/jboss/wildfly/customization/snowflake-jdbc-3.13.30.jar

    # Add Snowflake driver
    /subsystem=datasources/jdbc-driver=snowflake:add(driver-name="snowflake",driver-module-name="net.snowflake",driver-class-name=net.snowflake.client.jdbc.SnowflakeDriver)

    # Add datasources
    data-source add --jndi-name=java:/CRCBootStrapDS --name=CRCBootStrapDS --connection-url=${DS_HIVE_DB} --driver-name=snowflake --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/QueryToolDemoDS --name=QueryToolDemoDS --connection-url=${DS_CRC_DB} --driver-name=snowflake --user-name=${DS_CRC_USER} --password=${DS_CRC_PASS} --max-pool-size=200

    # data-source add --jndi-name=java:/IMBootStrapDS --name=IMBootStrapDS --connection-url=${DS_HIVE_DB} --driver-name=postgres --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS} --max-pool-size=200
    # data-source add --jndi-name=java:/IMDemoDS --name=IMDemoDS --connection-url=${DS_IM_DB} --driver-name=postgres --user-name=${DS_IM_USER} --password=${DS_IM_PASS} --max-pool-size=200

    data-source add --jndi-name=java:/OntologyBootStrapDS --name=OntologyBootStrapDS --connection-url=${DS_HIVE_DB} --driver-name=snowflake --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/OntologyDemoDS --name=OntologyDemoDS --connection-url=${DS_ONT_DB} --driver-name=snowflake --user-name=${DS_ONT_USER} --password=${DS_ONT_PASS} --max-pool-size=200

    data-source add --jndi-name=java:/PMBootStrapDS --name=PMBootStrapDS --connection-url=${DS_PM_DB} --driver-name=snowflake --user-name=${DS_PM_USER} --password=${DS_PM_PASS} --max-pool-size=200

    data-source add --jndi-name=java:/WorkplaceBootStrapDS --name=WorkplaceBootStrapDS --connection-url=${DS_HIVE_DB} --driver-name=snowflake --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/WorkplaceDemoDS --name=WorkplaceDemoDS --connection-url=${DS_WD_DB} --driver-name=snowflake --user-name=${DS_WD_USER} --password=${DS_WD_PASS} --max-pool-size=200
  
    # Execute the batch
    run-batch
EOF
}

function configure_datasources() {
  # Changing log level
  if [ "${ENVIRONMENT}" = "PROD" ]; then
    $JBOSS_CLI -c << EOF
    
    batch
    ## SANDBOX-GPC
    data-source add --jndi-name=java:/QueryToolSANDBOX-GPC --name=QueryToolSANDBOX-GPC --connection-url=${DS_CRC_DB_GPC} --driver-name=snowflake --user-name=${DS_CRC_USER} --password=${DS_CRC_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/OntologySANDBOX-GPC --name=OntologySANDBOX-GPC --connection-url=${DS_ONT_DB_GPC} --driver-name=snowflake --user-name=${DS_ONT_USER} --password=${DS_ONT_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/WorkplaceSANDBOX-GPC --name=WorkplaceSANDBOX-GPC --connection-url=${DS_WD_DB_GPC} --driver-name=snowflake --user-name=${DS_WD_USER} --password=${DS_WD_PASS} --max-pool-size=200 
    # Execute the batch
    run-batch
EOF
  else
    $JBOSS_CLI -c << EOF
    batch
    ## SANDBOX-VASANTHI
    data-source add --jndi-name=java:/QueryToolSANDBOX-VASANTHI --name=QueryToolSANDBOX-VASANTHI --connection-url=${DS_CRC_DB_VASANTHI} --driver-name=snowflake --user-name=${DS_CRC_USER} --password=${DS_CRC_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/OntologySANDBOX-VASANTHI --name=OntologySANDBOX-VASANTHI --connection-url=${DS_ONT_DB_VASANTHI} --driver-name=snowflake --user-name=${DS_ONT_USER} --password=${DS_ONT_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/WorkplaceSANDBOX-VASANTHI --name=WorkplaceSANDBOX-VASANTHI --connection-url=${DS_WD_DB_VASANTHI} --driver-name=snowflake --user-name=${DS_WD_USER} --password=${DS_WD_PASS} --max-pool-size=200
    
    ## SANDBOX-SUMAN
    data-source add --jndi-name=java:/QueryToolSANDBOX-SUMAN --name=QueryToolSANDBOX-SUMAN --connection-url=${DS_CRC_DB_SUMAN} --driver-name=snowflake --user-name=${DS_CRC_USER} --password=${DS_CRC_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/OntologySANDBOX-SUMAN --name=OntologySANDBOX-SUMAN --connection-url=${DS_ONT_DB_SUMAN} --driver-name=snowflake --user-name=${DS_ONT_USER} --password=${DS_ONT_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/WorkplaceSANDBOX-SUMAN --name=WorkplaceSANDBOX-SUMAN --connection-url=${DS_WD_DB_SUMAN} --driver-name=snowflake --user-name=${DS_WD_USER} --password=${DS_WD_PASS} --max-pool-size=200

    ## SANDBOX-TAMARA
    data-source add --jndi-name=java:/QueryToolSANDBOX-TAMARA --name=QueryToolSANDBOX-TAMARA --connection-url=${DS_CRC_DB_TAMARA} --driver-name=snowflake --user-name=${DS_CRC_USER} --password=${DS_CRC_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/OntologySANDBOX-TAMARA --name=OntologySANDBOX-TAMARA --connection-url=${DS_ONT_DB_TAMARA} --driver-name=snowflake --user-name=${DS_ONT_USER} --password=${DS_ONT_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/WorkplaceSANDBOX-TAMARA --name=WorkplaceSANDBOX-TAMARA --connection-url=${DS_WD_DB_TAMARA} --driver-name=snowflake --user-name=${DS_WD_USER} --password=${DS_WD_PASS} --max-pool-size=200

    ## SANDBOX-RANA
    data-source add --jndi-name=java:/QueryToolSANDBOX-RANA --name=QueryToolSANDBOX-RANA --connection-url=${DS_CRC_DB_RANA} --driver-name=snowflake --user-name=${DS_CRC_USER} --password=${DS_CRC_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/OntologySANDBOX-RANA --name=OntologySANDBOX-RANA --connection-url=${DS_ONT_DB_RANA} --driver-name=snowflake --user-name=${DS_ONT_USER} --password=${DS_ONT_PASS} --max-pool-size=200
    data-source add --jndi-name=java:/WorkplaceSANDBOX-RANA --name=WorkplaceSANDBOX-RANA --connection-url=${DS_WD_DB_RANA} --driver-name=snowflake --user-name=${DS_WD_USER} --password=${DS_WD_PASS} --max-pool-size=200
    # Execute the batch
    run-batch
EOF
  fi 
  
}

echo "=> Starting WildFly server"
$JBOSS_HOME/bin/$JBOSS_MODE.sh -b 0.0.0.0 -c $JBOSS_CONFIG &

echo "=> Waiting for the server to boot"
wait_for_server


echo "=> Creating data sources and AJP"
cofigure_wildfly

echo "=> Creating sandbox datasource"
configure_datasources

# Changing log level
# if [ "${DEBUG_ENABLED}" = "true" ]; then
#   enable_debug
# else
#   enable_info
# fi 

# Deploy the WAR
cp -r /opt/jboss/wildfly/customization/i2b2.war $JBOSS_HOME/$JBOSS_MODE/deployments/i2b2.war
touch $JBOSS_HOME/$JBOSS_MODE/deployments/i2b2.war.dodeploy

echo "=> Shutting down WildFly"
if [ "$JBOSS_MODE" = "standalone" ]; then
  $JBOSS_CLI -c ":shutdown"
else
  $JBOSS_CLI -c "/host=*:shutdown"
fi

echo "=> Restarting WildFly"
$JBOSS_HOME/bin/$JBOSS_MODE.sh -b 0.0.0.0
