alter table preceptor_assignment add column course_id bigint references course (id) on delete cascade;

alter table preceptor_assignment drop constraint preceptor_assignment_target_check;
alter table preceptor_assignment add constraint preceptor_assignment_target_check
    check (num_nonnulls(subject_section_id, course_section_id, program_cohort_id, course_id) = 1);

create unique index preceptor_assignment_person_course_uq on preceptor_assignment (person_id, course_id) where course_id is not null;
create index preceptor_assignment_course_idx on preceptor_assignment (course_id) where course_id is not null;
