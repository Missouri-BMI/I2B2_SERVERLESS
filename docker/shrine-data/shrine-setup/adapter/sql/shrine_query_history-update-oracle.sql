ALTER TABLE QUERY_RESULT MODIFY TYPE varchar(100);

declare
c_name varchar2(255 char);
begin

select c.constraint_name into c_name
from all_constraints c
         join all_cons_columns cc
              on  cc.table_name = c.table_name
                  and cc.constraint_name = c.constraint_name
where
        cc.table_name = 'QUERY_RESULT'
  and cc.column_name ='TYPE'
  and c.constraint_type = 'C'
  and lower(c.search_condition_vc) like '%type in%';

if c_name is not null then
        execute immediate
            'alter table query_result drop constraint "' || c_name || '"';
end if;
end;
/

ALTER TABLE BREAKDOWN_RESULT MODIFY (DATA_KEY NULL);
