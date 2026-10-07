alter table invoice add constraint ck_invoice_base_amount_not_negative check (base_amount >= 0);
alter table payment add constraint ck_payment_amount_not_negative check (amount >= 0);
alter table credit_note add constraint ck_credit_note_amount_not_negative check (amount >= 0);
alter table payment_allocation add constraint ck_payment_allocation_amount_applied_not_negative check (amount_applied >= 0);
alter table credit_application add constraint ck_credit_application_amount_applied_not_negative check (amount_applied >= 0);
alter table price_revision add constraint ck_price_revision_monthly_amount_not_negative check (monthly_amount >= 0);
alter table invoice_adjustment add constraint ck_invoice_adjustment_resolved_base_not_negative check (resolved_base >= 0);
