# Catálogo de errores y política de reintentos

> Generado por `ErrorCatalogTest`. No se edita a mano: `./mvnw test -Dtest=ErrorCatalogTest -Derror-catalog.write=true`.

## Cuerpo estándar

Todo error de la API sale con `{"code": "...", "retryable": true|false, "requestId": "...", "violations": [...], "details": {...}}`. `code` es estable (derivado del nombre de la excepción o fijo para los transversales), `retryable` indica si repetir el mismo pedido puede tener éxito, `requestId` (opcional) correlaciona con el log y `violations` (opcional) trae las reglas de negocio violadas y `details` (opcional, aditivo) trae datos estables del error: `PENDING_DEBT` informa `totalDue` (monto adeudado) e `installmentCount` (cuotas pendientes). Un `429` o `503` incluye además el header `Retry-After` en segundos. Los mensajes de las excepciones son internos y no se envían: el front traduce por `code`. Un `404` de una excepción que no declara código sale sin cuerpo a propósito (anti-enumeración: un recurso ajeno es indistinguible de uno inexistente).

## Política de reintentos

| HTTP | Reintentable | Por qué |
|---|---|---|
| 400 | no | El pedido es inválido; repetirlo igual falla igual |
| 401 | no | Hay que autenticarse o renovar el token antes de repetir |
| 403 | no | Los permisos no cambian por esperar |
| 404 | no | El recurso no existe |
| 409 | no | Conflicto con el estado persistido; hay que releer y decidir |
| 429 | sí | Límite de tasa; esperar `Retry-After` |
| 500 | no | Defecto del servidor; repetirlo repite el defecto |
| 502 | sí | Falla de un servicio externo (por ejemplo Mercado Pago), transitoria |
| 503 | sí | Servicio externo protegido por circuit breaker abierto (`EXTERNAL_SERVICE_UNAVAILABLE`); esperar `Retry-After` |
| 504 | sí | Reservado: hoy el backend no lo emite; si lo emite un proxy, se reintenta |

Regla del front (decisión del 3-nov-2026): GET, PUT y DELETE reintentables; POST solo con `Idempotency-Key`; máximo 3 intentos con espera exponencial y jitter, solo ante errores de red o 502, 503 y 504.

## Errores transversales

| Código | HTTP | Reintentable | Origen | Por qué |
|---|---|---|---|---|
| `ILLEGAL_ARGUMENT` | 400 | no | Argumento ilegal no previsto por el dominio | El pedido es inválido; repetirlo igual falla igual |
| `INVALID_PARAMETER_TYPE` | 400 | no | Parámetro de ruta o query con tipo incorrecto | El pedido es inválido; repetirlo igual falla igual |
| `MALFORMED_REQUEST_BODY` | 400 | no | JSON ilegible o con tipos incorrectos | El pedido es inválido; repetirlo igual falla igual |
| `MAX_UPLOAD_SIZE_EXCEEDED` | 400 | no | Archivo mayor al límite | El archivo es demasiado grande; repetirlo igual falla igual |
| `VALIDATION_FAILED` | 400 | no | Bean Validation del cuerpo | El pedido es inválido; repetirlo igual falla igual |
| `TOKEN_EXPIRED` | 401 | no | Token vencido o inválido (filtro de seguridad) | Hay que renovar el token con el refresh; el mismo pedido sin renovar falla igual |
| `UNAUTHENTICATED` | 401 | no | Falta el token (filtro de seguridad) | Hay que autenticarse; el cliente decide si renueva sesión |
| `FORBIDDEN` | 403 | no | Rol insuficiente (filtro de seguridad) | Los permisos no cambian por esperar |
| `CONCURRENT_MODIFICATION` | 409 | no | Otro usuario modificó el mismo registro | Hay que releer el registro antes de reintentar; repetir a ciegas pisa cambios ajenos |
| `DATA_INTEGRITY_VIOLATION` | 409 | no | Restricción de base de datos violada | Choca con el estado persistido; hay que releer y decidir |
| `ILLEGAL_STATE` | 409 | no | Estado ilegal no previsto por el dominio | Choca con el estado persistido; hay que releer y decidir |
| `INTERNAL_ERROR` | 500 | no | Error inesperado o integridad de datos rota | Es un defecto del servidor y repetir el mismo pedido lo repite; el detalle queda solo en el log |

## Errores de dominio

Cada excepción concreta del backend. El código sale del nombre de la clase; las que caen en el handler genérico (500) se informan como `INTERNAL_ERROR` y su detalle queda solo en el log.

