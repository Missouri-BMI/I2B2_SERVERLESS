-- Working tables in shrine_query_history
create table SHRINE_QUERY(
  id NUMBER NOT NULL,
  local_id VARCHAR2(256) not null,
  network_id NUMBER not null,
  username VARCHAR2(256) not null,
  domain VARCHAR2(256) not null,
  query_name VARCHAR2(256) not null,
  query_expression CLOB,
  date_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  has_been_run NUMBER(1) DEFAULT 0,
  flagged NUMBER(1) DEFAULT 0,
  flag_message CLOB null,
  query_xml CLOB,
  CONSTRAINT SHRINE_QUERY_id_pk PRIMARY KEY(id) using index
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence SHRINE_QUERY_ID start with 1 increment by 1;
create or replace trigger SHRINE_QUERY_id_Insert
before insert on SHRINE_QUERY
for each row
declare
    max_id NUMBER;
    cur_seq NUMBER;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select SHRINE_QUERY_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(id),0), :new.id) into max_id from SHRINE_QUERY;
        select SHRINE_QUERY_ID.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select SHRINE_QUERY_ID.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
-- end autoincrement

create table QUERY_RESULT (
  id NUMBER not null,
  local_id VARCHAR2(256) not null,
  query_id NUMBER not null,
  type VARCHAR2(30) CHECK (type IN ('PATIENTSET','PATIENT_COUNT_XML','PATIENT_AGE_COUNT_XML','PATIENT_RACE_COUNT_XML','PATIENT_VITALSTATUS_COUNT_XML','PATIENT_GENDER_COUNT_XML','ERROR')) not null,
  status VARCHAR2(30) not null,
  time_elapsed NUMBER null,
  last_updated timestamp default current_timestamp,
  constraint QUERY_RESULT_id_pk primary key(id),
  constraint fk_QUERY_RESULT_query_id foreign key (query_id) references SHRINE_QUERY (id) on delete cascade
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence QUERY_RESULT_ID start with 1 increment by 1;
create or replace trigger QUERY_RESULT_id_Insert
before insert on QUERY_RESULT
for each row
declare
    max_id NUMBER;
    cur_seq NUMBER;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select QUERY_RESULT_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(id),0), :new.id) into max_id from QUERY_RESULT;
        select QUERY_RESULT_ID.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select QUERY_RESULT_ID.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
-- end autoincrement


create table ERROR_RESULT(
  id NUMBER not null,
  result_id NUMBER not null,
  message VARCHAR2(256) not null,
  CODEC VARCHAR2(256) default 'Pre-1.20 Error',
  STAMP VARCHAR2(256) default 'Unknown time and machine',
  SUMMARY CLOB not null,
  PROBLEM_DESCRIPTION CLOB not null,
  DETAILS CLOB not null,
  constraint ERROR_RESULT_id_pk primary key(id),
  constraint fk_ERROR_RESULT_QR_id foreign key (result_id) references QUERY_RESULT (id) on delete cascade
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence ERROR_RESULT_ID start with 1 increment by 1;
create or replace trigger ERROR_RESULT_id_Insert
before insert on ERROR_RESULT
for each row
declare
    max_id NUMBER;
    cur_seq NUMBER;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select ERROR_RESULT_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(id),0), :new.id) into max_id from ERROR_RESULT;
        select ERROR_RESULT_ID.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select ERROR_RESULT_ID.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
-- end autoincrement

create table COUNT_RESULT(
  id NUMBER not null,
  result_id NUMBER not null,
  obfuscated_count NUMBER not null,
  date_created timestamp default current_timestamp,
  constraint COUNT_RESULT_id_pk primary key(id),
  constraint fk_COUNT_RESULT_QR_id foreign key (result_id) references QUERY_RESULT (id) on delete cascade
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence COUNT_RESULT_ID start with 1 increment by 1;
create or replace trigger COUNT_RESULT_id_Insert
before insert on COUNT_RESULT
for each row
declare
    max_id NUMBER;
    cur_seq NUMBER;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select COUNT_RESULT_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(id),0), :new.id) into max_id from COUNT_RESULT;
        select COUNT_RESULT_ID.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select COUNT_RESULT_ID.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
-- end autoincrement

create table BREAKDOWN_RESULT(
  id NUMBER not null,
  result_id NUMBER not null,
  data_key VARCHAR2(256) not null,
  obfuscated_value NUMBER not null,
  constraint BREAKDOWN_RESULT_id_pk primary key(id),
  constraint fk_BREAKDOWN_RESULT_QR_id foreign key (result_id) references QUERY_RESULT (id) on delete cascade
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence BREAKDOWN_RESULT_ID start with 1 increment by 1;
create or replace trigger BREAKDOWN_RESULT_id_Insert
before insert on BREAKDOWN_RESULT
for each row
declare
    max_id NUMBER;
    cur_seq NUMBER;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select BREAKDOWN_RESULT_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(id),0), :new.id) into max_id from BREAKDOWN_RESULT;
        select BREAKDOWN_RESULT_ID.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select BREAKDOWN_RESULT_ID.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
-- end autoincrement




create table "problems" (
    "id" INTEGER NOT NULL PRIMARY KEY,
    "codec" VARCHAR2(254) NOT NULL,
    "stampText" VARCHAR2(500) NOT NULL,
    "summary" CLOB NOT NULL,
    "description" CLOB NOT NULL,
    "detailsXml" CLOB NOT NULL,
    "epoch" NUMBER(19) NOT NULL
);

create index idx_epoch on "problems" ("epoch");

create SEQUENCE problems_idautoinc start with 1 increment by 1;
create or REPLACE TRIGGER problems_triggerid_id 
before insert on "problems"
for each row
declare
    max_id NUMBER;
    cur_seq NUMBER;
begin
    if :new."id" is null then
        -- No ID passed, get one from the sequence
        select problems_idautoinc.nextval into :new."id" from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max("id"),0), :new."id") into max_id from "problems";
        select problems_idautoinc.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select problems_idautoinc.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
