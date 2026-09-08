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

function enable_debug() {
  $JBOSS_CLI -c << EOF
    batch
    /subsystem=logging/logger=edu.harvard.i2b2:add ok
    /subsystem=logging/logger=edu.harvard.i2b2:write-attribute(name="level", value="DEBUG")
    run-batch
EOF
}

function create_mssql_ds() {
  $JBOSS_CLI -c << EOF
    batch
    # Add MSSQL module
    module add --name=com.mssql --resources=/opt/jboss/wildfly/customization/mssql-jdbc-7.4.1.jre8.jar --dependencies=javax.api,javax.transaction.api

    # Add MSSQL driver
    /subsystem=datasources/jdbc-driver=mssql:add(driver-name="mssql",driver-module-name="com.mssql",driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver)

    # Add datasources
    data-source add --jndi-name=java:/CRCBootStrapDS --name=CRCBootStrapDS --connection-url=jdbc:sqlserver://${DS_IP}:${DS_PORT};databasename=${DS_HIVE_DB} --driver-name=mssql --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS}
    data-source add --jndi-name=java:/QueryToolDemoDS --name=QueryToolDemoDS --connection-url=jdbc:sqlserver://${DS_CRC_IP}:${DS_CRC_PORT};databasename=${DS_CRC_DB} --driver-name=mssql --user-name=${DS_CRC_USER} --password=${DS_CRC_PASS}

    data-source add --jndi-name=java:/IMBootStrapDS --name=IMBootStrapDS --connection-url=jdbc:sqlserver://${DS_IM_IP}:${DS_IM_PORT} --driver-name=mssql --user-name=${DS_IM_USER} --password=${DS_IM_PASS}
    data-source add --jndi-name=java:/IMDemoDS --name=IMDemoDS --connection-url=jdbc:sqlserver://${DS_IM_IP}:${DS_IM_PORT} --driver-name=mssql --user-name=${DS_IM_USER} --password=${DS_IM_PASS}

    data-source add --jndi-name=java:/OntologyBootStrapDS --name=OntologyBootStrapDS --connection-url=jdbc:sqlserver://${DS_IP}:${DS_PORT};databasename=${DS_HIVE_DB} --driver-name=mssql --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS}
    data-source add --jndi-name=java:/OntologyDemoDS --name=OntologyDemoDS --connection-url=jdbc:sqlserver://${DS_ONT_IP}:${DS_ONT_PORT};databasename=${DS_ONT_DB} --driver-name=mssql --user-name=${DS_ONT_USER} --password=${DS_ONT_PASS}

    data-source add --jndi-name=java:/PMBootStrapDS --name=PMBootStrapDS --connection-url=jdbc:sqlserver://${DS_PM_IP}:${DS_PM_PORT};databasename=${DS_PM_DB} --driver-name=mssql --user-name=${DS_PM_USER} --password=${DS_PM_PASS}

    data-source add --jndi-name=java:/WorkplaceBootStrapDS --name=WorkplaceBootStrapDS --connection-url=jdbc:sqlserver://${DS_IP}:${DS_PORT};databasename=${DS_HIVE_DB} --driver-name=mssql --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS}
    data-source add --jndi-name=java:/WorkplaceDemoDS --name=WorkplaceDemoDS --connection-url=jdbc:sqlserver://${DS_WD_IP}:${DS_WD_PORT};databasename=${DS_WD_DB} --driver-name=mssql --user-name=${DS_WD_USER} --password=${DS_WD_PASS}

    # Execute the batch
    run-batch
EOF
}

function create_postgres_ds() {
  $JBOSS_CLI -c << EOF
    batch
    # Add Postgres module
    module add --name=org.postgresql --resources=/opt/jboss/wildfly/customization/postgresql-42.2.8.jar --dependencies=javax.api,javax.transaction.api

    # Add Postgres driver
    /subsystem=datasources/jdbc-driver=postgres:add(driver-name="postgres",driver-module-name="org.postgresql",driver-class-name=org.postgresql.Driver)

    # Add datasources
    data-source add --jndi-name=java:/CRCBootStrapDS --name=CRCBootStrapDS --connection-url=jdbc:postgresql://${DS_IP}:${DS_PORT}/i2b2?currentSchema=${DS_HIVE_DB} --driver-name=postgres --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS}
    data-source add --jndi-name=java:/QueryToolDemoDS --name=QueryToolDemoDS --connection-url=jdbc:postgresql://${DS_CRC_IP}:${DS_CRC_PORT}/i2b2?currentSchema=${DS_CRC_DB} --driver-name=postgres --user-name=${DS_CRC_USER} --password=${DS_CRC_PASS}

    data-source add --jndi-name=java:/IMBootStrapDS --name=IMBootStrapDS --connection-url=jdbc:postgresql://${DS_IM_IP}:${DS_IM_PORT}/i2b2?currentSchema=${DS_IM_DB} --driver-name=postgres --user-name=${DS_IM_USER} --password=${DS_IM_PASS}
    data-source add --jndi-name=java:/IMDemoDS --name=IMDemoDS --connection-url=jdbc:postgresql://${DS_IM_IP}:${DS_IM_PORT}/i2b2?currentSchema=${DS_IM_DB} --driver-name=postgres --user-name=${DS_IM_USER} --password=${DS_IM_PASS}

    data-source add --jndi-name=java:/OntologyBootStrapDS --name=OntologyBootStrapDS --connection-url=jdbc:postgresql://${DS_IP}:${DS_PORT}/i2b2?currentSchema=${DS_HIVE_DB} --driver-name=postgres --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS}
    data-source add --jndi-name=java:/OntologyDemoDS --name=OntologyDemoDS --connection-url=jdbc:postgresql://${DS_ONT_IP}:${DS_ONT_PORT}/i2b2?currentSchema=i2b2?currentSchema=${DS_ONT_DB} --driver-name=postgres --user-name=${DS_ONT_USER} --password=${DS_ONT_PASS}

    data-source add --jndi-name=java:/PMBootStrapDS --name=PMBootStrapDS --connection-url=jdbc:postgresql://${DS_PM_IP}:${DS_PM_PORT}/i2b2?currentSchema=${DS_PM_DB} --driver-name=postgres --user-name=${DS_PM_USER} --password=${DS_PM_PASS}

    data-source add --jndi-name=java:/WorkplaceBootStrapDS --name=WorkplaceBootStrapDS --connection-url=jdbc:postgresql://${DS_IP}:${DS_PORT}/i2b2?currentSchema=${DS_HIVE_DB} --driver-name=postgres --user-name=${DS_HIVE_USER} --password=${DS_HIVE_PASS}
    data-source add --jndi-name=java:/WorkplaceDemoDS --name=WorkplaceDemoDS --connection-url=jdbc:postgresql://${DS_WD_IP}:${DS_WD_PORT}/i2b2?currentSchema=${DS_WD_DB} --driver-name=postgres --user-name=${DS_WD_USER} --password=${DS_WD_PASS}

    # Execute the batch
    run-batch
EOF
}

