alter table invoice add column base_amount_currency varchar(3);
update invoice set base_amount_currency = 'ARS';
alter table invoice alter column base_amount_currency set not null;
alter table invoice add constraint ck_invoice_base_amount_currency check (base_amount_currency in ('ARS', 'USD'));

alter table payment add column amount_currency varchar(3);
update payment set amount_currency = 'ARS';
alter table payment alter column amount_currency set not null;
alter table payment add constraint ck_payment_amount_currency check (amount_currency in ('ARS', 'USD'));

alter table credit_note add column amount_currency varchar(3);
update credit_note set amount_currency = 'ARS';
alter table credit_note alter column amount_currency set not null;
alter table credit_note add constraint ck_credit_note_amount_currency check (amount_currency in ('ARS', 'USD'));

alter table payment_allocation add column amount_applied_currency varchar(3);
update payment_allocation set amount_applied_currency = 'ARS';
alter table payment_allocation alter column amount_applied_currency set not null;
alter table payment_allocation add constraint ck_payment_allocation_amount_applied_currency check (amount_applied_currency in ('ARS', 'USD'));

alter table credit_application add column amount_applied_currency varchar(3);
update credit_application set amount_applied_currency = 'ARS';
alter table credit_application alter column amount_applied_currency set not null;
alter table credit_application add constraint ck_credit_application_amount_applied_currency check (amount_applied_currency in ('ARS', 'USD'));

alter table price_revision add column monthly_amount_currency varchar(3);
update price_revision set monthly_amount_currency = 'ARS';
alter table price_revision alter column monthly_amount_currency set not null;
alter table price_revision add constraint ck_price_revision_monthly_amount_currency check (monthly_amount_currency in ('ARS', 'USD'));

alter table invoice_adjustment add column resolved_base_currency varchar(3);
update invoice_adjustment set resolved_base_currency = 'ARS';
alter table invoice_adjustment alter column resolved_base_currency set not null;
alter table invoice_adjustment add constraint ck_invoice_adjustment_resolved_base_currency check (resolved_base_currency in ('ARS', 'USD'));

alter table course add column currency varchar(3);
update course set currency = 'ARS';
alter table course alter column currency set not null;
alter table course add constraint ck_course_currency check (currency in ('ARS', 'USD'));

alter table program add column currency varchar(3);
update program set currency = 'ARS';
alter table program alter column currency set not null;
alter table program add constraint ck_program_currency check (currency in ('ARS', 'USD'));

update invoice_adjustment
set value = jsonb_build_object('type', 'NOMINAL', 'amount', value ->> 'amount', 'currency', 'ARS')
where value ->> 'type' = 'FIXED';
update invoice_adjustment
set value = jsonb_build_object('type', 'PROPORTIONAL', 'fraction', value ->> 'percentage')
where value ->> 'type' = 'PERCENTAGE';

update scholarship
set value = jsonb_build_object('type', 'NOMINAL', 'amount', value ->> 'amount', 'currency', 'ARS')
where value ->> 'type' = 'FIXED';
update scholarship
set value = jsonb_build_object('type', 'PROPORTIONAL', 'fraction', value ->> 'percentage')
where value ->> 'type' = 'PERCENTAGE';

update promotion_benefit
set value = jsonb_build_object('type', 'NOMINAL', 'amount', value ->> 'amount', 'currency', 'ARS')
where value ->> 'type' = 'FIXED';
update promotion_benefit
set value = jsonb_build_object('type', 'PROPORTIONAL', 'fraction', value ->> 'percentage')
where value ->> 'type' = 'PERCENTAGE';

update payment_plan_option
set upfront_discount = jsonb_build_object('type', 'NOMINAL', 'amount', upfront_discount ->> 'amount', 'currency', 'ARS')
where upfront_discount ->> 'type' = 'FIXED';
update payment_plan_option
set upfront_discount = jsonb_build_object('type', 'PROPORTIONAL', 'fraction', upfront_discount ->> 'percentage')
where upfront_discount ->> 'type' = 'PERCENTAGE';

alter table collections_policy alter column surcharge_percentage type numeric(7,6);
alter table collections_policy add constraint ck_collections_policy_surcharge_percentage check (surcharge_percentage between 0 and 1);
