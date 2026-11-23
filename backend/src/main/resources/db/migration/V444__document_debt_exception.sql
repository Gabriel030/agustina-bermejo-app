create table document_debt_exception (
    id bigserial primary key,
    version bigint not null default 0,
    student_id bigint not null references student (id),
    document_type varchar(40) not null,
    enrollment_id bigint,
    registration_id bigint,
    reason varchar(500) not null,
    status varchar(20) not null,
    issued_by_id bigint not null references person (id),
    issued_at timestamp not null,
    expires_at timestamp not null,
    used_at timestamp,
    used_by_id bigint references person (id),
    constraint document_debt_exception_status_enum check (status in ('ACTIVE', 'USED', 'EXPIRED')),
    constraint document_debt_exception_document_type_enum check (document_type in ('GRADUATION_CERTIFICATE', 'REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE')),
    constraint document_debt_exception_expiry_after_issue check (expires_at > issued_at),
    constraint document_debt_exception_used_consistency check ((status = 'USED') = (used_at is not null))
);

create index ix_document_debt_exception_active_lookup
    on document_debt_exception (student_id, document_type, expires_at)
    where status = 'ACTIVE';
create index ix_document_debt_exception_due on document_debt_exception (expires_at) where status = 'ACTIVE';
create index ix_document_debt_exception_issued_at on document_debt_exception (issued_at desc);

insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('document.debt-exception.issue', 'document', 'Emitir por excepción una constancia o certificado a un alumno con deuda: motivo obligatorio, vigencia de 1 a 90 días y uso único', false, true);

insert into role_permission (role, permission_id)
select 'ADMINISTRATIVO', permission.id
from permission
where permission.code = 'document.debt-exception.issue';