### HTTP 400

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `ADMISSION_WINDOW_CLOSED` | no | `AdmissionWindowClosedException` | enrollment |
| `AMBIGUOUS_SECTION` | no | `AmbiguousSectionException` | enrollment |
| `ANNUAL_UPFRONT_IDEMPOTENCY_KEY_INVALID` | no | `AnnualUpfrontIdempotencyKeyInvalidException` | billing |
| `ANNUAL_UPFRONT_RECEIPT_AMOUNT_MUST_BE_TOTAL` | no | `AnnualUpfrontReceiptAmountMustBeTotalException` | billing |
| `ANNUAL_UPFRONT_RECEIPT_LATE` | no | `AnnualUpfrontReceiptLateException` | billing |
| `ANNUAL_UPFRONT_RECEIPT_MUST_BE_ALONE` | no | `AnnualUpfrontReceiptMustBeAloneException` | billing |
| `ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED` | no | `AnnualUpfrontTermsNotAcceptedException` | billing |
| `APPLICATION_DOCUMENT_TOO_LARGE` | no | `ApplicationDocumentTooLargeException` | enrollment |
| `ATTENDANCE_STATUS_NOT_RECORDABLE` | no | `AttendanceStatusNotRecordableException` | attendance |
| `BANK_TRANSFER_DETAILS_REQUIRED` | no | `BankTransferDetailsRequiredException` | billing |
| `BIRTH_DATE_REQUIRED` | no | `BirthDateRequiredException` | identity |
| `CANCELLATION_REASON_REQUIRED` | no | `CancellationReasonRequiredException` | billing |
| `CASH_PAYMENT_AMOUNT_INVALID` | no | `CashPaymentAmountInvalidException` | billing |
| `CASH_PAYMENT_BACKDATE_REASON_INVALID` | no | `CashPaymentBackdateReasonInvalidException` | billing |
| `CASH_PAYMENT_BACKDATE_REASON_REQUIRED` | no | `CashPaymentBackdateReasonRequiredException` | billing |
| `CASH_PAYMENT_DATE_TOO_OLD` | no | `CashPaymentDateTooOldException` | billing |
| `CASH_PAYMENT_RECEIPT_NUMBER_INVALID` | no | `CashPaymentReceiptNumberInvalidException` | billing |
| `CERTIFICATE_NOT_ELIGIBLE` | no | `CertificateNotEligibleException` | graduation |
| `CHANGE_REASON_REQUIRED` | no | `ChangeReasonRequiredException` | billing |
| `CHARGEBACK_DATE_INVALID` | no | `ChargebackDateInvalidException` | billing |
| `CHARGEBACK_REFERENCE_INVALID` | no | `ChargebackReferenceInvalidException` | billing |
| `CIRCULAR_PREREQUISITE` | no | `CircularPrerequisiteException` | academicstructure |
| `CLASS_ON_HOLIDAY` | no | `ClassOnHolidayException` | attendance |
| `CREDIT_NOTE_JUSTIFICATION_REQUIRED` | no | `CreditNoteJustificationRequiredException` | billing |
| `CREDIT_NOTE_LINES_INVALID` | no | `CreditNoteLinesInvalidException` | billing |
| `CURRENCY_MISMATCH` | no | `CurrencyMismatchException` | billing |
| `CURRENCY_MISMATCH` | no | `CurrencyMismatchException` | billing |
| `CURRENCY_MISMATCH` | no | `CurrencyMismatchException` | shared |
| `CURRENCY_REQUIRED` | no | `CurrencyRequiredException` | billing |
| `CURRICULAR_YEAR_OUT_OF_RANGE` | no | `CurricularYearOutOfRangeException` | academicstructure |
| `CYCLE_INCOMPLETE` | no | `CycleIncompleteException` | academicstructure |
| `DIRECTOR_APPROVAL_SUBJECT_REQUIRED` | no | `DirectorApprovalSubjectRequiredException` | enrollment |
| `DISCOUNT_CLOSURE_REASON_REQUIRED` | no | `DiscountClosureReasonRequiredException` | billing |
| `DISCOUNT_TYPE_NOT_IN_CATALOG` | no | `DiscountTypeNotInCatalogException` | billing |
| `DNI_ALREADY_EXISTS` | no | `DniAlreadyExistsException` | identity |
| `DNI_REQUIRED` | no | `DniRequiredException` | enrollment |
| `DUPLICATE_PROMOTION_BENEFIT_FEE_TYPE` | no | `DuplicatePromotionBenefitFeeTypeException` | billing |
| `EFFECTIVE_FROM_BEFORE_NEXT_DAY` | no | `EffectiveFromBeforeNextDayException` | billing |
| `ELECTIVE_GROUP_NOT_ASSIGNED` | no | `ElectiveGroupNotAssignedException` | academicstructure |
| `EMAIL_ALREADY_EXISTS` | no | `EmailAlreadyExistsException` | identity |
| `EMPTY_AUDIENCE` | no | `EmptyAudienceException` | announcement |
| `ENROLLMENT_FEE_NOT_PAID` | no | `EnrollmentFeeNotPaidException` | enrollment |
| `ENROLLMENT_FEE_NOT_PAID_FOR_TERM` | no | `EnrollmentFeeNotPaidForTermException` | enrollment |
| `ENROLLMENT_WINDOW_CLOSED` | no | `EnrollmentWindowClosedException` | enrollment |
| `EVALUATION_DATE_ON_HOLIDAY` | no | `EvaluationDateOnHolidayException` | evaluation |
| `EVALUATION_DATE_OUTSIDE_TERM` | no | `EvaluationDateOutsideTermException` | evaluation |
| `EXAM_BOARD_REGISTRATION_WINDOW_CLOSED` | no | `ExamBoardRegistrationWindowClosedException` | evaluation |
| `EXAM_BOARD_SUBJECT_MISMATCH` | no | `ExamBoardSubjectMismatchException` | evaluation |
| `EXAM_DATE_ON_HOLIDAY` | no | `ExamDateOnHolidayException` | evaluation |
| `FEATURE_CHANGE_REASON_INVALID` | no | `FeatureChangeReasonInvalidException` | platform |
| `FEATURE_VALUE_INVALID` | no | `FeatureValueInvalidException` | platform |
| `FINAL_EXAM_REQUIRES_REGULAR_STUDENT` | no | `FinalExamRequiresRegularStudentException` | evaluation |
| `GATEWAY_CURRENCY_NOT_SUPPORTED` | no | `GatewayCurrencyNotSupportedException` | billing |
| `GRADE_ITEM_TYPE_NOT_PUBLISHABLE` | no | `GradeItemTypeNotPublishableException` | evaluation |
| `GUARDIAN_REQUIRED_FOR_MINOR` | no | `GuardianRequiredForMinorException` | identity |
| `IDEMPOTENCY_KEY_INVALID` | no | `IdempotencyKeyInvalidException` | billing |
| `INCOMPLETE_SUBJECT` | no | `IncompleteSubjectException` | academicstructure |
| `INSTALLMENT_COUNT_ONLY_FOR_MONTHLY_INSTALLMENTS` | no | `InstallmentCountOnlyForMonthlyInstallmentsException` | billing |
| `INVALID_AD_HOC_FEE_TYPE` | no | `InvalidAdHocFeeTypeException` | billing |
| `INVALID_ANNUAL_UPFRONT_SETTINGS` | no | `InvalidAnnualUpfrontSettingsException` | billing |
| `INVALID_BILLING_RUN_RANGE` | no | `InvalidBillingRunRangeException` | billing |
| `INVALID_CERTIFICATE_CODE` | no | `InvalidCertificateCodeException` | graduation |
| `INVALID_COLLECTION_REMINDER_SCHEDULE` | no | `InvalidCollectionReminderScheduleException` | billing |
| `INVALID_COLLECTION_REMINDER_SETTINGS` | no | `InvalidCollectionReminderSettingsException` | billing |
| `INVALID_CONFIGURATION_VALUES` | no | `InvalidConfigurationValuesException` | billing |
| `INVALID_COREQUISITE` | no | `InvalidCorequisiteException` | academicstructure |
| `INVALID_CREDIT_NOTE_REASON` | no | `InvalidCreditNoteReasonException` | billing |
| `INVALID_CURRENT_PASSWORD` | no | `InvalidCurrentPasswordException` | identity |
| `INVALID_DATE_RANGE` | no | `InvalidDateRangeException` | shared |
| `INVALID_DISCOUNT_DEFINITION` | no | `InvalidDiscountDefinitionException` | billing |
| `INVALID_DOCUMENT_NUMBER` | no | `InvalidDocumentNumberException` | shared |
| `INVALID_ELECTIVE_RANGE` | no | `InvalidElectiveRangeException` | academicstructure |
| `INVALID_EMAIL_ADDRESS` | no | `InvalidEmailAddressException` | shared |
| `INVALID_EMAIL_VERIFICATION_TOKEN` | no | `InvalidEmailVerificationTokenException` | shared |
| `INVALID_ENROLLMENT_FEE_CONFIGURATION` | no | `InvalidEnrollmentFeeConfigurationException` | billing |
| `INVALID_EVALUATION_DATE` | no | `InvalidEvaluationDateException` | evaluation |
| `INVALID_EVALUATION_SCHEME` | no | `InvalidEvaluationSchemeException` | evaluation |
| `INVALID_GRADE` | no | `InvalidGradeException` | shared |
| `INVALID_GRADE_ITEM_WEIGHT` | no | `InvalidGradeItemWeightException` | evaluation |
| `INVALID_ISSUANCE_SETTINGS` | no | `InvalidIssuanceSettingsException` | billing |
| `INVALID_JUSTIFICATION_INPUT` | no | `InvalidJustificationInputException` | attendance |
| `INVALID_LATE_PAYMENT_SETTINGS` | no | `InvalidLatePaymentSettingsException` | billing |
| `INVALID_MARKETING_DISCOUNT` | no | `InvalidMarketingDiscountException` | billing |
| `INVALID_MONTH_RANGE` | no | `InvalidMonthRangeException` | billing |
| `INVALID_MONTH_RANGE` | no | `InvalidMonthRangeException` | notification |
| `INVALID_PASSWORD_POLICY` | no | `InvalidPasswordPolicyException` | identity |
| `INVALID_PAYMENT_PLAN_SCOPE` | no | `InvalidPaymentPlanScopeException` | billing |
| `INVALID_PERCENTAGE` | no | `InvalidPercentageException` | shared |
| `INVALID_PREREQUISITE` | no | `InvalidPrerequisiteException` | academicstructure |
| `INVALID_PRICE_AMOUNT` | no | `InvalidPriceAmountException` | billing |
| `INVALID_PROGRAM_SUBJECT_CATEGORY` | no | `InvalidProgramSubjectCategoryException` | academicstructure |
| `INVALID_REGISTRATION_WINDOW` | no | `InvalidRegistrationWindowException` | academicstructure |
| `INVALID_SCHOLARSHIP_PERIOD` | no | `InvalidScholarshipPeriodException` | billing |
| `INVALID_SECTION_CHANGE` | no | `InvalidSectionChangeException` | enrollment |
| `INVALID_SECTION_EVALUATION_CONFIG_TRANSITION` | no | `InvalidSectionEvaluationConfigTransitionException` | evaluation |
| `INVALID_SECTION_OVERRIDES` | no | `InvalidSectionOverridesException` | enrollment |
| `INVALID_SHIFT_RANGES` | no | `InvalidShiftRangesException` | academicstructure |
| `INVALID_STAFF_ROLE` | no | `InvalidStaffRoleException` | identity |
| `INVALID_STUDENT_DISCOUNT` | no | `InvalidStudentDiscountException` | billing |
| `INVALID_TERM_DATES` | no | `InvalidTermDatesException` | academicstructure |
| `INVALID_WINDOW_CONFIGURATION` | no | `InvalidWindowConfigurationException` | enrollment |
| `INVALID_YEAR_BILLING_CONFIGURATION` | no | `InvalidYearBillingConfigurationException` | billing |
| `LEGACY_RECEIPT_HAS_NO_VERIFIABLE_PAYMENT` | no | `LegacyReceiptHasNoVerifiablePaymentException` | billing |
| `MAX_EXAM_ATTEMPTS_EXCEEDED` | no | `MaxExamAttemptsExceededException` | evaluation |
| `NEGATIVE_ADJUSTMENT_AMOUNT` | no | `NegativeAdjustmentAmountException` | shared |
| `NEGATIVE_MONEY_AMOUNT` | no | `NegativeMoneyAmountException` | shared |
| `NOT_ELIGIBLE_FOR_GRADUATION` | no | `NotEligibleForGraduationException` | graduation |
| `NO_EVALUATION_SCHEME` | no | `NoEvaluationSchemeException` | evaluation |
| `PAYMENT_DATE_IN_FUTURE` | no | `PaymentDateInFutureException` | billing |
| `PAYMENT_REJECTION_REASON_REQUIRED` | no | `PaymentRejectionReasonRequiredException` | billing |
| `PAYMENT_REJECTION_REASON_TOO_LONG` | no | `PaymentRejectionReasonTooLongException` | billing |
| `PAYMENT_REVIEW_NOTE_TOO_LONG` | no | `PaymentReviewNoteTooLongException` | billing |
| `PREREQUISITE_NOT_MET` | no | `PrerequisiteNotMetException` | enrollment |
| `PRICE_FILTER_CONFLICT` | no | `PriceFilterConflictException` | billing |
| `PROGRAM_ENROLLMENT_REQUIRED` | no | `ProgramEnrollmentRequiredException` | enrollment |
| `RECEIPT_INVOICE_CURRENCY_MISMATCH` | no | `ReceiptInvoiceCurrencyMismatchException` | billing |
| `REFUND_AMOUNT_INVALID` | no | `RefundAmountInvalidException` | billing |
| `REFUND_COMPLETION_INVALID` | no | `RefundCompletionInvalidException` | billing |
| `REFUND_DESTINATION_INVALID` | no | `RefundDestinationInvalidException` | billing |
| `REFUND_DESTINATION_REQUIRED` | no | `RefundDestinationRequiredException` | billing |
| `REFUND_FAILURE_REASON_INVALID` | no | `RefundFailureReasonInvalidException` | billing |
| `REJECTION_REASON_REQUIRED` | no | `RejectionReasonRequiredException` | billing |
| `REQUEST_FIELD_OUT_OF_RANGE` | no | `RequestFieldOutOfRangeException` | shared |
| `REQUEST_FIELD_REQUIRED` | no | `RequestFieldRequiredException` | shared |
| `REQUEST_FIELD_TOO_LONG` | no | `RequestFieldTooLongException` | shared |
| `REQUEST_LIST_TOO_LARGE` | no | `RequestListTooLargeException` | shared |
| `REQUIRED_APPLICATION_DOCUMENTS_MISSING` | no | `RequiredApplicationDocumentsMissingException` | enrollment |
| `RETROACTIVE_EFFECTIVE_DATE` | no | `RetroactiveEffectiveDateException` | billing |
| `REVOCATION_REASON_REQUIRED` | no | `RevocationReasonRequiredException` | billing |
| `SCHEDULED_DATE_IN_THE_PAST` | no | `ScheduledDateInThePastException` | announcement |
| `SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS` | no | `ScholarshipOnlyForMonthlyInstallmentsException` | billing |
| `SECTION_CAPACITY_EXCEEDS_CLASSROOM` | no | `SectionCapacityExceedsClassroomException` | academicstructure |
| `SECTION_SHIFT_REQUIRED` | no | `SectionShiftRequiredException` | academicstructure |
| `SEGMENT_NOT_AVAILABLE_FOR_SECTION` | no | `SegmentNotAvailableForSectionException` | announcement |
| `SLOT_OUTSIDE_SHIFT` | no | `SlotOutsideShiftException` | academicstructure |
| `STANDING_EVIDENCE_INVALID` | no | `StandingEvidenceInvalidException` | graduation |
| `STANDING_NOT_CONFIRMED` | no | `StandingNotConfirmedException` | graduation |
| `STUDENT_CURRENT_ACCOUNT_CURRENCY_MISMATCH` | no | `StudentCurrentAccountCurrencyMismatchException` | billing |
| `STUDENT_NOT_ACTIVE` | no | `StudentNotActiveException` | identity |
| `STUDENT_NOT_CONFIRMED` | no | `StudentNotConfirmedException` | identity |
| `STUDENT_NOT_REGULAR` | no | `StudentNotRegularException` | evaluation |
| `SUBJECT_NOT_IN_PROGRAM_PLAN` | no | `SubjectNotInProgramPlanException` | enrollment |
| `SUBMISSION_WINDOW_CLOSED` | no | `SubmissionWindowClosedException` | evaluation |
| `TERM_DATES_REQUIRED` | no | `TermDatesRequiredException` | shared |
| `TERM_ENROLLMENT_WINDOW_REQUIRED` | no | `TermEnrollmentWindowRequiredException` | shared |
| `UNKNOWN_AUDIENCE_SEGMENT` | no | `UnknownAudienceSegmentException` | announcement |
| `UNSUPPORTED_CURRENCY` | no | `UnsupportedCurrencyException` | shared |
| `UNSUPPORTED_FILE_TYPE` | no | `UnsupportedFileTypeException` | shared |
| `VERIFIED_PAYMENT_AMOUNT_INVALID` | no | `VerifiedPaymentAmountInvalidException` | billing |
| `VERIFIED_PAYMENT_CURRENCY_MISMATCH` | no | `VerifiedPaymentCurrencyMismatchException` | billing |
| `WITHDRAWAL_DATE_OUT_OF_ALLOWED_RANGE` | no | `WithdrawalDateOutOfAllowedRangeException` | enrollment |

