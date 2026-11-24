create table verifiable_document (
    code varchar(26) primary key,
    document_type varchar(40) not null,
    source_id bigint not null,
    registered_at timestamp not null,
    constraint verifiable_document_document_type_enum check (document_type in (
        'GRADUATION_CERTIFICATE', 'REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE',
        'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE')),
    constraint uq_verifiable_document_source unique (document_type, source_id)
);

insert into verifiable_document (code, document_type, source_id, registered_at)
select code, 'GRADUATION_CERTIFICATE', id, issued_at from graduation_certificate;

insert into verifiable_document (code, document_type, source_id, registered_at)
select code, 'REGULAR_STUDENT_CERTIFICATE', id, issued_at from regular_student_certificate;
