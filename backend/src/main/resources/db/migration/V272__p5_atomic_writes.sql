create unique index uk_course_enrollment_student_course_active
    on course_enrollment (student_id, course_id)
    where status in ('ENROLLED', 'IN_PROGRESS', 'WAITLISTED');
