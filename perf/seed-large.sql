\set ON_ERROR_STOP on
create extension if not exists pg_stat_statements;
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM attendance WHERE id >= 1000000;
DELETE FROM grade_item WHERE id >= 3000000;
DELETE FROM class_session WHERE program_section_id BETWEEN 14001 AND 14300;
DELETE FROM subject_section_enrollment WHERE id >= 100000;
DELETE FROM invoice_line WHERE id >= 10500000;
DELETE FROM invoice WHERE id >= 500000;
DELETE FROM student_current_account WHERE id BETWEEN 20001 AND 25000;
DELETE FROM program_enrollment WHERE id BETWEEN 20001 AND 25000;
DELETE FROM subject_section_instructor WHERE id BETWEEN 50001 AND 50300;
DELETE FROM subject_section WHERE id BETWEEN 14001 AND 14300;
DELETE FROM notification WHERE message LIKE 'Notificación perf %';
DELETE FROM audit_log WHERE reason LIKE 'perf %';
DELETE FROM person_role WHERE person_id BETWEEN 20001 AND 25000 OR person_id BETWEEN 30001 AND 30050;
DELETE FROM student WHERE id BETWEEN 20001 AND 25000;
DELETE FROM instructor WHERE id BETWEEN 30001 AND 30050;
DELETE FROM person WHERE id BETWEEN 20001 AND 25000 OR id BETWEEN 30001 AND 30050;
DELETE FROM address WHERE id BETWEEN 20001 AND 25000 OR id BETWEEN 30001 AND 30050;
COMMIT;
BEGIN;
INSERT INTO address (id, city, country, number, postal_code, state, street)
SELECT 20000+g, 'Ciudad', 'AR', g::text, '1000', 'Prov', 'Calle '||g FROM generate_series(1,5000) g;
INSERT INTO address (id, city, country, number, postal_code, state, street)
SELECT 30000+n, 'Ciudad', 'AR', n::text, '1000', 'Prov', 'Docente '||n FROM generate_series(1,50) n;
INSERT INTO person (address_id, id, dni, email, file, last_name, name, password, status, username, auth_provider, failed_login_attempts, email_verified, document_type)
SELECT 20000+g, 20000+g, (30000000+g)::text, 'perf'||g||'@perf.local', 'PF-'||g, 'Apellido'||g, 'Nombre'||g, (SELECT password FROM person WHERE id=1), 'ACTIVE', 'perf'||g, 'LOCAL', 0, true, 'DNI' FROM generate_series(1,5000) g;
INSERT INTO person (address_id, id, dni, email, file, last_name, name, password, status, username, auth_provider, failed_login_attempts, email_verified, document_type)
SELECT 30000+n, 30000+n, (31000000+n)::text, 'perft'||n||'@perf.local', 'PT-'||n, 'Docente'||n, 'Nombre'||n, (SELECT password FROM person WHERE id=1), 'ACTIVE', 'perft'||n, 'LOCAL', 0, true, 'DNI' FROM generate_series(1,50) n;
INSERT INTO person_role (person_id, role_id) SELECT 20000+g, 1 FROM generate_series(1,5000) g;
INSERT INTO person_role (person_id, role_id) SELECT 30000+n, 2 FROM generate_series(1,50) n;
INSERT INTO student (id, institution_id, person_id, onboarding_status) SELECT 20000+g, 1, 20000+g, 'CONFIRMADO' FROM generate_series(1,5000) g;
INSERT INTO instructor (id, person_id) SELECT 30000+n, 30000+n FROM generate_series(1,50) n;
INSERT INTO program_enrollment (id, program_id, student_id, status, version, enrollment_date)
SELECT 20000+g, 1+(g%2), 20000+g, 'ENROLLED', 0, timestamp '2026-01-15' + (g%300)*interval '1 day' FROM generate_series(1,5000) g;

INSERT INTO subject_section (id, term_id, subject_id, name, capacity, created_at, version, status, schedule, max_absence_percentage, pending_approval, evaluation_scheme_id)
SELECT 14000+g, 1, 1+(g%16), 'Comisión perf '||g, 40+(g%30), timestamp '2026-02-01', 0, 'ACTIVE', 'Lunes 18:00 a 20:00', 0.25, false, 1 FROM generate_series(1,300) g;
INSERT INTO subject_section_instructor (id, instructor_id, program_section_id, role)
SELECT 50000+g, 30000+1+((14000+g)%50), 14000+g, 'LEAD' FROM generate_series(1,300) g;

INSERT INTO subject_section_enrollment (id, program_enrollment_id, program_section_id, student_id, status, version, attempt_number, enrolled_at, final_score)
SELECT 100000 + s.g*10 + k, 20000+s.g, 14000 + 1 + ((floor(300*power(((s.g*7919)%10000)/10000.0, 2))::int + k*37) % 300), 20000+s.g,
       CASE WHEN (s.g*k)%20=0 THEN 'WAITLISTED' WHEN (s.g*k)%20=1 THEN 'WITHDRAWN' WHEN (s.g*k)%10<5 THEN 'APPROVED' ELSE 'ENROLLED' END,
       0, 1, timestamp '2026-03-01' + ((s.g+k)%90)*interval '1 day', CASE WHEN (s.g*k)%10<5 THEN 4 + ((s.g*k) % 7) ELSE NULL END
FROM generate_series(1,5000) s(g), generate_series(1,8) k;

