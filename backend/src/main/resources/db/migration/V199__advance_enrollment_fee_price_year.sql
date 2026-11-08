alter table price_version add column applies_to_lective_year integer null;
alter table price_version add constraint ck_price_version_lective_year check (applies_to_lective_year is null or applies_to_lective_year between 2000 and 2200);
create unique index uk_price_version_lective_year on price_version (price_id, applies_to_lective_year)
    where applies_to_lective_year is not null and status = 'SCHEDULED';

drop index uk_price_version_effective_date;
create unique index uk_price_version_effective_date on price_version (price_id, effective_from)
    where status in ('SCHEDULED', 'EFFECTIVE') and applies_to_lective_year is null;

alter table invoice drop constraint invoice_issuing_reason_check;
alter table invoice add constraint invoice_issuing_reason_check check (issuing_reason in
    ('MONTHLY_RUN', 'CATCH_UP', 'PAYMENT_PLAN_SELECTION', 'REACTIVATION', 'ENROLLMENT_FEE_ON_DEMAND',
     'YEAR_CONFIGURATION_CHANGE', 'ANNUAL_UPFRONT_PAYMENT', 'PROGRAM_ADMISSION', 'COURSE_MONTHLY_RUN',
     'COURSE_ADMISSION', 'AD_HOC_CHARGE', 'LATE_FEE', 'UPFRONT', 'LEGACY', 'ENROLLMENT_FEE_ADVANCE'));
