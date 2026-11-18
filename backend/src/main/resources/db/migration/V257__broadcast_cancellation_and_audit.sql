alter table broadcast add column cancelled_by_person_id bigint references person (id);
alter table broadcast add column cancelled_at timestamptz;
alter table broadcast add column cancellation_reason varchar(500);

alter table audit_log drop constraint ck_audit_log_action;
alter table audit_log drop constraint ck_audit_log_entity_type;

alter table audit_log add constraint ck_audit_log_action check (action in (
    'STAFF_WITHDRAWN', 'STAFF_REACTIVATED', 'STAFF_CREATED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
    'PASSWORD_CHANGED', 'STUDENT_WITHDRAWN', 'STUDENT_REACTIVATED', 'ENROLLMENT_WITHDRAWN_BY_STAFF',
    'ENROLLMENT_SECTION_CHANGED_BY_STAFF', 'DATA_IMPORT_CREATED', 'DATA_IMPORT_COMMITTED',
    'DATA_IMPORT_RETRIED', 'DATA_IMPORT_SEALED', 'DATA_IMPORT_ROLLED_BACK', 'DATA_IMPORT_ROLLBACK_BLOCKED',
    'DATA_IMPORT_REPORT_DOWNLOADED', 'CONTACT_CONSENT_GRANTED', 'CONTACT_CONSENT_REVOKED',
    'WAITLIST_PROMOTED_AUTOMATICALLY', 'WAITLIST_PROMOTED_BY_STAFF', 'WAITLIST_PRIORITIZED_BY_STAFF',
    'WAITLIST_REMOVED_BY_STAFF', 'WAITLIST_EXPIRED', 'BROADCAST_CONFIRMED', 'BROADCAST_CANCELLED'));

alter table audit_log add constraint ck_audit_log_entity_type check (entity_type in (
    'PERSON', 'STUDENT', 'SECTION_ENROLLMENT', 'COURSE_SECTION_ENROLLMENT', 'PROGRAM_ENROLLMENT',
    'COURSE_ENROLLMENT', 'DATA_IMPORT_BATCH', 'BROADCAST'));
