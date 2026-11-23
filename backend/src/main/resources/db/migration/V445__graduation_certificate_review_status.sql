alter table graduation_request add column certificate_review_status varchar(20) not null default 'NOT_REQUIRED';
alter table graduation_request add column certificate_reviewed_by_id bigint;
alter table graduation_request add column certificate_reviewed_at timestamp(6);

update graduation_request gr
set certificate_review_status = 'ENABLED'
where gr.status = 'APPROVED'
  and gr.decided_by_id is null
  and exists (
      select 1 from graduation_certificate gc
      where gc.program_enrollment_id = gr.program_enrollment_id
        and gc.status in ('VALID', 'REVOKED'));

update graduation_request
set certificate_review_status = 'PENDING_REVIEW'
where status = 'APPROVED'
  and decided_by_id is null
  and certificate_review_status = 'NOT_REQUIRED';

alter table graduation_request add constraint graduation_request_certificate_review_status_enum
    check (certificate_review_status in ('DISABLED', 'ENABLED', 'NOT_REQUIRED', 'PENDING_REVIEW')) not valid;
alter table graduation_request add constraint fk_graduation_request_certificate_reviewed_by
    foreign key (certificate_reviewed_by_id) references person (id) not valid;
alter table graduation_request add constraint ck_graduation_request_certificate_review_coherence
    check (
        (certificate_review_status in ('NOT_REQUIRED', 'PENDING_REVIEW')
            and certificate_reviewed_by_id is null and certificate_reviewed_at is null)
        or (certificate_review_status in ('ENABLED', 'DISABLED')
            and ((certificate_reviewed_by_id is null) = (certificate_reviewed_at is null)))) not valid;

create index ix_graduation_request_certificate_reviewed_by on graduation_request (certificate_reviewed_by_id)
    where certificate_reviewed_by_id is not null;
create index ix_graduation_request_certificate_review on graduation_request (certificate_review_status, id)
    where certificate_review_status <> 'NOT_REQUIRED';

insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('graduation.review_imported_certificate', 'graduation',
     'Habilitar o deshabilitar el certificado de egreso de un egreso importado. Solo el Director; no se puede delegar', true, true);

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
        || ', ''GRADUATION_CERTIFICATE_REVIEW_ENABLED'', ''GRADUATION_CERTIFICATE_REVIEW_DISABLED'')) not valid';
    execute 'alter table audit_log drop constraint ck_audit_log_entity_type';
    execute 'alter table audit_log add constraint ck_audit_log_entity_type check (entity_type in (' || entity_type_literals
        || ', ''GRADUATION_REQUEST'')) not valid';
end $$;
