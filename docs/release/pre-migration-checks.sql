\set ON_ERROR_STOP off
\pset null '(null)'
set default_transaction_read_only = on;
select to_regclass('flyway_schema_history') is not null as has_history \gset
\if :has_history
select coalesce(max(version::int), 0) as current_version from flyway_schema_history where success and version ~ '^[0-9]+$' \gset
\else
\set current_version 0
\endif
\echo ===== Diagnostico de solo lectura previo a las migraciones pendientes (V111 hasta la ultima) =====
\echo Version actual de la base: :current_version
\echo Cada bloque [V<n>] corre solo si la migracion V<n> todavia no esta aplicada (version actual < n); los bloques de migraciones ya aplicadas se omiten.
\echo Los bloques [V470] corren siempre y van al final: los lee SectionScheduleSlotExcludeMigrationTest.
\echo Cada consulta corre en su propia sentencia (sin transaccion): un error aislado no invalida las siguientes.

select :current_version < 111 as run_block \gset
\if :run_block
\echo
\echo [V111] Avisos de cobranza PROPOSED o SCHEDULED (la migracion ABORTA si hay filas): 'Hay una propuesta o version programada de Avisos de cobranza...'
select id, scope, status from configuration_version where scope = 'COLLECTION_REMINDERS' and status in ('PROPOSED', 'SCHEDULED') order by id;
\endif

select :current_version < 123 as run_block \gset
\if :run_block
\echo
\echo [V123] Asientos de cuenta corriente con payment_id que no esta en payment (la migracion ABORTA): 'V123 aborted: N current account entr(ies) point to a payment...'
select count(*) as entries_pointing_to_legacy_payments from current_account_entry e
where e.payment_id is not null
  and not exists (select 1 from payment p where p.id = e.payment_id and p.student_current_account_id = e.student_current_account_id);
\endif

select :current_version < 123 as run_block \gset
\if :run_block
\echo [V123] Pagos legacy que V123 DESCARTA (tabla legacy_payment se borra sin backfill):
select count(*) as legacy_payment_rows, coalesce(sum(amount), 0) as legacy_payment_total from legacy_payment;
\endif

select :current_version < 130 as run_block \gset
\if :run_block
\echo
\echo [V130] Comprobantes de transferencia con payment_id que no esta en payment (la migracion ABORTA): 'V130 aborted: N bank transfer receipt(s)...'
select count(*) as receipts_pointing_to_legacy_payments from bank_transfer_receipt r
where r.payment_id is not null and not exists (select 1 from payment p where p.id = r.payment_id);
\endif

select :current_version < 132 as run_block \gset
\if :run_block
\echo
\echo [V132] Becas legacy ACTIVE (la migracion ABORTA): 'Cannot drop the legacy scholarship tables while ACTIVE legacy scholarships exist...'
select status, count(*) as scholarships from scholarship group by status order by status;
\endif

select :current_version < 132 as run_block \gset
\if :run_block
\echo [V132] Becas legacy NO activas que V132 DESCARTA junto con sus cuotas:
select count(*) as scholarship_installments from scholarship_installment;
\endif

select :current_version < 139 as run_block \gset
\if :run_block
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
\endif

select :current_version < 139 as run_block \gset
\if :run_block
\echo [V139] Promociones legacy que V139 DESCARTA:
select (select count(*) from promotion) as promotions, (select count(*) from promotion_redemption) as redemptions;
\endif

select :current_version < 146 as run_block \gset
\if :run_block
\echo
\echo [V146] CONFLICTO V132/V139 -> V146 (la migracion ABORTA): 'invoice_discount has N row(s) without student_discount_id; link or remove them before enforcing D-199'
\echo         V132 y V139 ponen student_discount_id = null y ninguna migracion lo rellena; V146 exige NOT NULL. Cualquier fila en invoice_discount lo dispara.
select discount_type, count(*) as rows_total, count(*) filter (where student_discount_id is null) as rows_without_student_discount
from invoice_discount group by discount_type order by discount_type;
\endif

select :current_version < 160 as run_block \gset
\if :run_block
\echo
\echo [V160] Inscripciones cuyo payment_status se copia a enrollment_payment_standing y se BORRA de la inscripcion (irreversible); last_closed_month = mes anterior a la fecha de migracion:
select 'program_enrollment' as source, payment_status, count(*) from program_enrollment group by payment_status
union all
select 'course_enrollment', payment_status, count(*) from course_enrollment group by payment_status order by 1, 2;
\endif

select :current_version < 172 as run_block \gset
\if :run_block
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
\endif

select :current_version < 172 as run_block \gset
\if :run_block
\echo [V172] Total de filas de grade_item que se borraran (cero esperado):
select coalesce(sum(copies - 1), 0) as grade_items_to_delete from (
    select count(*) as copies from grade_item where program_section_enrollment_id is not null
    group by program_section_enrollment_id, type, coalesce(sequence_number, -1) having count(*) > 1
    union all
    select count(*) from grade_item where course_section_enrollment_id is not null
    group by course_section_enrollment_id, type, coalesce(sequence_number, -1) having count(*) > 1) d;
\endif

select :current_version < 174 as run_block \gset
\if :run_block
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
\endif

select :current_version < 180 as run_block \gset
\if :run_block
\echo
\echo [V180] Feriados: todos quedan con type = NATIONAL y name = description (no aborta). Revisar si hay feriados provinciales o locales cargados:
select count(*) as holidays, min(date) as first_date, max(date) as last_date from holiday;
\endif

select :current_version < 179 as run_block \gset
\if :run_block
\echo
\echo [V179] Comisiones a las que se les rellena shift (segun el primer horario; sin horario queda MORNING) y code (segun tipo y anio del periodo). No aborta; son datos derivados:
select count(*) as sections from subject_section;
\endif

select :current_version < 209 as run_block \gset
\if :run_block
\echo
\echo [V209/V210] CHECK de enums (la migracion ABORTA si hay valores fuera del enum de Java; no hay filas en la salida si todo esta bien). Se omiten tablas o columnas que la base aun no tiene. Cada fila: tabla, columna, valor, cantidad:
do $$
declare
  spec record;
  found record;
