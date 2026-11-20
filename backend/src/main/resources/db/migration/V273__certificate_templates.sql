create table certificate_template (
    id bigserial primary key,
    kind varchar(20) not null,
    constraint certificate_template_kind_enum check (kind in ('COURSE', 'PROGRAM')),
    constraint uq_certificate_template_kind unique (kind)
);

create table certificate_template_image (
    id bigserial primary key,
    template_id bigint not null references certificate_template (id),
    purpose varchar(20) not null,
    content_type varchar(40) not null,
    size_bytes bigint not null,
    width_pixels integer not null,
    height_pixels integer not null,
    sha256 varchar(64) not null,
    content bytea not null,
    uploaded_at timestamp not null,
    uploaded_by_id bigint,
    constraint certificate_template_image_purpose_enum check (purpose in ('LOGO', 'SIGNATURE')),
    constraint ck_certificate_template_image_png check (content_type = 'image/png'),
    constraint ck_certificate_template_image_size check (size_bytes between 1 and 2097152 and size_bytes = octet_length(content)),
    constraint ck_certificate_template_image_dimensions check (width_pixels > 0 and height_pixels > 0)
);
create index idx_certificate_template_image_template on certificate_template_image (template_id);

create table certificate_template_version (
    id bigserial primary key,
    template_id bigint not null references certificate_template (id),
    version_number integer,
    status varchar(20) not null,
    based_on_version_number integer,
    institute_name varchar(120),
    logo_image_id bigint references certificate_template_image (id),
    title varchar(80),
    intro_text varchar(160),
    approval_text varchar(160),
    dictation_text varchar(160),
    qr_label varchar(160),
    footer_legend varchar(240),
    first_signer_name varchar(80),
    first_signer_role varchar(80),
    first_signer_signature_image_id bigint references certificate_template_image (id),
    second_signer_name varchar(80),
    second_signer_role varchar(80),
    second_signer_signature_image_id bigint references certificate_template_image (id),
    updated_at timestamp not null,
    published_at timestamp,
    published_by_id bigint,
    published_by_name varchar(160),
    constraint certificate_template_version_status_enum check (status in ('DRAFT', 'PUBLISHED')),
    constraint ck_certificate_template_version_state check (
        (status = 'DRAFT' and version_number is null and published_at is null and published_by_name is null)
        or (status = 'PUBLISHED' and version_number is not null and version_number >= 1 and published_at is not null and published_by_name is not null))
);
create unique index uq_certificate_template_version_number on certificate_template_version (template_id, version_number) where version_number is not null;
create unique index uq_certificate_template_single_draft on certificate_template_version (template_id) where status = 'DRAFT';

create function forbid_published_certificate_template_version_change() returns trigger language plpgsql as $$
begin
    if old.status = 'PUBLISHED' then
        raise exception 'published certificate template version % is immutable: % is not allowed', old.id, tg_op
            using errcode = 'integrity_constraint_violation';
    end if;
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

create trigger trg_certificate_template_version_published_immutable before update or delete on certificate_template_version
    for each row execute function forbid_published_certificate_template_version_change();

create function forbid_certificate_template_image_change() returns trigger language plpgsql as $$
begin
    raise exception 'certificate template image % is immutable: % is not allowed', old.id, tg_op
        using errcode = 'integrity_constraint_violation';
end;
$$;

create trigger trg_certificate_template_image_immutable before update or delete on certificate_template_image
    for each row execute function forbid_certificate_template_image_change();

insert into certificate_template (kind) values ('PROGRAM'), ('COURSE');

insert into certificate_template_version (template_id, version_number, status, institute_name, title, intro_text, approval_text,
                                          dictation_text, qr_label, footer_legend, first_signer_name, first_signer_role,
                                          second_signer_name, second_signer_role, updated_at, published_at, published_by_name)
select t.id, 1, 'PUBLISHED', 'INSTITUTO AGUSTINA BERMEJO', 'CERTIFICADO DE APROBACIÓN', 'Se certifica que',
       case t.kind when 'PROGRAM' then 'ha aprobado la Tecnicatura en' else 'ha aprobado la Capacitación en' end,
       'Dictada y evaluada por el Instituto Agustina Bermejo', 'QR DE VALIDACIÓN DE AUTENTICIDAD',
       'Certificado emitido por el Instituto Agustina Bermejo. Verificá su autenticidad escaneando el código QR.',
       'Dirección', 'Secretaria Legal', 'Dirección', 'Director Académico', now(), now(), 'Sistema'
from certificate_template t;
