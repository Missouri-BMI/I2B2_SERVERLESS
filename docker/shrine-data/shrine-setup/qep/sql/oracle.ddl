create table "queriesSent" ("shrineNodeId" VARCHAR2(256) NOT NULL,"userName" VARCHAR2(256) NOT NULL,"networkQueryId" NUMBER NOT NULL,"queryName" VARCHAR2(256) NOT NULL,"queryTopicId" VARCHAR2(256),"queryTopicName" VARCHAR2(256),"timeQuerySent" NUMBER NOT NULL);
create table "previousQueries" ("networkId" NUMBER(19) NOT NULL,"userName" VARCHAR(256) NOT NULL,"domain" VARCHAR(256) NOT NULL,"queryName" VARCHAR(256) NOT NULL,"expression" CLOB,"dateCreated" NUMBER(19) NOT NULL,"deleted" CHAR NOT NULL check ("deleted" in ('1', '0')),"queryXml" CLOB NOT NULL,"changeDate" NUMBER(19) NOT NULL);
create table "queryFlags" ("networkId" NUMBER(19) NOT NULL,"flagged" CHAR NOT NULL check ("flagged" in ('1', '0')),"flagMessage" CLOB,"changeDate" NUMBER(19) NOT NULL);
create table "queryResults" ("resultId" NUMBER(19) NOT NULL,"networkQueryId" NUMBER(19) NOT NULL,"instanceId" NUMBER(19) NOT NULL,"adapterNode" VARCHAR(256) NOT NULL,"resultType" VARCHAR(256),"size" NUMBER(19) NOT NULL,"startDate" NUMBER(19),"endDate" NUMBER(19),"status" VARCHAR(256) NOT NULL,"statusMessage" CLOB,"changeDate" NUMBER(19) NOT NULL);
create table "queryBreakdownResults" ("networkQueryId" NUMBER(19) NOT NULL,"adapterNode" VARCHAR(256) NOT NULL,"resultId" NUMBER(19) NOT NULL,"resultType" VARCHAR(256) NOT NULL,"dataKey" VARCHAR(256) NOT NULL,"value" NUMBER(19) NOT NULL,"changeDate" NUMBER(19) NOT NULL);
create table "queryResultProblemDigests" ("networkQueryId" NUMBER(19) NOT NULL,"adapterNode" VARCHAR(256) NOT NULL,"codec" VARCHAR(256) NOT NULL,"stamp" VARCHAR(256) NOT NULL,"summary" CLOB NOT NULL,"description" CLOB NOT NULL,"details" CLOB NOT NULL,"changeDate" NUMBER(19) NOT NULL);
create table "RESULTS_OBSERVED" ("NETWORKQUERYID" NUMBER(19) NOT NULL,"CHECKSUM" NUMBER(19) NOT NULL,"OBSERVEDTIME" NUMBER(19) NOT NULL);
create table "QUERY_PROBLEM_DIGESTS" ("NETWORKQUERYID" NUMBER(19) NOT NULL,"CODEC" VARCHAR(256) NOT NULL,"STAMP" VARCHAR(256) NOT NULL,"SUMMARY" CLOB NOT NULL,"DESCRIPTION" CLOB NOT NULL,"DETAILS" CLOB NOT NULL,"CHANGEDATE" NUMBER(19) NOT NULL);

create index "queryResultsChangeDateIndex" on "queryResults" ("changeDate");
create index "queryResultsNetworkIdIndex" on "queryResults" ("networkQueryId");
create index "queryResultsAdapterNodeIndex" on "queryResults" ("adapterNode");

create index "queryBreakdownChangeDateIndex" on "queryBreakdownResults" ("changeDate");
create index "queryBreakdownQueryIdIndex" on "queryBreakdownResults" ("networkQueryId");
create index "queryBreakdownAdapterNodeIndex" on "queryBreakdownResults" ("adapterNode");
create index "queryBreakdownResultIdIndex" on "queryBreakdownResults" ("resultId");

create index "queryFlagsChangeDateIndex" on "queryFlags" ("changeDate");
create index "queryFlagsNetworkQueryIdIndex" on "queryFlags" ("networkId");

create index "previousQueriesUserNameIndex" on "previousQueries" ("userName");
create index "previousQueriesDomainIndex" on "previousQueries" ("domain");
create index "previousQueriesChangeDateIndex" on "previousQueries" ("changeDate");
create index "previousQueriesDeletedIndex" on "previousQueries" ("deleted");
create index "previousQueriesNetworkIdIndex" on "previousQueries" ("networkId");
create index "previousQueriesDCreatedIndex" on "previousQueries" ("dateCreated");

create index "problemsNetworkIdIndex" on "queryResultProblemDigests" ("networkQueryId");
create index "problemsAdapterNodeIndex" on "queryResultProblemDigests" ("adapterNode");
create index "problemsChangeDateIndex" on "queryResultProblemDigests" ("changeDate");

create index "resultsObservedQueryIdIndex" on "RESULTS_OBSERVED" ("NETWORKQUERYID");
create index "resultsObservedChecksumIndex" on "RESULTS_OBSERVED" ("CHECKSUM");

create index "queryProblemsNetworkIdIndex" on "QUERY_PROBLEM_DIGESTS" ("NETWORKQUERYID");

alter table "previousQueries" add "status" VARCHAR2(256) default 'Before V26' NOT NULL;

create index "queryResultsResultIdIndex" on "queryResults" ("resultId");
create index "queryResultsStatusIndex" on "queryResults" ("status");