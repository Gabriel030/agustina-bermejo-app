\set ON_ERROR_STOP on
\echo ===== Datos de ejemplo para el smoke de migraciones (base master V445 en adelante; las filas usan ids 9001..9999) =====

begin;

insert into address (id) overriding system value values (9001), (9002), (9003), (9004);
insert into institution (id) overriding system value values (9001);
insert into person (id, address_id, name, last_name)
overriding system value values
    (9001, 9001, 'Ana', 'Egresada'),
    (9002, 9002, 'Beto', 'Egresado'),
    (9003, 9003, 'Carla', 'Docente'),
    (9004, 9004, 'Dario', 'Administrativo');
insert into student (id, institution_id, person_id) overriding system value values (9001, 9001, 9001), (9002, 9001, 9002);
insert into instructor (id, person_id) overriding system value values (9001, 9003);

insert into term (id, year_number, type, status, description, start_date, end_date, enrollment_start, enrollment_end)
overriding system value values
    (9001, 2025, 'FIRST_SEMESTER', 'ACTIVE', 'periodo con fechas de inscripcion', '2025-03-01', '2025-07-31', '2025-02-01', '2025-03-15'),
    (9002, 2025, 'SECOND_SEMESTER', 'ACTIVE', 'periodo posterior', '2025-08-01', '2025-12-15', '2025-07-01', '2025-08-15');

insert into holiday (date, name, description, type) values ('2025-05-01', 'Dia del Trabajador', 'Dia del Trabajador', 'NATIONAL'), ('2025-07-09', 'Dia de la Independencia', 'Dia de la Independencia', 'NATIONAL');

insert into program (id, currency) overriding system value values (9001, 'ARS');
insert into program_enrollment (id, program_id, student_id, status, completion_date)
overriding system value values
    (9001, 9001, 9001, 'COMPLETED', '2025-08-01 10:00:00'),
    (9002, 9001, 9002, 'COMPLETED', '2025-08-01 10:00:00');

insert into graduation_request (id, program_enrollment_id, status, requested_at, decided_by_id, decided_at)
overriding system value values
    (9001, 9001, 'APPROVED', '2025-09-01 10:00:00', null, null),
    (9002, 9002, 'APPROVED', '2025-09-02 10:00:00', null, null);

insert into graduation_certificate (id, code, kind, program_enrollment_id, student_id, status, graduation_date, issued_at, issued_by_id,
                                    snapshot, template_ref, pdf_sha256, pdf_path)
overriding system value values
    (9001, '9GRAD00000000000000000SMK1', 'PROGRAM', 9001, 9001, 'VALID', '2025-09-01', '2025-09-05 10:00:00', 9004,
     '{}'::jsonb, 'graduation-v1', repeat('a', 64), '/smoke/9GRAD00000000000000000SMK1.pdf');

insert into subject (id, name, code) overriding system value values (9001, 'Materia de ejemplo', 'SM01');
insert into classroom (id, name, capacity) overriding system value values (9001, 'Aula smoke A', 30), (9002, 'Aula smoke B', 30);

insert into subject_section (id, term_id, subject_id, name, status)
overriding system value values
    (9001, 9001, 9001, 'Comision smoke ACTIVA 1', 'ACTIVE'),
    (9002, 9001, 9001, 'Comision smoke ACTIVA 2', 'ACTIVE'),
    (9003, 9001, 9001, 'Comision smoke INACTIVA que pisa el aula', 'INACTIVE');
insert into subject_section_instructor (id, instructor_id, program_section_id, role)
overriding system value values (9001, 9001, 9001, 'LEAD'), (9002, 9001, 9002, 'LEAD'), (9003, 9001, 9003, 'LEAD');

insert into section_schedule_slot (id, subject_section_id, classroom_id, day_of_week, start_time, end_time)
overriding system value values
    (9001, 9001, 9001, 'MONDAY', '09:00', '11:00'),
    (9002, 9002, 9001, 'MONDAY', '11:00', '13:00'),
    (9003, 9002, 9002, 'TUESDAY', '09:00', '11:00'),
    (9004, 9003, 9001, 'MONDAY', '09:30', '10:30');

insert into subject_section_enrollment (id, program_enrollment_id, program_section_id, student_id)
overriding system value values (9001, 9001, 9001, 9001);

insert into exam_board (id, term_id, subject_id, exam_date, status)
overriding system value values (9001, 9001, 9001, '2025-07-20 09:00:00', 'SCHEDULED');
insert into exam_board_registration (id, exam_board_id, program_section_enrollment_id, status, registered_at)
overriding system value values (9001, 9001, 9001, 'REGISTERED', '2025-07-10 10:00:00');

insert into exam_registration_certificate (id, code, student_id, registration_id, exam_board_id, status, issued_at, expires_at, issued_by_id,
                                           snapshot, template_ref, pdf_sha256, pdf_path)
overriding system value values
    (9001, '9EXAM00000000000000000SMK1', 9001, 9001, 9001, 'VALID', '2025-07-11 10:00:00', '2025-07-20 09:00:00', 9004,
     '{}'::jsonb, 'exam-registration-v1', repeat('b', 64), '/smoke/9EXAM00000000000000000SMK1.pdf'),
    (9002, '9GRAD00000000000000000SMK1', 9001, 9001, 9001, 'EXPIRED', '2025-06-01 10:00:00', '2025-06-10 10:00:00', 9004,
     '{}'::jsonb, 'exam-registration-v1', repeat('c', 64), '/smoke/9EXAM-dup-code.pdf');

commit;
