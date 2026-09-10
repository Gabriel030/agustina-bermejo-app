-- Fase 6.0: baja completa del sistema de facturación viejo (Fase 4: invoice/payment) y de todo
-- lo mergeado de Épica 8 (Fase 1: precios/mora/becas; Fase 2: cohortes y motor de facturación
-- recurrente) — confirmado obsoleto frente a Fase 6 (Facturación y cobranzas), ver
-- "Roadmap backend/Facturación y cobranzas/Plan de implementación" en el vault.

-- Primero las columnas que referencian tablas a eliminar, para no chocar con las FK.
alter table program_enrollment_application drop column if exists payment_receipt_id;
alter table program_enrollment_application drop column if exists program_cohort_id;
alter table course_enrollment_application drop column if exists payment_receipt_id;
alter table program_enrollment drop column if exists program_cohort_id;

drop table if exists payment_receipt;
drop table if exists payment;
drop table if exists invoice;

drop table if exists promotion;
drop table if exists discount;
drop table if exists billing_period;
drop table if exists late_fee_policy;
drop table if exists fee_schedule;
drop table if exists program_cohort;

alter table program drop column if exists installments_per_academic_year;
