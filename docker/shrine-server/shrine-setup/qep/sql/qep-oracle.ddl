-- Query-entry-point tables --

create table "QUERY_SENT" (
    "SHRINE_NODE_ID" varchar2(256) not null,
    "USER_NAME" varchar2(256) not null,
    "NETWORK_QUERY_ID" number not null,
    "QUERY_NAME" varchar2(256) not null,
    "TIME_QUERY_SENT" number not null
);

create table "PREVIOUS_QUERY" (
    "NETWORK_ID" number(19) not null,
    "USER_NAME" varchar(256) not null,
    "DOMAIN" varchar(256) not null,
    "QUERY_NAME" varchar(256) not null,
    "QUERY_NOTES" varchar(256),
    "QUERY_FAVED" CHAR not null check ("QUERY_FAVED" in ('1', '0')),
    "DATE_CREATED" number(19) not null,
    "DELETED" CHAR not null check ("DELETED" in ('1', '0')),
    "CHANGE_DATE" number(19) not null,
    "STATUS" varchar2(256) not null,
    "QUERY_JSON" CLOB not null
);

create table "QEP_QUERY_RESULT" (
    "RESULT_ID" number(19) not null,
    "NETWORK_QUERY_ID" number(19) not null,
    "INSTANCE_ID" number(19) not null,
    "ADAPTER_NODE" varchar(256) not null,
    "RESULT_TYPE" varchar(256),
    "SIZE" number(19) not null,
    "START_DATE" number(19),
    "END_DATE" number(19),
    "STATUS" varchar(256) not null,
    "STATUS_MESSAGE" CLOB,
    "METADATA_JSON" CLOB not null,
    "CHANGE_DATE" number(19) not null
);

create table "QUERY_BREAKDOWN_RESULT" (
    "NETWORK_QUERY_ID" number(19) not null,
    "ADAPTER_NODE" varchar(256) not null,
    "RESULT_ID" number(19) not null,
    "RESULT_TYPE" varchar(256) not null,
    "DATA_KEY" varchar2(2000) null,
    "VALUE" number(19) not null,
    "CHANGE_DATE" number(19) not null
);

create table "QUERY_RESULT_PROBLEM_DIGEST" (
    "NETWORK_QUERY_ID" number(19) not null,
    "ADAPTER_NODE" varchar(256) not null,
    "CODEC" varchar(256) not null,
    "STAMP" varchar(256) not null,
    "SUMMARY" CLOB not null,
    "DESCRIPTION" CLOB not null,
    "DETAILS" CLOB not null,
    "CHANGE_DATE" number(19) not null
);

create table "RESULTS_OBSERVED" (
    "NETWORK_QUERY_ID" number(19) not null,
    "CHECKSUM" number(19) not null,
    "OBSERVED_TIME" number(19) not null
);
create table "QUERY_PROBLEM_DIGEST" (
    "NETWORK_QUERY_ID" number(19) not null,
    "CODEC" varchar(256) not null,
    "STAMP" varchar(256) not null,
    "SUMMARY" CLOB not null,
    "DESCRIPTION" CLOB not null,
    "DETAILS" CLOB not null,
    "CHANGE_DATE" number(19) not null
);

create index "QUERY_RESULTS_CHANGE_DATE_INDEX" on "QEP_QUERY_RESULT" ("CHANGE_DATE");
create index "QUERY_RESULTS_NETWORK_ID_INDEX" on "QEP_QUERY_RESULT" ("NETWORK_QUERY_ID");
create index "QUERY_RESULTS_ADAPTER_NODE_INDEX" on "QEP_QUERY_RESULT" ("ADAPTER_NODE");
create index "QUERY_RESULTS_RESULT_ID_INDEX" on "QEP_QUERY_RESULT" ("RESULT_ID");
create index "QUERY_RESULTS_STATUS_INDEX" on "QEP_QUERY_RESULT" ("STATUS");

create index "QUERY_BREAKDOWN_CHANGE_DATE_INDEX" on "QUERY_BREAKDOWN_RESULT" ("CHANGE_DATE");
create index "QUERY_BREAKDOWN_QUERY_ID_INDEX" on "QUERY_BREAKDOWN_RESULT" ("NETWORK_QUERY_ID");
create index "QUERY_BREAKDOWN_RESULT_ID_INDEX" on "QUERY_BREAKDOWN_RESULT" ("RESULT_ID");

create index "PREVIOUS_QUERIES_QUERY_FAVED_INDEX" on "PREVIOUS_QUERY" ("QUERY_FAVED");
create index "PREVIOUS_QUERIES_USER_NAME_INDEX" on "PREVIOUS_QUERY" ("USER_NAME");
create index "PREVIOUS_QUERIES_DOMAIN_INDEX" on "PREVIOUS_QUERY" ("DOMAIN");
create index "PREVIOUS_QUERIES_CHANGE_DATE_INDEX" on "PREVIOUS_QUERY" ("CHANGE_DATE");
create index "PREVIOUS_QUERIES_DELETED_INDEX" on "PREVIOUS_QUERY" ("DELETED");
create index "PREVIOUS_QUERIES_NETWORK_ID_INDEX" on "PREVIOUS_QUERY" ("NETWORK_ID");
create index "PREVIOUS_QUERIES_DCREATED_INDEX" on "PREVIOUS_QUERY" ("DATE_CREATED");

create index "PROBLEMS_NETWORK_ID_INDEX" on "QUERY_RESULT_PROBLEM_DIGEST" ("NETWORK_QUERY_ID");
create index "PROBLEMS_ADAPT_ER_NODE_INDEX" on "QUERY_RESULT_PROBLEM_DIGEST" ("ADAPTER_NODE");
create index "PROBLEMS_CHANGE_DATE_INDEX" on "QUERY_RESULT_PROBLEM_DIGEST" ("CHANGE_DATE");

create index "RESULTS_OBSERVED_QUERY_ID_INDEX" on "RESULTS_OBSERVED" ("NETWORK_QUERY_ID");
create index "RESULTS_OBSERVED_CHECKSUM_INDEX" on "RESULTS_OBSERVED" ("CHECKSUM");

create index "QUERY_PROBLEMS_NETWORK_ID_INDEX" on "QUERY_PROBLEM_DIGEST" ("NETWORK_QUERY_ID");
