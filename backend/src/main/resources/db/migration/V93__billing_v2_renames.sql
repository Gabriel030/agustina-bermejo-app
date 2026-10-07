alter table collections_policy rename to late_payment_settings;
alter sequence collections_policy_id_seq rename to late_payment_settings_id_seq;
alter index collections_policy_pkey rename to late_payment_settings_pkey;
alter table late_payment_settings rename constraint fk_collections_policy_created_by to fk_late_payment_settings_created_by;
alter table late_payment_settings rename constraint ck_collections_policy_surcharge_percentage to ck_late_payment_settings_surcharge_percentage;
alter table late_payment_settings rename constraint ck_collections_policy_grace_not_negative to ck_late_payment_settings_grace_not_negative;
alter table late_payment_settings rename constraint ck_collections_policy_suspension_days_not_negative to ck_late_payment_settings_suspension_days_not_negative;
alter table late_payment_settings rename constraint ck_collections_policy_grace_within_suspension to ck_late_payment_settings_grace_within_suspension;
alter table late_payment_settings rename constraint ck_collections_policy_suspension_invoice_count to ck_late_payment_settings_suspension_invoice_count;

alter table payment_receipt rename to bank_transfer_receipt;
alter sequence payment_receipt_id_seq rename to bank_transfer_receipt_id_seq;
alter index payment_receipt_pkey rename to bank_transfer_receipt_pkey;
alter index uk_payment_receipt_pending rename to uk_bank_transfer_receipt_pending;
alter index ix_payment_receipt_payment_id rename to ix_bank_transfer_receipt_payment_id;
alter index ix_payment_receipt_invoice_id rename to ix_bank_transfer_receipt_invoice_id;
alter table bank_transfer_receipt rename constraint fk_payment_receipt_invoice to fk_bank_transfer_receipt_invoice;
alter table bank_transfer_receipt rename constraint fk_payment_receipt_reviewed_by to fk_bank_transfer_receipt_reviewed_by;
alter table bank_transfer_receipt rename constraint fk_payment_receipt_payment to fk_bank_transfer_receipt_payment;
alter table bank_transfer_receipt rename constraint payment_receipt_uploaded_by_id_fkey to bank_transfer_receipt_uploaded_by_id_fkey;

alter table program_enrollment rename column billing_standing to payment_status;
alter table program_enrollment rename column billing_standing_updated_at to payment_status_updated_at;
alter table course_enrollment rename column billing_standing to payment_status;
alter table course_enrollment rename column billing_standing_updated_at to payment_status_updated_at;

update payment_plan_option set cadence = 'ANNUAL_UPFRONT' where cadence = 'UPFRONT';

drop index uk_legacy_invoice_billable_period;
alter table legacy_invoice drop constraint ck_invoice_issue_reason;
alter table legacy_invoice drop constraint ck_invoice_priced_charges_have_revision;
alter table legacy_invoice drop constraint ck_invoice_enrollment_fee_has_slot;

update legacy_invoice set fee_type = 'MONTHLY_INSTALLMENT' where fee_type = 'RECURRING_FEE';
update legacy_invoice set fee_type = 'EXAM_FEE' where fee_type = 'EXAM_RIGHT';
update legacy_invoice set fee_type = 'DEGREE_ISSUANCE_FEE' where fee_type = 'DEGREE_ISSUANCE';
update price_revision set fee_type = 'MONTHLY_INSTALLMENT' where fee_type = 'RECURRING_FEE';
update price_revision set fee_type = 'EXAM_FEE' where fee_type = 'EXAM_RIGHT';
update price_revision set fee_type = 'DEGREE_ISSUANCE_FEE' where fee_type = 'DEGREE_ISSUANCE';
update promotion_benefit set fee_type = 'MONTHLY_INSTALLMENT' where fee_type = 'RECURRING_FEE';

update legacy_invoice set issue_reason = 'MONTHLY_RUN' where issue_reason = 'MONTHLY_JOB';
update legacy_invoice set issue_reason = 'PAYMENT_PLAN_SELECTION' where issue_reason = 'PLAN_SELECTION';
update legacy_invoice set issue_reason = 'YEAR_CONFIGURATION_CHANGE' where issue_reason = 'CONFIGURATION_CHANGE';
update legacy_invoice set issue_reason = 'COURSE_MONTHLY_RUN' where issue_reason = 'COURSE_MONTHLY';
update legacy_invoice set issue_reason = 'PROGRAM_ADMISSION' where issue_reason = 'ADMISSION';
update legacy_invoice set issue_reason = 'AD_HOC_CHARGE' where issue_reason = 'AD_HOC';

alter table legacy_invoice add constraint ck_invoice_issue_reason check (issue_reason in
    ('MONTHLY_RUN', 'CATCH_UP', 'PAYMENT_PLAN_SELECTION', 'REACTIVATION', 'ENROLLMENT_FEE_ON_DEMAND',
     'YEAR_CONFIGURATION_CHANGE', 'UPFRONT', 'PROGRAM_ADMISSION', 'COURSE_MONTHLY_RUN', 'COURSE_ADMISSION',
     'AD_HOC_CHARGE', 'LEGACY'));
alter table legacy_invoice add constraint ck_invoice_priced_charges_have_revision check (
    issue_reason in ('LEGACY', 'AD_HOC_CHARGE') or price_revision_id is not null);
alter table legacy_invoice add constraint ck_invoice_enrollment_fee_has_slot check (
    fee_type <> 'ENROLLMENT_FEE' or issue_reason = 'LEGACY' or course_enrollment_id is not null
    or (template_id is not null and enrollment_fee_slot is not null));
create unique index uk_legacy_invoice_billable_period on legacy_invoice
    (coalesce(course_enrollment_id, 0), coalesce(program_enrollment_id, 0), fee_type, period)
    where fee_type in ('MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE')
      and (course_enrollment_id is not null or program_enrollment_id is not null);

alter table invoice drop constraint invoice_issuing_reason_check;
update invoice set issuing_reason = 'MONTHLY_RUN' where issuing_reason = 'MONTHLY_JOB';
update invoice set issuing_reason = 'PAYMENT_PLAN_SELECTION' where issuing_reason = 'PLAN_SELECTION';
update invoice set issuing_reason = 'YEAR_CONFIGURATION_CHANGE' where issuing_reason = 'CONFIGURATION_CHANGE';
update invoice set issuing_reason = 'COURSE_MONTHLY_RUN' where issuing_reason = 'COURSE_MONTHLY';
update invoice set issuing_reason = 'PROGRAM_ADMISSION' where issuing_reason = 'ADMISSION';
update invoice set issuing_reason = 'AD_HOC_CHARGE' where issuing_reason = 'AD_HOC';
alter table invoice add constraint invoice_issuing_reason_check check (issuing_reason in
    ('MONTHLY_RUN', 'CATCH_UP', 'PAYMENT_PLAN_SELECTION', 'REACTIVATION', 'ENROLLMENT_FEE_ON_DEMAND',
     'YEAR_CONFIGURATION_CHANGE', 'ANNUAL_UPFRONT_PAYMENT', 'PROGRAM_ADMISSION', 'COURSE_MONTHLY_RUN',
     'COURSE_ADMISSION', 'AD_HOC_CHARGE', 'LATE_FEE', 'UPFRONT', 'LEGACY'));
