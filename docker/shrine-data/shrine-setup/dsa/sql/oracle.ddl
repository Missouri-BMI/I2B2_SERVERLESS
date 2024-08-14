create table "users" ("userName" VARCHAR(254) NOT NULL PRIMARY KEY,"fullName" VARCHAR(254) NOT NULL,"isSteward" NUMBER NOT NULL);
create table "topics" ("id" INTEGER NOT NULL,"name" VARCHAR(254) NOT NULL,"description" CLOB NOT NULL,"createdBy" VARCHAR(254) NOT NULL,"createDate" NUMBER NOT NULL,"state" VARCHAR(254) NOT NULL,"changedBy" VARCHAR(254) NOT NULL,"changeDate" NUMBER NOT NULL);
create index "changeDateIndex" on "topics" ("changeDate");
create index "changedByIndex" on "topics" ("changedBy");
create index "createDateIndex" on "topics" ("createDate");
create index "createdByIndex" on "topics" ("createdBy");
create index "idIndex" on "topics" ("id");
create index "stateIndex" on "topics" ("state");
create index "topicNameIndex" on "topics" ("name");

-- handcrafted autoincrement from http://earlruby.org/2009/01/creating-auto-increment-columns-in-oracle/
create table "queries" (
    "stewardId" NUMBER NOT NULL,
    "id" NUMBER NOT NULL,
    "name" VARCHAR(254) NOT NULL,
    "researcher" VARCHAR(254) NOT NULL,
    "topic" INTEGER,
    "queryContents" CLOB NOT NULL,
    "stewardResponse" VARCHAR(254) NOT NULL,
    "date" NUMBER NOT NULL,
    CONSTRAINT stewardIdPk PRIMARY KEY("stewardId") using index
    );
create sequence stewardIdSeq start with 1 increment by 1;

create or replace trigger stewardIdInsert
before insert on "queries"
for each row
declare
    max_id number;
    cur_seq number;
begin
    if :new."stewardId" is null then
        -- No ID passed, get one from the sequence
        select stewardIdSeq.nextval into :new."stewardId" from dual;
    else
        -- ID was set via insert, so update the sequence
        select greatest(nvl(max("stewardId"),0), :new."stewardId") into max_id from "queries";
        select stewardIdSeq.nextval into cur_seq from dual;
        while cur_seq < max_id
        loop
            select stewardIdSeq.nextval into cur_seq from dual;
        end loop;
    end if;
end;
/
-- end autoincrement

create index "dateIndex" on "queries" ("date");
create index "externalIdIndex" on "queries" ("id");
create index "queryNameIndex" on "queries" ("name");
create index "stewardResponseIndex" on "queries" ("stewardResponse");
create index "topicIdIndex" on "queries" ("topic");
create table "userTopic" ("researcher" VARCHAR(254) NOT NULL,"topicId" INTEGER NOT NULL,"state" VARCHAR(254) NOT NULL,"changedBy" VARCHAR(254) NOT NULL,"changeDate" NUMBER NOT NULL);
create unique index "researcherTopicIdIndex" on "userTopic" ("researcher","topicId");

create table "userAudit" ("researcher" VARCHAR(254) NOT NULL,"queryCount" INTEGER NOT NULL,"changeDate" NUMBER NOT NULL);