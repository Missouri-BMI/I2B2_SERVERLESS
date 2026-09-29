-- Adapter tables --

/* 700 max length of i2b2 ontology term path */
create table ADAPTER_MAPPING (
    SHRINE_KEY RAW(700) not null,
    ADAPTER_KEY RAW(700) not null
);

create table ADAPTER_MAPPING_META (
    FILENAME varchar2(255) not null,
    FILE_LAST_MODIFIED number not null,
    LOADED_FROM_FILE number not null,
    CHECKSUM number not null
);

create index IX_ADAPTER_MAPPING_LOOKUP on ADAPTER_MAPPING(rawtohex(SHRINE_KEY));

-- Working tables in ADAPTER
create table SHRINE_QUERY(
  ID number not null,
  LOCAL_ID varchar2(256) not null,
  NETWORK_ID number not null,
  USERNAME varchar2(256) not null,
  DOMAIN varchar2(256) not null,
  QUERY_NAME varchar2(256) not null,
  QUERY_EXPRESSION clob,
  DATE_CREATED timestamp default current_timestamp,
  HAS_BEEN_RUN number(1) default 0,
  QUERY_XML clob,
  constraint SHRINE_QUERY_ID_PK primary key(ID) using index
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence SHRINE_QUERY_ID start with 1 increment by 1;
create or replace trigger SHRINE_QUERY_ID_INSERT
before insert on SHRINE_QUERY
for each row
declare
    max_id number;
    cur_seq number;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select SHRINE_QUERY_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(ID),0), :new.id) into max_id from SHRINE_QUERY;
        select SHRINE_QUERY_ID.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select SHRINE_QUERY_ID.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
-- end autoincrement

create table ADAPTER_QUERY_RESULT (
  ID number not null,
  LOCAL_ID varchar2(256) not null,
  QUERY_ID number not null,
  TYPE varchar(100) not null,
  STATUS varchar2(30) not null,
  TIME_ELAPSED number null,
  LAST_UPDATED timestamp default current_timestamp,
  constraint QUERY_RESULT_ID_PK primary key(ID),
  constraint FK_QUERY_RESULT_QUERY_ID foreign key (QUERY_ID) references SHRINE_QUERY (ID) on delete cascade
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence QUERY_RESULT_ID start with 1 increment by 1;
create or replace trigger QUERY_RESULT_ID_INSERT
before insert on ADAPTER_QUERY_RESULT
for each row
declare
    max_id number;
    cur_seq number;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select QUERY_RESULT_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(ID),0), :new.id) into max_id from ADAPTER_QUERY_RESULT;
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
  ID number not null,
  RESULT_ID number not null,
  MESSAGE varchar2(256) not null,
  CODEC varchar2(256) default 'Pre-1.20 Error',
  STAMP varchar2(256) default 'Unknown time and machine',
  SUMMARY clob not null,
  PROBLEM_DESCRIPTION clob not null,
  DETAILS clob not null,
  constraint ERROR_RESULT_ID_PK primary key(ID),
  constraint FK_ERROR_RESULT_QR_ID foreign key (RESULT_ID) references ADAPTER_QUERY_RESULT (ID) on delete cascade
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence ERROR_RESULT_ID start with 1 increment by 1;
create or replace trigger ERROR_RESULT_ID_INSERT
before insert on ERROR_RESULT
for each row
declare
    max_id number;
    cur_seq number;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select ERROR_RESULT_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(ID),0), :new.id) into max_id from ERROR_RESULT;
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
  ID number not null,
  RESULT_ID number not null,
  OBFUSCATED_COUNT number not null,
  DATE_CREATED timestamp default current_timestamp,
  constraint COUNT_RESULT_ID_PK primary key(ID),
  constraint FK_COUNT_RESULT_QR_id foreign key (RESULT_ID) references ADAPTER_QUERY_RESULT (ID) on delete cascade
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence COUNT_RESULT_ID start with 1 increment by 1;
create or replace trigger COUNT_RESULT_ID_INSERT
before insert on COUNT_RESULT
for each row
declare
    max_id number;
    cur_seq number;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select COUNT_RESULT_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(ID),0), :new.id) into max_id from COUNT_RESULT;
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
  ID number not null,
  RESULT_ID number not null,
  DATA_KEY varchar2(256) null,
  OBFUSCATED_VALUE number not null,
  constraint BREAKDOWN_RESULT_ID_PK primary key(ID),
  constraint FK_BREAKDOWN_RESULT_QR_ID foreign key (RESULT_ID) references ADAPTER_QUERY_RESULT (ID) on delete cascade
);

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create sequence BREAKDOWN_RESULT_ID start with 1 increment by 1;
create or replace trigger BREAKDOWN_RESULT_ID_INSERT
before insert on BREAKDOWN_RESULT
for each row
declare
    max_id number;
    cur_seq number;
begin
    if :new.id is null then
        -- No ID passed, get one from the sequence
        select BREAKDOWN_RESULT_ID.nextval into :new.id from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max(ID),0), :new.id) into max_id from BREAKDOWN_RESULT;
        select BREAKDOWN_RESULT_ID.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select BREAKDOWN_RESULT_ID.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
-- end autoincrement
