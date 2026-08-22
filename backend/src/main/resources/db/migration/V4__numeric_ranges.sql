-- Etapa 3: rangos numéricos sin validar a nivel de base (hallazgo l del análisis).

alter table grade_item drop constraint chk_grade_item_single_target;
alter table grade_item add constraint chk_grade_item_single_target check (
    ((course_section_enrollment_id is not null and program_section_enrollment_id is null)
     or
     (course_section_enrollment_id is null and program_section_enrollment_id is not null))
    and (weight is null or (weight >= 0 and weight <= 1))
);

alter table evaluation_scheme add constraint chk_evaluation_scheme_final_weight
    check (final_weight is null or (final_weight >= 0 and final_weight <= 1));

-- Escala de notas del instituto: 0 a 10.
alter table course_subject_prerequisite add constraint chk_course_prereq_min_grade
    check (min_grade is null or (min_grade >= 0 and min_grade <= 10));

alter table program_subject_prerequisite add constraint chk_prog_prereq_min_grade
    check (min_grade is null or (min_grade >= 0 and min_grade <= 10));
