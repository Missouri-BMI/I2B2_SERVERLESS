-- Query-entry-point tables --

create table QUERY_SENT (
    SHRINE_NODE_ID varchar(max) not null,
    USER_NAME varchar(max) not null,
    NETWORK_QUERY_ID bigint not null,
    QUERY_NAME varchar(max) not null,
    TIME_QUERY_SENT bigint not null
);

create table PREVIOUS_QUERY (
    NETWORK_ID bigint not null,
    USER_NAME varchar(max) not null,
    DOMAIN varchar(max) not null,
    QUERY_NAME varchar(max) not null,
    QUERY_NOTES varchar(max),
    QUERY_FAVED bit not null,
    DATE_CREATED bigint not null,
    DELETED bit not null,
    CHANGE_DATE bigint not null,
    STATUS varchar(max) not null,
    QUERY_JSON varchar(max) not null
);

create table QEP_QUERY_RESULT (
    RESULT_ID bigint not null,
    NETWORK_QUERY_ID bigint not null,
    INSTANCE_ID bigint not null,
    ADAPTER_NODE varchar(max) not null,
    RESULT_TYPE varchar(max),
    SIZE bigint not null,
    START_DATE bigint,
    END_DATE bigint,
    STATUS varchar(max) not null,
    STATUS_MESSAGE varchar(max),
    METADATA_JSON varchar(max) not null,
    CHANGE_DATE bigint not null
);

create table QUERY_BREAKDOWN_RESULT (
    NETWORK_QUERY_ID bigint not null,
    ADAPTER_NODE varchar(max) not null,
    RESULT_ID bigint not null,
    RESULT_TYPE varchar(max) not null,
    DATA_KEY varchar(max) null,
    VALUE bigint not null,
    CHANGE_DATE bigint not null
);

create table QUERY_RESULT_PROBLEM_DIGEST (
    NETWORK_QUERY_ID bigint not null,
    ADAPTER_NODE varchar(max) not null,
    CODEC varchar(max) not null,
    STAMP varchar(max) not null,
    SUMMARY varchar(max) not null,
    DESCRIPTION varchar(max) not null,
    DETAILS varchar(max) not null,
    CHANGE_DATE bigint not null
);

create table RESULTS_OBSERVED (
    NETWORK_QUERY_ID bigint not null,
    CHECKSUM bigint not null,
    OBSERVED_TIME bigint not null
);

create table QUERY_PROBLEM_DIGEST (
    NETWORK_QUERY_ID bigint not null,
    CODEC varchar(max) not null,
    STAMP varchar(max) not null,
    SUMMARY varchar(max) not null,
    DESCRIPTION varchar(max) not null,
    DETAILS varchar(max) not null,
    CHANGE_DATE bigint not null
);

create index QUERY_RESULTS_CHANGE_DATE_INDEX on QEP_QUERY_RESULT (CHANGE_DATE);
create index QUERY_RESULTS_NETWORK_QUERY_ID_INDEX on QEP_QUERY_RESULT (NETWORK_QUERY_ID);
create index QUERY_RESULTS_RESULT_ID_INDEX on QEP_QUERY_RESULT (RESULT_ID);

create index QUERY_BREAKDOWN_RESULTS_CHANGE_DATE_INDEX on QUERY_BREAKDOWN_RESULT (CHANGE_DATE);
create index QUERY_BREAKDOWN_RESULTS_NETWORK_QUERY_ID_INDEX on QUERY_BREAKDOWN_RESULT (NETWORK_QUERY_ID);
create index QUERY_BREAKDOWN_RESULTS_RESULT_ID_INDEX on QUERY_BREAKDOWN_RESULT (RESULT_ID);

create index PREVIOUS_QUERIES_QUERY_FAVED_INDEX on PREVIOUS_QUERY (QUERY_FAVED);
create index PREVIOUS_QUERIES_CHANGE_DATE_INDEX on PREVIOUS_QUERY (CHANGE_DATE);
create index PREVIOUS_QUERIES_DELETED_INDEX on PREVIOUS_QUERY (DELETED);
create index PREVIOUS_QUERIES_NETWORK_ID_INDEX on PREVIOUS_QUERY (NETWORK_ID);
create index PREVIOUS_QUERIES_DATE_CREATED_INDEX on PREVIOUS_QUERY (DATE_CREATED);

create index PROBLEMS_NETWORK_ID_INDEX on QUERY_RESULT_PROBLEM_DIGEST (NETWORK_QUERY_ID);
create index PROBLEMS_CHANGE_DATE_INDEX on QUERY_RESULT_PROBLEM_DIGEST (CHANGE_DATE);

create index RESULTS_OBSERVED_QUERY_ID_INDEX on RESULTS_OBSERVED (NETWORK_QUERY_ID);
create index RESULTS_OBSERVED_CHECKSUM_INDEX on RESULTS_OBSERVED (CHECKSUM);

create index QUERY_PROBLEMS_NETWORK_ID_INDEX on QUERY_PROBLEM_DIGEST (NETWORK_QUERY_ID);
