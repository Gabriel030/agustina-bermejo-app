alter table program_enrollment_application add column expired_at timestamp(6);

alter table program_enrollment_application add constraint ck_program_enrollment_application_status
    check (status in ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'));

alter table program_enrollment_application add constraint ck_program_enrollment_application_expired_at
    check ((status = 'EXPIRED') = (expired_at is not null));
