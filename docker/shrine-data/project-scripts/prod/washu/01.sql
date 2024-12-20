USE DATABASE I2B2_SHRINE_WASHU_PROD;
BEGIN
    USE SCHEMA i2b2hive;

    UPDATE crc_db_lookup SET C_DOMAIN_ID = 'wustl.edu' WHERE C_DOMAIN_ID = 'i2b2demo';
    UPDATE im_db_lookup SET C_DOMAIN_ID = 'wustl.edu' WHERE C_DOMAIN_ID = 'i2b2demo';
    UPDATE ont_db_lookup SET C_DOMAIN_ID = 'wustl.edu' WHERE C_DOMAIN_ID = 'i2b2demo';
    UPDATE work_db_lookup SET C_DOMAIN_ID = 'wustl.edu' WHERE C_DOMAIN_ID = 'i2b2demo';
END;

BEGIN
    USE SCHEMA I2B2PM;
    UPDATE pm_hive_data SET DOMAIN_NAME = 'wustl.edu' WHERE DOMAIN_NAME = 'i2b2demo';
  
END;

BEGIN
    USE SCHEMA I2B2PM;
    truncate table PM_PROJECT_USER_ROLES;
    truncate table PM_USER_DATA;
    truncate table PM_USER_PARAMS;
  
END;
