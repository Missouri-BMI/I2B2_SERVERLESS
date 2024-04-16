/* Audit db tables in adapterAuditDB */
use adapterAuditDB;

create table `queriesReceived` (`shrineNodeId` TEXT NOT NULL,`userName` TEXT NOT NULL,`networkQueryId` BIGINT NOT NULL,`queryName` TEXT NOT NULL,`topicId` TEXT,`topicName` TEXT,`timeQuerySent` BIGINT NOT NULL,`timeReceived` BIGINT NOT NULL, `userDomainName` TEXT);
create table `executionsStarted` (`networkQueryId` BIGINT NOT NULL,`queryName` TEXT NOT NULL,`timeExecutionStarted` BIGINT NOT NULL);
create table `executionsCompleted` (`networkQueryId` BIGINT NOT NULL,`replyId` BIGINT NOT NULL,`queryName` TEXT NOT NULL,`timeExecutionCompleted` BIGINT NOT NULL);
create table `resultsSent` (`networkQueryId` BIGINT NOT NULL,`replyId` BIGINT NOT NULL,`queryName` TEXT NOT NULL,`timeResultsSent` BIGINT NOT NULL);

create table `ADAPTER_MAPPING` (`SHRINE_KEY` VARCHAR(700) NOT NULL,`ADAPTER_KEY` VARCHAR(700) NOT NULL); -- 700 max length of i2b2 ontology term path
create table `ADAPTER_MAPPING_META` (`FILENAME` VARCHAR(255) NOT NULL,`FILE_LAST_MODIFIED` BIGINT NOT NULL,`LOADED_FROM_FILE` BIGINT NOT NULL,`CHECKSUM` BIGINT NOT NULL);
create index `IX_ADAPTER_MAPPING_LOOKUP` on `ADAPTER_MAPPING`(`SHRINE_KEY`);
