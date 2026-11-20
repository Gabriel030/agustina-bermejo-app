alter table academic_offering_cycle
    add constraint academic_offering_cycle_status_enum check (status in ('APPROVED', 'CLOSED', 'DRAFT', 'PENDING_APPROVAL', 'REJECTED')) not valid;

alter table announcement
    add constraint announcement_audience_key_enum check (audience_key in ('ABSENCES', 'ALL', 'EXAM_FAILED', 'EXAM_MISSING', 'EXAM_PASSED', 'FAILED_COURSE', 'FINAL_FAILED', 'FINAL_PASSED', 'MUST_TAKE_FINAL', 'PASSED', 'PROMOTED', 'TP_FAILED', 'TP_MISSING', 'TP_PASSED')) not valid,
    add constraint announcement_audience_quantifier_enum check (audience_quantifier in ('ALL', 'ANY')) not valid,
    add constraint announcement_category_enum check (category in ('ADMIN', 'EVALUATION', 'MATERIAL', 'REMINDER', 'SCHEDULING')) not valid,
    add constraint announcement_status_enum check (status in ('SCHEDULED', 'SENT')) not valid;

alter table attendance
    add constraint attendance_status_enum check (status in ('ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT')) not valid;

alter table attendance_change_request
    add constraint attendance_change_request_previous_status_enum check (previous_status in ('ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT')) not valid,
    add constraint attendance_change_request_requested_status_enum check (requested_status in ('ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT')) not valid,
    add constraint attendance_change_request_status_enum check (status in ('APPROVED', 'PENDING', 'REJECTED')) not valid;

alter table attendance_correction
    add constraint attendance_correction_corrected_status_enum check (corrected_status in ('ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT')) not valid,
    add constraint attendance_correction_previous_status_enum check (previous_status in ('ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED', 'LATE', 'PRESENT')) not valid;

alter table batch_execution_log
    add constraint batch_execution_log_status_enum check (status in ('FAILED', 'PARTIAL_FAILURE', 'SUCCESS')) not valid;

alter table certificate_issuance
    add constraint certificate_issuance_type_enum check (type in ('APPROVED_SUBJECTS', 'REGULAR_STUDENT')) not valid;

alter table class_session
    add constraint class_session_status_enum check (status in ('CANCELED', 'HELD', 'SCHEDULED')) not valid;

alter table classroom
    add constraint classroom_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid;

alter table commission_closure_reminder
    add constraint commission_closure_reminder_section_kind_enum check (section_kind in ('COURSE', 'PROGRAM')) not valid;

alter table course
    add constraint course_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid,
    add constraint course_teaching_mode_enum check (teaching_mode in ('HYBRID', 'IN_PERSON', 'ONLINE')) not valid;

alter table course_enrollment
    add constraint course_enrollment_status_enum check (status in ('APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN')) not valid;

alter table course_section
    add constraint course_section_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid;

alter table course_section_enrollment
    add constraint course_section_enrollment_status_enum check (status in ('APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN')) not valid;

alter table course_section_instructor
    add constraint course_section_instructor_role_enum check (role in ('ASSISTANT', 'GUEST', 'LAB', 'LEAD', 'PRACTICAL')) not valid;

alter table course_subject
    add constraint course_subject_curricular_term_enum check (curricular_term in ('ANNUAL', 'FIRST', 'SECOND')) not valid,
    add constraint course_subject_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid,
    add constraint course_subject_subject_recurrence_enum check (subject_recurrence in ('ANNUAL', 'PROMOTIONAL')) not valid;

alter table course_subject_prerequisite
    add constraint course_subject_prerequisite_requirement_type_enum check (requirement_type in ('APPROVED', 'PASSED_COURSEWORK')) not valid;

alter table director_approval_request
    add constraint director_approval_request_status_enum check (status in ('APPROVED', 'REJECTED', 'REQUESTED')) not valid,
    add constraint director_approval_request_type_enum check (type in ('LATE_EXAM', 'PREREQUISITE_WAIVER')) not valid;

