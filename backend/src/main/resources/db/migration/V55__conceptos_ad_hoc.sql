-- Fase 6.7: conceptos ad-hoc (derecho de examen, recursada, emisión de título, trámites
-- administrativos) — cobros puntuales que no siguen la plantilla anual ni el calendario mensual.
--
-- Corrección respecto de la spec original (Fase 6.1 nunca las declaró, a pesar de lo que decía
-- el comentario de esa migración): subject_id/exam_board_registration_id no existían todavía,
-- se agregan recién acá. Ambas nullable y sin CHECK de exclusividad entre sí — un EXAM_RIGHT
-- referencia naturalmente las dos (la materia y la mesa puntual).
alter table invoice
    add column subject_id bigint,
    add column exam_board_registration_id bigint,
    add column description varchar(500);

alter table invoice
    add constraint fk_invoice_subject foreign key (subject_id) references subject,
    add constraint fk_invoice_exam_board_registration foreign key (exam_board_registration_id) references exam_board_registration;

create index ix_invoice_subject on invoice (subject_id);
create index ix_invoice_exam_board_registration on invoice (exam_board_registration_id);

-- uk_invoice_billable_period ya quedó acotado a los FeeType recurrentes desde V45 (con el nombre
-- RECURRING_FEE) — los FeeType ad-hoc nuevos (EXAM_RIGHT, RETAKEN_SUBJECT_FEE, DEGREE_ISSUANCE,
-- ADMINISTRATIVE_FEE) ya quedan fuera de ese índice sin tocar nada más.
