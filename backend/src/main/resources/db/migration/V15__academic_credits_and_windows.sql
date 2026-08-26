-- Spec Estructura académica. Créditos y ventanas de admisión/inscripción. Default 0
-- porque no existe servicio todavía que haya podido crear filas de program/program_subject.
alter table program add column total_credits_required integer not null default 0;
alter table program add column admission_start timestamp;
alter table program add column admission_end timestamp;

alter table program_subject add column credits integer not null default 0;

alter table term add column enrollment_start timestamp;
alter table term add column enrollment_end timestamp;
