-- Nombre/label de comisión, para distinguir "Comisión A" de "Comisión B" de la misma
-- materia en el mismo período. Default temporal para no romper ddl-auto=validate si
-- hubiera filas (no debería haber ninguna todavía).
alter table program_section add column name varchar(255) not null default 'Comisión única';
alter table course_section add column name varchar(255) not null default 'Comisión única';
alter table program_section alter column name drop default;
alter table course_section alter column name drop default;

-- instructor_id queda redundante con program_section_instructor/course_section_instructor
-- (que sí soporta múltiples docentes con rol) — se elimina el mecanismo duplicado.
alter table program_section drop column instructor_id;
alter table course_section drop column instructor_id;

-- No permitir asignar al mismo docente con el mismo rol dos veces en la misma comisión.
alter table program_section_instructor
    add constraint uk_program_section_instructor unique (program_section_id, instructor_id, role);
alter table course_section_instructor
    add constraint uk_course_section_instructor unique (course_section_id, instructor_id, role);
