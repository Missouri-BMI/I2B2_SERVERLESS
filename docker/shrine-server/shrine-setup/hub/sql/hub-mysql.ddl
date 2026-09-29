-- hub-related tables --

create table TABLE_VERSION (
  NAME varchar(256) not null primary key,
  VERSION int not null
);
create table HUB_QUERY_RESULT (
  ID bigint not null,
  PROTOCOL_VERSION int not null,
  ITEM_VERSION int not null,
  CREATE_DATE bigint not null,
  CHANGE_DATE bigint not null,
  JSON text not null,
  QUERY_ID bigint not null,
  NODE_ID bigint not null,
  STATUS text not null
);

create index RESULT_ID_INDEX on HUB_QUERY_RESULT (ID);
create index RESULT_ITEM_VERSION_INDEX on HUB_QUERY_RESULT (ITEM_VERSION);
create index RESULT_CREATE_DATE_INDEX on HUB_QUERY_RESULT (CREATE_DATE);
create index RESULT_CHANGE_DATE_INDEX on HUB_QUERY_RESULT (CHANGE_DATE);
create index RESULT_QUERY_ID_IN on HUB_QUERY_RESULT (QUERY_ID);
create index RESULT_NODE_ID_INDEX on HUB_QUERY_RESULT (NODE_ID);

create table QUERY (
  ID bigint not null,
  PROTOCOL_VERSION int not null,
  ITEM_VERSION int not null,
  CREATE_DATE bigint not null,
  CHANGE_DATE bigint not null,
  JSON text not null,
  RESEARCHER_ID bigint not null,
  STATUS text not null
);

create index QUERY_ID_INDEX on QUERY (ID);
create index QUERY_ITEM_VERSION_INDEX on QUERY (ITEM_VERSION);
create index QUERY_CREATE_DATE_INDEX on QUERY (CREATE_DATE);
create index QUERY_CHANGE_DATE_INDEX on QUERY (CHANGE_DATE);
create index QUERY_RESEARCHER_ID_INDEX on QUERY (RESEARCHER_ID);

create table NODE (
  ID bigint not null,
  PROTOCOL_VERSION int not null,
  ITEM_VERSION int not null,
  CREATE_DATE bigint not null,
  CHANGE_DATE bigint not null,
  JSON text not null,
  `KEY` text not null
);

create table NODE_SYSTEM_SPEC (
  ID bigint not null,
  PROTOCOL_VERSION int not null,
  ITEM_VERSION int not null,
  CREATE_DATE bigint not null,
  CHANGE_DATE bigint not null,
  JSON text not null,
  `KEY` text not null
);

create index NODE_ID_INDEX on NODE (ID);
create index NODE_ITEM_VERSION_INDEX on NODE (ITEM_VERSION);
create index NODE_CREATE_DATE_INDEX on NODE (CREATE_DATE);
create index NODE_CHANGE_DATE_INDEX on NODE (CHANGE_DATE);
-- todo can this be indexed in mysql? Do we have to move away from the text type? create index NODE_KEY_INDEX on NODE (key);

create table NETWORK (
  ID bigint not null,
  PROTOCOL_VERSION int not null,
  ITEM_VERSION int not null,
  CREATE_DATE bigint not null,
  CHANGE_DATE bigint not null,
  JSON text not null
);

create index NETWORK_ID_INDEX on NETWORK (ID);
create index NETWORK_ITEM_VERSION_INDEX on NETWORK (ITEM_VERSION);
create index NETWORK_CREATE_DATE_INDEX on NETWORK (CREATE_DATE);
create index NETWORK_CHANGE_DATE_INDEX on NETWORK (CHANGE_DATE);

create table RESEARCHER (
  ID bigint not null,
  PROTOCOL_VERSION int not null,
  ITEM_VERSION int not null,
  CREATE_DATE bigint not null,
  CHANGE_DATE bigint not null,
  JSON text not null
);

create index RESEARCHER_ID_INDEX on RESEARCHER (ID);
create index RESEARCHER_ITEM_VERSION_INDEX on RESEARCHER (ITEM_VERSION);
create index RESEARCHER_CREATE_DATE_INDEX on RESEARCHER (CREATE_DATE);
create index RESEARCHER_CHANGE_DATE_INDEX on RESEARCHER (CHANGE_DATE);