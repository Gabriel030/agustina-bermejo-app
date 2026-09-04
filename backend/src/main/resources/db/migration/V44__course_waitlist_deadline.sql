-- Cupo del curso (Course.capacity) pasa a hacerse cumplir en CourseEnrollmentService.matricular
-- (antes era un campo decorativo, sin lectura en ningún lado más que el DTO de respuesta). Este
-- campo nuevo define el corte de la lista de espera: positivo = días después de term.start_date,
-- negativo = antes, null = sin corte automático (lista de espera abierta indefinidamente).
alter table course add column waitlist_deadline_offset_days integer;
