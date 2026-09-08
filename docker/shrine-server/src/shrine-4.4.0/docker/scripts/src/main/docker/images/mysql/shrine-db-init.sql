create database ADAPTER;
create database QEP;
create database HUB;
-- handle database for authz here as well?

use ADAPTER;
source /data/shrine-setup/adapter/sql/adapter-mysql.ddl;
source /data/shrine-setup/adapter/sql/adapter-update-mysql.sql;

use QEP;
source /data/shrine-setup/qep/sql/qep-mysql.ddl;
source /data/shrine-setup/qep/sql/qep-mysql-update.sql;

use HUB;
source /data/shrine-setup/hub/sql/hub-mysql.ddl;
source /data/shrine-setup/hub/sql/hub-mysql-update.sql;
