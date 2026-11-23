create table exam_registration_certificate (
    id bigserial primary key,
    version bigint not null default 0,
    code varchar(26) not null unique,
    student_id bigint not null references student(id),
    registration_id bigint not null references exam_board_registration(id),
    exam_board_id bigint not null references exam_board(id),
    status varchar(20) not null,
    issued_at timestamp not null,
    expires_at timestamp not null,
    issued_by_id bigint not null references person(id),
    recipient_type varchar(30),
    recipient_text varchar(80),
    registry_number varchar(20),
    snapshot jsonb not null,
    template_ref varchar(40) not null,
    pdf_sha256 varchar(64) not null,
    pdf_path varchar(300) not null,
    render_engine_version varchar(20),
    revoked_at timestamp,
    revoked_by_id bigint references person(id),
    revocation_reason varchar(500),
    constraint exam_registration_certificate_status_enum check (status in ('VALID', 'EXPIRED', 'REVOKED', 'REPLACED')),
    constraint exam_registration_certificate_recipient_type_enum check (recipient_type is null or recipient_type in ('HEALTH_INSURANCE', 'EMPLOYER', 'STUDENT_TRANSPORT', 'OTHER')),
    constraint ck_exam_registration_cert_recipient_pair check ((recipient_type is null) = (recipient_text is null))
);

create index ix_exam_registration_cert_student_issued on exam_registration_certificate (student_id, issued_at);
create index ix_exam_registration_cert_registration on exam_registration_certificate (registration_id, status);
create index ix_exam_registration_cert_board on exam_registration_certificate (exam_board_id);
create index ix_exam_registration_cert_issued_by on exam_registration_certificate (issued_by_id);
create index ix_exam_registration_cert_revoked_by on exam_registration_certificate (revoked_by_id) where revoked_by_id is not null;
