alter table program_enrollment
    add constraint uk_program_enrollment_id_student unique (id, student_id);

alter table course_enrollment
    add constraint uk_course_enrollment_id_student unique (id, student_id);

alter table subject_section_enrollment
    add constraint fk_subject_section_enrollment_parent_student
        foreign key (program_enrollment_id, student_id) references program_enrollment (id, student_id) not valid;

alter table course_section_enrollment
    add constraint fk_course_section_enrollment_parent_student
        foreign key (course_enrollment_id, student_id) references course_enrollment (id, student_id) not valid;
