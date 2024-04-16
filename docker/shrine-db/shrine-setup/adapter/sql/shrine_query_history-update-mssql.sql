use shrine_query_history;
ALTER TABLE QUERY_RESULT ALTER COLUMN TYPE varchar(100) not null;

DECLARE @ConstraintName VARCHAR(256)
SET @ConstraintName = (
select  d.name
 from
     sys.tables t
     join sys.check_constraints d on d.parent_object_id = t.object_id
     join sys.columns c on c.object_id = t.object_id and c.column_id = d.parent_column_id
 where
     t.name = 'QUERY_RESULT'
     and c.name = 'TYPE'
    )
EXEC('ALTER TABLE QUERY_RESULT DROP CONSTRAINT ' + @ConstraintName);

ALTER TABLE BREAKDOWN_RESULT ALTER COLUMN DATA_KEY varchar(255) NULL;
