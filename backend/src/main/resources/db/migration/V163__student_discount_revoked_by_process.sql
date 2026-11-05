alter table student_discount add column revoked_by_process varchar(80);

alter table student_discount drop constraint ck_student_discount_revocation;
alter table student_discount add constraint ck_student_discount_revocation check ((status = 'REVOKED') = (revoked_at is not null)
    and (revoked_at is null or (num_nonnulls(revoked_by_id, revoked_by_process) = 1 and revocation_reason is not null and revoked_from_period is not null)));