### HTTP 401

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `INVALID_CREDENTIALS` | no | `InvalidCredentialsException` | identity |
| `INVALID_GOOGLE_TOKEN` | no | `InvalidGoogleTokenException` | identity |
| `INVALID_MP_WEBHOOK_SIGNATURE` | no | `InvalidMpWebhookSignatureException` | billing |
| `INVALID_PASSWORD_RESET_TOKEN` | no | `InvalidPasswordResetTokenException` | identity |
| `INVALID_REFRESH_TOKEN` | no | `InvalidRefreshTokenException` | identity |

### HTTP 403

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `AUTHORIZATION_INBOX_ACCESS_DENIED` | no | `AuthorizationInboxAccessDeniedException` | billing |
| `AUTHORIZATION_REQUEST_ACCESS_DENIED` | no | `AuthorizationRequestAccessDeniedException` | billing |
| `NOT_EXAM_BOARD_TRIBUNAL_MEMBER` | no | `NotExamBoardTribunalMemberException` | evaluation |
| `NOT_OWN_ENROLLMENT` | no | `NotOwnEnrollmentException` | billing |
| `NOT_OWN_SECTION` | no | `NotOwnSectionException` | academicstructure |
| `NOT_SECTION_INSTRUCTOR` | no | `NotSectionInstructorException` | announcement |
| `NOT_SECTION_LEAD_INSTRUCTOR` | no | `NotSectionLeadInstructorException` | evaluation |
| `OWN_AUTHORIZATION_REQUEST_DECISION` | no | `OwnAuthorizationRequestDecisionException` | billing |
| `ROLE_NOT_ASSIGNABLE_BY_ACTOR` | no | `RoleNotAssignableByActorException` | identity |

### HTTP 404

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `ANNOUNCEMENT_NOT_FOUND` | no | `AnnouncementNotFoundException` | announcement |
| `ANNUAL_UPFRONT_ENROLLMENT_NOT_FOUND` | no | `AnnualUpfrontEnrollmentNotFoundException` | billing |
| `ANNUAL_UPFRONT_REQUEST_NOT_FOUND` | no | `AnnualUpfrontRequestNotFoundException` | billing |
| `ATTENDANCE_CHANGE_REQUEST_NOT_FOUND` | no | `AttendanceChangeRequestNotFoundException` | attendance |
| `ATTENDANCE_NOT_FOUND` | no | `AttendanceNotFoundException` | attendance |
| `AUTHORIZATION_REQUEST_NOT_FOUND` | no | `AuthorizationRequestNotFoundException` | billing |
| `BILLING_RUN_STEP_NOT_FOUND` | no | `BillingRunStepNotFoundException` | billing |
| `CERTIFICATE_NOT_FOUND` | no | `CertificateNotFoundException` | graduation |
| `CHARGEBACK_NOT_FOUND` | no | `ChargebackNotFoundException` | billing |
| `CLASSROOM_NOT_FOUND` | no | `ClassroomNotFoundException` | academicstructure |
| `CLASS_SESSION_NOT_FOUND` | no | `ClassSessionNotFoundException` | attendance |
| `CONFIGURATION_VERSION_NOT_FOUND` | no | `ConfigurationVersionNotFoundException` | billing |
| `COREQUISITE_NOT_FOUND` | no | `CorequisiteNotFoundException` | academicstructure |
| `COURSE_ENROLLMENT_NOT_FOUND` | no | `CourseEnrollmentNotFoundException` | enrollment |
| `COURSE_NOT_FOUND` | no | `CourseNotFoundException` | academicstructure |
| `COURSE_SUBJECT_NOT_FOUND` | no | `CourseSubjectNotFoundException` | academicstructure |
| `CREDIT_NOTE_INVOICE_NOT_FOUND` | no | `CreditNoteInvoiceNotFoundException` | billing |
| `CYCLE_NOT_FOUND` | no | `CycleNotFoundException` | academicstructure |
| `DIRECTOR_APPROVAL_REQUEST_NOT_FOUND` | no | `DirectorApprovalRequestNotFoundException` | enrollment |
| `DISCOUNT_DEFINITION_NOT_FOUND` | no | `DiscountDefinitionNotFoundException` | billing |
| `DISCOUNT_SCOPE_OFFERING_NOT_FOUND` | no | `DiscountScopeOfferingNotFoundException` | billing |
| `ELECTIVE_GROUP_NOT_FOUND` | no | `ElectiveGroupNotFoundException` | academicstructure |
| `ENROLLMENT_FEE_EXEMPTION_NOT_FOUND` | no | `EnrollmentFeeExemptionNotFoundException` | enrollment |
| `ENROLLMENT_NOT_FOUND_FOR_DISCOUNT` | no | `EnrollmentNotFoundForDiscountException` | billing |
| `ENROLLMENT_PAYMENT_STANDING_NOT_FOUND` | no | `EnrollmentPaymentStandingNotFoundException` | billing |
| `EVALUATION_SCHEME_NOT_FOUND` | no | `EvaluationSchemeNotFoundException` | evaluation |
| `EXAM_BOARD_NOT_FOUND` | no | `ExamBoardNotFoundException` | evaluation |
| `EXAM_BOARD_REGISTRATION_NOT_FOUND` | no | `ExamBoardRegistrationNotFoundException` | evaluation |
| `GOOGLE_ACCOUNT_NOT_LINKED` | no | `GoogleAccountNotLinkedException` | identity |
| `GRADE_ITEM_NOT_FOUND` | no | `GradeItemNotFoundException` | evaluation |
| `GRADUATION_CERTIFICATE_NOT_FOUND` | no | `GraduationCertificateNotFoundException` | graduation |
| `GRADUATION_REQUEST_NOT_FOUND` | no | `GraduationRequestNotFoundException` | graduation |
| `HOLIDAY_NOT_FOUND` | no | `HolidayNotFoundException` | academicstructure |
| `INSTITUTION_NOT_FOUND` | no | `InstitutionNotFoundException` | identity |
| `INSTRUCTOR_NOT_FOUND` | no | `InstructorNotFoundException` | academicstructure |
| `INVOICE_NOT_FOUND` | no | `InvoiceNotFoundException` | billing |
| `MONTHLY_INVOICE_RUN_ITEM_NOT_FOUND` | no | `MonthlyInvoiceRunItemNotFoundException` | billing |
| `MONTHLY_INVOICE_RUN_NOT_FOUND` | no | `MonthlyInvoiceRunNotFoundException` | billing |
| `NIGHTLY_BILLING_RUN_NOT_FOUND` | no | `NightlyBillingRunNotFoundException` | billing |
| `NOTIFICATION_DELIVERY_NOT_FOUND` | no | `NotificationDeliveryNotFoundException` | notification |
| `NOTIFICATION_NOT_FOUND` | no | `NotificationNotFoundException` | notification |
| `NO_ACTIVE_LATE_PAYMENT_SETTINGS` | no | `NoActiveLatePaymentSettingsException` | billing |
| `PAYMENT_GATEWAY_NOT_FOUND` | no | `PaymentGatewayNotFoundException` | billing |
| `PAYMENT_NOT_FOUND` | no | `PaymentNotFoundException` | billing |
| `PAYMENT_PLAN_OPTION_NOT_FOUND` | no | `PaymentPlanOptionNotFoundException` | billing |
| `PAYMENT_RECEIPT_FILE_NOT_FOUND` | no | `PaymentReceiptFileNotFoundException` | billing |
| `PAYMENT_RECEIPT_NOT_FOUND` | no | `PaymentReceiptNotFoundException` | billing |
| `PERSON_NOT_FOUND` | no | `PersonNotFoundException` | identity |
| `PREREQUISITE_NOT_FOUND` | no | `PrerequisiteNotFoundException` | academicstructure |
| `PRICE_NOT_FOUND` | no | `PriceNotFoundException` | billing |
| `PRICE_VERSION_NOT_FOUND` | no | `PriceVersionNotFoundException` | billing |
| `PROGRAM_COHORT_NOT_FOUND` | no | `ProgramCohortNotFoundException` | billing |
| `PROGRAM_ENROLLMENT_APPLICATION_NOT_FOUND` | no | `ProgramEnrollmentApplicationNotFoundException` | enrollment |
| `PROGRAM_ENROLLMENT_NOT_FOUND` | no | `ProgramEnrollmentNotFoundException` | enrollment |
| `PROGRAM_NOT_FOUND` | no | `ProgramNotFoundException` | academicstructure |
| `PROGRAM_SUBJECT_NOT_FOUND` | no | `ProgramSubjectNotFoundException` | academicstructure |
| `PROGRAM_YEAR_BILLING_TEMPLATE_NOT_FOUND` | no | `ProgramYearBillingTemplateNotFoundException` | billing |
| `PROMOTION_NOT_FOUND` | no | `PromotionNotFoundException` | billing |
| `RECEIPT_INVOICE_OF_ANOTHER_ACCOUNT` | no | `ReceiptInvoiceOfAnotherAccountException` | billing |
| `REFUND_NOT_FOUND` | no | `RefundNotFoundException` | billing |
| `REGULAR_CERTIFICATE_NOT_FOUND` | no | `RegularCertificateNotFoundException` | graduation |
| `REMINDER_SCHEDULE_TARGET_NOT_FOUND` | no | `ReminderScheduleTargetNotFoundException` | billing |
| `ROLE_NOT_FOUND` | no | `RoleNotFoundException` | identity |
| `SECTION_ENROLLMENT_NOT_FOUND` | no | `SectionEnrollmentNotFoundException` | enrollment |
| `SECTION_EVALUATION_CONFIG_NOT_FOUND` | no | `SectionEvaluationConfigNotFoundException` | evaluation |
| `SECTION_INSTRUCTOR_NOT_FOUND` | no | `SectionInstructorNotFoundException` | academicstructure |
| `SECTION_NOT_FOUND` | no | `SectionNotFoundException` | academicstructure |
| `STUDENT_DISCOUNT_NOT_FOUND` | no | `StudentDiscountNotFoundException` | billing |
| `STUDENT_NOT_FOUND` | no | `StudentNotFoundException` | identity |
| `SUBJECT_NOT_FOUND` | no | `SubjectNotFoundException` | academicstructure |
| `TERM_NOT_FOUND` | no | `TermNotFoundException` | academicstructure |
| `UNKNOWN_METRIC_PANEL` | no | `UnknownMetricPanelException` | billing |

