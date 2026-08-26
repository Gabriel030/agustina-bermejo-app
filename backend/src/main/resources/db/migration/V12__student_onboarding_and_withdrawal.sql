-- Spec Gestión de personas. onboarding_status distingue "prospecto" (preinscripción
-- pública, sin documentación verificada) de "confirmado" (alta definitiva). Default
-- CONFIRMADO para que los Student ya existentes (insertados antes de esta spec) no
-- queden en un estado que no les corresponde.
alter table student add column onboarding_status varchar(20) not null default 'CONFIRMADO';

-- Motivo de baja (RF-PR-03): enum acotado + detalle libre, ambos nullable porque solo
-- se completan cuando el alumno efectivamente causa baja.
alter table student add column withdrawal_reason varchar(20);
alter table student add column withdrawal_detail varchar(500);
