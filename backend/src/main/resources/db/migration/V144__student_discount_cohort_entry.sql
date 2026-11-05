alter table student_discount drop constraint student_discount_origin_check;
alter table student_discount add constraint student_discount_origin_check check (origin in ('DIRECT_GRANT', 'COHORT_ACQUISITION', 'COHORT_ENTRY_CONFIGURATION'));

alter table student_discount drop constraint student_discount_discount_type_check;
alter table student_discount add constraint student_discount_discount_type_check check (discount_type in ('SCHOLARSHIP', 'INDIVIDUAL', 'COHORT_PROMOTION', 'COHORT_ENTRY'));

alter table student_discount drop constraint ck_student_discount_installments_monthly;
alter table student_discount add constraint ck_student_discount_installments_monthly
    check (installments_total is null or applicable_invoice_type = 'MONTHLY_INSTALLMENT' or discount_type = 'COHORT_ENTRY');

alter table student_discount add column configuration_version_id bigint references configuration_version (id);
alter table student_discount alter column granted_by_id drop not null;

alter table student_discount add constraint ck_student_discount_granted_by
    check (granted_by_id is not null or origin = 'COHORT_ENTRY_CONFIGURATION');
alter table student_discount add constraint ck_student_discount_cohort_entry_shape
    check ((origin = 'COHORT_ENTRY_CONFIGURATION') = (discount_type = 'COHORT_ENTRY')
        and (origin = 'COHORT_ENTRY_CONFIGURATION') = (configuration_version_id is not null)
        and (origin <> 'COHORT_ENTRY_CONFIGURATION'
            or (discount_definition_id is null and applicable_invoice_type = 'ENROLLMENT_FEE' and installments_total = 1)));

create index ix_student_discount_configuration_version on student_discount (configuration_version_id) where configuration_version_id is not null;
