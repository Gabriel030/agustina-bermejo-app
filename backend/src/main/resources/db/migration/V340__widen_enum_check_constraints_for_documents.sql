do $$
declare
    type_literals text;
    action_literals text;
    entity_type_literals text;
begin
    select string_agg(distinct quote_literal(m[1]), ', ') into type_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'notification_type_enum' and c.conrelid = 'notification'::regclass;
    select string_agg(distinct quote_literal(m[1]), ', ') into action_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'ck_audit_log_action' and c.conrelid = 'audit_log'::regclass;
    select string_agg(distinct quote_literal(m[1]), ', ') into entity_type_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'ck_audit_log_entity_type' and c.conrelid = 'audit_log'::regclass;
    execute 'alter table notification drop constraint notification_type_enum';
    execute 'alter table notification add constraint notification_type_enum check (type in (' || type_literals
        || ', ''EXAM_CERTIFICATE_ISSUED'')) not valid';
    execute 'alter table audit_log drop constraint ck_audit_log_action';
    execute 'alter table audit_log add constraint ck_audit_log_action check (action in (' || action_literals
        || ', ''DOCUMENT_PDF_DOWNLOADED'', ''DOCUMENT_PDF_HASH_MISMATCH'', ''DOCUMENT_TEMPLATE_PUBLISHED'', ''REGULAR_CERTIFICATE_ISSUED'', ''REGULAR_CERTIFICATE_REVOKED'''
        || ', ''APPROVED_SUBJECTS_CERT_ISSUED'', ''APPROVED_SUBJECTS_CERT_REVOKED'', ''EXAM_CERTIFICATE_ISSUED'', ''EXAM_CERTIFICATE_BATCH_ISSUED'''
        || ', ''EXAM_CERTIFICATE_REVOKED'', ''DOCUMENT_GENERATED_BY_STUDENT'', ''DOCUMENT_SETTINGS_CHANGED'')) not valid';
    execute 'alter table audit_log drop constraint ck_audit_log_entity_type';
    execute 'alter table audit_log add constraint ck_audit_log_entity_type check (entity_type in (' || entity_type_literals
        || ', ''CERTIFICATE_DOCUMENT'', ''CERTIFICATE_TEMPLATE'', ''EXAM_CERTIFICATE'')) not valid';
end $$;
