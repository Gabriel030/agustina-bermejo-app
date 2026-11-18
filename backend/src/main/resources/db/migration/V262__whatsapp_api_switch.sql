alter table institute_setting drop constraint institute_setting_code_enum;
alter table institute_setting add constraint institute_setting_code_enum check (code in ('BILLING_NOTIFICATIONS', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY', 'WHATSAPP_API'));

alter table institute_setting_change_log drop constraint institute_setting_change_log_code_enum;
alter table institute_setting_change_log add constraint institute_setting_change_log_code_enum check (code in ('BILLING_NOTIFICATIONS', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY', 'WHATSAPP_API'));

insert into institute_setting (code, enabled, version, updated_at) values ('WHATSAPP_API', false, 0, now())
on conflict (code) do nothing;

do $$
declare
    action_literals text;
    entity_type_literals text;
begin
    select string_agg(distinct quote_literal(m[1]), ', ') into action_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'ck_audit_log_action' and c.conrelid = 'audit_log'::regclass;
    select string_agg(distinct quote_literal(m[1]), ', ') into entity_type_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'ck_audit_log_entity_type' and c.conrelid = 'audit_log'::regclass;
    execute 'alter table audit_log drop constraint ck_audit_log_action';
    execute 'alter table audit_log add constraint ck_audit_log_action check (action in (' || action_literals || ', ''WHATSAPP_API_ENABLED'', ''WHATSAPP_API_DISABLED''))';
    execute 'alter table audit_log drop constraint ck_audit_log_entity_type';
    execute 'alter table audit_log add constraint ck_audit_log_entity_type check (entity_type in (' || entity_type_literals || ', ''INSTITUTE_SETTING''))';
end $$;