### HTTP 409

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `ACTIVE_TERM_ALREADY_EXISTS` | no | `ActiveTermAlreadyExistsException` | academicstructure |
| `ADMISSION_FEE_AWAITING_PAYMENT` | no | `AdmissionFeeAwaitingPaymentException` | enrollment |
| `ALREADY_ENROLLED_IN_COURSE` | no | `AlreadyEnrolledInCourseException` | enrollment |
| `ALREADY_ENROLLED_IN_PROGRAM` | no | `AlreadyEnrolledInProgramException` | enrollment |
| `ALREADY_ENROLLED_IN_SUBJECT` | no | `AlreadyEnrolledInSubjectException` | enrollment |
| `ALREADY_HAS_PENDING_APPLICATION` | no | `AlreadyHasPendingApplicationException` | enrollment |
| `ALREADY_REGISTERED_IN_ANOTHER_EXAM_BOARD` | no | `AlreadyRegisteredInAnotherExamBoardException` | evaluation |
| `ALREADY_REGISTERED_IN_EXAM_BOARD` | no | `AlreadyRegisteredInExamBoardException` | evaluation |
| `ANNOUNCEMENT_ALREADY_SENT` | no | `AnnouncementAlreadySentException` | announcement |
| `ANNUAL_UPFRONT_OF_PROGRAM_MUST_USE_REQUEST_ENDPOINT` | no | `AnnualUpfrontOfProgramMustUseRequestEndpointException` | billing |
| `ANNUAL_UPFRONT_PAYMENT_ALREADY_INITIATED` | no | `AnnualUpfrontPaymentAlreadyInitiatedException` | billing |
| `ANNUAL_UPFRONT_PAYMENT_EXPIRED` | no | `AnnualUpfrontPaymentExpiredException` | billing |
| `ANNUAL_UPFRONT_PLAN_CANNOT_BE_CHANGED` | no | `AnnualUpfrontPlanCannotBeChangedException` | billing |
| `ANNUAL_UPFRONT_REQUEST_REJECTED` | no | `AnnualUpfrontRequestRejectedException` | billing |
| `APPLICATION_DOCUMENTS_INCOMPLETE` | no | `ApplicationDocumentsIncompleteException` | enrollment |
| `APPLICATION_DOCUMENTS_LOCKED` | no | `ApplicationDocumentsLockedException` | enrollment |
| `ATTENDANCE_ALREADY_RECORDED` | no | `AttendanceAlreadyRecordedException` | attendance |
| `AUTHORIZATION_PENDING` | no | `AuthorizationPendingException` | billing |
| `BILLING_CONFIGURATION_FROZEN` | no | `BillingConfigurationFrozenException` | billing |
| `CANNOT_CHANGE_AUDIENCE_OF_SENT_ANNOUNCEMENT` | no | `CannotChangeAudienceOfSentAnnouncementException` | announcement |
| `CANNOT_JUSTIFY` | no | `CannotJustifyException` | attendance |
| `CANNOT_WITHDRAW` | no | `CannotWithdrawException` | enrollment |
| `CASH_PAYMENT_PREVIEW_OUTDATED` | no | `CashPaymentPreviewOutdatedException` | billing |
| `CHARGEBACK_ALREADY_REVERSED` | no | `ChargebackAlreadyReversedException` | billing |
| `CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT` | no | `ChargebackMustBeForTheFullAmountException` | billing |
| `CHARGE_REJECTED` | no | `ChargeRejectedException` | billing |
| `CLASSROOM_IN_USE` | no | `ClassroomInUseException` | academicstructure |
| `CLASS_SESSION_ALREADY_CANCELED` | no | `ClassSessionAlreadyCanceledException` | attendance |
| `COHORT_ADMISSION_WINDOW_CLOSED` | no | `CohortAdmissionWindowClosedException` | billing |
| `COHORT_PROMOTION_ALREADY_ACQUIRED` | no | `CohortPromotionAlreadyAcquiredException` | billing |
| `CONCURRENT_REGULAR_CERTIFICATE_ISSUE` | no | `ConcurrentRegularCertificateIssueException` | graduation |
| `COREQUISITE_ALREADY_EXISTS` | no | `CorequisiteAlreadyExistsException` | academicstructure |
| `COURSE_ADMISSION_ALREADY_CHARGED` | no | `CourseAdmissionAlreadyChargedException` | enrollment |
| `COURSE_ADMISSION_CHARGE_REJECTED` | no | `CourseAdmissionChargeRejectedException` | enrollment |
| `COURSE_NOT_AVAILABLE` | no | `CourseNotAvailableException` | enrollment |
| `COURSE_PAYMENT_PLAN_ALREADY_SET` | no | `CoursePaymentPlanAlreadySetException` | billing |
| `CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED` | no | `CreditNoteDirectorApprovalRequiredException` | billing |
| `CREDIT_NOTE_DISTRIBUTION_MISMATCH` | no | `CreditNoteDistributionMismatchException` | billing |
| `CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT` | no | `CreditNoteExceedsCreditableAmountException` | billing |
| `CREDIT_NOTE_ISSUING_REJECTED` | no | `CreditNoteIssuingRejectedException` | billing |
| `CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT` | no | `CreditNoteRefundExceedsPaidAmountException` | billing |
| `CURRENT_ACCOUNT_ENTRY_SOURCE_MISMATCH` | no | `CurrentAccountEntrySourceMismatchException` | billing |
| `CYCLE_ALREADY_IN_PROGRESS` | no | `CycleAlreadyInProgressException` | academicstructure |
| `CYCLE_NOT_EDITABLE` | no | `CycleNotEditableException` | academicstructure |
| `CYCLE_NOT_PENDING_APPROVAL` | no | `CycleNotPendingApprovalException` | academicstructure |
| `DIRECTOR_ALREADY_EXISTS` | no | `DirectorAlreadyExistsException` | identity |
| `DIRECTOR_APPROVAL_REQUEST_ALREADY_ACTIVE` | no | `DirectorApprovalRequestAlreadyActiveException` | enrollment |
| `DISCOUNT_DEFINITION_CLOSED` | no | `DiscountDefinitionClosedException` | billing |
| `DISCOUNT_EXCEEDS_LINE_AMOUNT` | no | `DiscountExceedsLineAmountException` | billing |
| `DISCOUNT_INVOICE_TYPE_MISMATCH` | no | `DiscountInvoiceTypeMismatchException` | billing |
| `DRAFT_NOT_PENDING` | no | `DraftNotPendingException` | billing |
| `DUE_DATE_BEFORE_ISSUE_DATE` | no | `DueDateBeforeIssueDateException` | billing |
| `DUPLICATE_CHARGE` | no | `DuplicateChargeException` | billing |
| `DUPLICATE_INVOICE_CHARGE` | no | `DuplicateInvoiceChargeException` | billing |
| `ELECTIVE_GROUP_ALREADY_EXISTS` | no | `ElectiveGroupAlreadyExistsException` | academicstructure |
| `ELECTIVE_GROUP_IN_USE` | no | `ElectiveGroupInUseException` | academicstructure |
| `ENROLLMENT_FEE_ALREADY_ISSUED` | no | `EnrollmentFeeAlreadyIssuedException` | billing |
| `ENROLLMENT_FEE_EXEMPTION_ALREADY_EXISTS` | no | `EnrollmentFeeExemptionAlreadyExistsException` | enrollment |
| `ENROLLMENT_FEE_EXEMPTION_ALREADY_REVOKED` | no | `EnrollmentFeeExemptionAlreadyRevokedException` | enrollment |
| `ENROLLMENT_FEE_EXEMPTION_NOT_NEEDED` | no | `EnrollmentFeeExemptionNotNeededException` | enrollment |
| `EVALUATION_SCHEME_IN_USE` | no | `EvaluationSchemeInUseException` | evaluation |
| `EXAM_BOARD_CLOSED` | no | `ExamBoardClosedException` | evaluation |
| `EXAM_BOARD_HAS_GRADES` | no | `ExamBoardHasGradesException` | evaluation |
| `EXAM_BOARD_NOT_YET_TAKEN` | no | `ExamBoardNotYetTakenException` | evaluation |
| `EXAM_BOARD_REGISTRATION_NOT_WITHDRAWABLE` | no | `ExamBoardRegistrationNotWithdrawableException` | evaluation |
| `FEATURE_ALREADY_IN_REQUESTED_STATE` | no | `FeatureAlreadyInRequestedStateException` | platform |
| `GLOBAL_ACCOUNT_INCONSISTENCY` | no | `GlobalAccountInconsistencyException` | billing |
| `GRADE_ITEM_PUBLISHED_LOCKED` | no | `GradeItemPublishedLockedException` | evaluation |
| `GRADUATION_CERTIFICATE_ALREADY_ISSUED` | no | `GraduationCertificateAlreadyIssuedException` | graduation |
| `GRADUATION_CERTIFICATE_NOT_ELIGIBLE` | no | `GraduationCertificateNotEligibleException` | graduation |
| `GRADUATION_REQUEST_ALREADY_ACTIVE` | no | `GraduationRequestAlreadyActiveException` | graduation |
| `GRADUATION_REQUIREMENTS_NOT_MET` | no | `GraduationRequirementsNotMetException` | graduation |
| `HOLIDAYS_NOT_LOADED_FOR_YEAR` | no | `HolidaysNotLoadedForYearException` | academicstructure |
| `HOLIDAY_ALREADY_EXISTS` | no | `HolidayAlreadyExistsException` | academicstructure |
| `IDEMPOTENCY_KEY_CONFLICT` | no | `IdempotencyKeyConflictException` | billing |
| `IDEMPOTENCY_KEY_REUSED` | no | `IdempotencyKeyReusedException` | platform |
| `ILLEGAL_ANNUAL_UPFRONT_REQUEST_TRANSITION` | no | `IllegalAnnualUpfrontRequestTransitionException` | billing |
| `ILLEGAL_AUTHORIZATION_REQUEST_TRANSITION` | no | `IllegalAuthorizationRequestTransitionException` | billing |
| `ILLEGAL_BILLING_RUN_TRANSITION` | no | `IllegalBillingRunTransitionException` | billing |
| `ILLEGAL_CONFIGURATION_VERSION_STATUS_TRANSITION` | no | `IllegalConfigurationVersionStatusTransitionException` | billing |
| `ILLEGAL_DISCOUNT_DEFINITION_TRANSITION` | no | `IllegalDiscountDefinitionTransitionException` | billing |
| `ILLEGAL_ENROLLMENT_PAYMENT_STATUS_TRANSITION` | no | `IllegalEnrollmentPaymentStatusTransitionException` | billing |
| `ILLEGAL_INVOICE_DOCUMENT_STATUS_TRANSITION` | no | `IllegalInvoiceDocumentStatusTransitionException` | billing |
| `ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION` | no | `IllegalInvoicePaymentStatusTransitionException` | billing |
| `ILLEGAL_NOTIFICATION_DELIVERY_STATUS_TRANSITION` | no | `IllegalNotificationDeliveryStatusTransitionException` | notification |
| `ILLEGAL_PAYMENT_STATUS_TRANSITION` | no | `IllegalPaymentStatusTransitionException` | billing |
| `ILLEGAL_PRICE_VERSION_STATUS_TRANSITION` | no | `IllegalPriceVersionStatusTransitionException` | billing |
| `ILLEGAL_REFUND_STATUS_TRANSITION` | no | `IllegalRefundStatusTransitionException` | billing |
| `ILLEGAL_STUDENT_DISCOUNT_TRANSITION` | no | `IllegalStudentDiscountTransitionException` | billing |
| `INCOMPLETE_SECTION_SETUP` | no | `IncompleteSectionSetupException` | enrollment |
| `INSTITUTE_SETTING_ALREADY_IN_REQUESTED_STATE` | no | `InstituteSettingAlreadyInRequestedStateException` | platform |
| `INSTRUCTOR_ALREADY_ASSIGNED` | no | `InstructorAlreadyAssignedException` | academicstructure |
| `INSTRUCTOR_SCHEDULE_CONFLICT` | no | `InstructorScheduleConflictException` | academicstructure |
| `INSUFFICIENT_ACCOUNT_CREDIT` | no | `InsufficientAccountCreditException` | billing |
| `INVALID_ATTENDANCE_CHANGE_REQUEST_STATE` | no | `InvalidAttendanceChangeRequestStateException` | attendance |
| `INVALID_BILLING_MODE_TRANSITION` | no | `InvalidBillingModeTransitionException` | platform |
| `INVALID_DIRECTOR_APPROVAL_REQUEST_STATE` | no | `InvalidDirectorApprovalRequestStateException` | enrollment |
| `INVALID_GRADUATION_REQUEST_STATE` | no | `InvalidGraduationRequestStateException` | graduation |
| `INVALID_PAYMENT_RECEIPT_STATE` | no | `InvalidPaymentReceiptStateException` | billing |
| `INVALID_PROGRAM_ENROLLMENT_APPLICATION_STATE` | no | `InvalidProgramEnrollmentApplicationStateException` | enrollment |
| `INVALID_TRANSITION_CONFIRMATION` | no | `InvalidTransitionConfirmationException` | platform |
| `INVOICE_ALREADY_ATTACHED` | no | `InvoiceAlreadyAttachedException` | billing |
| `INVOICE_ALREADY_UNDER_REVIEW` | no | `InvoiceAlreadyUnderReviewException` | billing |
| `INVOICE_ATTACHMENT_MISMATCH` | no | `InvoiceAttachmentMismatchException` | billing |
| `INVOICE_ISSUING_REJECTED` | no | `InvoiceIssuingRejectedException` | billing |
| `INVOICE_LINES_ENROLLMENT_MISMATCH` | no | `InvoiceLinesEnrollmentMismatchException` | billing |
| `INVOICE_LINE_NOT_ALLOWED_FOR_INVOICE_TYPE` | no | `InvoiceLineNotAllowedForInvoiceTypeException` | billing |
| `INVOICE_NOT_PAYABLE` | no | `InvoiceNotPayableException` | billing |
| `ISSUED_INVOICE_CANNOT_BE_MODIFIED` | no | `IssuedInvoiceCannotBeModifiedException` | billing |
| `JUSTIFICATION_DEADLINE_EXPIRED` | no | `JustificationDeadlineExpiredException` | attendance |
| `LAST_DIGITAL_PAYMENT_METHOD_REQUIRED` | no | `LastDigitalPaymentMethodRequiredException` | platform |
| `LATE_FEE_CORRECTION_REJECTED` | no | `LateFeeCorrectionRejectedException` | billing |
| `LATE_FEE_ISSUING_REJECTED` | no | `LateFeeIssuingRejectedException` | billing |
| `LEGACY_RECEIPT_PENDING_ON_INVOICE` | no | `LegacyReceiptPendingOnInvoiceException` | billing |
| `MANUAL_ISSUANCE_OUTSIDE_CURRENT_MONTH` | no | `ManualIssuanceOutsideCurrentMonthException` | billing |
| `MANUAL_ISSUANCE_REQUIRES_SIMULATION` | no | `ManualIssuanceRequiresSimulationException` | billing |
| `MONTHLY_INVOICE_RUN_ALREADY_EXISTS` | no | `MonthlyInvoiceRunAlreadyExistsException` | billing |
| `MONTHLY_INVOICE_RUN_NOT_AWAITING_REVIEW` | no | `MonthlyInvoiceRunNotAwaitingReviewException` | billing |
| `NIGHTLY_BILLING_NOT_COMPLETED` | no | `NightlyBillingNotCompletedException` | billing |
| `NOT_REGULAR` | no | `NotRegularException` | graduation |
| `NO_ACTIVE_PAYMENT_PLAN` | no | `NoActivePaymentPlanException` | billing |
| `NO_EFFECTIVE_CONFIGURATION` | no | `NoEffectiveConfigurationException` | billing |
| `NO_EFFECTIVE_PRICE` | no | `NoEffectivePriceException` | billing |
| `NO_PENDING_GRADES_TO_PUBLISH` | no | `NoPendingGradesToPublishException` | evaluation |
| `OUTSTANDING_DEBT_BLOCKS_REENROLLMENT` | no | `OutstandingDebtBlocksReenrollmentException` | billing |
| `PARTIAL_PAYMENT_NOT_ALLOWED` | no | `PartialPaymentNotAllowedException` | billing |
| `PAYMENT_ALLOCATION_REJECTED` | no | `PaymentAllocationRejectedException` | billing |
| `PAYMENT_ALREADY_APPROVED` | no | `PaymentAlreadyApprovedException` | billing |
| `PAYMENT_ALREADY_REFUNDED` | no | `PaymentAlreadyRefundedException` | billing |
| `PAYMENT_EXCEEDS_INVOICE_BALANCE` | no | `PaymentExceedsInvoiceBalanceException` | billing |
| `PAYMENT_GATEWAY_CAPABILITY_NOT_SUPPORTED` | no | `PaymentGatewayCapabilityNotSupportedException` | billing |
| `PAYMENT_GATEWAY_UNAVAILABLE` | no | `PaymentGatewayUnavailableException` | billing |
| `PAYMENT_HAS_PENDING_REFUND` | no | `PaymentHasPendingRefundException` | billing |
| `PAYMENT_PLAN_OPTION_MISMATCH` | no | `PaymentPlanOptionMismatchException` | billing |
| `PAYMENT_RECEIPT_ALREADY_PENDING` | no | `PaymentReceiptAlreadyPendingException` | billing |
| `PAYMENT_RECEIPT_ALREADY_REVIEWED` | no | `PaymentReceiptAlreadyReviewedException` | billing |
| `PENDING_BANK_TRANSFER_ON_INVOICE` | no | `PendingBankTransferOnInvoiceException` | billing |
| `PENDING_COURSE_ENROLLMENT_APPLICATION` | no | `PendingCourseEnrollmentApplicationException` | enrollment |
| `PENDING_DEBT` | no | `PendingDebtException` | billing |
| `POSSIBLE_DUPLICATE_BANK_TRANSFER` | no | `PossibleDuplicateBankTransferException` | billing |
| `PREREQUISITE_ALREADY_EXISTS` | no | `PrerequisiteAlreadyExistsException` | academicstructure |
| `PRICE_CHANGED_CONCURRENTLY` | no | `PriceChangedConcurrentlyException` | billing |
| `PROGRAM_ENROLLMENT_MISSING_COHORT` | no | `ProgramEnrollmentMissingCohortException` | billing |
| `PROGRAM_NOT_AVAILABLE` | no | `ProgramNotAvailableException` | enrollment |
| `PROGRAM_SUBJECT_ALREADY_EXISTS` | no | `ProgramSubjectAlreadyExistsException` | academicstructure |
| `PROGRAM_YEAR_BILLING_TEMPLATE_MISMATCH` | no | `ProgramYearBillingTemplateMismatchException` | billing |
| `PROMOTION_ALREADY_CLOSED` | no | `PromotionAlreadyClosedException` | billing |
| `PROPOSAL_ALREADY_OPEN` | no | `ProposalAlreadyOpenException` | billing |
| `PROPOSAL_EXPIRED` | no | `ProposalExpiredException` | billing |
| `PROPOSAL_NO_LONGER_OPEN` | no | `ProposalNoLongerOpenException` | billing |
| `PROSPECT_ALREADY_CONFIRMED` | no | `ProspectAlreadyConfirmedException` | identity |
| `RECEIPT_INVOICE_NOT_PAYABLE` | no | `ReceiptInvoiceNotPayableException` | billing |
| `RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST` | no | `ReceiptMustCoverOldestInvoicesFirstException` | billing |
| `RECEIPT_TRANSFER_DATE_MISMATCH` | no | `ReceiptTransferDateMismatchException` | billing |
| `REFUND_BLOCKED_BY_OVERDUE_DEBT` | no | `RefundBlockedByOverdueDebtException` | billing |
| `REFUND_EXCEEDS_AVAILABLE_AMOUNT` | no | `RefundExceedsAvailableAmountException` | billing |
| `REFUND_METHOD_MISMATCH` | no | `RefundMethodMismatchException` | billing |
| `REFUND_REQUEST_NOT_PENDING` | no | `RefundRequestNotPendingException` | billing |
| `REGULAR_CERTIFICATE_ALREADY_ISSUED` | no | `RegularCertificateAlreadyIssuedException` | graduation |
| `RUN_ITEM_NOT_RETRYABLE` | no | `RunItemNotRetryableException` | billing |
| `SCHOLARSHIP_NOT_AVAILABLE_WITH_ANNUAL_UPFRONT` | no | `ScholarshipNotAvailableWithAnnualUpfrontException` | billing |
| `SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT` | no | `ScholarshipStartBeforeNextInstallmentException` | billing |
| `SECTION_CODE_ALREADY_EXISTS` | no | `SectionCodeAlreadyExistsException` | academicstructure |
| `SECTION_MODIFICATION_ALREADY_PENDING` | no | `SectionModificationAlreadyPendingException` | academicstructure |
| `SECTION_MODIFICATION_NOT_PENDING` | no | `SectionModificationNotPendingException` | academicstructure |
| `SECTION_SCHEDULE_CONFLICT` | no | `SectionScheduleConflictException` | academicstructure |
| `STAFF_PASSWORD_RESET_BLOCKED` | no | `StaffPasswordResetBlockedException` | identity |
| `STALE_FEATURE_VERSION` | no | `StaleFeatureVersionException` | platform |
| `STALE_INSTITUTE_SETTING_VERSION` | no | `StaleInstituteSettingVersionException` | platform |
| `STUDENT_DISCOUNT_OVERLAP` | no | `StudentDiscountOverlapException` | billing |
| `STUDENT_DISCOUNT_PROPOSAL_EXPIRED` | no | `StudentDiscountProposalExpiredException` | billing |
| `STUDENT_FREE_BY_ABSENCES` | no | `StudentFreeByAbsencesException` | evaluation |
| `STUDENT_HAS_ACTIVE_ENROLLMENTS` | no | `StudentHasActiveEnrollmentsException` | identity |
| `STUDENT_HAS_DEBT` | no | `StudentHasDebtException` | graduation |
| `SUBJECT_ALREADY_EXISTS` | no | `SubjectAlreadyExistsException` | academicstructure |
| `SUBJECT_INVALID_TRANSITION` | no | `SubjectInvalidTransitionException` | academicstructure |
| `SUBJECT_NOT_ASSIGNABLE_TO_PLAN` | no | `SubjectNotAssignableToPlanException` | academicstructure |
| `SUBJECT_NOT_OPENABLE_FOR_SECTIONS` | no | `SubjectNotOpenableForSectionsException` | academicstructure |
| `TERM_ENROLLMENT_OVERRIDE_CLOSED` | no | `TermEnrollmentOverrideClosedException` | enrollment |
| `UNKNOWN_GUARDED_MODULE` | no | `UnknownGuardedModuleException` | platform |
| `UPFRONT_WINDOW_CLOSED` | no | `UpfrontWindowClosedException` | billing |
| `USERNAME_ALREADY_EXISTS` | no | `UsernameAlreadyExistsException` | identity |
| `VERSION_ALREADY_EFFECTIVE` | no | `VersionAlreadyEffectiveException` | billing |
| `WITHDRAWAL_REDUCTION_REJECTED` | no | `WithdrawalReductionRejectedException` | billing |

