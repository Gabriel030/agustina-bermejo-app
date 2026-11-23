select 'students_enrollable' as item, count(*) from student where id between 400001 and 400000 + :students
union all select 'students_graded', count(*) from student where id > 400000 + :students and id < 410000
union all select 'teachers', count(*) from instructor where id between 410001 and 410999
union all select 'enrollment_sections', count(*) from subject_section where id between 1400001 and 1400000 + :enroll_sections
union all select 'grade_sections', count(*) from subject_section where id > 1400000 + :enroll_sections
union all select 'section_enrollments', count(*) from subject_section_enrollment where id >= 2000000
union all select 'program_enrollments_with_template', count(*) from program_enrollment where id between 400001 and 499999 and current_billing_template_id is not null
union all select 'payment_plan_selections', count(*) from payment_plan_selection where program_enrollment_id between 400001 and 499999
union all select 'exam_boards', count(*) from exam_board where classroom like 'LOAD-%'
union all select 'exam_registrations', count(*) from exam_board_registration where program_section_enrollment_id >= 2000000
union all select 'open_enrollment_terms', count(*) from term where id = :term_id and enrollment_override = 'OPEN';
