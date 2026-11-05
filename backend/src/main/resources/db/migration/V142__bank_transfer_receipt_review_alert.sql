alter table bank_transfer_receipt add column review_alert_sent_at timestamp(6);

create index ix_bank_transfer_receipt_review_alert_pending
    on bank_transfer_receipt (uploaded_at)
    where status = 'PENDING_REVIEW' and review_alert_sent_at is null;
