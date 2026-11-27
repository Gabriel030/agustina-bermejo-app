drop index if exists uk_cycle_program_term_active;
drop index if exists uk_cycle_course_term_active;

create unique index uk_cycle_program_term_active
    on academic_offering_cycle (program_id, term_id)
    where status <> 'REJECTED' and program_id is not null;

create unique index uk_cycle_course_term_active
    on academic_offering_cycle (course_id, term_id)
    where status <> 'REJECTED' and course_id is not null;
