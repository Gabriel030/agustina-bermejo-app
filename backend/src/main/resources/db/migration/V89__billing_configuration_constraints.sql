alter table collections_policy drop column withdrawal_after_days;
alter table collections_policy drop column withdrawal_after_invoice_count;
alter table collections_policy add constraint ck_collections_policy_grace_not_negative check (grace_period_days >= 0);
alter table collections_policy add constraint ck_collections_policy_grace_within_suspension check (grace_period_days <= suspension_after_days);
alter table collections_policy add constraint ck_collections_policy_suspension_invoice_count check (suspension_after_invoice_count >= 1);
alter table program_year_billing_template add constraint ck_template_recurring_fee_invoices_count check (recurring_fee_invoices_count >= 1);