begin
  for spec in
    select * from (values
      ('academic_offering_cycle', 'status', array['APPROVED', 'CLOSED', 'DRAFT', 'PENDING_APPROVAL', 'REJECTED']),
      ('announcement', 'audience_key', array['ABSENCES', 'ALL', 'EXAM_FAILED', 'EXAM_MISSING', 'EXAM_PASSED', 'FAILED_COURSE', 'FINAL_FAILED', 'FINAL_PASSED', 'MUST_TAKE_FINAL', 'PASSED', 'PROMOTED', 'TP_FAILED', 'TP_MISSING', 'TP_PASSED']),
      ('announcement', 'audience_quantifier', array['ALL', 'ANY']),
      ('announcement', 'category', array['ADMIN', 'EVALUATION', 'MATERIAL', 'REMINDER', 'SCHEDULING']),
      ('announcement', 'status', array['SCHEDULED', 'SENT']),
      ('approved_subjects_certificate', 'recipient_type', array['EMPLOYER', 'HEALTH_INSURANCE', 'OTHER', 'STUDENT_TRANSPORT']),
      ('approved_subjects_certificate', 'status', array['EXPIRED', 'REVOKED', 'VALID']),
      ('attendance', 'status', array['ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT']),
      ('attendance_change_request', 'previous_status', array['ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT']),
      ('attendance_change_request', 'requested_status', array['ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT']),
      ('attendance_change_request', 'status', array['APPROVED', 'PENDING', 'REJECTED']),
      ('attendance_correction', 'corrected_status', array['ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT']),
      ('attendance_correction', 'previous_status', array['ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT']),
      ('authorization_request', 'operation_type', array['ACQUIRE_COHORT_PROMOTION', 'ALLOCATE_ACCOUNT_CREDIT', 'APPROVE_BANK_TRANSFER_RECEIPT_PAYMENT', 'APPROVE_DRAFT_INVOICES', 'APPROVE_PENDING_BANK_TRANSFER_PAYMENT', 'CANCEL_CONFIGURATION_VERSION', 'CANCEL_INVOICE', 'CANCEL_INVOICES_AFTER_WITHDRAWAL', 'CANCEL_PRICE_VERSION', 'CHANGE_BILLING_CONFIGURATION', 'CHANGE_PRICE', 'CLOSE_DISCOUNT_DEFINITION', 'COMPLETE_REFUND', 'CREATE_DISCOUNT_DEFINITION', 'CREATE_LATE_PAYMENT_SETTINGS', 'DISCARD_ANNUAL_UPFRONT_REQUEST', 'FAIL_REFUND', 'GRANT_STUDENT_DISCOUNT', 'ISSUE_CREDIT_NOTE', 'ISSUE_INVOICE', 'ISSUE_LATE_FEE_CORRECTION_CREDIT_NOTE', 'ISSUE_LATE_FEE_DEBIT_NOTE', 'ISSUE_REFUND', 'REGISTER_ANNUAL_UPFRONT_CASH_PAYMENT', 'REGISTER_CHARGEBACK', 'REGISTER_GATEWAY_PAYMENT', 'REGISTER_PAYMENT', 'REJECT_PENDING_BANK_TRANSFER_PAYMENT', 'REQUEST_ANNUAL_UPFRONT', 'REVERSE_CHARGEBACK', 'REVOKE_BANK_TRANSFER_APPROVAL', 'REVOKE_STUDENT_DISCOUNT', 'SUBMIT_BANK_TRANSFER_RECEIPT']),
      ('authorization_request', 'required_role', array['DIRECTOR', 'DIRECTOR_OR_ADMINISTRATIVE']),
      ('authorization_request_status_change', 'event', array['APPROVE', 'EXPIRE', 'OPEN', 'REJECT', 'WITHDRAW']),
      ('authorization_request_status_change', 'from_status', array['APPROVED', 'EXPIRED', 'PENDING', 'REJECTED', 'WITHDRAWN']),
      ('authorization_request_status_change', 'to_status', array['APPROVED', 'EXPIRED', 'PENDING', 'REJECTED', 'WITHDRAWN']),
      ('bank_transfer_receipt', 'declared_amount_currency', array['ARS', 'USD']),
      ('bank_transfer_receipt', 'status', array['APPLIED_TO_DEBT', 'APPROVED', 'PENDING_REVIEW', 'REJECTED']),
      ('batch_execution_log', 'status', array['FAILED', 'PARTIAL_FAILURE', 'SUCCESS']),
      ('billing_run', 'step', array['ACCOUNT_CONSISTENCY', 'ACCOUNT_CREDIT_ALLOCATION', 'ADVANCE_ENROLLMENT_FEE_EXPIRY', 'ANNUAL_UPFRONT_EXPIRY', 'COLLECTIONS_SNAPSHOT', 'COLLECTION_REMINDER_PLANNING', 'ENROLLMENT_BILLING_COMPLIANCE', 'GATEWAY_SYNC', 'LATE_FEE_ACCRUAL', 'LATE_FEE_WITHDRAWAL_CORRECTION', 'OVERDUE_DETECTION', 'TERM_END_WITHDRAWAL']),
      ('billing_run_item', 'subject_type', array['ANNUAL_UPFRONT_REQUEST', 'COURSE_ENROLLMENT', 'CURRENT_ACCOUNT', 'INVOICE', 'LATE_FEE_CHARGE', 'PROGRAM_ENROLLMENT', 'STUDENT']),
      ('certificate_exception_approval', 'kind', array['COURSE', 'PROGRAM']),
      ('certificate_exception_approval', 'organism_kind', array['CONSUMER_DEFENSE', 'COURT', 'EDUCATION_MINISTRY', 'OTHER']),
      ('certificate_exception_approval', 'status', array['APPROVED', 'CONSUMED', 'EXPIRED', 'PENDING', 'REJECTED', 'WITHDRAWN']),
      ('certificate_exception_status_change', 'event', array['APPROVE', 'CONSUME', 'DIRECT_APPROVE', 'EXPIRE', 'REJECT', 'REQUEST', 'WITHDRAW']),
      ('certificate_exception_status_change', 'from_status', array['APPROVED', 'CONSUMED', 'EXPIRED', 'PENDING', 'REJECTED', 'WITHDRAWN']),
      ('certificate_exception_status_change', 'to_status', array['APPROVED', 'CONSUMED', 'EXPIRED', 'PENDING', 'REJECTED', 'WITHDRAWN']),
      ('certificate_issuance', 'type', array['APPROVED_SUBJECTS', 'REGULAR_STUDENT']),
      ('certificate_template', 'document_type', array['APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'REGULAR_STUDENT_CERTIFICATE']),
      ('certificate_template', 'kind', array['COURSE', 'PROGRAM']),
      ('certificate_template_image', 'purpose', array['LOGO', 'SIGNATURE']),
      ('certificate_template_version', 'status', array['DRAFT', 'PUBLISHED']),
      ('chargeback', 'amount_currency', array['ARS', 'USD']),
      ('class_session', 'status', array['CANCELED', 'HELD', 'SCHEDULED']),
      ('classroom', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('commission_closure_reminder', 'section_kind', array['COURSE', 'PROGRAM']),
      ('configuration_version_status_change', 'event', array['APPROVE_FOR_FUTURE', 'APPROVE_FOR_TODAY', 'CANCEL', 'EXPIRE', 'PROPOSE', 'PUBLISH_FOR_FUTURE', 'PUBLISH_FOR_TODAY', 'REACH_START', 'REJECT', 'SUPERSEDE', 'WITHDRAW']),
      ('configuration_version_status_change', 'from_status', array['CANCELLED', 'EFFECTIVE', 'EXPIRED', 'PROPOSED', 'REJECTED', 'SCHEDULED', 'SUPERSEDED', 'WITHDRAWN']),
      ('configuration_version_status_change', 'to_status', array['CANCELLED', 'EFFECTIVE', 'EXPIRED', 'PROPOSED', 'REJECTED', 'SCHEDULED', 'SUPERSEDED', 'WITHDRAWN']),
      ('course', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('course', 'teaching_mode', array['HYBRID', 'IN_PERSON', 'ONLINE']),
      ('course_enrollment', 'status', array['APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN']),
      ('course_section', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('course_section_enrollment', 'status', array['APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN']),
      ('course_section_instructor', 'role', array['ASSISTANT', 'GUEST', 'LAB', 'LEAD', 'PRACTICAL']),
      ('course_subject', 'curricular_term', array['ANNUAL', 'FIRST', 'SECOND']),
      ('course_subject', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('course_subject', 'subject_recurrence', array['ANNUAL', 'PROMOTIONAL']),
      ('course_subject_prerequisite', 'requirement_type', array['APPROVED', 'PASSED_COURSEWORK']),
      ('credit_note', 'free_credit_amount_currency', array['ARS', 'USD']),
      ('credit_note', 'offset_amount_currency', array['ARS', 'USD']),
      ('credit_note', 'total_amount_currency', array['ARS', 'USD']),
      ('credit_note_line', 'amount_currency', array['ARS', 'USD']),
      ('credit_note_line', 'free_part_currency', array['ARS', 'USD']),
      ('credit_note_line', 'offset_part_currency', array['ARS', 'USD']),
      ('current_account_entry', 'allocatable_amount_currency', array['ARS', 'USD']),
      ('current_account_entry', 'amount_currency', array['ARS', 'USD']),
      ('current_account_entry', 'currency', array['ARS', 'USD']),
      ('current_account_entry', 'running_balance_amount_currency', array['ARS', 'USD']),
      ('director_approval_request', 'status', array['APPROVED', 'REJECTED', 'REQUESTED']),
      ('director_approval_request', 'type', array['LATE_EXAM', 'PREREQUISITE_WAIVER']),
      ('discount_definition_status_change', 'event', array['DEFINITION_CLOSED', 'DEFINITION_CREATED']),
      ('discount_definition_status_change', 'from_status', array['ACTIVE', 'CLOSED']),
      ('discount_definition_status_change', 'to_status', array['ACTIVE', 'CLOSED']),
      ('document_debt_exception', 'document_type', array['APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'GRADUATION_CERTIFICATE', 'REGULAR_STUDENT_CERTIFICATE']),
      ('document_debt_exception', 'status', array['ACTIVE', 'EXPIRED', 'USED']),
      ('evaluation_scheme', 'aggregation_method', array['LAST_ATTEMPT', 'MAX_SCORE', 'SIMPLE_AVERAGE', 'WEIGHTED_AVERAGE']),
      ('exam_board', 'status', array['CANCELED', 'CLOSED', 'SCHEDULED']),
      ('exam_board_registration', 'status', array['ABSENT', 'GRADED', 'REGISTERED', 'WITHDRAWN']),
      ('exam_registration_certificate', 'recipient_type', array['EMPLOYER', 'HEALTH_INSURANCE', 'OTHER', 'STUDENT_TRANSPORT']),
      ('exam_registration_certificate', 'status', array['EXPIRED', 'REPLACED', 'REVOKED', 'VALID']),
      ('feature_change_log', 'code', array['ANALYTICS', 'BILLING', 'BILLING_NOTIFICATIONS', 'EXTERNAL_BILLING', 'FISCAL_INVOICING', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY']),
      ('gateway_checkout', 'currency', array['ARS', 'USD']),
      ('gateway_checkout', 'gateway', array['DLOCAL', 'MANUAL', 'MERCADO_PAGO']),
      ('grade_item', 'type', array['EXAM', 'FINAL', 'TP']),
      ('grade_item_audit', 'grade_item_type', array['EXAM', 'FINAL', 'TP']),
      ('grade_item_audit', 'kind', array['CORRECTION', 'FINAL_REVERT', 'RECOVERY', 'SYSTEM_REVERT']),
      ('grade_item_audit', 'section_kind', array['COURSE', 'PROGRAM']),
      ('graduation_certificate_registry_counter', 'kind', array['COURSE', 'PROGRAM']),
      ('graduation_request', 'certificate_review_status', array['DISABLED', 'ENABLED', 'NOT_REQUIRED', 'PENDING_REVIEW']),
      ('graduation_request', 'status', array['ADMIN_VERIFIED', 'APPROVED', 'REJECTED', 'REQUESTED']),
      ('holiday', 'type', array['BRIDGE', 'MUNICIPAL', 'NATIONAL', 'NON_WORKING_DAY', 'OTHER', 'PROVINCIAL']),
      ('identity_contact_change_log', 'change_type', array['DOCUMENT_CHANGED', 'EMAIL_CHANGE_CONFIRMED', 'EMAIL_CHANGE_REQUESTED']),
      ('import_batch', 'status', array['COMMITTED', 'COMMITTING', 'DRAFT', 'FAILED', 'FILES_UPLOADED', 'PARTIAL', 'ROLLED_BACK', 'ROLLING_BACK', 'SEALED', 'VALIDATED', 'VALIDATING', 'VALIDATION_FAILED']),
      ('import_batch_event', 'type', array['COMMITTED', 'CREATED', 'FILE_UPLOADED', 'RETRIED', 'ROLLBACK_BLOCKED', 'ROLLED_BACK', 'SEALED', 'VALIDATED']),
      ('import_created_entity', 'entity_type', array['ATTENDANCES', 'CURRICULUM', 'GRADE_ITEMS', 'GRADUATIONS', 'PERSONS', 'PREREQUISITES', 'PROGRAM_ENROLLMENTS', 'PROGRAMS', 'SECTION_ENROLLMENTS', 'SECTIONS', 'SUBJECTS', 'TERMS']),
      ('import_file', 'entity_type', array['ATTENDANCES', 'CURRICULUM', 'GRADE_ITEMS', 'GRADUATIONS', 'PERSONS', 'PREREQUISITES', 'PROGRAM_ENROLLMENTS', 'PROGRAMS', 'SECTION_ENROLLMENTS', 'SECTIONS', 'SUBJECTS', 'TERMS']),
      ('import_row', 'entity_type', array['ATTENDANCES', 'CURRICULUM', 'GRADE_ITEMS', 'GRADUATIONS', 'PERSONS', 'PREREQUISITES', 'PROGRAM_ENROLLMENTS', 'PROGRAMS', 'SECTION_ENROLLMENTS', 'SECTIONS', 'SUBJECTS', 'TERMS']),
      ('import_row', 'status', array['COMMITTED', 'CONFLICT', 'ERROR', 'FAILED', 'PENDING', 'UNCHANGED', 'VALID']),
      ('institute_setting', 'code', array['BILLING_NOTIFICATIONS', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY', 'WHATSAPP_API']),
      ('institute_setting_change_log', 'code', array['BILLING_NOTIFICATIONS', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY', 'WHATSAPP_API']),
      ('invitation_wave', 'status', array['CANCELLED', 'COMPLETED', 'DRAFT', 'PAUSED', 'RUNNING']),
      ('invitation_wave_recipient', 'exclusion_reason', array['ALREADY_ACTIVE', 'IN_OTHER_WAVE', 'NOT_ELIGIBLE', 'NO_EMAIL', 'SHARED_EMAIL', 'WAVE_CANCELLED']),
      ('invitation_wave_recipient', 'last_error_code', array['BULKHEAD_FULL', 'CIRCUIT_OPEN', 'PROVIDER_REJECTED', 'PROVIDER_UNAVAILABLE', 'TOKEN_ISSUE_FAILED', 'UNKNOWN']),
      ('invitation_wave_recipient', 'status', array['ACTIVATED', 'EXCLUDED', 'FAILED', 'PENDING', 'QUEUED', 'SENT']),
      ('invoice', 'credited_amount_currency', array['ARS', 'USD']),
      ('invoice', 'discounts_total_currency', array['ARS', 'USD']),
      ('invoice', 'lines_total_currency', array['ARS', 'USD']),
      ('invoice', 'outstanding_amount_currency', array['ARS', 'USD']),
      ('invoice', 'paid_amount_currency', array['ARS', 'USD']),
      ('invoice', 'total_currency', array['ARS', 'USD']),
      ('invoice_discount', 'base_amount_currency', array['ARS', 'USD']),
      ('invoice_discount', 'discount_amount_currency', array['ARS', 'USD']),
      ('invoice_line', 'amount_currency', array['ARS', 'USD']),
      ('invoice_line', 'credited_amount_currency', array['ARS', 'USD']),
      ('invoice_line', 'currency', array['ARS', 'USD']),
      ('invoice_line', 'discount_amount_currency', array['ARS', 'USD']),
      ('invoice_line', 'free_credited_amount_currency', array['ARS', 'USD']),
      ('legal_acceptance', 'document_type', array['PRIVACY', 'TERMS']),
      ('legal_document_publication', 'document_type', array['PRIVACY', 'TERMS']),
      ('monthly_invoice_simulation_line', 'total_currency', array['ARS', 'USD']),
      ('notification', 'type', array['ANNOUNCEMENT_PUBLISHED', 'ANNOUNCEMENT_UPDATED', 'ATTENDANCE_CHANGE_REQUEST_APPROVED', 'ATTENDANCE_CHANGE_REQUEST_REJECTED', 'ATTENDANCE_JUSTIFICATION_APPROVED', 'ATTENDANCE_JUSTIFICATION_REJECTED', 'AUTHORIZATION_REQUESTED', 'AUTHORIZATION_REQUEST_EXPIRED', 'CERTIFICATE_EXCEPTION_DECIDED', 'CERTIFICATE_EXCEPTION_DIRECT_CREATED', 'CERTIFICATE_EXCEPTION_REQUESTED', 'CLOSURE_APPROVED', 'CLOSURE_APPROVED_SUMMARY', 'CLOSURE_CONFIRMATION_REMINDER', 'CLOSURE_EXCEPTION_GRANTED', 'CLOSURE_EXCEPTION_GRANTED_TEACHER', 'CLOSURE_REOPENED', 'COLLECTION_REMINDER', 'CONFIGURATION_CHANGE_APPROVED', 'CONFIGURATION_CHANGE_REJECTED', 'CONFIGURATION_VERSION_CANCELLED', 'COURSE_WAITLIST_EXPIRED', 'CREDIT_NOTE_ISSUED', 'DIRECTOR_APPROVAL_REQUEST_APPROVED', 'DIRECTOR_APPROVAL_REQUEST_REJECTED', 'DISCOUNT_QUOTA_EXHAUSTED', 'DRAFT_INVOICES_AWAITING_REVIEW', 'ENROLLMENT_FEE_EXEMPTION_GRANTED', 'ENROLLMENT_FEE_EXEMPTION_REVOKED', 'EXAM_BOARD_CANCELED', 'EXAM_BOARD_RESCHEDULED', 'EXAM_CERTIFICATE_ISSUED', 'GATEWAY_PAYMENT_WITH_GATEWAY_OFF', 'GRADES_PUBLISHED', 'GRADE_CORRECTED', 'GRADUATION_CERTIFICATE_ISSUED', 'GRADUATION_CERTIFICATE_REISSUED', 'GRADUATION_CERTIFICATE_REVOKED', 'GRADUATION_REQUEST_APPROVED', 'GRADUATION_REQUEST_CREATED', 'GRADUATION_REQUEST_DEBT_BLOCKED', 'GRADUATION_REQUEST_OUTDATED', 'GRADUATION_REQUEST_PENDING_REVIEW', 'GRADUATION_REQUEST_REJECTED', 'GRADUATION_REQUEST_VERIFIED', 'HOLIDAYS_MISSING_FOR_NEXT_YEAR', 'INCONSISTENT_ACCOUNT_DETECTED', 'LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW', 'LATE_FEE_ISSUED', 'LATE_FEE_ISSUED_AFTER_FISCAL_WINDOW', 'LATE_PAYMENT_SETTINGS_CREATED', 'MONTHLY_INVOICE_ITEM_RETRIES_EXHAUSTED', 'MONTHLY_INVOICE_RUN_MISSED', 'NIGHTLY_RUN_COMPLETED_WITH_ERRORS', 'NIGHTLY_RUN_FAILED', 'NIGHTLY_RUN_MISSED', 'OFFER_CYCLE_APPROVED', 'OFFER_CYCLE_REJECTED', 'OFFER_CYCLE_SUBMITTED', 'OVERDUE_CONTACT_LIST_UPDATED', 'PAYMENT_CHARGED_BACK', 'PAYMENT_LEFT_AS_ACCOUNT_CREDIT', 'PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT', 'PAYMENT_RECEIPT_APPROVED', 'PAYMENT_RECEIPT_REJECTED', 'PAYMENT_RECEIPT_REVIEW_OVERDUE', 'PRICE_CHANGE_APPROVED', 'PRICE_CHANGE_REJECTED', 'PROGRAM_APPLICATION_APPROVED', 'PROGRAM_APPLICATION_RECEIVED', 'PROGRAM_ENROLLMENT_STALLED', 'REGULAR_CERTIFICATE_READY', 'REGULAR_CERTIFICATE_REJECTED', 'SECTION_CHANGED', 'SECTION_CHANGED_TEACHER', 'SECTION_EVAL_CONFIG_APPROVED', 'SECTION_EVAL_CONFIG_PROPOSED', 'SECTION_EVAL_CONFIG_REJECTED', 'SECTION_EVAL_CONFIG_REMINDER', 'SECTION_MODIFICATION_APPROVED', 'SECTION_MODIFICATION_REJECTED', 'SECTION_MODIFICATION_REQUESTED', 'STUDENT_REACTIVATED', 'SUBJECT_APPROVAL_REVERTED', 'SUBJECT_EQUIVALENCE_RECORDED', 'SUBJECT_EQUIVALENCE_VOIDED', 'SUBJECT_WITHDRAWN', 'SUBJECT_WITHDRAWN_TEACHER', 'SUSPENSION_LIFTED', 'WAITLIST_ENTRY_EXPIRED', 'WAITLIST_EXPIRING_SOON', 'WAITLIST_PAUSED_BY_SCHEDULE_OVERLAP', 'WAITLIST_PROMOTED', 'WAITLIST_REMOVED_BY_STAFF']),
      ('notification_delivery', 'pricing_category', array['MARKETING', 'UTILITY']),
      ('notification_preference', 'channel', array['APP', 'EMAIL']),
      ('notification_preference', 'event', array['ACCOUNT_CREATED', 'EXAM_BOARD_REGISTERED', 'GRADE_POSTED', 'HOLIDAY_CALENDAR_REMINDER', 'INSTITUTIONAL_NOTICE', 'INVOICE_GENERATED', 'PAYMENT_DUE_SOON', 'SUBJECT_ENROLLMENT']),
      ('payment', 'amount_currency', array['ARS', 'USD']),
      ('payment', 'approved_amount_currency', array['ARS', 'USD']),
      ('payment', 'refunded_amount_currency', array['ARS', 'USD']),
      ('payment_checkout_item', 'outstanding_at_selection_currency', array['ARS', 'USD']),
      ('payment_plan_option', 'cadence', array['ANNUAL_UPFRONT', 'MONTHLY']),
      ('permission_discrepancy_counter', 'direction', array['LEGACY_ALLOWED_GRID_DENIED', 'LEGACY_DENIED_GRID_ALLOWED']),
      ('permission_module_mode', 'mode', array['ENFORCE', 'LEGACY', 'SHADOW']),
      ('person', 'auth_provider', array['GOOGLE', 'LOCAL']),
      ('person', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('platform_feature', 'code', array['ANALYTICS', 'BILLING', 'BILLING_NOTIFICATIONS', 'EXTERNAL_BILLING', 'FISCAL_INVOICING', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY']),
      ('price_version_status_change', 'event', array['APPROVE_FOR_FUTURE', 'APPROVE_FOR_TODAY', 'CANCEL', 'EXPIRE', 'PROPOSE', 'PUBLISH_FOR_FUTURE', 'PUBLISH_FOR_TODAY', 'REACH_START', 'REJECT', 'SUPERSEDE', 'WITHDRAW']),
      ('price_version_status_change', 'from_status', array['CANCELLED', 'EFFECTIVE', 'EXPIRED', 'PROPOSED', 'REJECTED', 'SCHEDULED', 'SUPERSEDED', 'WITHDRAWN']),
      ('price_version_status_change', 'to_status', array['CANCELLED', 'EFFECTIVE', 'EXPIRED', 'PROPOSED', 'REJECTED', 'SCHEDULED', 'SUPERSEDED', 'WITHDRAWN']),
      ('program', 'level', array['DIPLOMA', 'EXTENSION', 'GRADUATE', 'UNDERGRADUATE']),
      ('program', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('program', 'teaching_mode', array['HYBRID', 'IN_PERSON', 'ONLINE']),
      ('program_cohort', 'intake', array['FIRST', 'SECOND']),
      ('program_enrollment', 'status', array['COMPLETED', 'ENROLLED', 'PAUSED', 'WITHDRAWN']),
      ('program_subject', 'category', array['ADDITIONAL_MANDATORY', 'ELECTIVE', 'MANDATORY']),
      ('program_subject', 'curricular_term', array['ANNUAL', 'FIRST', 'SECOND']),
      ('program_subject', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('program_subject', 'subject_recurrence', array['ANNUAL', 'PROMOTIONAL']),
      ('program_subject_prerequisite', 'requirement_type', array['APPROVED', 'PASSED_COURSEWORK']),
      ('refund', 'amount_currency', array['ARS', 'USD']),
      ('regular_certificate_request', 'channel', array['STAFF', 'STUDENT_PORTAL']),
      ('regular_certificate_request', 'kind', array['COURSE', 'PROGRAM']),
      ('regular_certificate_request', 'outcome', array['ALREADY_ISSUED', 'ISSUED', 'NOT_REGULAR', 'PENDING_STAFF', 'REJECTED']),
      ('exam_certificate', 'status', array['VALID', 'EXPIRED', 'REVOKED', 'REPLACED']),
      ('exam_certificate', 'origin', array['SELF_SERVICE', 'ISSUED_BY_TEACHER', 'ISSUED_BY_STAFF']),
      ('exam_certificate', 'recipient_type', array['HEALTH_INSURANCE', 'EMPLOYER', 'STUDENT_TRANSPORT', 'OTHER']),
      ('regular_student_certificate', 'issue_channel', array['STAFF', 'STUDENT_PORTAL']),
      ('regular_student_certificate', 'kind', array['COURSE', 'PROGRAM']),
      ('regular_student_certificate', 'standing_basis', array['BILLING_NOT_ENFORCED', 'BILLING_VERIFIED', 'EXCEPTION_APPROVED', 'MANUALLY_CONFIRMED']),
      ('regular_student_certificate', 'status', array['EXPIRED', 'REVOKED', 'VALID']),
      ('section_evaluation_config', 'status', array['APPROVED', 'DRAFT', 'PENDING_APPROVAL', 'REJECTED']),
      ('section_grade_item_publication', 'grade_item_type', array['EXAM', 'FINAL', 'TP']),
      ('section_grade_item_publication', 'section_kind', array['COURSE', 'PROGRAM']),
      ('section_modification_proposal_slot', 'day_of_week', array['FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY']),
      ('section_schedule_slot', 'day_of_week', array['FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY']),
      ('student', 'onboarding_status', array['CONFIRMADO', 'PROSPECTO']),
      ('student', 'withdrawal_reason', array['ABANDONO', 'CAMBIO_CARRERA', 'OTRO']),
      ('student_current_account', 'balance_amount_currency', array['ARS', 'USD']),
      ('student_current_account', 'unallocated_credit_amount_currency', array['ARS', 'USD']),
      ('student_discount_status_change', 'event', array['CONSUMPTION_RETURNED', 'DISCOUNT_GRANTED', 'DISCOUNT_REVOKED', 'ENROLLMENT_ENDED', 'LAST_INSTALLMENT_CONSUMED']),
      ('student_discount_status_change', 'from_status', array['ACTIVE', 'ENDED', 'EXHAUSTED', 'REVOKED']),
      ('student_discount_status_change', 'to_status', array['ACTIVE', 'ENDED', 'EXHAUSTED', 'REVOKED']),
      ('student_document', 'type', array['ANALITICO_SECUNDARIO', 'DNI_DORSO', 'DNI_FRENTE']),
      ('student_status_change', 'change_type', array['REACTIVATED', 'WITHDRAWN']),
      ('subject', 'grade_mode', array['NUMERIC', 'PASS_FAIL']),
      ('subject', 'status', array['ACTIVE', 'DRAFT', 'INACTIVE', 'PENDING_APPROVAL', 'REJECTED']),
      ('subject_equivalence', 'status', array['ACTIVE', 'VOIDED']),
      ('subject_section', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('subject_section_enrollment', 'status', array['APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN']),
      ('subject_section_instructor', 'role', array['ASSISTANT', 'GUEST', 'LAB', 'LEAD', 'PRACTICAL']),
      ('term', 'enrollment_override', array['CLOSED', 'OPEN']),
      ('term', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('verifiable_document', 'document_type', array['APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'GRADUATION_CERTIFICATE', 'REGULAR_STUDENT_CERTIFICATE']),
      ('whatsapp_template_mapping', 'status', array['APPROVED', 'PENDING', 'REJECTED'])
    ) as t(tbl, col, allowed)
  loop
    if to_regclass(spec.tbl) is null or not exists (
      select 1 from information_schema.columns
      where table_schema = current_schema() and table_name = spec.tbl and column_name = spec.col
    ) then
      continue;
    end if;
    for found in execute format(
      'select %I::text as value, count(*) as rows from %I where %I is not null and %I::text <> all ($1) group by 1 order by 1',
      spec.col, spec.tbl, spec.col, spec.col) using spec.allowed
    loop
      raise notice '[V209/V210] %.% valor fuera del enum: % (% filas)', spec.tbl, spec.col, found.value, found.rows;
    end loop;
  end loop;
end $$;
\endif

select :current_version < 214 as run_block \gset
\if :run_block
\echo
\echo [V214] Columnas legado de institution que V214 DESCARTA (si sms_password > 0, rotar esa credencial en el proveedor de SMS: docs/release/institution-sms-credential.md):
select count(*) filter (where facebook is not null) as facebook,
       count(*) filter (where tweeter is not null) as tweeter,
       count(*) filter (where pref_notas is not null) as pref_notas,
       count(*) filter (where pref_calendario is not null) as pref_calendario,
       count(*) filter (where pref_asignar_curso is not null) as pref_asignar_curso,
       count(*) filter (where sms_url is not null) as sms_url,
       count(*) filter (where sms_usuario is not null) as sms_usuario,
       count(*) filter (where sms_password is not null) as sms_password
from institution;
\endif

select :current_version < 215 as run_block \gset
\if :run_block
\echo
\echo [V215] Filas de billing_period que V215 DESCARTA (configuracion sin efecto en la facturacion; solo informativo):
select count(*) as billing_period_rows from billing_period;
\endif

select :current_version < 266 as run_block \gset
\if :run_block
\echo
\echo [V266] Destinatarios de envios COMPLETED o CANCELLED con mas de 12 meses que BroadcastRecipientPurgeJob BORRARA (V266 no aborta; solo informativo). Los envios sin totales congelados no se borran:
select count(*) filter (where b.sent_total is not null) as recipient_rows_to_purge,
       count(*) filter (where b.sent_total is null) as rows_kept_without_frozen_totals
from broadcast_recipient r join broadcast b on b.id = r.broadcast_id
where (b.status = 'CANCELLED' and b.cancelled_at < now() - interval '12 months')
   or (b.status = 'COMPLETED' and b.completed_at < now() - interval '12 months');
\endif

select :current_version < 272 as run_block \gset
\if :run_block
\echo
\echo [V272] Alumnos con mas de una matriculacion activa (ENROLLED, IN_PROGRESS, WAITLISTED) al mismo curso; V272 crea un indice unico parcial y ABORTA si hay filas (resolver antes: retirar o fusionar las duplicadas):
select student_id, course_id, count(*) as active_enrollments, array_agg(id order by id) as enrollment_ids
from course_enrollment
where status in ('ENROLLED', 'IN_PROGRESS', 'WAITLISTED')
group by student_id, course_id
having count(*) > 1;
\endif

select :current_version < 428 as run_block \gset
\if :run_block
\echo
\echo [V428] Solicitudes de pago anual adelantado cuyo payment_id no existe en payment; V428 ABORTA si hay filas (corregir antes: apuntar al pago correcto o dejar payment_id en null; no se borran datos):
select r.id as annual_upfront_request_id, r.payment_id, r.status
from annual_upfront_request r
where r.payment_id is not null
  and not exists (select 1 from payment p where p.id = r.payment_id)
order by r.id;
\endif

select :current_version < 446 as run_block \gset
\if :run_block
\echo
\echo [V446/V451] Egresos importados APROBADOS sin decided_by_id: V446 les reclasifica certificate_review_status (no aborta; V447 valida los CHECK y no puede fallar con estos valores). ENABLED si ya tienen un certificado de egreso VALID o REVOKED; PENDING_REVIEW si no (queda a la espera de que el Director habilite o deshabilite el certificado). Cantidad por estado resultante:
select case when exists (select 1 from graduation_certificate gc
                         where gc.program_enrollment_id = gr.program_enrollment_id and gc.status in ('VALID', 'REVOKED'))
            then 'ENABLED' else 'PENDING_REVIEW' end as certificate_review_status_after_migration,
       count(*) as graduation_requests
from graduation_request gr
where gr.status = 'APPROVED' and gr.decided_by_id is null
group by 1
order by 1;
\endif

select :current_version < 452 as run_block \gset
\if :run_block
\echo
\echo [V452] Codigos de certificados repetidos entre graduation_certificate y regular_student_certificate; V452 ABORTA al poblar verifiable_document (clave primaria por code) si hay filas (corregir antes: regenerar el codigo de uno de los dos):
select c.code, array_agg(c.source order by c.source) as sources, array_agg(c.id order by c.source) as certificate_ids
from (select code, id, 'graduation_certificate' as source from graduation_certificate
      union all
      select code, id, 'regular_student_certificate' from regular_student_certificate) c
group by c.code
having count(*) > 1
order by c.code;
\endif

select :current_version < 463 as run_block \gset
\if :run_block
\echo
\echo [V463] Franjas horarias que V463 recalcula (term_period, occupies, time_span) con un UPDATE que dispara el trigger; V470 vuelve a recalcularlas y crea la proyeccion por docente (no aborta; solo informativo, dimensiona el tiempo del UPDATE):
select count(*) as section_schedule_slots from section_schedule_slot;
\endif

select :current_version < 465 as run_block \gset
\if :run_block
\echo
\echo [V465] Constancias de inscripcion a examen cuyo codigo ya existe en el registro verificable (V465 no aborta pero las OMITE del registro: queda sin verificacion publica). El registro se puebla antes con los codigos de graduation_certificate y regular_student_certificate (V452):
select e.id as exam_registration_certificate_id, e.code, other.source as code_already_used_by
from exam_registration_certificate e
join (select code, 'graduation_certificate' as source from graduation_certificate
      union all
      select code, 'regular_student_certificate' from regular_student_certificate) other on other.code = e.code
order by e.id;
\endif

select :current_version < 474 as run_block \gset
\if :run_block
\echo
\echo [V474] Inscripciones a carrera activas (ENROLLED o PAUSED) duplicadas por alumno y carrera (la migracion ABORTA al crear el unico parcial): 'could not create unique index uk_program_enrollment_student_program_active'
select student_id, program_id, count(*) as active_rows, array_agg(id order by id) as ids
from program_enrollment
where status in ('ENROLLED', 'PAUSED')
group by student_id, program_id
having count(*) > 1
order by student_id, program_id;
\endif

select :current_version < 475 as run_block \gset
\if :run_block
\echo
\echo [V475] Solicitudes de constancia PENDING_STAFF duplicadas por inscripcion (la migracion ABORTA al crear el unico parcial): 'could not create unique index uq_regular_cert_request_pending_per_enrollment'
select kind, coalesce(program_enrollment_id, course_enrollment_id) as enrollment_id,
       count(*) as pending_rows, array_agg(id order by id) as ids
from regular_certificate_request
where outcome = 'PENDING_STAFF'
group by kind, coalesce(program_enrollment_id, course_enrollment_id)
having count(*) > 1
order by 1, 2;
\endif

select :current_version < 476 as run_block \gset
\if :run_block
\echo
\echo [V476] Bajas sin fecha efectiva y carreras COMPLETED sin fecha de finalizacion (la migracion NO aborta: los CHECK quedan NOT VALID y las filas existentes siguen; la validacion posterior ABORTA si quedan filas; la correccion afecta recargos y resumen post-baja y es decision de producto):
select 'program_enrollment WITHDRAWN sin withdrawal_effective_date' as violation, count(*) as violating_rows, min(id) as first_id
from program_enrollment where status = 'WITHDRAWN' and withdrawal_effective_date is null
union all
select 'program_enrollment COMPLETED sin completion_date', count(*), min(id)
from program_enrollment where status = 'COMPLETED' and completion_date is null;
\endif

select :current_version < 478 as run_block \gset
\if :run_block
\echo
\echo [V478] Inscripciones a comision cuyo alumno difiere del de su inscripcion padre (la FK compuesta se crea NOT VALID y no aborta; corregir antes de ejecutar VALIDATE CONSTRAINT fk_subject_section_enrollment_parent_student / fk_course_section_enrollment_parent_student; no se borran datos):
select 'subject_section_enrollment' as tbl, count(*) as violating_rows, min(sse.id) as first_id
from subject_section_enrollment sse join program_enrollment pe on pe.id = sse.program_enrollment_id
where sse.student_id <> pe.student_id
union all
select 'course_section_enrollment', count(*), min(cse.id)
from course_section_enrollment cse join course_enrollment ce on ce.id = cse.course_enrollment_id
where cse.student_id <> ce.student_id;
\endif

\echo
\echo [V470] Franjas de aula solapadas (misma aula, mismo dia y hora, periodos de termino que se pisan, ambas de comisiones ACTIVE); V470 ABORTA al crear el EXCLUDE si hay filas (corregir antes: mover una franja a otra aula u horario o desactivar la comision; no se borran datos):
with active_slots as (
    select s.id, s.classroom_id, s.day_of_week, s.start_time, s.end_time,
           tsrange(t.start_date, t.end_date, '[]') as term_period
    from section_schedule_slot s
    left join subject_section ss on ss.id = s.subject_section_id
    left join course_section cs on cs.id = s.course_section_id
    join term t on t.id = coalesce(ss.term_id, cs.term_id)
    where coalesce(ss.status, cs.status) = 'ACTIVE'
      and t.start_date is not null and t.end_date is not null and t.end_date >= t.start_date
)
select a.id as slot_id, b.id as overlapping_slot_id, a.classroom_id, a.day_of_week, a.start_time, a.end_time
from active_slots a
join active_slots b on a.id < b.id and a.classroom_id = b.classroom_id and a.day_of_week = b.day_of_week
    and floor(extract(epoch from a.start_time)) < floor(extract(epoch from b.end_time))
    and floor(extract(epoch from b.start_time)) < floor(extract(epoch from a.end_time))
    and a.term_period && b.term_period
order by a.classroom_id, a.id;

\echo [V470] Franjas de docente solapadas (mismo docente asignado a dos comisiones ACTIVE con franjas que se pisan); V470 ABORTA al poblar la proyeccion si hay filas:
with instructor_slots as (
    select distinct s.id as slot_id, l.instructor_id, s.day_of_week, s.start_time, s.end_time,
           tsrange(t.start_date, t.end_date, '[]') as term_period
    from section_schedule_slot s
    left join subject_section ss on ss.id = s.subject_section_id
    left join course_section cs on cs.id = s.course_section_id
    join term t on t.id = coalesce(ss.term_id, cs.term_id)
    join (select program_section_id as subject_section_id, null::bigint as course_section_id, instructor_id from subject_section_instructor
          union all
          select null::bigint, course_section_id, instructor_id from course_section_instructor) l
        on (l.subject_section_id is not null and l.subject_section_id = s.subject_section_id)
        or (l.course_section_id is not null and l.course_section_id = s.course_section_id)
    where coalesce(ss.status, cs.status) = 'ACTIVE'
      and t.start_date is not null and t.end_date is not null and t.end_date >= t.start_date
)
select a.instructor_id, a.slot_id, b.slot_id as overlapping_slot_id, a.day_of_week, a.start_time, a.end_time
from instructor_slots a
join instructor_slots b on a.slot_id < b.slot_id and a.instructor_id = b.instructor_id and a.day_of_week = b.day_of_week
    and floor(extract(epoch from a.start_time)) < floor(extract(epoch from b.end_time))
    and floor(extract(epoch from b.start_time)) < floor(extract(epoch from a.end_time))
    and a.term_period && b.term_period
order by a.instructor_id, a.slot_id;

\echo ===== fin =====
