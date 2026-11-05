set session_replication_role = replica;

insert into term (id, year_number, type, status, description, start_date, end_date, enrollment_start, enrollment_end)
overriding system value values
    (9001, 2025, 'FIRST_SEMESTER', 'CLOSED', 'periodo sin fechas de inscripcion', '2025-03-01', '2025-07-31', null, null),
    (9002, 2025, 'SECOND_SEMESTER', 'CLOSED', 'periodo sin fechas', null, null, null, null);

insert into holiday (date, description) values ('2025-05-01', 'Dia del Trabajador'), ('2025-07-09', 'Dia de la Independencia');

insert into grade_item (id, type, sequence_number, score, program_section_enrollment_id, version)
overriding system value values
    (9101, 'PARTIAL', 1, 6.00, 1, 0),
    (9102, 'PARTIAL', 1, 8.00, 1, 0),
    (9103, 'PARTIAL', 1, 9.00, 1, 0),
    (9104, 'FINAL', null, 7.00, 1, 0);
insert into grade_item (id, type, sequence_number, score, course_section_enrollment_id, version)
overriding system value values
    (9105, 'PARTIAL', 2, 5.00, 2, 0),
    (9106, 'PARTIAL', 2, 4.00, 2, 0);

insert into invoice_discount (id, invoice_line_id, discount_type, concept, currency, base_amount, base_amount_currency, discount_amount, discount_amount_currency)
overriding system value values (9201, 1, 'ANNUAL_UPFRONT', 'descuento de ejemplo', 'ARS', 1000, 'ARS', 100, 'ARS');

reset session_replication_role;
