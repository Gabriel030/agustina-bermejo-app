\set ON_ERROR_STOP on
create extension if not exists pg_stat_statements;
BEGIN;
INSERT INTO term (id, year_number, start_date, end_date, description, type, status, enrollment_start, enrollment_end, enrollment_override)
SELECT 100+g, 2022+g, make_date(2022+g,3,1), make_date(2022+g,12,20), 'Ciclo '||(2022+g), 'ANNUAL', 'INACTIVE', make_date(2022+g,1,1), make_date(2022+g,12,20), 'OPEN'
FROM generate_series(1,3) g;

INSERT INTO subject (id, name, status, max_final_exam_attempts, code, weekly_hours, grade_mode)
SELECT 1000+g, 'Materia perf '||g, 'ACTIVE', 3, 'P'||lpad(g::text,3,'0'), 4, 'NUMERIC' FROM generate_series(1,40) g;
INSERT INTO program_subject (id, program_id, subject_id, curricular_year, curricular_term, subject_recurrence, status, credits, sort_order)
SELECT 1000+g, 1, 1000+g, 1+(g-1)/14, 'FIRST_SEMESTER', 'ANNUAL', 'ACTIVE', 6, g FROM generate_series(1,40) g;

INSERT INTO subject_section (id, term_id, subject_id, name, capacity, created_at, version, status, schedule, max_absence_percentage, pending_approval, evaluation_scheme_id)
SELECT 10000 + k*1000 + s*10 + c, CASE WHEN k=4 THEN 1 ELSE 100+k END, 1000+s, 'Comisión '||chr(64+c), 45, now(), 0, 'ACTIVE', 'Lunes 18:00 a 20:00', 0.25, false, 1
FROM generate_series(1,4) k, generate_series(1,40) s, generate_series(1,2) c;

INSERT INTO address (id, city, country, number, postal_code, state, street)
SELECT 20000+g, 'Ciudad', 'AR', g::text, '1000', 'Prov', 'Calle '||g FROM generate_series(1,700) g;
INSERT INTO person (address_id, id, dni, email, file, last_name, name, password, status, username, auth_provider, failed_login_attempts, email_verified, document_type)
SELECT 20000+g, 20000+g, (30000000+g)::text, 'perf'||g||'@perf.local', 'PF-'||g, 'Apellido'||g, 'Nombre'||g, (SELECT password FROM person WHERE id=1), 'ACTIVE', 'perf'||g, 'LOCAL', 0, true, 'DNI' FROM generate_series(1,700) g;
INSERT INTO person_role (person_id, role_id) SELECT 20000+g, CASE WHEN g<=650 THEN 1 ELSE 2 END FROM generate_series(1,700) g;
INSERT INTO student (id, institution_id, person_id, onboarding_status) SELECT 20000+g, 1, 20000+g, 'CONFIRMADO' FROM generate_series(1,650) g;
INSERT INTO instructor (id, person_id) SELECT 20000+g, 20000+g FROM generate_series(651,700) g;

INSERT INTO program_enrollment (id, program_id, student_id, status, version, enrollment_date)
SELECT 20000+g, 1+(g%2), 20000+g, 'ENROLLED', 0, now() - interval '400 days' FROM generate_series(1,650) g;

INSERT INTO subject_section_instructor (id, instructor_id, program_section_id, role)
SELECT row_number() over () + 5000, 20650 + 1 + (ss.id % 50), ss.id, 'LEAD' FROM subject_section ss WHERE ss.id >= 10000;

-- 8 enrollments per student, spread over the 3 terms (section ids deterministic)
INSERT INTO subject_section_enrollment (id, program_enrollment_id, program_section_id, student_id, status, version, attempt_number, enrolled_at, final_score)
SELECT 100000 + s.g*10 + k, 20000+s.g, 10000 + (((k%4)+1)*1000) + (1 + ((s.g*7 + k*5) % 40))*10 + (1 + (s.g + k) % 2), 20000+s.g,
       CASE WHEN k%4 < 3 THEN 'APPROVED' ELSE 'ENROLLED' END, 0, 1, now() - interval '200 days', CASE WHEN k%4<3 THEN 4 + (s.g*k % 7) ELSE NULL END
FROM generate_series(1,650) s(g), generate_series(1,8) k;

