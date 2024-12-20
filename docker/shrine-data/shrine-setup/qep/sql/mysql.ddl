use qepAuditDB;

create table `queriesSent` (`shrineNodeId` TEXT NOT NULL,`userName` TEXT NOT NULL,`networkQueryId` BIGINT NOT NULL,`queryName` TEXT NOT NULL,`queryTopicId` TEXT,`queryTopicName` TEXT,`timeQuerySent` BIGINT NOT NULL);
create table `previousQueries` (`networkId` BIGINT NOT NULL,`userName` TEXT NOT NULL,`domain` TEXT NOT NULL,`queryName` TEXT NOT NULL,`expression` TEXT,`dateCreated` BIGINT NOT NULL,`deleted` BOOLEAN NOT NULL,`queryXml` TEXT NOT NULL,`changeDate` BIGINT NOT NULL);
create table `queryFlags` (`networkId` BIGINT NOT NULL,`flagged` BOOLEAN NOT NULL,`flagMessage` TEXT NOT NULL,`changeDate` BIGINT NOT NULL);
create table `queryResults` (`resultId` BIGINT NOT NULL,`networkQueryId` BIGINT NOT NULL,`instanceId` BIGINT NOT NULL,`adapterNode` TEXT NOT NULL,`resultType` TEXT,`size` BIGINT NOT NULL,`startDate` BIGINT,`endDate` BIGINT,`status` TEXT NOT NULL,`statusMessage` TEXT,`changeDate` BIGINT NOT NULL);
create table `queryBreakdownResults` (`networkQueryId` BIGINT NOT NULL,`adapterNode` TEXT NOT NULL,`resultId` BIGINT NOT NULL,`resultType` TEXT NOT NULL,`dataKey` TEXT NOT NULL,`value` BIGINT NOT NULL,`changeDate` BIGINT NOT NULL);
create table `queryResultProblemDigests` (`networkQueryId` BIGINT NOT NULL,`adapterNode` TEXT NOT NULL,`codec` TEXT NOT NULL,`stamp` TEXT NOT NULL,`summary` TEXT NOT NULL,`description` TEXT NOT NULL,`details` TEXT NOT NULL,`changeDate` BIGINT NOT NULL);
create table `RESULTS_OBSERVED` (`NETWORKQUERYID` BIGINT NOT NULL,`CHECKSUM` BIGINT NOT NULL,`OBSERVEDTIME` BIGINT NOT NULL);
create table `QUERY_PROBLEM_DIGESTS` (`NETWORKQUERYID` BIGINT NOT NULL,`CODEC` TEXT NOT NULL,`STAMP` TEXT NOT NULL,`SUMMARY` TEXT NOT NULL,`DESCRIPTION` TEXT NOT NULL,`DETAILS` TEXT NOT NULL,`CHANGEDATE` BIGINT NOT NULL);

create index queryResultsChangeDateIndex on queryResults (changeDate);
create index queryResultsNetworkQueryIdIndex on queryResults (networkQueryId);
create index queryResultsAdapterNodeIndex on queryResults (adapterNode(255));

create index queryBreakdownResultsChangeDateIndex on queryBreakdownResults (changeDate);
create index queryBreakdownResultsNetworkQueryIdIndex on queryBreakdownResults (networkQueryId);
create index queryBreakdownResultsAdapterNodeIndex on queryBreakdownResults (adapterNode(255));
create index queryBreakdownResultsResultIdIndex on queryBreakdownResults (resultId);

create index queryFlagsChangeDateIndex on queryFlags (changeDate);
create index queryFlagsNetworkQueryIdIndex on queryFlags (networkId);

create index previousQueriesUserNameIndex on previousQueries (userName(255));
create index previousQueriesDomainIndex on previousQueries (domain(255));
create index previousQueriesChangeDateIndex on previousQueries (changeDate);
create index previousQueriesDeletedIndex on previousQueries (deleted);
create index previousQueriesNetworkIdIndex on previousQueries (networkId);
create index previousQueriesDateCreatedIndex on previousQueries (dateCreated);

create index problemsNetworkIdIndex on queryResultProblemDigests(networkQueryId);
create index problemsAdapterNodeIndex on queryResultProblemDigests(adapterNode(255));
create index problemsChangeDateIndex on queryResultProblemDigests(changeDate);

create index resultsObservedQueryIdIndex on RESULTS_OBSERVED(NETWORKQUERYID);
create index resultsObservedChecksumIndex on RESULTS_OBSERVED(CHECKSUM);

create index queryProblemsNetworkIdIndex on QUERY_PROBLEM_DIGESTS(NETWORKQUERYID);

alter table `previousQueries` add column `status` VARCHAR(255) NOT NULL DEFAULT 'Before V26';

create index queryResultsResultIdIndex on queryResults (resultId);
create index queryResultsStatusIndex on queryResults (status(255));
