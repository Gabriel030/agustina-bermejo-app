alter table holiday add column scope varchar(20) not null default 'BOTH';

alter table holiday add constraint holiday_scope_enum check (scope in ('CLASSES', 'BILLING', 'BOTH')) not valid;
