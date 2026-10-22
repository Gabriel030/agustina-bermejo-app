alter table student_discount drop constraint student_discount_origin_check;
alter table student_discount add constraint student_discount_origin_check check (origin in ('DIRECT_GRANT', 'COHORT_ACQUISITION'));

alter table student_discount drop constraint student_discount_discount_type_check;
alter table student_discount add constraint student_discount_discount_type_check check (discount_type in ('SCHOLARSHIP', 'INDIVIDUAL', 'COHORT_PROMOTION'));

alter table student_discount add constraint ck_student_discount_cohort_promotion_origin
    check ((origin = 'COHORT_ACQUISITION') = (discount_type = 'COHORT_PROMOTION'));
alter table student_discount add constraint ck_student_discount_cohort_promotion_has_definition
    check (discount_type <> 'COHORT_PROMOTION' or discount_definition_id is not null);

create unique index ux_student_discount_cohort_acquisition_program_enrollment on student_discount (program_enrollment_id)
    where origin = 'COHORT_ACQUISITION' and program_enrollment_id is not null;
create unique index ux_student_discount_cohort_acquisition_course_enrollment on student_discount (course_enrollment_id)
    where origin = 'COHORT_ACQUISITION' and course_enrollment_id is not null;
create index ix_student_discount_definition_cohort_acquisition on student_discount (discount_definition_id)
    where origin = 'COHORT_ACQUISITION';
