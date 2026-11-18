alter table broadcast add column sent_total integer;
alter table broadcast add column failed_total integer;
alter table broadcast add column excluded_total integer;
alter table broadcast add column discarded_total integer;

alter table broadcast add column excluded_by_reason jsonb;

create function broadcast_reason_counts_sum(counts jsonb) returns integer
    language sql immutable parallel safe
    as $$ select coalesce(sum(value::integer), 0)::integer from jsonb_each_text(counts) $$;

update broadcast b set
    sent_total = (select count(*) from broadcast_recipient r where r.broadcast_id = b.id and r.status = 'SENT'),
    failed_total = (select count(*) from broadcast_recipient r where r.broadcast_id = b.id and r.status = 'FAILED'),
    excluded_total = (select count(*) from broadcast_recipient r where r.broadcast_id = b.id and r.status = 'EXCLUDED'),
    discarded_total = (select count(*) from broadcast_recipient r where r.broadcast_id = b.id and r.status in ('SKIPPED', 'INVALID_NUMBER')),
    excluded_by_reason = coalesce((select jsonb_object_agg(x.reason, x.total)
        from (select r.exclusion_reason as reason, count(*) as total from broadcast_recipient r
              where r.broadcast_id = b.id and r.status = 'EXCLUDED' group by r.exclusion_reason) x), '{}'::jsonb)
where b.status in ('COMPLETED', 'CANCELLED') and b.sent_total is null;

alter table broadcast add constraint ck_broadcast_frozen_totals check (
    (sent_total is null and failed_total is null and excluded_total is null and discarded_total is null and excluded_by_reason is null)
    or (sent_total >= 0 and failed_total >= 0 and excluded_total >= 0 and discarded_total >= 0
        and excluded_by_reason is not null and broadcast_reason_counts_sum(excluded_by_reason) = excluded_total));

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

create index ix_broadcast_recipient_delivery_id on broadcast_recipient (delivery_id);
