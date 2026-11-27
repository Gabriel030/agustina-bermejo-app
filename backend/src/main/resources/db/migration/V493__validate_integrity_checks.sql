alter table subject_section validate constraint subject_section_capacity_positive;
alter table course_section validate constraint course_section_capacity_positive;
alter table course validate constraint course_capacity_positive;
alter table classroom validate constraint classroom_capacity_positive;
alter table subject_section_enrollment validate constraint subject_section_enrollment_attempt_number_positive;
alter table course_section_enrollment validate constraint course_section_enrollment_attempt_number_positive;

do $$
begin
    if not exists (
        select 1 from subject_section_enrollment e
        where not exists (select 1 from program_enrollment p where p.id = e.program_enrollment_id and p.student_id = e.student_id)
    ) then
        alter table subject_section_enrollment validate constraint fk_subject_section_enrollment_parent_student;
    else
        raise warning 'fk_subject_section_enrollment_parent_student left NOT VALID: orphan rows exist';
    end if;

    if not exists (
        select 1 from course_section_enrollment e
        where not exists (select 1 from course_enrollment c where c.id = e.course_enrollment_id and c.student_id = e.student_id)
    ) then
        alter table course_section_enrollment validate constraint fk_course_section_enrollment_parent_student;
    else
        raise warning 'fk_course_section_enrollment_parent_student left NOT VALID: orphan rows exist';
    end if;
end $$;
