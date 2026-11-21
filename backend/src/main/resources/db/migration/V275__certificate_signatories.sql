create table certificate_signatory (
    id bigserial primary key,
    name varchar(80) not null,
    role varchar(80) not null,
    active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint ck_certificate_signatory_text check (length(btrim(name)) > 0 and length(btrim(role)) > 0)
);

create table certificate_signatory_permission (
    signatory_id bigint not null references certificate_signatory (id),
    document_type varchar(40) not null,
    primary key (signatory_id, document_type),
    constraint ck_certificate_signatory_permission_type check (document_type in (
        'GRADUATION_APPROVAL', 'REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_CERTIFICATE'))
);

create table certificate_signatory_signature (
    id bigserial primary key,
    signatory_id bigint not null references certificate_signatory (id),
    content_type varchar(40) not null,
    size_bytes bigint not null,
    width_pixels integer not null,
    height_pixels integer not null,
    sha256 varchar(64) not null,
    content bytea not null,
    uploaded_at timestamp not null,
    uploaded_by_id bigint,
    constraint uq_certificate_signatory_signature_signatory unique (signatory_id),
    constraint ck_certificate_signatory_signature_png check (content_type = 'image/png'),
    constraint ck_certificate_signatory_signature_size check (size_bytes between 1 and 2097152 and size_bytes = octet_length(content)),
    constraint ck_certificate_signatory_signature_dimensions check (width_pixels > 0 and height_pixels > 0)
);

alter table certificate_template_version
    add column signatory1_id bigint references certificate_signatory (id),
    add column signatory2_id bigint references certificate_signatory (id),
    add constraint ck_certificate_template_version_signatories_distinct check (
        signatory1_id is null or signatory2_id is null or signatory1_id <> signatory2_id);
create index idx_certificate_template_version_signatory1 on certificate_template_version (signatory1_id) where signatory1_id is not null;
create index idx_certificate_template_version_signatory2 on certificate_template_version (signatory2_id) where signatory2_id is not null;

create temporary table tmp_signatory_pair on commit drop as
select name, role, image_id, version_id
from (
    select first_signer_name as name, first_signer_role as role, first_signer_signature_image_id as image_id, id as version_id
    from certificate_template_version
    where first_signer_name is not null and first_signer_role is not null
    union all
    select second_signer_name, second_signer_role, second_signer_signature_image_id, id
    from certificate_template_version
    where second_signer_name is not null and second_signer_role is not null
) pairs;

insert into certificate_signatory (name, role, active, created_at, updated_at)
select name, role, true, now(), now()
from (select distinct name, role from tmp_signatory_pair) distinct_pairs
order by role, name;

insert into certificate_signatory_permission (signatory_id, document_type)
select id, 'GRADUATION_APPROVAL' from certificate_signatory;

insert into certificate_signatory_signature (signatory_id, content_type, size_bytes, width_pixels, height_pixels, sha256, content, uploaded_at, uploaded_by_id)
select distinct on (s.id) s.id, i.content_type, i.size_bytes, i.width_pixels, i.height_pixels, i.sha256, i.content, now(), null
from tmp_signatory_pair p
join certificate_signatory s on s.name = p.name and s.role = p.role
join certificate_template_image i on i.id = p.image_id
order by s.id, p.version_id desc;

update certificate_template_version v
set signatory1_id = (select s.id from certificate_signatory s where s.name = v.first_signer_name and s.role = v.first_signer_role),
    signatory2_id = (select s.id from certificate_signatory s where s.name = v.second_signer_name and s.role = v.second_signer_role
                     and s.id is distinct from (select s1.id from certificate_signatory s1 where s1.name = v.first_signer_name and s1.role = v.first_signer_role))
where v.status = 'DRAFT';

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
        || ', ''CERTIFICATE_SIGNATORY_CREATED'', ''CERTIFICATE_SIGNATORY_UPDATED'', ''CERTIFICATE_SIGNATORY_PERMS_CHANGED'''
        || ', ''CERTIFICATE_SIGNATORY_DEACTIVATED'', ''CERTIFICATE_SIGNATORY_ACTIVATED'', ''CERTIFICATE_SIGNATORY_SIGNATURE_UPLOADED'''
        || ', ''CERTIFICATE_SIGNATORY_SIGNATURE_DELETED''))';
    execute 'alter table audit_log drop constraint ck_audit_log_entity_type';
    execute 'alter table audit_log add constraint ck_audit_log_entity_type check (entity_type in (' || entity_type_literals
        || ', ''CERTIFICATE_SIGNATORY''))';
end $$;
