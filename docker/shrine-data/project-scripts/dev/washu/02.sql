-- admin user 

insert into PM_USER_DATA (user_id, full_name, password, status_cd) values ('mhmcb', 'Md Saber Hossain', '9117d59a69dc49807671a51f10ab7f', 'A');

INSERT INTO PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD)
VALUES('@', 'mhmcb', 'ADMIN', 'A');

insert into PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD) values ('ACT', 'mhmcb', 'USER', 'A');
 
 INSERT INTO PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD)
VALUES('ACT', 'mhmcb', 'MANAGER', 'A');
insert into PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD) values ('ACT', 'mhmcb', 'DATA_OBFSC', 'A');

insert into PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD) values ('ACT', 'mhmcb', 'DATA_AGG', 'A');

-- AGG_SERVICE_ACCOUNT
INSERT INTO PM_USER_DATA (USER_ID, FULL_NAME, PASSWORD, STATUS_CD)
VALUES('AGG_SERVICE_ACCOUNT', 'AGG_SERVICE_ACCOUNT', '9117d59a69dc49807671a51f10ab7f', 'A');
INSERT INTO PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD)
VALUES('ACT', 'AGG_SERVICE_ACCOUNT', 'USER', 'A');
INSERT INTO PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD)
VALUES('ACT', 'AGG_SERVICE_ACCOUNT', 'MANAGER', 'A');
INSERT INTO PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD)
VALUES('ACT', 'AGG_SERVICE_ACCOUNT', 'DATA_OBFSC', 'A');
INSERT INTO PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD)
VALUES('ACT', 'AGG_SERVICE_ACCOUNT', 'DATA_AGG', 'A');


-- Chapter 8.1
-- SHRINE User and Other Users in i2b2
insert into PM_USER_DATA (user_id, full_name, password, status_cd) values ('shrine', 'SHRINE User', '9117d59a69dc49807671a51f10ab7f', 'A');
  
-- The password hash you see above is for 'demouser' . Use pass-gen/gen.sh
 
insert into PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD) values ('ACT', 'shrine', 'USER', 'A');
insert into PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD) values ('ACT', 'shrine', 'DATA_OBFSC', 'A');
insert into PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD) values ('ACT', 'shrine', 'DATA_AGG', 'A');


-- Chapter 8.5
--Setting Up the Data Steward User
insert into PM_USER_DATA (user_id, full_name, password, status_cd) values ('shrine_steward', 'SHRINE Data Steward User', '9117d59a69dc49807671a51f10ab7f', 'A');
  
insert into PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD) values ('ACT', 'shrine_steward', 'USER', 'A');
 
insert into PM_PROJECT_USER_ROLES (PROJECT_ID, USER_ID, USER_ROLE_CD, STATUS_CD) values ('ACT', 'shrine_steward', 'DATA_OBFSC', 'A');
 
insert into PM_USER_PARAMS (DATATYPE_CD, USER_ID, PARAM_NAME_CD, VALUE, CHANGEBY_CHAR, STATUS_CD) values ('T', 'shrine_steward', 'DataSteward', 'true', 'mhmcb', 'A');

--TODO:
update I2B2_SHRINE_WASHU_DEV.I2B2DATA.QT_QUERY_RESULT_TYPE
set USER_ROLE_CD = 'DATA_OBFSC'
where USER_ROLE_CD = 'DATA_LDS';