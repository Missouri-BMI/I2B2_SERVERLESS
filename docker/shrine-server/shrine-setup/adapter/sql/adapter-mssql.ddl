-- Adapter tables --

create table ADAPTER_MAPPING (
    SHRINE_KEY varchar(700) not null,
    ADAPTER_KEY varchar(700) not null); -- 700 max length of i2b2 ontology term path

create table ADAPTER_MAPPING_META (
    FILENAME varchar(255) not null,
    FILE_LAST_MODIFIED bigint not null,
    LOADED_FROM_FILE bigint not null,
    CHECKSUM bigint not null);

create index IX_ADAPTER_MAPPING_LOOKUP on ADAPTER_MAPPING(SHRINE_KEY);

create table SHRINE_QUERY(
  ID int not null identity(1,1),
  LOCAL_ID varchar(255) not null,
  NETWORK_ID bigint not null,
  USERNAME varchar(255) not null,
  DOMAIN varchar(255) not null,
  QUERY_NAME varchar(255) not null,
  QUERY_EXPRESSION text,
  DATE_CREATED datetime default current_timestamp,
  HAS_BEEN_RUN bit not null default 0,
  constraint QUERY_ID_PK primary key clustered (ID asc),
  QUERY_XML text
);

create nonclustered index IX_SHRINE_QUERY_NETWORK_ID on dbo.SHRINE_QUERY (NETWORK_ID asc);
create nonclustered index IX_SHRINE_QUERY_LOCAL_ID on dbo.SHRINE_QUERY (LOCAL_ID asc);
create nonclustered index IX_SHRINE_QUERY_USERNAME_DOMAIN on dbo.SHRINE_QUERY (USERNAME, DOMAIN asc);

create table ADAPTER_QUERY_RESULT(
  ID int not null identity(1,1),
  LOCAL_ID varchar(255) not null,
  QUERY_ID int not null,
  TYPE varchar(100) not null,
  STATUS varchar(30) not null,
  TIME_ELAPSED int null,
  LAST_UPDATED datetime default current_timestamp,
  constraint QUERY_RESULT_ID_PK primary key(ID),
  constraint FK_QUERY_RESULT_QUERY_ID foreign key (QUERY_ID) references SHRINE_QUERY (ID) on delete cascade
);

create table ERROR_RESULT(
  ID int not null identity(1,1),
  RESULT_ID int not null,
  MESSAGE varchar(255) not null,
  CODEC text not null default 'Pre-1.20 Error',
  SUMMARY text not null default 'Pre-1.20 Error',
  PROBLEM_DESCRIPTION text not null default 'Pre-1.20 Error',
  DETAILS text not null default 'Pre-1.20 Error',
  STAMP text not null default 'Unknown time and machine',
  constraint ERROR_RESULT_ID_PK primary key(ID),
  constraint FK_ERROR_RESULT_QUERY_RESULT_ID foreign key (RESULT_ID) references ADAPTER_QUERY_RESULT (ID) on delete cascade
);

create table COUNT_RESULT(
  ID int not null identity(1,1),
  RESULT_ID int not null,
  OBFUSCATED_COUNT int not null,
  DATE_CREATED datetime default current_timestamp,
  constraint COUNT_RESULT_ID_PK primary key(ID),
  constraint FK_COUNT_RESULT_QUERY_RESULT_ID foreign key (RESULT_ID) references ADAPTER_QUERY_RESULT (ID) on delete cascade
);

create table BREAKDOWN_RESULT(
  ID int not null identity(1,1),
  RESULT_ID int not null,
  DATA_KEY varchar(255) null,
  OBFUSCATED_VALUE int not null,
  constraint BREAKDOWN_RESULT_ID_PK primary key(ID),
  constraint FK_BREAKDOWN_RESULT_QUERY_RESULT_ID foreign key (RESULT_ID) references ADAPTER_QUERY_RESULT (ID) on delete cascade
);