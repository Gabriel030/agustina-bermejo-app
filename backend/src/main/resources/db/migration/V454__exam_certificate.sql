create table exam_certificate (
    id bigserial primary key,
    version bigint not null default 0,
    code varchar(26) not null unique,
    registration_id bigint not null references exam_board_registration(id),
    exam_board_id bigint not null references exam_board(id),
    student_id bigint not null references student(id),
    status varchar(20) not null,
    origin varchar(20) not null,
    issued_at timestamp not null,
    expires_at timestamp not null,
    validity_days int not null,
    issued_by_id bigint not null references person(id),
    recipient_type varchar(30),
    recipient_text varchar(80),
    snapshot jsonb not null,
    template_ref varchar(40) not null,
    registry_number varchar(20),
    pdf_sha256 varchar(64) not null,
    render_engine_version varchar(20),
    pdf_path varchar(300) not null,
    revoked_at timestamp,
    revoked_by_id bigint references person(id),
    revocation_reason varchar(500),
    constraint exam_certificate_status_enum check (status in ('VALID', 'EXPIRED', 'REVOKED', 'REPLACED')),
    constraint exam_certificate_origin_enum check (origin in ('SELF_SERVICE', 'ISSUED_BY_TEACHER', 'ISSUED_BY_STAFF')),
    constraint exam_certificate_recipient_type_enum check (recipient_type is null or recipient_type in ('HEALTH_INSURANCE', 'EMPLOYER', 'STUDENT_TRANSPORT', 'OTHER'))
);

create unique index uq_exam_certificate_registration_valid on exam_certificate (registration_id) where status = 'VALID';
create index ix_exam_certificate_board on exam_certificate (exam_board_id);
create index ix_exam_certificate_student on exam_certificate (student_id);
create index ix_exam_certificate_registration on exam_certificate (registration_id);