-- sessions: 24 per 2026 section (term 103), attendance and grades
INSERT INTO class_session (id, version, program_section_id, class_date, status, created_by_id, created_at)
SELECT 200000 + ss.id*30 + d, 0, ss.id, date '2026-03-02' + d*7, CASE WHEN date '2026-03-02' + d*7 < current_date THEN 'HELD' ELSE 'SCHEDULED' END, 1, now()
FROM subject_section ss, generate_series(0,23) d WHERE ss.term_id=1 AND ss.id>=10000;
INSERT INTO attendance (id, version, program_section_enrollment_id, class_date, status, recorded_by_id)
SELECT 1000000 + row_number() over (), 0, e.id, cs.class_date,
       (ARRAY['PRESENT','PRESENT','PRESENT','PRESENT','ABSENT','LATE','JUSTIFIED'])[1 + ((e.id + cs.id) % 7)], 1
FROM subject_section_enrollment e JOIN class_session cs ON cs.program_section_id = e.program_section_id WHERE cs.status='HELD' AND e.id >= 100000;
INSERT INTO grade_item (id, score, sequence_number, program_section_enrollment_id, date, type, version)
SELECT 3000000 + row_number() over (), 1 + ((e.id*k) % 10), k, e.id, now() - (k||' weeks')::interval, CASE WHEN k<=3 THEN 'TP' ELSE 'EXAM' END, 0
FROM subject_section_enrollment e, generate_series(1,5) k WHERE e.id >= 100000;

INSERT INTO student_current_account (id, version, student_id, currency, balance_amount, balance_amount_currency, balance_side, unallocated_credit_amount, unallocated_credit_amount_currency, last_entry_sequence, opened_at)
SELECT 20000+g, 0, 20000+g, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', 0, now() - interval '300 days' FROM generate_series(1,650) g;
INSERT INTO invoice (id, version, student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status, payment_status, invoice_number, issue_date, due_date, lines_total, lines_total_currency, discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency, credited_amount, credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at, student_snapshot)
SELECT 500000 + s.g*20 + m, 0, 20000+s.g, 'ARS', 'INVOICE', 'MONTHLY_INSTALLMENT', 'MONTHLY_RUN', 'ISSUED',
       CASE WHEN m<=6 THEN 'PAID' WHEN m<=8 THEN 'OVERDUE' ELSE 'PENDING' END, 'F-'||lpad((500000 + s.g*20 + m)::text,8,'0'),
       date '2026-03-01' + (m-1)*30, date '2026-03-10' + (m-1)*30, 80000,'ARS',0,'ARS',80000,'ARS',
       CASE WHEN m<=6 THEN 80000 ELSE 0 END,'ARS',0,'ARS',CASE WHEN m<=6 THEN 0 ELSE 80000 END,'ARS', now(), now(), jsonb_build_object('fullName','Nombre'||s.g||' Apellido'||s.g,'documentNumber',(30000000+s.g)::text,'address','Calle '||s.g)
FROM generate_series(1,650) s(g), generate_series(1,10) m;
INSERT INTO invoice_line (id, invoice_id, currency, line_number, fee_type, concept, period, program_enrollment_id, price_version_id, charge_key, amount, amount_currency, discount_amount, discount_amount_currency, credited_amount, credited_amount_currency, free_credited_amount_currency)
SELECT 500000 + s.g*20 + m + 10000000, 500000 + s.g*20 + m, 'ARS', 1, 'MONTHLY_INSTALLMENT', 'Cuota '||m, date '2026-03-01' + (m-1)*30, 20000+s.g, 1, 'perf-'||s.g||'-'||m, 80000,'ARS',0,'ARS',0,'ARS','ARS'
FROM generate_series(1,650) s(g), generate_series(1,10) m;
COMMIT;
ANALYZE;
BEGIN;
INSERT INTO classroom (id, name, capacity, status) SELECT 100+g, 'Aula '||g, 20+g*3, 'ACTIVE' FROM generate_series(1,15) g;
INSERT INTO section_schedule_slot (id, subject_section_id, classroom_id, day_of_week, start_time, end_time)
SELECT 100000 + row_number() over (), ss.id, 101 + (ss.id % 15), (ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY'])[1 + (ss.id/10 % 5)], time '18:00', time '20:00'
FROM subject_section ss WHERE ss.id >= 14000;
COMMIT;
