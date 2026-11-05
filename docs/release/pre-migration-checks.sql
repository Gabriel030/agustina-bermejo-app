\set ON_ERROR_STOP off
\pset null '(null)'
\echo ===== Diagnostico de solo lectura previo a V111..V182 (correr sobre una base en V110) =====
begin read only;

\echo
\echo [V111] Avisos de cobranza PROPOSED o SCHEDULED (la migracion ABORTA si hay filas): 'Hay una propuesta o version programada de Avisos de cobranza...'
select id, scope, status from configuration_version where scope = 'COLLECTION_REMINDERS' and status in ('PROPOSED', 'SCHEDULED') order by id;

\echo
\echo [V123] Asientos de cuenta corriente con payment_id que no esta en payment (la migracion ABORTA): 'V123 aborted: N current account entr(ies) point to a payment...'
select count(*) as entries_pointing_to_legacy_payments from current_account_entry e
where e.payment_id is not null
  and not exists (select 1 from payment p where p.id = e.payment_id and p.student_current_account_id = e.student_current_account_id);
\echo [V123] Pagos legacy que V123 DESCARTA (tabla legacy_payment se borra sin backfill):
select count(*) as legacy_payment_rows, coalesce(sum(amount), 0) as legacy_payment_total from legacy_payment;

\echo
\echo [V130] Comprobantes de transferencia con payment_id que no esta en payment (la migracion ABORTA): 'V130 aborted: N bank transfer receipt(s)...'
select count(*) as receipts_pointing_to_legacy_payments from bank_transfer_receipt r
where r.payment_id is not null and not exists (select 1 from payment p where p.id = r.payment_id);

\echo
\echo [V132] Becas legacy ACTIVE (la migracion ABORTA): 'Cannot drop the legacy scholarship tables while ACTIVE legacy scholarships exist...'
select status, count(*) as scholarships from scholarship group by status order by status;
\echo [V132] Becas legacy NO activas que V132 DESCARTA junto con sus cuotas:
select count(*) as scholarship_installments from scholarship_installment;

\echo
\echo [V139] Canjes de promocion con plan abierto y cuotas pendientes (la migracion ABORTA): 'Cannot drop the legacy promotion tables: N redemption(s)...'
select count(*) as redemptions_with_pending_installments
from promotion_redemption r
join payment_plan_selection s on s.id = r.payment_plan_selection_id
join promotion_benefit b on b.promotion_id = r.promotion_id
where s.effective_to is null
  and b.invoice_scope_count is not null
  and b.invoice_scope_count > (select count(*)
                               from invoice_discount d
                               join invoice_line l on l.id = d.invoice_line_id
                               join invoice i on i.id = l.invoice_id
                               join student_current_account a on a.id = i.student_current_account_id
                               where d.discount_type = 'PROMOTION'
                                 and d.student_discount_id = b.id
                                 and i.document_status <> 'DISCARDED'
                                 and a.student_id = coalesce((select pe.student_id from program_enrollment pe where pe.id = s.program_enrollment_id),
                                                             (select ce.student_id from course_enrollment ce where ce.id = s.course_enrollment_id)));
\echo [V139] Promociones legacy que V139 DESCARTA:
select (select count(*) from promotion) as promotions, (select count(*) from promotion_redemption) as redemptions;

\echo
\echo [V146] CONFLICTO V132/V139 -> V146 (la migracion ABORTA): 'invoice_discount has N row(s) without student_discount_id; link or remove them before enforcing D-199'
\echo         V132 y V139 ponen student_discount_id = null y ninguna migracion lo rellena; V146 exige NOT NULL. Cualquier fila en invoice_discount lo dispara.
select discount_type, count(*) as rows_total, count(*) filter (where student_discount_id is null) as rows_without_student_discount
from invoice_discount group by discount_type order by discount_type;

\echo
\echo [V160] Inscripciones cuyo payment_status se copia a enrollment_payment_standing y se BORRA de la inscripcion (irreversible); last_closed_month = mes anterior a la fecha de migracion:
select 'program_enrollment' as source, payment_status, count(*) from program_enrollment group by payment_status
union all
select 'course_enrollment', payment_status, count(*) from course_enrollment group by payment_status order by 1, 2;

\echo
\echo [V172] Calificaciones duplicadas que V172 BORRA (conserva el id mayor) por inscripcion + tipo + secuencia:
select 'program' as target, program_section_enrollment_id as enrollment_id, type, sequence_number,
       count(*) as copies, count(*) - 1 as rows_deleted,
       array_agg(id order by id) as ids, array_agg(score order by id) as scores, array_agg(exam_board_id order by id) as exam_boards
from grade_item where program_section_enrollment_id is not null
group by program_section_enrollment_id, type, sequence_number having count(*) > 1
union all
select 'course', course_section_enrollment_id, type, sequence_number,
       count(*), count(*) - 1, array_agg(id order by id), array_agg(score order by id), array_agg(exam_board_id order by id)
from grade_item where course_section_enrollment_id is not null
group by course_section_enrollment_id, type, sequence_number having count(*) > 1
order by 1, 2, 3, 4;
\echo [V172] Total de filas de grade_item que se borraran (cero esperado):
select coalesce(sum(copies - 1), 0) as grade_items_to_delete from (
    select count(*) as copies from grade_item where program_section_enrollment_id is not null
    group by program_section_enrollment_id, type, coalesce(sequence_number, -1) having count(*) > 1
    union all
    select count(*) from grade_item where course_section_enrollment_id is not null
    group by course_section_enrollment_id, type, coalesce(sequence_number, -1) having count(*) > 1) d;

\echo
\echo [V174/V176] Periodos (term) con fechas que la migracion INVENTA (no aborta; revisar que las fechas resultantes sean aceptables):
\echo   enrollment_start nulo -> start_date, o now() (la fecha de migracion); enrollment_end nulo o anterior al inicio -> end_date, o start_date + 1 anio.
\echo   start_date nulo -> enrollment_start o now(); end_date nulo o anterior al inicio -> enrollment_end o start_date + 1 anio.
select id, type, year_number, description, start_date, end_date, enrollment_start, enrollment_end,
       (enrollment_start is null) as will_invent_enrollment_start,
       (enrollment_end is null or enrollment_end < enrollment_start) as will_invent_or_fix_enrollment_end,
       (start_date is null) as will_invent_start_date,
       (end_date is null or end_date < start_date) as will_invent_or_fix_end_date
from term
where enrollment_start is null or enrollment_end is null or enrollment_end < enrollment_start
   or start_date is null or end_date is null or end_date < start_date
order by id;

\echo
\echo [V180] Feriados: todos quedan con type = NATIONAL y name = description (no aborta). Revisar si hay feriados provinciales o locales cargados:
select count(*) as holidays, min(date) as first_date, max(date) as last_date from holiday;

\echo
\echo [V179] Comisiones a las que se les rellena shift (segun el primer horario; sin horario queda MORNING) y code (segun tipo y anio del periodo). No aborta; son datos derivados:
select count(*) as sections from subject_section;

rollback;
\echo ===== fin =====
