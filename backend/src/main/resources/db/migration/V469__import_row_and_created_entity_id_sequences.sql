alter table import_row alter column id drop identity;
alter table import_created_entity alter column id drop identity;

create sequence import_row_seq increment by 50 minvalue 1 start with 1;
create sequence import_created_entity_seq increment by 50 minvalue 1 start with 1;

select setval('import_row_seq', coalesce((select max(id) from import_row), 0) + 1000);
select setval('import_created_entity_seq', coalesce((select max(id) from import_created_entity), 0) + 1000);

alter table import_row alter column id set default (nextval('import_row_seq') - 1);
alter table import_created_entity alter column id set default (nextval('import_created_entity_seq') - 1);

alter sequence import_row_seq owned by import_row.id;
alter sequence import_created_entity_seq owned by import_created_entity.id;
