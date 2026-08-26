-- Etapa 1: integridad crítica (hallazgos b, c del análisis + nullable=false en FKs obligatorias por negocio, confirmadas una por una).

-- UNIQUE en person.dni y person.email: evita legajos duplicados con el mismo documento o correo.
alter table person add constraint uk_person_dni unique (dni);
alter table person add constraint uk_person_email unique (email);

-- CHECK XOR en grade_item: refuerza a nivel de base la regla que hoy solo vive en @PrePersist/@PreUpdate.
alter table grade_item add constraint chk_grade_item_single_target check (
    (course_section_enrollment_id is not null and program_section_enrollment_id is null)
    or
    (course_section_enrollment_id is null and program_section_enrollment_id is not null)
);

-- FKs obligatorias por negocio, hoy nullable por default de JPA.
alter table course alter column term_id set not null;
alter table course_enrollment alter column student_id set not null;
alter table course_enrollment alter column course_id set not null;
alter table course_subject alter column course_id set not null;
alter table program_enrollment alter column student_id set not null;
alter table program_enrollment alter column program_id set not null;
alter table program_subject alter column program_id set not null;
alter table study_program_course alter column study_program_id set not null;
alter table study_program_course alter column course_id set not null;
alter table instructor alter column person_id set not null;
alter table student alter column person_id set not null;
alter table student alter column institution_id set not null;
alter table person alter column address_id set not null;

-- person.status pasa de tinyint ordinal (0/1/2) a texto (ACTIVE/INACTIVE/DELETED): un reordenamiento futuro
-- del enum StatusType en el código no debe cambiar el significado de datos ya guardados.
alter table person add column status_str varchar(20);
update person set status_str = case status when 0 then 'ACTIVE' when 1 then 'INACTIVE' when 2 then 'DELETED' end;
alter table person drop column status;
alter table person rename column status_str to status;
