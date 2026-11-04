create index if not exists ix_subject_section_term on subject_section (term_id);
create index if not exists ix_subject_section_enrollment_program_enrollment on subject_section_enrollment (program_enrollment_id);