### HTTP 429

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `RATE_LIMIT_EXCEEDED` | sí | `RateLimitExceededException` | identity |
| `REQUEST_RATE_LIMIT_EXCEEDED` | sí | `RequestRateLimitExceededException` | shared |

### HTTP 500

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `INTERNAL_ERROR` | no | `CreditNoteSourceIntegrityException` | billing |
| `INTERNAL_ERROR` | no | `EmailDeliveryException` | shared |
| `INTERNAL_ERROR` | no | `FeatureNotProvisionedException` | platform |
| `INTERNAL_ERROR` | no | `InstituteSettingNotProvisionedException` | platform |
| `INTERNAL_ERROR` | no | `InvalidInvoiceAmountsException` | billing |
| `INTERNAL_ERROR` | no | `InvoiceStatusInconsistentWithAmountsException` | billing |
| `INTERNAL_ERROR` | no | `PaymentSourceIntegrityException` | billing |
| `INTERNAL_ERROR` | no | `UnsupportedGatewayOperationException` | billing |

### HTTP 502

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `GOOGLE_SERVICE_UNAVAILABLE` | sí | `GoogleServiceUnavailableException` | identity |
| `MERCADO_PAGO_GATEWAY` | sí | `MercadoPagoGatewayException` | billing |
| `TRANSACTIONAL_EMAIL_DELIVERY` | sí | `TransactionalEmailDeliveryException` | shared |

