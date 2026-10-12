alter table program_cohort add column enrollment_fee_month integer;
update program_cohort set enrollment_fee_month = 2;
alter table program_cohort alter column enrollment_fee_month set not null;
alter table program_cohort add constraint ck_program_cohort_enrollment_fee_month check (enrollment_fee_month between 1 and 12);
