alter table broadcast add column completed_at timestamptz;

alter table broadcast_recipient add column anonymized_at timestamptz;

alter table broadcast_recipient drop constraint ck_broadcast_recipient_reason;
alter table broadcast_recipient add constraint ck_broadcast_recipient_reason
    check (status <> 'EXCLUDED' or exclusion_reason is not null or anonymized_at is not null);

alter table audit_log drop constraint ck_audit_log_action;
alter table audit_log add constraint ck_audit_log_action check (action in (
    'STAFF_WITHDRAWN', 'STAFF_REACTIVATED', 'STAFF_CREATED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
    'PASSWORD_CHANGED', 'STUDENT_WITHDRAWN', 'STUDENT_REACTIVATED', 'ENROLLMENT_WITHDRAWN_BY_STAFF',
    'ENROLLMENT_SECTION_CHANGED_BY_STAFF', 'DATA_IMPORT_CREATED', 'DATA_IMPORT_COMMITTED',
    'DATA_IMPORT_RETRIED', 'DATA_IMPORT_SEALED', 'DATA_IMPORT_ROLLED_BACK', 'DATA_IMPORT_ROLLBACK_BLOCKED',
    'DATA_IMPORT_REPORT_DOWNLOADED', 'CONTACT_CONSENT_GRANTED', 'CONTACT_CONSENT_REVOKED',
    'WAITLIST_PROMOTED_AUTOMATICALLY', 'WAITLIST_PROMOTED_BY_STAFF', 'WAITLIST_PRIORITIZED_BY_STAFF',
    'WAITLIST_REMOVED_BY_STAFF', 'WAITLIST_EXPIRED', 'BROADCAST_CONFIRMED', 'BROADCAST_CANCELLED',
    'BROADCAST_RECIPIENT_DATA_ANONYMIZED'));

create table audit_log_archive (
    id bigint primary key,
    occurred_at timestamp(6) not null,
    actor_person_id bigint,
    actor_role varchar(30),
    action varchar(40) not null,
    entity_type varchar(40) not null,
    entity_id bigint not null,
    before_value varchar(100),
    after_value varchar(100),
    reason varchar(500)
);

create index ix_audit_log_archive_occurred_at on audit_log_archive (occurred_at desc, id desc);
create index ix_audit_log_archive_entity on audit_log_archive (entity_type, entity_id, occurred_at desc);
