alter table academic_offering_cycle add column submitted_by bigint references person (id);
alter table academic_offering_cycle add column submitted_at timestamp;
alter table academic_offering_cycle add column decision_idempotency_key varchar(100);

update academic_offering_cycle
set submitted_by = created_by,
    submitted_at = created_at
where status in ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CLOSED');
