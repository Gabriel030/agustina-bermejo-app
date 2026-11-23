create table approved_subjects_certificate (
    id bigserial primary key,
    version bigint not null default 0,
    code varchar(26) not null unique,
    program_enrollment_id bigint not null references program_enrollment(id),
    student_id bigint not null references student(id),
    status varchar(20) not null,
    issued_at timestamp not null,
    expires_at timestamp not null,
    validity_days int not null,
    issued_by_id bigint not null references person(id),
    recipient_type varchar(30),
    recipient_text varchar(80),
    snapshot jsonb not null,
    template_ref varchar(40) not null,
    render_engine_version varchar(20) not null,
    registry_number varchar(20),
    pdf_sha256 varchar(64) not null,
    revoked_at timestamp,
    revoked_by_id bigint references person(id),
    revocation_reason varchar(500),
    constraint approved_subjects_certificate_status_enum check (status in ('EXPIRED', 'REVOKED', 'VALID')),
    constraint approved_subjects_certificate_recipient_type_enum check (recipient_type in ('EMPLOYER', 'HEALTH_INSURANCE', 'OTHER', 'STUDENT_TRANSPORT')),
    constraint ck_approved_subjects_cert_recipient_pair check ((recipient_type is null) = (recipient_text is null)),
    constraint ck_approved_subjects_cert_recipient_text check (recipient_text is null or (char_length(recipient_text) <= 80 and recipient_text !~ E'[\\r\\n]'))
);

create index ix_approved_subjects_cert_student on approved_subjects_certificate (student_id);
create index ix_approved_subjects_cert_enrollment on approved_subjects_certificate (program_enrollment_id, issued_at);
create index ix_approved_subjects_cert_issued_by on approved_subjects_certificate (issued_by_id);
