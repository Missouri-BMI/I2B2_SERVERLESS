#!/bin/bash


mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2<<-EOSQL	
    DROP DATABASE IF EXISTS adapterAuditDB;
    DROP DATABASE IF EXISTS qepAuditDB;
    DROP DATABASE IF EXISTS stewardDB;
    DROP DATABASE IF EXISTS shrine_query_history;
	
    CREATE DATABASE adapterAuditDB;
    CREATE DATABASE qepAuditDB;
    CREATE DATABASE stewardDB;
    CREATE DATABASE shrine_query_history;
EOSQL

mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2 adapterAuditDB < ./adapter/sql/adapterAuditDB-mysql.ddl
mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2 shrine_query_history < ./adapter/sql/shrine_query_history-mysql.ddl
mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2 shrine_query_history < ./adapter/sql/shrine_query_history-update-mysql.sql
mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2 stewardDB < ./dsa/sql/mysql.ddl
mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2 qepAuditDB < ./qep/sql/mysql.ddl
mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2 qepAuditDB < ./qep/sql/mysql-update.sql

mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2 qepAuditDB < ./hub/sql/mysql.ddl
mariadb --host=shrine-db-washu.chmnhhv9fwkb.us-east-2.rds.amazonaws.com --port=3306 --user=shrineAdmin --password=2nzTEnhkbCMHJsUx9qr2 qepAuditDB < ./hub/sql/mysql-update.ddl


