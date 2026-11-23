alter table certificate_signatory_permission drop constraint ck_certificate_signatory_permission_type;

alter table certificate_signatory_permission
    add constraint ck_certificate_signatory_permission_type check (document_type in (
        'GRADUATION_APPROVAL', 'REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE')) not valid;
