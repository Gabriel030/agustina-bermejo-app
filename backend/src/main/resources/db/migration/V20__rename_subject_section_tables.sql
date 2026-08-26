-- Las clases SubjectSection/SubjectSectionEnrollment/SubjectSectionInstructor se renombraron
-- en la spec de Inscripciones académicas (Fase 2), reusando deliberadamente las tablas físicas
-- "program_section*" para no sumar una migración de rename "sin necesidad real". El nombre
-- físico desactualizado terminó generando confusión real (una copia vieja de la entidad, con
-- su nombre viejo, quedó dando vueltas por referencia a la tabla) — se renombran ahora las 3
-- tablas para que coincidan con el nombre de clase vigente. Las columnas (program_section_id,
-- program_section_enrollment_id, etc.) NO se tocan: son solo las tablas.
alter table program_section rename to subject_section;
alter table program_section_enrollment rename to subject_section_enrollment;
alter table program_section_instructor rename to subject_section_instructor;
