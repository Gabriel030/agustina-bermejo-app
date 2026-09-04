-- Decisión 2026-09-04: "TUITION" resultaba confuso — se renombra a "RECURRING_FEE" (cuota
-- periódica de cursada, en contraposición a ENROLLMENT_FEE que es el pago único de matrícula).
-- Actualiza las filas existentes antes de tocar el índice/columna que las referencian.
update invoice set fee_type = 'RECURRING_FEE' where fee_type = 'TUITION';

alter table program_year_billing_template
    rename column tuition_invoices_count to recurring_fee_invoices_count;

-- El índice único parcial de V43 tenía el valor viejo hardcodeado en su predicado — hay que
-- recrearlo con el nombre nuevo (mismas columnas, mismo predicado salvo ese cambio).
drop index uk_invoice_billable_period;

create unique index uk_invoice_billable_period on invoice
    (coalesce(course_enrollment_id, 0), coalesce(program_enrollment_id, 0), fee_type, period)
    where fee_type in ('RECURRING_FEE', 'ENROLLMENT_FEE', 'SUBSCRIPTION_FEE')
      and (course_enrollment_id is not null or program_enrollment_id is not null);