### HTTP 503

| Código | Reintentable | Excepción | Módulo |
|---|---|---|---|
| `EXTERNAL_SERVICE_UNAVAILABLE` | sí | `CircuitOpenException` | shared |

## Idempotencia de las escrituras

Se asume idempotentes por contrato HTTP los PUT y DELETE. Los POST y PATCH de abajo se clasifican por su manejo de `Idempotency-Key`: `obligatoria` (el backend la exige), `opcional` (la acepta; sin clave un reintento puede duplicar el efecto) o `no` (no la lee; un reintento puede duplicar el efecto).

### Idempotency-Key obligatoria

| Método | Ruta | Handler |
|---|---|---|
| POST | `/me/program-enrollments/{id}/annual-upfront-requests` | `AnnualUpfrontRequestController#requestAnnualUpfront` |
| POST | `/monthly-invoice-runs/{monthlyRunId}/draft-invoices/approvals` | `MonthlyInvoiceRunController#approveDraftInvoices` |

### Idempotency-Key opcional

| Método | Ruta | Handler |
|---|---|---|
| POST | `/course-enrollments/{id}/invoices/ad-hoc` | `CourseEnrollmentController#createAdHocInvoice` |
| POST | `/invoices/{invoiceId}/credit-notes` | `CreditNoteController#issueOrRequestCreditNote` |
| POST | `/me/bank-transfer-receipts` | `BankTransferReceiptSubmissionController#submitOwnReceipt` |
| POST | `/me/course-enrollments` | `MeEnrollmentController#enrollInCourse` |
| POST | `/me/refund-requests` | `RefundController#requestOwnRefund` |
| POST | `/platform/provisioning/first-director` | `PlatformProvisioningController#provisionFirstDirector` |
| POST | `/program-enrollments/{id}/invoices/ad-hoc` | `ProgramEnrollmentController#createAdHocInvoice` |
| POST | `/students/{studentId}/bank-transfer-receipts` | `BankTransferReceiptSubmissionController#submitReceiptOnBehalfOfStudent` |
| POST | `/students/{studentId}/cash-payments` | `StudentPaymentController#registerCashPayment` |
| POST | `/students/{studentId}/refund-requests` | `RefundController#requestRefundOnBehalfOfStudent` |

