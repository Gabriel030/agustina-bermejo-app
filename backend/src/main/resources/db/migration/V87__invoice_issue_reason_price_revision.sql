alter table invoice add column issue_reason varchar(30);
update invoice set issue_reason = 'LEGACY';
alter table invoice alter column issue_reason set not null;
alter table invoice add constraint ck_invoice_issue_reason check (issue_reason in
    ('MONTHLY_JOB','CATCH_UP','PLAN_SELECTION','REACTIVATION','ENROLLMENT_FEE_ON_DEMAND','CONFIGURATION_CHANGE',
     'UPFRONT','ADMISSION','COURSE_MONTHLY','COURSE_ADMISSION','AD_HOC','LEGACY'));
alter table invoice add column price_revision_id bigint references price_revision (id);
alter table invoice add constraint ck_invoice_priced_charges_have_revision check (
    issue_reason in ('LEGACY','AD_HOC') or price_revision_id is not null);
alter table invoice add constraint ck_invoice_enrollment_fee_has_slot check (
    fee_type <> 'ENROLLMENT_FEE' or issue_reason = 'LEGACY' or course_enrollment_id is not null
    or (template_id is not null and enrollment_fee_slot is not null));
create index ix_invoice_price_revision on invoice (price_revision_id);
