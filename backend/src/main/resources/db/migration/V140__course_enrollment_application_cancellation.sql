alter table course_enrollment_application add column cancelled_at timestamp(6);

alter table course_enrollment_application add constraint ck_course_enrollment_application_cancelled_not_enrolled
    check (cancelled_at is null or course_enrollment_id is null);

create index ix_course_enrollment_application_pending_invoice
    on course_enrollment_application (invoice_id)
    where course_enrollment_id is null and cancelled_at is null;
