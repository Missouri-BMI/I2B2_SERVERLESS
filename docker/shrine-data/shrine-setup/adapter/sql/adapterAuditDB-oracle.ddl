/* Audit db tables in adapterAuditDB */
create table "queriesReceived" ("shrineNodeId" VARCHAR2(256) NOT NULL,"userName" VARCHAR2(256) NOT NULL,"networkQueryId" NUMBER NOT NULL,"queryName" VARCHAR2(256) NOT NULL,"topicId" VARCHAR2(256),"topicName" VARCHAR2(256),"timeQuerySent" NUMBER NOT NULL,"timeReceived" NUMBER NOT NULL, "userDomainName" VARCHAR2(256));
create table "executionsStarted" ("networkQueryId" NUMBER NOT NULL,"queryName" VARCHAR2(256) NOT NULL,"timeExecutionStarted" NUMBER NOT NULL);
create table "executionsCompleted" ("networkQueryId" NUMBER NOT NULL,"replyId" NUMBER NOT NULL,"queryName" VARCHAR2(256) NOT NULL,"timeExecutionCompleted" NUMBER NOT NULL);
create table "resultsSent" ("networkQueryId" NUMBER NOT NULL,"replyId" NUMBER NOT NULL,"queryName" VARCHAR2(256) NOT NULL,"timeResultsSent" NUMBER NOT NULL);

/* 700 max length of i2b2 ontology term path */
create table "ADAPTER_MAPPING" ("SHRINE_KEY" VARCHAR2(700) NOT NULL,"ADAPTER_KEY" VARCHAR2(700) NOT NULL);
create table "ADAPTER_MAPPING_META" ("FILENAME" VARCHAR2(255) NOT NULL,"FILE_LAST_MODIFIED" NUMBER NOT NULL,"LOADED_FROM_FILE" NUMBER NOT NULL,"CHECKSUM" NUMBER NOT NULL);
create index "IX_ADAPTER_MAPPING_LOOKUP" on "ADAPTER_MAPPING"("SHRINE_KEY");
