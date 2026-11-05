alter table invoice add column monthly_invoice_run_id bigint;
alter table invoice add constraint fk_invoice_monthly_invoice_run foreign key (monthly_invoice_run_id) references monthly_invoice_run (id);
create index ix_invoice_run_draft on invoice (monthly_invoice_run_id) where document_status = 'DRAFT';

alter table monthly_invoice_run drop constraint ck_monthly_invoice_run_issuance_mode;
alter table monthly_invoice_run add constraint ck_monthly_invoice_run_issuance_mode check (issuance_mode in ('DIRECT', 'REVIEW'));
alter table monthly_invoice_run drop constraint ck_monthly_invoice_run_status;
alter table monthly_invoice_run add constraint ck_monthly_invoice_run_status check (status in ('RUNNING', 'COMPLETED', 'COMPLETED_WITH_FAILURES', 'AWAITING_REVIEW', 'FAILED'));