alter table evaluation_scheme
    add constraint evaluation_scheme_aggregation_method_enum check (aggregation_method in ('LAST_ATTEMPT', 'MAX_SCORE', 'SIMPLE_AVERAGE', 'WEIGHTED_AVERAGE')) not valid;

alter table exam_board
    add constraint exam_board_status_enum check (status in ('CANCELED', 'CLOSED', 'SCHEDULED')) not valid;

alter table exam_board_registration
    add constraint exam_board_registration_status_enum check (status in ('ABSENT', 'GRADED', 'REGISTERED', 'WITHDRAWN')) not valid;

alter table feature_change_log
    add constraint feature_change_log_code_enum check (code in ('ANALYTICS', 'BILLING', 'BILLING_NOTIFICATIONS', 'EXTERNAL_BILLING', 'FISCAL_INVOICING', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY')) not valid;

alter table grade_item
    add constraint grade_item_type_enum check (type in ('EXAM', 'FINAL', 'TP')) not valid;

alter table grade_item_audit
    add constraint grade_item_audit_grade_item_type_enum check (grade_item_type in ('EXAM', 'FINAL', 'TP')) not valid,
    add constraint grade_item_audit_kind_enum check (kind in ('CORRECTION', 'FINAL_REVERT', 'RECOVERY')) not valid,
    add constraint grade_item_audit_section_kind_enum check (section_kind in ('COURSE', 'PROGRAM')) not valid;

alter table graduation_request
    add constraint graduation_request_status_enum check (status in ('ADMIN_VERIFIED', 'APPROVED', 'REJECTED', 'REQUESTED')) not valid;

alter table holiday
    add constraint holiday_type_enum check (type in ('BRIDGE', 'MUNICIPAL', 'NATIONAL', 'NON_WORKING_DAY', 'OTHER', 'PROVINCIAL')) not valid;

alter table identity_contact_change_log
    add constraint identity_contact_change_log_change_type_enum check (change_type in ('DOCUMENT_CHANGED', 'EMAIL_CHANGE_CONFIRMED', 'EMAIL_CHANGE_REQUESTED')) not valid;

alter table institute_setting
    add constraint institute_setting_code_enum check (code in ('BILLING_NOTIFICATIONS', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY')) not valid;

alter table institute_setting_change_log
    add constraint institute_setting_change_log_code_enum check (code in ('BILLING_NOTIFICATIONS', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY')) not valid;

alter table notification
    add constraint notification_type_enum check (type in ('ANNOUNCEMENT_PUBLISHED', 'ANNOUNCEMENT_UPDATED', 'ATTENDANCE_CHANGE_REQUEST_APPROVED', 'ATTENDANCE_CHANGE_REQUEST_REJECTED', 'ATTENDANCE_JUSTIFICATION_APPROVED', 'ATTENDANCE_JUSTIFICATION_REJECTED', 'AUTHORIZATION_REQUESTED', 'AUTHORIZATION_REQUEST_EXPIRED', 'CLOSURE_APPROVED', 'CLOSURE_APPROVED_SUMMARY', 'CLOSURE_CONFIRMATION_REMINDER', 'CLOSURE_EXCEPTION_GRANTED', 'CLOSURE_REOPENED', 'COLLECTION_REMINDER', 'CONFIGURATION_CHANGE_APPROVED', 'CONFIGURATION_CHANGE_REJECTED', 'CONFIGURATION_VERSION_CANCELLED', 'COURSE_WAITLIST_EXPIRED', 'CREDIT_NOTE_ISSUED', 'DIRECTOR_APPROVAL_REQUEST_APPROVED', 'DIRECTOR_APPROVAL_REQUEST_REJECTED', 'DISCOUNT_QUOTA_EXHAUSTED', 'DRAFT_INVOICES_AWAITING_REVIEW', 'ENROLLMENT_FEE_EXEMPTION_GRANTED', 'ENROLLMENT_FEE_EXEMPTION_REVOKED', 'EXAM_BOARD_CANCELED', 'EXAM_BOARD_RESCHEDULED', 'GATEWAY_PAYMENT_WITH_GATEWAY_OFF', 'GRADES_PUBLISHED', 'GRADE_CORRECTED', 'GRADUATION_REQUEST_APPROVED', 'GRADUATION_REQUEST_CREATED', 'GRADUATION_REQUEST_DEBT_BLOCKED', 'GRADUATION_REQUEST_OUTDATED', 'GRADUATION_REQUEST_PENDING_REVIEW', 'GRADUATION_REQUEST_REJECTED', 'GRADUATION_REQUEST_VERIFIED', 'HOLIDAYS_MISSING_FOR_NEXT_YEAR', 'INCONSISTENT_ACCOUNT_DETECTED', 'LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW', 'LATE_FEE_ISSUED', 'LATE_FEE_ISSUED_AFTER_FISCAL_WINDOW', 'LATE_PAYMENT_SETTINGS_CREATED', 'MONTHLY_INVOICE_ITEM_RETRIES_EXHAUSTED', 'MONTHLY_INVOICE_RUN_MISSED', 'NIGHTLY_RUN_COMPLETED_WITH_ERRORS', 'NIGHTLY_RUN_FAILED', 'NIGHTLY_RUN_MISSED', 'OFFER_CYCLE_APPROVED', 'OFFER_CYCLE_REJECTED', 'OFFER_CYCLE_SUBMITTED', 'OVERDUE_CONTACT_LIST_UPDATED', 'PAYMENT_CHARGED_BACK', 'PAYMENT_LEFT_AS_ACCOUNT_CREDIT', 'PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT', 'PAYMENT_RECEIPT_APPROVED', 'PAYMENT_RECEIPT_REJECTED', 'PAYMENT_RECEIPT_REVIEW_OVERDUE', 'PRICE_CHANGE_APPROVED', 'PRICE_CHANGE_REJECTED', 'PROGRAM_APPLICATION_APPROVED', 'PROGRAM_APPLICATION_RECEIVED', 'PROGRAM_ENROLLMENT_STALLED', 'SECTION_EVAL_CONFIG_APPROVED', 'SECTION_EVAL_CONFIG_PROPOSED', 'SECTION_EVAL_CONFIG_REJECTED', 'SECTION_EVAL_CONFIG_REMINDER', 'SUSPENSION_LIFTED')) not valid;

alter table notification_preference
    add constraint notification_preference_channel_enum check (channel in ('APP', 'EMAIL')) not valid,
    add constraint notification_preference_event_enum check (event in ('ACCOUNT_CREATED', 'EXAM_BOARD_REGISTERED', 'GRADE_POSTED', 'HOLIDAY_CALENDAR_REMINDER', 'INSTITUTIONAL_NOTICE', 'INVOICE_GENERATED', 'PAYMENT_DUE_SOON', 'SUBJECT_ENROLLMENT')) not valid;

alter table person
    add constraint person_auth_provider_enum check (auth_provider in ('GOOGLE', 'LOCAL')) not valid,
    add constraint person_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid;

alter table platform_feature
    add constraint platform_feature_code_enum check (code in ('ANALYTICS', 'BILLING', 'BILLING_NOTIFICATIONS', 'EXTERNAL_BILLING', 'FISCAL_INVOICING', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_GATEWAY')) not valid;

alter table program
    add constraint program_level_enum check (level in ('DIPLOMA', 'EXTENSION', 'GRADUATE', 'UNDERGRADUATE')) not valid,
    add constraint program_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid,
    add constraint program_teaching_mode_enum check (teaching_mode in ('HYBRID', 'IN_PERSON', 'ONLINE')) not valid;

alter table program_cohort
    add constraint program_cohort_intake_enum check (intake in ('FIRST', 'SECOND')) not valid;

alter table program_enrollment
    add constraint program_enrollment_status_enum check (status in ('COMPLETED', 'ENROLLED', 'PAUSED', 'WITHDRAWN')) not valid;

alter table program_subject
    add constraint program_subject_category_enum check (category in ('ADDITIONAL_MANDATORY', 'ELECTIVE', 'MANDATORY')) not valid,
    add constraint program_subject_curricular_term_enum check (curricular_term in ('ANNUAL', 'FIRST', 'SECOND')) not valid,
    add constraint program_subject_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid,
    add constraint program_subject_subject_recurrence_enum check (subject_recurrence in ('ANNUAL', 'PROMOTIONAL')) not valid;

alter table program_subject_prerequisite
    add constraint program_subject_prerequisite_requirement_type_enum check (requirement_type in ('APPROVED', 'PASSED_COURSEWORK')) not valid;

alter table regular_certificate_request
    add constraint regular_certificate_request_channel_enum check (channel in ('STAFF', 'STUDENT_PORTAL')) not valid,
    add constraint regular_certificate_request_kind_enum check (kind in ('COURSE', 'PROGRAM')) not valid,
    add constraint regular_certificate_request_outcome_enum check (outcome in ('ALREADY_ISSUED', 'ISSUED', 'NOT_REGULAR', 'PENDING_STAFF', 'REJECTED')) not valid;

alter table regular_student_certificate
    add constraint regular_student_certificate_issue_channel_enum check (issue_channel in ('STAFF', 'STUDENT_PORTAL')) not valid,
    add constraint regular_student_certificate_kind_enum check (kind in ('COURSE', 'PROGRAM')) not valid,
    add constraint regular_student_certificate_standing_basis_enum check (standing_basis in ('BILLING_VERIFIED', 'EXCEPTION_APPROVED', 'MANUALLY_CONFIRMED')) not valid,
    add constraint regular_student_certificate_status_enum check (status in ('EXPIRED', 'REVOKED', 'VALID')) not valid;

alter table section_evaluation_config
    add constraint section_evaluation_config_status_enum check (status in ('APPROVED', 'DRAFT', 'PENDING_APPROVAL', 'REJECTED')) not valid;

alter table section_grade_item_publication
    add constraint section_grade_item_publication_grade_item_type_enum check (grade_item_type in ('EXAM', 'FINAL', 'TP')) not valid,
    add constraint section_grade_item_publication_section_kind_enum check (section_kind in ('COURSE', 'PROGRAM')) not valid;

alter table section_modification_proposal_slot
    add constraint section_modification_proposal_slot_day_of_week_enum check (day_of_week in ('FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY')) not valid;

alter table section_schedule_slot
    add constraint section_schedule_slot_day_of_week_enum check (day_of_week in ('FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY')) not valid;

alter table student
    add constraint student_onboarding_status_enum check (onboarding_status in ('CONFIRMADO', 'PROSPECTO')) not valid,
    add constraint student_withdrawal_reason_enum check (withdrawal_reason in ('ABANDONO', 'CAMBIO_CARRERA', 'OTRO')) not valid;

alter table student_document
    add constraint student_document_type_enum check (type in ('ANALITICO_SECUNDARIO', 'DNI_DORSO', 'DNI_FRENTE')) not valid;

alter table student_status_change
    add constraint student_status_change_change_type_enum check (change_type in ('REACTIVATED', 'WITHDRAWN')) not valid;

alter table subject
    add constraint subject_grade_mode_enum check (grade_mode in ('NUMERIC', 'PASS_FAIL')) not valid,
    add constraint subject_status_enum check (status in ('ACTIVE', 'DRAFT', 'INACTIVE', 'PENDING_APPROVAL', 'REJECTED')) not valid;

alter table subject_section
    add constraint subject_section_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid;

alter table subject_section_enrollment
    add constraint subject_section_enrollment_status_enum check (status in ('APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN')) not valid;

alter table subject_section_instructor
    add constraint subject_section_instructor_role_enum check (role in ('ASSISTANT', 'GUEST', 'LAB', 'LEAD', 'PRACTICAL')) not valid;

alter table term
    add constraint term_enrollment_override_enum check (enrollment_override in ('CLOSED', 'OPEN')) not valid,
    add constraint term_status_enum check (status in ('ACTIVE', 'DELETED', 'INACTIVE')) not valid;