INSERT INTO class_session (id, version, program_section_id, class_date, status, created_by_id, created_at)
SELECT 200000 + ss.id*30 + d, 0, ss.id, date '2026-03-02' + d*7, CASE WHEN d<6 THEN 'HELD' ELSE 'SCHEDULED' END, 1, timestamp '2026-02-01'
FROM subject_section ss, generate_series(0,23) d WHERE ss.id BETWEEN 14001 AND 14300;
INSERT INTO attendance (id, version, program_section_enrollment_id, class_date, status, recorded_by_id)
SELECT 1000000 + row_number() over (order by e.id, cs.class_date), 0, e.id, cs.class_date,
       (ARRAY['PRESENT','PRESENT','PRESENT','PRESENT','ABSENT','LATE','JUSTIFIED'])[1 + ((e.id + cs.id) % 7)], 1
FROM subject_section_enrollment e JOIN class_session cs ON cs.program_section_id = e.program_section_id
WHERE cs.status='HELD' AND e.status IN ('APPROVED','ENROLLED') AND e.id >= 100000 AND e.program_section_id BETWEEN 14001 AND 14300;
INSERT INTO grade_item (id, score, sequence_number, program_section_enrollment_id, date, type, version)
SELECT 3000000 + row_number() over (order by e.id, k), 1 + ((e.id*k) % 10), k, e.id, timestamp '2026-06-01' + k*interval '7 days', CASE WHEN k<=3 THEN 'TP' ELSE 'EXAM' END, 0
FROM subject_section_enrollment e, generate_series(1,2) k WHERE e.id >= 100000 AND e.status IN ('APPROVED','ENROLLED');

INSERT INTO student_current_account (id, version, student_id, currency, balance_amount, balance_amount_currency, balance_side, unallocated_credit_amount, unallocated_credit_amount_currency, last_entry_sequence, opened_at)
SELECT 20000+g, 0, 20000+g, 'ARS',
       CASE WHEN g%5<3 THEN (1 + (g*g)%4)*80000 ELSE 0 END, 'ARS', CASE WHEN g%5<3 THEN 'OWES' ELSE 'ZERO' END, 0, 'ARS', 0, timestamp '2026-01-15'
FROM generate_series(1,5000) g;
INSERT INTO invoice (id, version, student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status, payment_status, invoice_number, issue_date, due_date, lines_total, lines_total_currency, discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency, credited_amount, credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at, student_snapshot)
SELECT 500000 + s.g*20 + m, 0, 20000+s.g, 'ARS', 'INVOICE', 'MONTHLY_INSTALLMENT', 'MONTHLY_RUN', 'ISSUED',
       CASE WHEN s.g%5<3 AND m > 6-(1+(s.g*s.g)%4) THEN 'OVERDUE' ELSE 'PAID' END, 'F-'||lpad((500000 + s.g*20 + m)::text,8,'0'),
       date '2026-03-01' + (m-1)*30, date '2026-03-10' + (m-1)*30, 80000,'ARS',0,'ARS',80000,'ARS',
       CASE WHEN s.g%5<3 AND m > 6-(1+(s.g*s.g)%4) THEN 0 ELSE 80000 END,'ARS',0,'ARS',CASE WHEN s.g%5<3 AND m > 6-(1+(s.g*s.g)%4) THEN 80000 ELSE 0 END,'ARS', timestamp '2026-03-01', timestamp '2026-03-01',
       jsonb_build_object('fullName','Nombre'||s.g||' Apellido'||s.g,'documentNumber',(30000000+s.g)::text,'address','Calle '||s.g)
FROM generate_series(1,5000) s(g), generate_series(1,6) m;
INSERT INTO invoice_line (id, invoice_id, currency, line_number, fee_type, concept, period, program_enrollment_id, price_version_id, charge_key, amount, amount_currency, discount_amount, discount_amount_currency, credited_amount, credited_amount_currency, free_credited_amount_currency)
SELECT 500000 + s.g*20 + m + 10000000, 500000 + s.g*20 + m, 'ARS', 1, 'MONTHLY_INSTALLMENT', 'Cuota '||m, date '2026-03-01' + (m-1)*30, 20000+s.g, 1, 'perf-'||s.g||'-'||m, 80000,'ARS',0,'ARS',0,'ARS','ARS'
FROM generate_series(1,5000) s(g), generate_series(1,6) m;

INSERT INTO notification (recipient_id, type, message, read, related_entity_type, related_entity_id, created_at)
SELECT CASE WHEN n%10=0 THEN (SELECT id FROM person WHERE username='director.demo') ELSE 20001 + floor(5000*power(((n*7919)%10000)/10000.0, 2))::int END,
       (ARRAY['ANNOUNCEMENT_PUBLISHED','CLOSURE_APPROVED','ATTENDANCE_JUSTIFICATION_APPROVED'])[1+n%3], 'Notificación perf '||n, (n%7<4), 'STUDENT', 20001+(n%5000), timestamp '2026-03-01' + n*interval '4 minutes'
FROM generate_series(1,50000) n;

INSERT INTO audit_log (occurred_at, actor_person_id, actor_role, action, entity_type, entity_id, before_value, after_value, reason)
SELECT timestamp '2026-03-01' + n*interval '2 minutes', (SELECT id FROM person WHERE username='director.demo'), 'DIRECTOR',
       (ARRAY['STUDENT_WITHDRAWN','STUDENT_REACTIVATED','PASSWORD_CHANGED','ENROLLMENT_SECTION_CHANGED_BY_STAFF'])[1+n%4],
       CASE WHEN n%4=2 THEN 'PERSON' ELSE 'STUDENT' END, 20001 + floor(5000*power(((n::bigint*104729)%10000)/10000.0, 2))::int, 'ACTIVE', 'INACTIVE', 'perf '||n
FROM generate_series(1,100000) n;
COMMIT;
ANALYZE;