### Idempotency-Key no

| Método | Ruta | Handler |
|---|---|---|
| POST | `/academic-offering-cycles` | `AcademicOfferingCycleController#create` |
| POST | `/academic-offering-cycles/{id}/approve` | `AcademicOfferingCycleController#approve` |
| POST | `/academic-offering-cycles/{id}/reject` | `AcademicOfferingCycleController#reject` |
| POST | `/academic-offering-cycles/{id}/sections` | `AcademicOfferingCycleController#createSection` |
| POST | `/academic-offering-cycles/{id}/submit` | `AcademicOfferingCycleController#submit` |
| POST | `/attendance/change-requests/{id}/approve` | `AttendanceChangeRequestController#approve` |
| POST | `/attendance/change-requests/{id}/reject` | `AttendanceChangeRequestController#reject` |
| POST | `/attendance/{id}/approve-justification` | `AttendanceController#approveJustification` |
| POST | `/attendance/{id}/change-requests` | `AttendanceChangeRequestController#create` |
| POST | `/attendance/{id}/justification-proof` | `AttendanceController#submitJustificationProof` |
| POST | `/attendance/{id}/justify` | `AttendanceController#justify` |
| POST | `/attendance/{id}/reject-justification` | `AttendanceController#rejectJustification` |
| POST | `/auth/forgot-password` | `AuthController#forgotPassword` |
| POST | `/auth/google` | `AuthController#loginWithGoogle` |
| POST | `/auth/google/register` | `AuthController#registerWithGoogle` |
| POST | `/auth/login` | `AuthController#login` |
| POST | `/auth/logout` | `AuthController#logout` |
| POST | `/auth/refresh` | `AuthController#refresh` |
| POST | `/auth/register` | `AuthController#register` |
| POST | `/auth/reset-password` | `AuthController#resetPassword` |
| POST | `/auth/verify-reset-code` | `AuthController#verifyResetCode` |
| POST | `/authorization-requests/{id}/approve` | `AuthorizationRequestController#approve` |
| POST | `/authorization-requests/{id}/reject` | `AuthorizationRequestController#reject` |
| POST | `/authorization-requests/{id}/withdraw` | `AuthorizationRequestController#withdraw` |
| POST | `/bank-transfer-receipts/{id}/approve` | `BankTransferReceiptController#approve` |
| POST | `/bank-transfer-receipts/{id}/reject` | `BankTransferReceiptController#reject` |
| POST | `/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS/changes` | `CollectionReminderSettingsController#proposeOrPublishChange` |
| POST | `/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS/validation` | `CollectionReminderSettingsController#validateChange` |
| POST | `/billing-configuration/collection-reminder-schedules/{group}/changes` | `CollectionReminderScheduleController#proposeGroupChange` |
| POST | `/billing-configuration/collection-reminder-schedules/{group}/exceptions/{targetId}/changes` | `CollectionReminderScheduleController#proposeExceptionChange` |
| POST | `/billing-configuration/collection-reminder-schedules/{group}/exceptions/{targetId}/removal` | `CollectionReminderScheduleController#proposeExceptionRemoval` |
| POST | `/billing-configuration/collection-reminder-schedules/{group}/exceptions/{targetId}/validation` | `CollectionReminderScheduleController#validateExceptionChange` |
| POST | `/billing-configuration/collection-reminder-schedules/{group}/validation` | `CollectionReminderScheduleController#validateGroupChange` |
| POST | `/billing-periods` | `BillingPeriodController#create` |
| POST | `/billing-runs/executions` | `BillingRunController#executeTodaysNightlyRun` |
| POST | `/chargebacks/{chargebackId}/reversal` | `PaymentReversalController#reverseChargeback` |
| POST | `/chargebacks/{chargebackId}/reversal/preview` | `PaymentReversalPreviewController#previewChargebackReversal` |
| POST | `/classrooms` | `ClassroomController#create` |
| POST | `/classrooms/{id}/deactivate` | `ClassroomController#deactivate` |
| PATCH | `/cohorts/{id}/entry-fee-discount` | `ProgramYearBillingTemplateController#updateEntryFeeDiscount` |
| POST | `/collection-reminders/impact` | `CollectionReminderImpactController#getImpact` |
| POST | `/course-enrollments/{id}/payment-plan` | `PaymentPlanSelectionController#selectForCourse` |
| POST | `/course-enrollments/{id}/student-discounts` | `StudentDiscountController#grantForCourseEnrollment` |
| POST | `/course-enrollments/{id}/student-discounts/preview` | `StudentDiscountController#previewForCourseEnrollment` |
| POST | `/course-enrollments/{id}/withdraw` | `CourseEnrollmentController#withdraw` |
| POST | `/course-section-enrollments/{id}/grade-items` | `GradeItemController#loadCourseEnrollmentGrade` |
| POST | `/course-section-enrollments/{id}/withdraw` | `CourseSectionEnrollmentController#withdraw` |
| POST | `/course-sections/{id}/activate` | `CourseSectionController#reactivate` |
| POST | `/course-sections/{id}/attendance` | `AttendanceController#loadCourseSectionAttendance` |
| POST | `/course-sections/{id}/class-sessions/{date}/cancel` | `AttendanceController#cancelCourseSectionClassSession` |
| POST | `/course-sections/{id}/deactivate` | `CourseSectionController#deactivate` |
| POST | `/course-sections/{id}/grade-items/publish` | `GradePublicationController#publishCourseSectionGradeItem` |
| POST | `/course-sections/{id}/instructors` | `CourseSectionController#assignInstructor` |
| POST | `/course-sections/{sectionId}/announcements` | `AnnouncementController#createForCourseSection` |
| POST | `/course-sections/{sectionId}/audience-preview` | `AnnouncementController#previewForCourseSection` |
| POST | `/course-subjects/{id}/activate` | `CourseSubjectController#reactivate` |
| POST | `/course-subjects/{id}/deactivate` | `CourseSubjectController#deactivate` |
| POST | `/course-subjects/{id}/sections` | `CourseSectionController#create` |
| POST | `/courses` | `CourseController#create` |
| POST | `/courses/{id}/activate` | `CourseController#reactivate` |
| POST | `/courses/{id}/deactivate` | `CourseController#deactivate` |
| POST | `/courses/{id}/payment-plan-options` | `PaymentPlanOptionController#createForCourse` |
| POST | `/courses/{id}/promotions` | `PromotionController#createForCourse` |
| POST | `/courses/{id}/subjects` | `CourseSubjectController#addModule` |
| POST | `/director-approval-requests` | `DirectorApprovalRequestController#request` |
| POST | `/director-approval-requests/{id}/approve` | `DirectorApprovalRequestController#approve` |
| POST | `/director-approval-requests/{id}/reject` | `DirectorApprovalRequestController#reject` |
| POST | `/discount-definitions` | `DiscountDefinitionController#createDiscountDefinition` |
| POST | `/discount-definitions/validation` | `DiscountDefinitionController#validateDiscountDefinition` |
| POST | `/discount-definitions/{id}/closure` | `DiscountDefinitionController#closeDiscountDefinition` |
| POST | `/elective-groups` | `ElectiveGroupController#create` |
| POST | `/enrollment-fee-exemptions/{id}/revoke` | `EnrollmentFeeController#revoke` |
| POST | `/evaluation-schemes` | `EvaluationSchemeController#create` |
| POST | `/exam-board-registrations/{id}/withdraw` | `ExamBoardRegistrationController#withdraw` |
| POST | `/exam-boards` | `ExamBoardController#create` |
| POST | `/exam-boards/{id}/cancel` | `ExamBoardChangeController#cancel` |
| POST | `/exam-boards/{id}/close` | `ExamBoardController#close` |
| POST | `/exam-boards/{id}/registrations` | `ExamBoardRegistrationController#register` |
| POST | `/exam-boards/{id}/reschedule` | `ExamBoardChangeController#reschedule` |
| POST | `/exam-boards/{id}/tribunal` | `ExamBoardController#assignTribunal` |
| POST | `/graduation-certificates` | `GraduationCertificateController#issue` |
| POST | `/graduation-requests/{id}/approve` | `GraduationRequestController#approve` |
| POST | `/graduation-requests/{id}/reject` | `GraduationRequestController#reject` |
| POST | `/graduation-requests/{id}/verify-debt` | `GraduationRequestController#verifyDebt` |
| POST | `/holidays` | `HolidayController#create` |
| POST | `/invoices/{invoiceId}/cancellations` | `InvoiceCancellationController#cancelOrRequestInvoiceCancellation` |
| POST | `/invoices/{invoiceId}/cancellations/preview` | `InvoiceCancellationController#previewInvoiceCancellation` |
| POST | `/late-payment-settings` | `LatePaymentSettingsController#create` |
| POST | `/me/announcements/{id}/read` | `MeAnnouncementController#markRead` |
| POST | `/me/annual-upfront-requests/{id}/discard` | `AnnualUpfrontRequestController#discardAnnualUpfrontRequest` |
| POST | `/me/attendance/{id}/justify` | `MeAcademicRecordController#justifyMyAbsence` |
| POST | `/me/avatar` | `PersonController#uploadAvatar` |
| POST | `/me/exam-board-registrations` | `MeExamBoardRegistrationController#register` |
| POST | `/me/exam-board-registrations/{id}/withdraw` | `MeExamBoardRegistrationController#withdraw` |
| POST | `/me/invoices/{id}/bank-transfer-receipts` | `MeInvoiceController#uploadReceipt` |
| POST | `/me/invoices/{id}/mp-preference` | `MeInvoiceController#createMpPreference` |
| POST | `/me/notifications/{id}/read` | `MeNotificationController#markAsRead` |
| POST | `/me/program-enrollment-applications` | `ProgramEnrollmentApplicationController#request` |
| POST | `/me/program-enrollment-applications/with-documents` | `ProgramEnrollmentApplicationWithDocumentsController#requestWithDocuments` |
| POST | `/me/program-enrollment-applications/{id}/documents` | `ProgramEnrollmentApplicationController#addDocument` |
| POST | `/me/program-enrollments/{id}/graduation-request` | `MeGraduationRequestController#request` |
| POST | `/me/refund-requests/preview` | `RefundPreviewController#previewOwnRefund` |
| POST | `/me/section-enrollments` | `MeEnrollmentController#enroll` |
| POST | `/me/section-enrollments/{id}/change-section` | `MeEnrollmentController#changeMySection` |
| POST | `/me/section-enrollments/{id}/withdraw` | `MeEnrollmentController#withdraw` |
| POST | `/me/section-evaluation-configs/{sectionId}/submit` | `MeSectionEvaluationConfigController#submitForApproval` |
| POST | `/monthly-invoice-runs/manual-issuances` | `ManualMonthlyInvoiceIssuanceController#issueMonthlyInvoicesManually` |
| POST | `/monthly-invoice-runs/simulations` | `MonthlyInvoiceSimulationController#simulateMonthlyInvoices` |
| POST | `/monthly-invoice-runs/{monthlyRunId}/draft-invoices/discards` | `MonthlyInvoiceRunController#discardDraftInvoices` |
| POST | `/monthly-invoice-runs/{monthlyRunId}/items/{itemId}/retries` | `MonthlyInvoiceRunController#retryItem` |
| POST | `/payment-config/qr` | `PaymentGatewayConfigController#uploadQr` |
| POST | `/payments/mp-webhook` | `MpWebhookController#webhook` |
| POST | `/payments/{paymentId}/approval-revocation` | `PaymentReversalController#revokeBankTransferApproval` |
| POST | `/payments/{paymentId}/approval-revocation/preview` | `PaymentReversalPreviewController#previewApprovalRevocation` |
| POST | `/payments/{paymentId}/chargebacks` | `PaymentReversalController#registerChargeback` |
| POST | `/payments/{paymentId}/chargebacks/preview` | `PaymentReversalPreviewController#previewChargebackRegistration` |
| POST | `/price-versions/{priceVersionId}/cancellation` | `PriceController#cancelScheduledPriceVersion` |
| POST | `/prices/{priceId}/versions` | `PriceController#publishPriceVersion` |
| POST | `/prices/{priceId}/versions/impact` | `PriceController#getPriceChangeImpact` |
| POST | `/prices/{priceId}/versions/validation` | `PriceController#validatePriceChange` |
| POST | `/program-enrollment-applications/{id}/approve` | `ProgramEnrollmentApplicationController#approve` |
| POST | `/program-enrollment-applications/{id}/reject` | `ProgramEnrollmentApplicationController#reject` |
| POST | `/program-enrollments/{id}/advance-billing-year` | `ProgramEnrollmentController#advanceAcademicYear` |
| POST | `/program-enrollments/{id}/enrollment-fee-exemptions` | `EnrollmentFeeController#grant` |
| POST | `/program-enrollments/{id}/payment-plan` | `PaymentPlanSelectionController#selectForProgram` |
| POST | `/program-enrollments/{id}/section-enrollments` | `SubjectSectionEnrollmentController#enroll` |
| POST | `/program-enrollments/{id}/student-discounts` | `StudentDiscountController#grantForProgramEnrollment` |
| POST | `/program-enrollments/{id}/student-discounts/preview` | `StudentDiscountController#previewForProgramEnrollment` |
| POST | `/program-enrollments/{id}/withdraw` | `ProgramEnrollmentController#withdraw` |
| POST | `/program-sections/{id}/activate` | `SubjectSectionController#reactivate` |
| POST | `/program-sections/{id}/attendance` | `AttendanceController#loadProgramSectionAttendance` |
| POST | `/program-sections/{id}/class-sessions/{date}/cancel` | `AttendanceController#cancelProgramSectionClassSession` |
| POST | `/program-sections/{id}/deactivate` | `SubjectSectionController#deactivate` |
| POST | `/program-sections/{id}/grade-items/publish` | `GradePublicationController#publishProgramSectionGradeItem` |
| POST | `/program-sections/{id}/instructors` | `SubjectSectionController#assignInstructor` |
| POST | `/program-subjects/{id}/activate` | `ProgramSubjectController#reactivate` |
| POST | `/program-subjects/{id}/corequisites` | `ProgramSubjectController#addCorequisite` |
| POST | `/program-subjects/{id}/deactivate` | `ProgramSubjectController#deactivate` |
| POST | `/program-subjects/{id}/prerequisites` | `ProgramSubjectController#addPrerequisite` |
| POST | `/programs` | `ProgramController#create` |
| POST | `/programs/{id}/activate` | `ProgramController#reactivate` |
| POST | `/programs/{id}/deactivate` | `ProgramController#deactivate` |
| POST | `/programs/{id}/elective-groups` | `ElectiveGroupController#assignToProgram` |
| POST | `/programs/{id}/payment-plan-options` | `PaymentPlanOptionController#createForProgram` |
| POST | `/programs/{id}/promotions` | `PromotionController#createForProgram` |
| POST | `/programs/{id}/subjects` | `ProgramSubjectController#addSubject` |
| POST | `/programs/{id}/year-billing-templates` | `ProgramYearBillingTemplateController#create` |
| POST | `/promotions/{id}/close` | `PromotionController#close` |
| POST | `/prospects` | `ProspectController#createProspect` |
| POST | `/prospects/by-staff` | `ProspectStaffController#createProspectByStaff` |
| POST | `/prospects/{id}/confirm` | `ProspectController#confirm` |
| POST | `/prospects/{id}/documents` | `ProspectController#addDocument` |
| POST | `/prospects/{id}/send-verification` | `ProspectController#sendVerification` |
| POST | `/refunds/{refundId}/completion` | `RefundController#completeRefund` |
| POST | `/refunds/{refundId}/failure` | `RefundController#failRefund` |
| POST | `/regular-certificates` | `RegularCertificateController#issue` |
| POST | `/section-enrollments/{id}/change-section` | `SubjectSectionEnrollmentController#changeSection` |
| POST | `/section-enrollments/{id}/grade-items` | `GradeItemController#loadProgramEnrollmentGrade` |
| POST | `/section-enrollments/{id}/withdraw` | `SubjectSectionEnrollmentController#withdraw` |
| POST | `/section-evaluation-configs/{sectionId}/approve` | `SectionEvaluationConfigController#approve` |
| POST | `/section-evaluation-configs/{sectionId}/reject` | `SectionEvaluationConfigController#reject` |
| POST | `/staff` | `StaffController#createStaff` |
| POST | `/staff/{id}/reactivate` | `StaffController#reactivate` |
| POST | `/staff/{id}/reset-password` | `StaffController#resetPassword` |
| POST | `/staff/{id}/withdraw` | `StaffController#withdraw` |
| POST | `/student-discounts/{id}/revocation` | `StudentDiscountController#revokeStudentDiscount` |
| POST | `/students/{id}/course-enrollments` | `CourseEnrollmentController#enroll` |
| POST | `/students/{id}/program-enrollments` | `ProgramEnrollmentController#enroll` |
| POST | `/students/{id}/reset-password` | `StudentController#resetPassword` |
| POST | `/students/{id}/withdraw` | `StudentController#withdraw` |
| POST | `/students/{studentId}/cash-payments/preview` | `StudentPaymentController#previewCashPayment` |
| POST | `/students/{studentId}/current-account/allocations` | `CurrentAccountAllocationController#allocateCredit` |
| POST | `/students/{studentId}/refund-requests/preview` | `RefundPreviewController#previewRefundOnBehalfOfStudent` |
| POST | `/subject-sections/{id}/modification-request` | `AcademicOfferingCycleController#requestSectionModification` |
| POST | `/subject-sections/{id}/modification-request/approve` | `AcademicOfferingCycleController#approveSectionModification` |
| POST | `/subject-sections/{id}/modification-request/reject` | `AcademicOfferingCycleController#rejectSectionModification` |
| POST | `/subject-sections/{sectionId}/announcements` | `AnnouncementController#createForSubjectSection` |
| POST | `/subject-sections/{sectionId}/audience-preview` | `AnnouncementController#previewForSubjectSection` |
| POST | `/subjects` | `SubjectController#create` |
| POST | `/subjects/{id}/activate` | `SubjectController#reactivate` |
| POST | `/subjects/{id}/approve` | `SubjectController#approve` |
| POST | `/subjects/{id}/deactivate` | `SubjectController#deactivate` |
| POST | `/subjects/{id}/reject` | `SubjectController#reject` |
| POST | `/subjects/{id}/sections` | `SubjectSectionController#create` |
| POST | `/subjects/{id}/submit-for-approval` | `SubjectController#submitForApproval` |
| POST | `/terms` | `TermController#create` |
| POST | `/terms/{id}/activate` | `TermController#reactivate` |
| POST | `/terms/{id}/deactivate` | `TermController#deactivate` |
| POST | `/terms/{id}/enrollment/clear-override` | `TermController#removeOverride` |
| POST | `/terms/{id}/enrollment/close` | `TermController#closeEnrollment` |
| POST | `/terms/{id}/enrollment/open` | `TermController#openEnrollment` |
| POST | `/year-billing-templates/{id}/cohorts` | `ProgramYearBillingTemplateController#createCohort` |
| PATCH | `/year-billing-templates/{id}/configuration` | `ProgramYearBillingTemplateController#updateConfiguration` |

