alter table certificate_exception_approval add column created_by_director boolean not null default false;

alter table certificate_exception_approval drop constraint ck_certificate_exception_decider_not_requester;
alter table certificate_exception_approval add constraint ck_certificate_exception_decider_not_requester
    check (created_by_director or decided_by_id <> requested_by_id);
alter table certificate_exception_approval add constraint ck_certificate_exception_director_creation
    check (not created_by_director or (decided_by_id = requested_by_id and status <> 'PENDING'));

alter table certificate_exception_status_change drop constraint certificate_exception_status_change_event_enum;
alter table certificate_exception_status_change add constraint certificate_exception_status_change_event_enum
    check (event in ('APPROVE', 'CONSUME', 'DIRECT_APPROVE', 'EXPIRE', 'REJECT', 'REQUEST', 'WITHDRAW'));

do $$
declare
    action_literals text;
    entity_type_literals text;
    notification_literals text;
begin
    select string_agg(distinct quote_literal(m[1]), ', ') into action_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'ck_audit_log_action' and c.conrelid = 'audit_log'::regclass;
    select string_agg(distinct quote_literal(m[1]), ', ') into entity_type_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'ck_audit_log_entity_type' and c.conrelid = 'audit_log'::regclass;
    select string_agg(distinct quote_literal(m[1]), ', ') into notification_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'notification_type_enum' and c.conrelid = 'notification'::regclass;
    execute 'alter table audit_log drop constraint ck_audit_log_action';
    execute 'alter table audit_log add constraint ck_audit_log_action check (action in (' || action_literals
        || ', ''CERTIFICATE_EXCEPTION_DIRECT_CREATED'')) not valid';
    execute 'alter table audit_log drop constraint ck_audit_log_entity_type';
    execute 'alter table audit_log add constraint ck_audit_log_entity_type check (entity_type in (' || entity_type_literals
        || ', ''CERTIFICATE_EXCEPTION'')) not valid';
    execute 'alter table notification drop constraint notification_type_enum';
    execute 'alter table notification add constraint notification_type_enum check (type in (' || notification_literals
        || ', ''CERTIFICATE_EXCEPTION_DIRECT_CREATED'')) not valid';
end $$;
