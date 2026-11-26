create unique index uk_program_enrollment_student_program_active
    on program_enrollment (student_id, program_id)
    where status in ('ENROLLED', 'PAUSED');
