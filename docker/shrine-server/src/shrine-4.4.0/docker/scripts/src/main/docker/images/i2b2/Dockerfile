ARG I2B2_WILDFLY_TAG
FROM i2b2/i2b2-wildfly:${I2B2_WILDFLY_TAG}
LABEL maintainer = "Harvard Catalyst Informatics"

COPY docker/scripts/src/main/docker/images/i2b2/execute.sh /opt/jboss/wildfly/customization/execute.sh
COPY docker/scripts/src/main/docker/images/i2b2/module.xml /opt/jboss/wildfly/modules/com/mssql/main/module.xml
COPY docker/scripts/src/main/docker/images/i2b2/standalone.xml /opt/jboss/wildfly/standalone/configuration/standalone.xml

ENV I2B2_DS_TYPE=mssql
ENV I2B2_DS_IP=i2b2-mssql
ENV I2B2_DS_PORT=1433
ENV I2B2_DS_PM_USER=SA
ENV I2B2_DS_ONT_USER=SA
ENV I2B2_DS_CRC_USER=SA
ENV I2B2_DS_WD_USER=SA
ENV I2B2_DS_WD_USER=SA
ENV I2B2_DS_HIVE_USER=SA
ENV I2B2_DS_PM_PASS="<YourStrong@Passw0rd>"
ENV I2B2_DS_WD_PASS="<YourStrong@Passw0rd>"
ENV I2B2_DS_HIVE_PASS="<YourStrong@Passw0rd>"
ENV I2B2_MSSQL_SA_PASSWORD="<YourStrong@Passw0rd>"
ENV I2B2_DS_WD_USER=SA
ENV I2B2_DS_HIVE_USER=SA
ENV I2B2_DS_PM_PASS="<YourStrong@Passw0rd>"
ENV I2B2_DS_WD_PASS="<YourStrong@Passw0rd>"
ENV I2B2_DS_HIVE_PASS="<YourStrong@Passw0rd>"
ENV I2B2_MSSQL_SA_PASSWORD="<YourStrong@Passw0rd>"
ENV I2B2_DS_CRC_IP=i2b2-mssql
ENV I2B2_DS_CRC_USER=SA
ENV I2B2_DS_CRC_PASS="<YourStrong@Passw0rd>"
ENV I2B2_DS_CRC_PORT=1433
ENV I2B2_DS_CRC_DB=i2b2demodata
ENV I2B2_DS_ONT_IP=i2b2-mssql
ENV I2B2_DS_ONT_USER=SA
ENV I2B2_DS_ONT_PASS="<YourStrong@Passw0rd>"
ENV I2B2_DS_ONT_PORT=1433
ENV I2B2_DS_ONT_DB=i2b2metadata