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
      ('certificate_issuance', 'type', array['APPROVED_SUBJECTS', 'REGULAR_STUDENT']),
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
      ('evaluation_scheme', 'aggregation_method', array['LAST_ATTEMPT', 'MAX_SCORE', 'SIMPLE_AVERAGE', 'WEIGHTED_AVERAGE']),
      ('exam_board', 'status', array['CANCELED', 'CLOSED', 'SCHEDULED']),
      ('exam_board_registration', 'status', array['ABSENT', 'GRADED', 'REGISTERED', 'WITHDRAWN']),
      ('feature_change_log', 'code', array['ANALYTICS', 'BILLING', 'BILLING_NOTIFICATIONS', 'EXTERNAL_BILLING', 'FISCAL_INVOICING', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY']),
      ('grade_item', 'type', array['EXAM', 'FINAL', 'TP']),
      ('grade_item_audit', 'grade_item_type', array['EXAM', 'FINAL', 'TP']),
      ('grade_item_audit', 'kind', array['CORRECTION', 'FINAL_REVERT', 'RECOVERY', 'SYSTEM_REVERT']),
      ('grade_item_audit', 'section_kind', array['COURSE', 'PROGRAM']),
      ('graduation_request', 'status', array['ADMIN_VERIFIED', 'APPROVED', 'REJECTED', 'REQUESTED']),
      ('holiday', 'type', array['BRIDGE', 'MUNICIPAL', 'NATIONAL', 'NON_WORKING_DAY', 'OTHER', 'PROVINCIAL']),
      ('identity_contact_change_log', 'change_type', array['DOCUMENT_CHANGED', 'EMAIL_CHANGE_CONFIRMED', 'EMAIL_CHANGE_REQUESTED']),
      ('institute_setting', 'code', array['BILLING_NOTIFICATIONS', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY']),
      ('institute_setting_change_log', 'code', array['BILLING_NOTIFICATIONS', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY']),
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
      ('monthly_invoice_simulation_line', 'total_currency', array['ARS', 'USD']),
      ('notification', 'type', array['ANNOUNCEMENT_PUBLISHED', 'ANNOUNCEMENT_UPDATED', 'ATTENDANCE_CHANGE_REQUEST_APPROVED', 'ATTENDANCE_CHANGE_REQUEST_REJECTED', 'ATTENDANCE_JUSTIFICATION_APPROVED', 'ATTENDANCE_JUSTIFICATION_REJECTED', 'AUTHORIZATION_REQUESTED', 'AUTHORIZATION_REQUEST_EXPIRED', 'CLOSURE_APPROVED', 'CLOSURE_APPROVED_SUMMARY', 'CLOSURE_CONFIRMATION_REMINDER', 'CLOSURE_EXCEPTION_GRANTED', 'CLOSURE_EXCEPTION_GRANTED_TEACHER', 'CLOSURE_REOPENED', 'COLLECTION_REMINDER', 'CONFIGURATION_CHANGE_APPROVED', 'CONFIGURATION_CHANGE_REJECTED', 'CONFIGURATION_VERSION_CANCELLED', 'COURSE_WAITLIST_EXPIRED', 'CREDIT_NOTE_ISSUED', 'DIRECTOR_APPROVAL_REQUEST_APPROVED', 'DIRECTOR_APPROVAL_REQUEST_REJECTED', 'DISCOUNT_QUOTA_EXHAUSTED', 'DRAFT_INVOICES_AWAITING_REVIEW', 'ENROLLMENT_FEE_EXEMPTION_GRANTED', 'ENROLLMENT_FEE_EXEMPTION_REVOKED', 'EXAM_BOARD_CANCELED', 'EXAM_BOARD_RESCHEDULED', 'GATEWAY_PAYMENT_WITH_GATEWAY_OFF', 'GRADES_PUBLISHED', 'GRADE_CORRECTED', 'GRADUATION_REQUEST_APPROVED', 'GRADUATION_REQUEST_CREATED', 'GRADUATION_REQUEST_DEBT_BLOCKED', 'GRADUATION_REQUEST_OUTDATED', 'GRADUATION_REQUEST_PENDING_REVIEW', 'GRADUATION_REQUEST_REJECTED', 'GRADUATION_REQUEST_VERIFIED', 'HOLIDAYS_MISSING_FOR_NEXT_YEAR', 'INCONSISTENT_ACCOUNT_DETECTED', 'LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW', 'LATE_FEE_ISSUED', 'LATE_FEE_ISSUED_AFTER_FISCAL_WINDOW', 'LATE_PAYMENT_SETTINGS_CREATED', 'MONTHLY_INVOICE_ITEM_RETRIES_EXHAUSTED', 'MONTHLY_INVOICE_RUN_MISSED', 'NIGHTLY_RUN_COMPLETED_WITH_ERRORS', 'NIGHTLY_RUN_FAILED', 'NIGHTLY_RUN_MISSED', 'OFFER_CYCLE_APPROVED', 'OFFER_CYCLE_REJECTED', 'OFFER_CYCLE_SUBMITTED', 'OVERDUE_CONTACT_LIST_UPDATED', 'PAYMENT_CHARGED_BACK', 'PAYMENT_LEFT_AS_ACCOUNT_CREDIT', 'PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT', 'PAYMENT_RECEIPT_APPROVED', 'PAYMENT_RECEIPT_REJECTED', 'PAYMENT_RECEIPT_REVIEW_OVERDUE', 'PRICE_CHANGE_APPROVED', 'PRICE_CHANGE_REJECTED', 'PROGRAM_APPLICATION_APPROVED', 'PROGRAM_APPLICATION_RECEIVED', 'PROGRAM_ENROLLMENT_STALLED', 'REGULAR_CERTIFICATE_READY', 'REGULAR_CERTIFICATE_REJECTED', 'SECTION_EVAL_CONFIG_APPROVED', 'SECTION_EVAL_CONFIG_PROPOSED', 'SECTION_EVAL_CONFIG_REJECTED', 'SECTION_EVAL_CONFIG_REMINDER', 'STUDENT_REACTIVATED', 'SUBJECT_APPROVAL_REVERTED', 'SUSPENSION_LIFTED']),
      ('notification_preference', 'channel', array['APP', 'EMAIL']),
      ('notification_preference', 'event', array['ACCOUNT_CREATED', 'EXAM_BOARD_REGISTERED', 'GRADE_POSTED', 'HOLIDAY_CALENDAR_REMINDER', 'INSTITUTIONAL_NOTICE', 'INVOICE_GENERATED', 'PAYMENT_DUE_SOON', 'SUBJECT_ENROLLMENT']),
      ('payment', 'amount_currency', array['ARS', 'USD']),
      ('payment', 'approved_amount_currency', array['ARS', 'USD']),
      ('payment', 'refunded_amount_currency', array['ARS', 'USD']),
      ('payment_checkout_item', 'outstanding_at_selection_currency', array['ARS', 'USD']),
      ('payment_plan_option', 'cadence', array['ANNUAL_UPFRONT', 'MONTHLY']),
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
      ('regular_student_certificate', 'issue_channel', array['STAFF', 'STUDENT_PORTAL']),
      ('regular_student_certificate', 'kind', array['COURSE', 'PROGRAM']),
      ('regular_student_certificate', 'standing_basis', array['BILLING_VERIFIED', 'EXCEPTION_APPROVED', 'MANUALLY_CONFIRMED']),
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
      ('subject_section', 'status', array['ACTIVE', 'DELETED', 'INACTIVE']),
      ('subject_section_enrollment', 'status', array['APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN']),
      ('subject_section_instructor', 'role', array['ASSISTANT', 'GUEST', 'LAB', 'LEAD', 'PRACTICAL']),
      ('term', 'enrollment_override', array['CLOSED', 'OPEN']),
      ('term', 'status', array['ACTIVE', 'DELETED', 'INACTIVE'])
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

\echo
\echo [V215] Filas de billing_period que V215 DESCARTA (configuracion sin efecto en la facturacion; solo informativo):
select count(*) as billing_period_rows from billing_period;

rollback;
\echo ===== fin =====
