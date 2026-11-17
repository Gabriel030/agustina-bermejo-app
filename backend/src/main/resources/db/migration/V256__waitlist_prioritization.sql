alter table subject_section_enrollment add column waitlist_prioritized_at timestamp(6);
alter table course_section_enrollment add column waitlist_prioritized_at timestamp(6);
alter table course_enrollment add column waitlist_prioritized_at timestamp(6);

create index ix_subject_section_enrollment_waitlist on subject_section_enrollment (program_section_id, waitlist_prioritized_at, enrolled_at, id) where status = 'WAITLISTED';
create index ix_course_section_enrollment_waitlist on course_section_enrollment (course_section_id, waitlist_prioritized_at, enrolled_at, id) where status = 'WAITLISTED';
create index ix_course_enrollment_waitlist on course_enrollment (course_id, waitlist_prioritized_at, enrollment_date, id) where status = 'WAITLISTED';

alter table audit_log drop constraint ck_audit_log_action;

alter table audit_log add constraint ck_audit_log_action check (action in (
    'STAFF_WITHDRAWN', 'STAFF_REACTIVATED', 'STAFF_CREATED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
    'PASSWORD_CHANGED', 'STUDENT_WITHDRAWN', 'STUDENT_REACTIVATED', 'ENROLLMENT_WITHDRAWN_BY_STAFF',
    'ENROLLMENT_SECTION_CHANGED_BY_STAFF', 'DATA_IMPORT_CREATED', 'DATA_IMPORT_COMMITTED',
    'DATA_IMPORT_RETRIED', 'DATA_IMPORT_SEALED', 'DATA_IMPORT_ROLLED_BACK', 'DATA_IMPORT_ROLLBACK_BLOCKED',
    'DATA_IMPORT_REPORT_DOWNLOADED', 'CONTACT_CONSENT_GRANTED', 'CONTACT_CONSENT_REVOKED',
    'WAITLIST_PROMOTED_AUTOMATICALLY', 'WAITLIST_PROMOTED_BY_STAFF', 'WAITLIST_PRIORITIZED_BY_STAFF',
    'WAITLIST_REMOVED_BY_STAFF', 'WAITLIST_EXPIRED'));
