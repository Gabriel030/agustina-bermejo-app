insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('academicstructure.manage_preceptor_assignments', 'academicstructure', 'Ver, asignar y quitar preceptores de una comisión de carrera o de curso', false, false);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'academicstructure.manage_preceptor_assignments')
) as grants (role, code)
join permission on permission.code = grants.code;

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
    execute 'alter table audit_log add constraint ck_audit_log_action check (action in (' || action_literals
        || ', ''PRECEPTOR_ASSIGNED'', ''PRECEPTOR_UNASSIGNED'')) not valid';
    execute 'alter table audit_log drop constraint ck_audit_log_entity_type';
    execute 'alter table audit_log add constraint ck_audit_log_entity_type check (entity_type in (' || entity_type_literals
        || ', ''PRECEPTOR_ASSIGNMENT'')) not valid';
end $$;

alter table audit_log validate constraint ck_audit_log_action;
alter table audit_log validate constraint ck_audit_log_entity_type;
