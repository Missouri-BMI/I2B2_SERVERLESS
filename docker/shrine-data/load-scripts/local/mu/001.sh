#!/bin/bash


mariadb --user=root --password=Password123<<-EOSQL	
    DROP DATABASE IF EXISTS adapterAuditDB;
    DROP DATABASE IF EXISTS qepAuditDB;
    DROP DATABASE IF EXISTS stewardDB;
    DROP DATABASE IF EXISTS shrine_query_history;
	
    CREATE DATABASE adapterAuditDB;
    CREATE DATABASE qepAuditDB;
    CREATE DATABASE stewardDB;
    CREATE DATABASE shrine_query_history;
EOSQL

mariadb --user=root --password=Password123 adapterAuditDB < ./adapter/sql/adapterAuditDB-mysql.ddl
mariadb --user=root --password=Password123 shrine_query_history < ./adapter/sql/shrine_query_history-mysql.ddl
mariadb --user=root --password=Password123 shrine_query_history < ./adapter/sql/shrine_query_history-update-mysql.sql
mariadb --user=root --password=Password123 stewardDB < ./dsa/sql/mysql.ddl
mariadb --user=root --password=Password123 qepAuditDB < ./qep/sql/mysql.ddl
mariadb --user=root --password=Password123 qepAuditDB < ./qep/sql/mysql-update.sql

mariadb --user=root --password=Password123 qepAuditDB < ./hub/sql/mysql.ddl
mariadb --user=root --password=Password123 qepAuditDB < ./hub/sql/mysql-update.ddl