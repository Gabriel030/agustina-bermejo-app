\set ON_ERROR_STOP on
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM exam_board_registration WHERE program_section_enrollment_id >= 2000000;
DELETE FROM subject_section_enrollment WHERE id >= 2000000;
DELETE FROM payment_plan_selection WHERE program_enrollment_id BETWEEN 400001 AND 499999;
DELETE FROM student_current_account WHERE id BETWEEN 400001 AND 499999;
DELETE FROM program_enrollment WHERE id BETWEEN 400001 AND 499999;
DELETE FROM subject_section_instructor WHERE id >= 1500000;
DELETE FROM subject_section WHERE id >= 1400000;
DELETE FROM person_role WHERE person_id BETWEEN 400001 AND 499999 OR person_id BETWEEN 410001 AND 410999;
DELETE FROM student WHERE id BETWEEN 400001 AND 499999;
DELETE FROM instructor WHERE id BETWEEN 410001 AND 410999;
DELETE FROM person WHERE id BETWEEN 400001 AND 499999 OR id BETWEEN 410001 AND 410999;
DELETE FROM address WHERE id BETWEEN 400001 AND 499999 OR id BETWEEN 410001 AND 410999;
COMMIT;
BEGIN;
INSERT INTO address (id, city, country, number, postal_code, state, street)
SELECT 400000+g, 'Ciudad', 'AR', g::text, '1000', 'Prov', 'Carga '||g FROM generate_series(1, :students + :graded_students) g;
INSERT INTO address (id, city, country, number, postal_code, state, street)
SELECT 410000+n, 'Ciudad', 'AR', n::text, '1000', 'Prov', 'Docente carga '||n FROM generate_series(1, :teachers) n;
INSERT INTO person (address_id, id, dni, email, file, last_name, name, password, status, username, auth_provider, failed_login_attempts, email_verified, document_type)
SELECT 400000+g, 400000+g, (50000000+g)::text, 'loads'||g||'@perf.local', 'LS-'||g, 'Carga'||g, 'Alumno'||g, (SELECT password FROM person WHERE id=1), 'ACTIVE', 'loads'||g, 'LOCAL', 0, true, 'DNI' FROM generate_series(1, :students + :graded_students) g;
INSERT INTO person (address_id, id, dni, email, file, last_name, name, password, status, username, auth_provider, failed_login_attempts, email_verified, document_type)
SELECT 410000+n, 410000+n, (51000000+n)::text, 'loadt'||n||'@perf.local', 'LT-'||n, 'CargaDocente'||n, 'Docente'||n, (SELECT password FROM person WHERE id=1), 'ACTIVE', 'loadt'||n, 'LOCAL', 0, true, 'DNI' FROM generate_series(1, :teachers) n;
INSERT INTO person_role (person_id, role_id) SELECT 400000+g, 1 FROM generate_series(1, :students + :graded_students) g;
INSERT INTO person_role (person_id, role_id) SELECT 410000+n, 2 FROM generate_series(1, :teachers) n;
INSERT INTO student (id, institution_id, person_id, onboarding_status) SELECT 400000+g, 1, 400000+g, 'CONFIRMADO' FROM generate_series(1, :students + :graded_students) g;
INSERT INTO instructor (id, person_id) SELECT 410000+n, 410000+n FROM generate_series(1, :teachers) n;
INSERT INTO program_enrollment (id, program_id, student_id, status, version, enrollment_date, program_cohort_id, current_billing_template_id)
SELECT 400000+g, :program_id, 400000+g, 'ENROLLED', 0, current_date - 30, :cohort_id, :template_id FROM generate_series(1, :students + :graded_students) g;
INSERT INTO payment_plan_selection (program_enrollment_id, payment_plan_option_id, effective_from, created_by_id, created_at, version)
SELECT 400000+g, :monthly_option_id, current_date - 30, 1, now(), 0 FROM generate_series(1, :students + :graded_students) g;
INSERT INTO student_current_account (id, version, student_id, currency, balance_amount, balance_amount_currency, balance_side, unallocated_credit_amount, unallocated_credit_amount_currency, last_entry_sequence, opened_at)
SELECT 400000+g, 0, 400000+g, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', 0, timestamp '2026-01-15' FROM generate_series(1, :students + :graded_students) g;
INSERT INTO subject_section (id, term_id, subject_id, name, capacity, created_at, version, status, schedule, max_absence_percentage, pending_approval, evaluation_scheme_id)
SELECT 1400000+g, :term_id, 1+(g%16), CASE WHEN g<=:enroll_sections THEN 'Comisión carga inscripción ' ELSE 'Comisión carga notas ' END || g, :capacity, now(), 0, 'ACTIVE', 'Lunes 18:00 a 20:00', 0.25, false, :scheme_id
FROM generate_series(1, :enroll_sections + :grade_sections) g;
INSERT INTO subject_section_instructor (id, instructor_id, program_section_id, role)
SELECT 1500000+g, 410000+1+(g%:teachers), 1400000+g, 'LEAD' FROM generate_series(1, :enroll_sections + :grade_sections) g;
INSERT INTO subject_section_enrollment (id, program_enrollment_id, program_section_id, student_id, status, version, attempt_number, enrolled_at)
SELECT 2000000 + s.g*10 + k, 400000+:students+s.g, 1400000+:enroll_sections+1+((s.g*7+k) % :grade_sections), 400000+:students+s.g, 'ENROLLED', 0, 1, now() - interval '60 days'
FROM generate_series(1, :graded_students) s(g), generate_series(1, 3) k;
INSERT INTO exam_board_registration (version, exam_board_id, program_section_enrollment_id, status, registered_at)
SELECT 0, b.id, e.id, 'REGISTERED', now()
FROM exam_board b JOIN subject_section ss ON ss.subject_id = b.subject_id AND ss.id >= 1400000
JOIN subject_section_enrollment e ON e.program_section_id = ss.id AND e.id >= 2000000
WHERE b.classroom LIKE 'LOAD-%' AND b.status = 'SCHEDULED';
COMMIT;