function create_oracle_ds() {
  echo "Not implemeted yet"
}


echo "=> Assigning defaults for unspecified variables"
DEBUG_ENABLED="false"
case ${DS_TYPE} in
  "mssql")
    DEFAULT_DS_PORT="1433"
    ;;
  "oracle")
    DEFAULT_DS_PORT="1521"
    ;;
  "postgres")
    DEFAULT_DS_PORT="5432"
    ;;
esac

DS_PORT=${DS_PORT:-${DEFAULT_DS_PORT}}

echo "DS_IP:$DS_IP"
echo "DS_CRC_IP:$DS_CRC_IP"
echo "DS_ONT_IP:$DS_ONT_IP"
echo "DS_PM_IP:$DS_PM_IP"

echo "DS_TYPE:$DS_TYPE"
echo "DS_IP:$DS_IP"
echo "DS_PORT:$DS_PORT"

echo "DS_CRC_IP:$DS_CRC_IP"
echo "DS_CRC_PORT:$DS_CRC_PORT"
echo "DS_CRC_DB:$DS_CRC_DB"

DS_CRC_IP=${DS_CRC_IP:-${DS_IP}}
DS_ONT_IP=${DS_ONT_IP:-${DS_IP}}
DS_PM_IP=${DS_PM_IP:-${DS_IP}}

echo "DS_CRC_IP:$DS_CRC_IP"
echo "DS_CRC_PORT:$DS_CRC_PORT"
echo "DS_CRC_DB:$DS_CRC_DB"

DS_CRC_IP=${DS_CRC_IP:-${DS_IP}}
DS_ONT_IP=${DS_ONT_IP:-${DS_IP}}
DS_PM_IP=${DS_PM_IP:-${DS_IP}}
DS_WD_IP=${DS_WD_IP:-${DS_IP}}

echo "DS_ONT_IP:$DS_ONT_IP"

DS_CRC_PORT=${DS_CRC_PORT:-${DS_PORT}}
DS_ONT_PORT=${DS_ONT_PORT:-${DS_PORT}}
DS_PM_PORT=${DS_PM_PORT:-${DS_PORT}}
DS_WD_PORT=${DS_WD_PORT:-${DS_PORT}}

DS_CRC_PASS=${DS_CRC_PASS:-${DS_PASS}}
DS_ONT_PASS=${DS_ONT_PASS:-${DS_PASS}}
DS_PM_PASS=${DS_PM_PASS:-${DS_PASS}}
DS_WD_PASS=${DS_WD_PASS:-${DS_PASS}}
DS_HIVE_PASS=${DS_HIVE_PASS:-${DS_PASS}}

echo "DS_CRC_IP:$DS_CRC_IP"
echo "DS_CRC_PORT:$DS_CRC_PORT"
echo "DS_CRC_DB:$DS_CRC_DB"
#DS_CRC_IP='i2b2-mssql'
#DS_CRC_PASS='<YourStrong@Passw0rd>'
echo "DS_CRC_IP:$DS_CRC_IP"
echo "DS_CRC_PORT:$DS_CRC_PORT"
echo "DS_CRC_DB:$DS_CRC_DB"

#Add mssql jar
mkdir -p /opt/jboss/wildfly/modules/com/mssql/main
cp /opt/jboss/wildfly/customization/mssql-jdbc-7.4.1.jre8.jar /opt/jboss/wildfly/modules/com/mssql/main

# Deploy the WAR
cp -r /opt/jboss/wildfly/customization/i2b2.war $JBOSS_HOME/$JBOSS_MODE/deployments/i2b2.war
touch $JBOSS_HOME/$JBOSS_MODE/deployments/i2b2.war.dodeploy

echo "=> Starting WildFly server"
$JBOSS_HOME/bin/$JBOSS_MODE.sh -b 0.0.0.0 -c $JBOSS_CONFIG -Djboss.http.port=${JBOSS_HTTP_PORT:-8080}

