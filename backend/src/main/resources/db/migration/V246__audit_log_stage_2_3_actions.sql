alter table audit_log drop constraint ck_audit_log_action;
alter table audit_log drop constraint ck_audit_log_entity_type;

alter table audit_log add constraint ck_audit_log_action check (action in (
    'STAFF_WITHDRAWN', 'STAFF_REACTIVATED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
    'PASSWORD_CHANGED', 'STUDENT_WITHDRAWN', 'STUDENT_REACTIVATED', 'ENROLLMENT_WITHDRAWN_BY_STAFF',
    'ENROLLMENT_SECTION_CHANGED_BY_STAFF', 'DATA_IMPORT_CREATED', 'DATA_IMPORT_COMMITTED',
    'DATA_IMPORT_RETRIED', 'DATA_IMPORT_SEALED', 'DATA_IMPORT_ROLLED_BACK', 'DATA_IMPORT_ROLLBACK_BLOCKED',
    'DATA_IMPORT_REPORT_DOWNLOADED'));

alter table audit_log add constraint ck_audit_log_entity_type check (entity_type in (
    'PERSON', 'STUDENT', 'SECTION_ENROLLMENT', 'COURSE_SECTION_ENROLLMENT', 'PROGRAM_ENROLLMENT',
    'COURSE_ENROLLMENT', 'DATA_IMPORT_BATCH'));
