alter table broadcast add column sent_total integer;
alter table broadcast add column failed_total integer;
alter table broadcast add column excluded_total integer;
alter table broadcast add column discarded_total integer;

alter table broadcast add constraint ck_broadcast_frozen_totals check (
    (sent_total is null and failed_total is null and excluded_total is null and discarded_total is null)
    or (sent_total >= 0 and failed_total >= 0 and excluded_total >= 0 and discarded_total >= 0));

alter table audit_log drop constraint ck_audit_log_action;
alter table audit_log add constraint ck_audit_log_action check (action in (
    'STAFF_WITHDRAWN', 'STAFF_REACTIVATED', 'STAFF_CREATED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
    'PASSWORD_CHANGED', 'STUDENT_WITHDRAWN', 'STUDENT_REACTIVATED', 'ENROLLMENT_WITHDRAWN_BY_STAFF',
    'ENROLLMENT_SECTION_CHANGED_BY_STAFF', 'DATA_IMPORT_CREATED', 'DATA_IMPORT_COMMITTED',
    'DATA_IMPORT_RETRIED', 'DATA_IMPORT_SEALED', 'DATA_IMPORT_ROLLED_BACK', 'DATA_IMPORT_ROLLBACK_BLOCKED',
    'DATA_IMPORT_REPORT_DOWNLOADED', 'CONTACT_CONSENT_GRANTED', 'CONTACT_CONSENT_REVOKED',
    'WAITLIST_PROMOTED_AUTOMATICALLY', 'WAITLIST_PROMOTED_BY_STAFF', 'WAITLIST_PRIORITIZED_BY_STAFF',
    'WAITLIST_REMOVED_BY_STAFF', 'WAITLIST_EXPIRED', 'BROADCAST_CONFIRMED', 'BROADCAST_CANCELLED',
    'BROADCAST_RECIPIENT_DATA_ANONYMIZED', 'BROADCAST_COMPLETED'));
