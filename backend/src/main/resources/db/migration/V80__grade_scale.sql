update grade_item set score = greatest(1, least(10, round(score::numeric, 2))) where score is not null;
alter table grade_item alter column score type numeric(4,2);
alter table grade_item add constraint ck_grade_item_score check (score is null or score between 1 and 10);
alter table grade_item drop column max_score;

update evaluation_scheme set
    min_regular_grade = greatest(1, least(10, round(min_regular_grade::numeric, 2))),
    min_approval_grade = greatest(1, least(10, round(min_approval_grade::numeric, 2))),
    promotion_min_grade = greatest(1, least(10, round(promotion_min_grade::numeric, 2)));
alter table evaluation_scheme alter column min_regular_grade type numeric(4,2);
alter table evaluation_scheme alter column min_approval_grade type numeric(4,2);
alter table evaluation_scheme alter column promotion_min_grade type numeric(4,2);
alter table evaluation_scheme add constraint ck_evaluation_scheme_min_regular_grade
    check (min_regular_grade is null or min_regular_grade between 1 and 10);
alter table evaluation_scheme add constraint ck_evaluation_scheme_min_approval_grade
    check (min_approval_grade is null or min_approval_grade between 1 and 10);
alter table evaluation_scheme add constraint ck_evaluation_scheme_promotion_min_grade
    check (promotion_min_grade is null or promotion_min_grade between 1 and 10);

update subject_section_enrollment set final_score = greatest(1, least(10, round(final_score::numeric, 2))) where final_score is not null;
alter table subject_section_enrollment alter column final_score type numeric(4,2);
alter table subject_section_enrollment add constraint ck_subject_section_enrollment_final_score
    check (final_score is null or final_score between 1 and 10);

update course_section_enrollment set final_score = greatest(1, least(10, round(final_score::numeric, 2))) where final_score is not null;
alter table course_section_enrollment alter column final_score type numeric(4,2);
alter table course_section_enrollment add constraint ck_course_section_enrollment_final_score
    check (final_score is null or final_score between 1 and 10);

alter table program_subject_prerequisite drop constraint chk_prog_prereq_min_grade;
update program_subject_prerequisite set min_grade = greatest(1, least(10, round(min_grade::numeric, 2))) where min_grade is not null;
alter table program_subject_prerequisite alter column min_grade type numeric(4,2);
alter table program_subject_prerequisite add constraint ck_program_subject_prerequisite_min_grade
    check (min_grade is null or min_grade between 1 and 10);

alter table course_subject_prerequisite drop constraint chk_course_prereq_min_grade;
update course_subject_prerequisite set min_grade = greatest(1, least(10, round(min_grade::numeric, 2))) where min_grade is not null;
alter table course_subject_prerequisite alter column min_grade type numeric(4,2);
alter table course_subject_prerequisite add constraint ck_course_subject_prerequisite_min_grade
    check (min_grade is null or min_grade between 1 and 10);
