create table shedlock (
    name varchar(64) primary key,
    lock_until timestamp not null,
    locked_at timestamp not null,
    locked_by varchar(255) not null
);

create index ix_invoice_status_due_date on invoice (status, due_date);

create index ix_subject_section_enrollment_section on subject_section_enrollment (program_section_id);
create index ix_course_section_enrollment_section on course_section_enrollment (course_section_id);

create index ix_course_enrollment_status on course_enrollment (status);
create index ix_program_enrollment_status on program_enrollment (status);
create index ix_person_role_role on person_role (role_id);
