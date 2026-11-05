alter table discount_definition drop constraint ck_discount_definition_scope_only_cohort_promotion;

alter table discount_definition add constraint ck_discount_definition_scope_by_type check (
    discount_type = 'COHORT_PROMOTION'
    or (discount_type = 'ANNUAL_UPFRONT' and num_nonnulls(program_cohort_id, course_id) = 0)
    or num_nonnulls(program_id, program_cohort_id, course_id, enrollment_window_from, enrollment_window_to, quota) = 0);
