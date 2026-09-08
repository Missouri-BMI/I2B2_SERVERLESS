-- Query-entry-point tables --

create table QUERY_SENT (
    SHRINE_NODE_ID text not null,
    USER_NAME text not null,
    NETWORK_QUERY_ID bigint not null,
    QUERY_NAME text not null,
    TIME_QUERY_SENT bigint not null
);

create table PREVIOUS_QUERY (
    NETWORK_ID bigint not null,
    USER_NAME text not null,
    DOMAIN text not null,
    QUERY_NAME text not null,
    QUERY_NOTES text,
    QUERY_FAVED boolean not null,
    DATE_CREATED bigint not null,
    DELETED boolean not null,
    CHANGE_DATE bigint not null,
    STATUS varchar(255) not null ,
    QUERY_JSON text not null
);

create table QEP_QUERY_RESULT (
    RESULT_ID bigint not null,
    NETWORK_QUERY_ID bigint not null,
    INSTANCE_ID bigint not null,
    ADAPTER_NODE text not null,
    RESULT_TYPE text,
    SIZE bigint not null,
    START_DATE bigint,
    END_DATE bigint,
    STATUS text not null,
    STATUS_MESSAGE text,
    CHANGE_DATE bigint not null,
    METADATA_JSON text not null
);

create table QUERY_BREAKDOWN_RESULT (
    NETWORK_QUERY_ID bigint not null,
    ADAPTER_NODE text not null,
    RESULT_ID bigint not null,
    RESULT_TYPE text not null,
    DATA_KEY text null,
    VALUE bigint not null,
    CHANGE_DATE bigint not null
);

create table QUERY_RESULT_PROBLEM_DIGEST (
    NETWORK_QUERY_ID bigint not null,
    ADAPTER_NODE text not null,
    CODEC text not null,
    STAMP text not null,
    SUMMARY text not null,
    DESCRIPTION text not null,
    DETAILS text not null,
    CHANGE_DATE bigint not null
);

create table RESULTS_OBSERVED (
    NETWORK_QUERY_ID bigint not null,
    CHECKSUM bigint not null,
    OBSERVED_TIME bigint not null
);
create table QUERY_PROBLEM_DIGEST (
    NETWORK_QUERY_ID bigint not null,
    CODEC text not null,
    STAMP text not null,
    SUMMARY text not null,
    DESCRIPTION text not null,
    DETAILS text not null,
    CHANGE_DATE bigint not null
);

create index QUERY_RESULTS_CHANGE_DATE_INDEX on QEP_QUERY_RESULT (CHANGE_DATE);
create index QUERY_RESULTS_NETWORK_QUERY_ID_INDEX on QEP_QUERY_RESULT (NETWORK_QUERY_ID);
create index QUERY_RESULTS_ADAPTER_NODE_INDEX on QEP_QUERY_RESULT (ADAPTER_NODE(255));
create index QUERY_RESULTS_RESULT_ID_INDEX on QEP_QUERY_RESULT (RESULT_ID);
create index QUERY_RESULTS_STATUS_INDEX on QEP_QUERY_RESULT (STATUS(255));

create index QUERY_BREAKDOWN_RESULTS_CHANGE_DATE_INDEX on QUERY_BREAKDOWN_RESULT (CHANGE_DATE);
create index QUERY_BREAKDOWN_RESULTS_NETWORK_QUERY_ID_INDEX on QUERY_BREAKDOWN_RESULT (NETWORK_QUERY_ID);
create index QUERY_BREAKDOWN_RESULTS_ADAPTER_NODE_INDEX on QUERY_BREAKDOWN_RESULT (ADAPTER_NODE(255));
create index QUERY_BREAKDOWN_RESULTS_RESULT_ID_INDEX on QUERY_BREAKDOWN_RESULT (RESULT_ID);

create index PREVIOUS_QUERIES_QUERY_FAVED_INDEX on PREVIOUS_QUERY (QUERY_FAVED);
create index PREVIOUS_QUERIES_USER_NAME_INDEX on PREVIOUS_QUERY (USER_NAME(255));
create index PREVIOUS_QUERIES_DOMAIN_INDEX on PREVIOUS_QUERY (DOMAIN(255));
create index PREVIOUS_QUERIES_CHANGE_DATE_INDEX on PREVIOUS_QUERY (CHANGE_DATE);
create index PREVIOUS_QUERIES_DELETED_INDEX on PREVIOUS_QUERY (DELETED);
create index PREVIOUS_QUERIES_NETWORK_ID_INDEX on PREVIOUS_QUERY (NETWORK_ID);
create index PREVIOUS_QUERIES_DATE_CREATED_INDEX on PREVIOUS_QUERY (DATE_CREATED);

create index PROBLEMS_NETWORK_ID_INDEX on QUERY_RESULT_PROBLEM_DIGEST(NETWORK_QUERY_ID);
create index PROBLEMS_ADAPTER_NODE_INDEX on QUERY_RESULT_PROBLEM_DIGEST(ADAPTER_NODE(255));
create index PROBLEMS_CHANGE_DATE_INDEX on QUERY_RESULT_PROBLEM_DIGEST(CHANGE_DATE);

create index RESULTS_OBSERVED_QUERY_ID_INDEX on RESULTS_OBSERVED(NETWORK_QUERY_ID);
create index RESULTS_OBSERVED_CHECKSUM_INDEX on RESULTS_OBSERVED(CHECKSUM);

create index QUERY_PROBLEMS_NETWORK_ID_INDEX on QUERY_PROBLEM_DIGEST(NETWORK_QUERY_ID);
