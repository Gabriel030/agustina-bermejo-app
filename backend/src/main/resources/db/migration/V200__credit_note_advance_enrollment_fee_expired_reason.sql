alter table credit_note drop constraint ck_credit_note_reason;
alter table credit_note add constraint ck_credit_note_reason
    check (reason in ('BILLING_ERROR', 'CANCELLATION', 'LATE_FEE_ANNULMENT', 'WITHDRAWAL', 'LATE_FEE_CORRECTION', 'ADVANCE_ENROLLMENT_FEE_EXPIRED'));
