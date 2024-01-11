#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER"<<-EOSQL
	                                    
	CREATE DATABASE i2b2_demo;
	CREATE DATABASE i2b2_act;

EOSQL


psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d i2b2_demo<<-EOSQL
	CREATE SCHEMA i2b2data;
	CREATE SCHEMA i2b2metadata;
	CREATE SCHEMA i2b2hive;
	CREATE SCHEMA i2b2pm;
    CREATE SCHEMA i2b2workdata;
EOSQL


psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d i2b2_act<<-EOSQL
	CREATE SCHEMA i2b2data;
	CREATE SCHEMA i2b2metadata;
	CREATE SCHEMA i2b2hive;
	CREATE SCHEMA i2b2pm;
    CREATE SCHEMA i2b2workdata;
EOSQL