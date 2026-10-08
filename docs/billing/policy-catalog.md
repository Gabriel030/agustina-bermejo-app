# Catálogo de validaciones de facturación

Generado por `PolicyCatalogTest`; no se edita a mano. Para regenerarlo: `./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true` y commitear.

## Momento ANNUAL_UPFRONT_SETTINGS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AnnualUpfrontSettingsCoherencePolicy | INVALID_ANNUAL_UPFRONT_SETTINGS | D-155, D-156 |

## Momento BILLING_CONFIGURATION_CHANGE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ConfigurationChangeReasonRequiredPolicy | CHANGE_REASON_REQUIRED | D-095 |
| 2 | EffectiveDateNotRetroactivePolicy | RETROACTIVE_EFFECTIVE_DATE | D-059, D-111 |

## Momento BILLING_CONFIGURATION_PROPOSAL

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | SingleOpenProposalPerTopicPolicy | PROPOSAL_ALREADY_OPEN | D-116 |

## Momento CONFIGURATION_PROPOSAL_APPROVAL

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ProposalNotExpiredPolicy | PROPOSAL_EXPIRED | D-180 |

## Momento CONFIGURATION_VERSION_CANCELLATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CancellationReasonRequiredPolicy | CANCELLATION_REASON_REQUIRED | D-115 |
| 2 | ScheduledVersionCancellablePolicy | VERSION_ALREADY_EFFECTIVE | D-115 |

## Momento COURSE_INVOICE_PLANNING_ELIGIBILITY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AcademicallyActiveEnrollmentPolicy | NOT_ACTIVE | D-050 |
| 2 | NotSuspendedOrWithdrawnForNonPaymentPolicy | SUSPENDED_OR_WITHDRAWN | D-066, D-053 |
| 3 | PaymentPlanChosenPolicy | NO_PAYMENT_PLAN | D-050 |

## Momento COURSE_MONTHLY_INSTALLMENT_PLANNING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | MonthlyInstallmentNotAlreadyIssuedPolicy | ALREADY_ISSUED | D-009, D-039 |
| 2 | CourseInstallmentWithinTermPolicy | OUTSIDE_TERM | D-039 |
| 3 | InstallmentCapPolicy | INSTALLMENT_CAP_REACHED | D-034, D-039 |

## Momento ENROLLMENT_FEE_PLANNING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | BillingWhilePausedPolicy | PAUSED_NOT_BILLED | D-050 |
| 2 | FinalExamsOnlyExemptionPolicy | ONLY_FINAL_EXAMS, NOT_REQUIRED_ONLY_FINAL_EXAMS | D-016, D-037, D-014 |
| 3 | EnrollmentFeeSlotNotAlreadyIssuedPolicy | ALREADY_ISSUED, FEE_CANCELLED | D-009 |

## Momento ENROLLMENT_FEE_REQUIREMENT

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | EnrollmentFeeSettledPolicy | ENROLLMENT_FEE_NOT_ISSUED, ENROLLMENT_FEE_NOT_PAID_FOR_TERM | D-166, D-010 |

## Momento ENROLLMENT_FEE_REQUIREMENT_APPLICABILITY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CertificateEnrollmentFeeExemptionPolicy | NOT_REQUIRED_FOR_INACTIVE_OR_COMPLETED | D-015 |
| 2 | EnrollmentFeeBlocksScopePolicy | NOT_REQUIRED_SCOPE_NOT_BLOCKED | D-013 |
| 3 | FinalExamsOnlyExemptionPolicy | ONLY_FINAL_EXAMS, NOT_REQUIRED_ONLY_FINAL_EXAMS | D-016, D-037, D-014 |
| 4 | EnrollmentFeeSlotNotBeforeEntryPolicy | NOT_REQUIRED_BEFORE_ENTRY | D-020 |
| 5 | EnrollmentFeeExemptionGrantedPolicy | NOT_REQUIRED_EXEMPTION_GRANTED | D-027 |

## Momento INVOICE_DISCOUNT_SELECTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | DiscountMatchesInvoiceTypePolicy | DISCOUNT_NOT_APPLICABLE_TO_INVOICE_TYPE | D-129 |
| 2 | BestDiscountPerInvoiceTypePolicy | DISCOUNT_OUTBID_BY_A_BETTER_ONE | D-073, D-074, D-129 |

## Momento INVOICE_ISSUING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | InvoiceIssuingReasonMatchesFeeTypePolicy | FEE_TYPE_NOT_ALLOWED_FOR_REASON | D-050 |
| 2 | DueDateNotBeforeIssueDatePolicy | DUE_DATE_BEFORE_ISSUE_DATE | D-057 |
| 3 | EnrollmentFeeSlotRequiredPolicy | ENROLLMENT_FEE_SLOT_REQUIRED | D-002, D-009 |
| 4 | ChargeRecipientMatchesEnrollmentPolicy | ENROLLMENT_RECIPIENT_MISMATCH, ENROLLMENT_REQUIRED | D-097, D-009 |
| 5 | OfferingCurrencyPolicy | CURRENCY_MISMATCH | D-097 |
| 6 | ChargeKeyNotAlreadyUsedPolicy | ALREADY_ISSUED | D-009, D-021 |
| 7 | EnrollmentFeeSlotWithinTemplateCountPolicy | ENROLLMENT_FEE_SLOT_NOT_CONFIGURED | D-009, D-036 |

## Momento ISSUANCE_SETTINGS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | IssuanceSettingsCoherencePolicy | INVALID_ISSUANCE_SETTINGS | D-053, D-160 |

## Momento LATE_PAYMENT_SETTINGS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | GracePeriodNotNegativePolicy | NEGATIVE_GRACE_PERIOD | D-147 |
| 2 | SuspensionDaysNotNegativePolicy | NEGATIVE_SUSPENSION_DAYS | D-147 |
| 3 | GracePeriodWithinSuspensionDaysPolicy | GRACE_PERIOD_EXCEEDS_SUSPENSION_DAYS | D-147 |
| 4 | SuspensionInvoiceCountAtLeastOnePolicy | SUSPENSION_INVOICE_COUNT_BELOW_ONE | D-147 |
| 5 | EffectiveDateNotRetroactivePolicy | RETROACTIVE_EFFECTIVE_DATE | D-059, D-111 |

## Momento LATE_PAYMENT_TERMS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | LateFeeSettingsCoherencePolicy | INVALID_LATE_PAYMENT_SETTINGS | D-061, D-147, D-149 |

## Momento LEGACY_INVOICE_ISSUING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ReasonFeeTypePolicy | FEE_TYPE_NOT_ALLOWED_FOR_REASON | D-050 |
| 2 | DueDateNotBeforeIssueDatePolicy | DUE_DATE_BEFORE_ISSUE_DATE | D-057 |
| 3 | EnrollmentFeeSlotRequiredPolicy | ENROLLMENT_FEE_SLOT_REQUIRED | D-002, D-009 |
| 4 | OfferingCurrencyPolicy | CURRENCY_MISMATCH | D-097 |
| 5 | NotAlreadyIssuedPolicy | ALREADY_ISSUED | D-009 |
| 6 | EnrollmentFeeSlotWithinTemplateCountPolicy | ENROLLMENT_FEE_SLOT_NOT_CONFIGURED | D-009, D-036 |

## Momento MONTHLY_INSTALLMENT_PLANNING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | NotBeforeEntryMonthPolicy | BEFORE_ENTRY | D-049 |
| 2 | MonthlyCadencePolicy | PLAN_NOT_MONTHLY | D-040 |
| 3 | InstallmentWithinLectiveYearRangePolicy | OUTSIDE_TERM, YEAR_NOT_CONFIGURED | D-001, D-006 |
| 4 | BillingWhilePausedPolicy | PAUSED_NOT_BILLED | D-050 |
| 5 | InstallmentCapPolicy | INSTALLMENT_CAP_REACHED | D-034, D-039 |
| 6 | FinalExamsOnlyExemptionPolicy | ONLY_FINAL_EXAMS, NOT_REQUIRED_ONLY_FINAL_EXAMS | D-016, D-037, D-014 |
| 7 | MonthlyInstallmentNotAlreadyIssuedPolicy | ALREADY_ISSUED | D-009, D-039 |

## Momento PAYMENT_PLAN_SELECTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AnnualUpfrontAvailableForProgramPolicy | ANNUAL_UPFRONT_NOT_AVAILABLE | D-131, D-191 |

## Momento PROGRAM_INVOICE_PLANNING_ELIGIBILITY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AcademicallyActiveEnrollmentPolicy | NOT_ACTIVE | D-050 |
| 2 | NotSuspendedOrWithdrawnForNonPaymentPolicy | SUSPENDED_OR_WITHDRAWN | D-066, D-053 |
| 3 | StudyPlanNotCompletedPolicy | PLAN_COMPLETED | D-038 |
| 4 | PaymentPlanChosenPolicy | NO_PAYMENT_PLAN | D-050 |

## Momento YEAR_BILLING_CONFIGURATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | TermHasDatesPolicy | TERM_WITHOUT_DATES | D-002 |
| 2 | SecondEnrollmentFeeAfterFirstPolicy | SECOND_ENROLLMENT_FEE_NOT_AFTER_FIRST | D-002 |
| 3 | FirstEnrollmentFeeNotAfterTermStartPolicy | FIRST_ENROLLMENT_FEE_AFTER_TERM_START | D-002 |
| 4 | SecondEnrollmentFeeWithinTermPolicy | SECOND_ENROLLMENT_FEE_OUTSIDE_TERM | D-002 |
| 5 | InstallmentCountWithinTermSpanPolicy | INSTALLMENT_COUNT_EXCEEDS_TERM_SPAN | D-034 |

## Momento YEAR_BILLING_CONFIGURATION_FREEZE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | EnrollmentFeeCountFrozenPolicy | ENROLLMENT_FEE_COUNT_FROZEN | D-009, D-036 |
| 2 | EnrollmentFeeMonthFrozenPolicy | ENROLLMENT_FEE_MONTH_FROZEN | D-036 |
| 3 | EnrollmentFeeLeadMonthsFrozenPolicy | ENROLLMENT_FEE_LEAD_MONTHS_FROZEN | D-201 |
| 4 | IssuedInstallmentsWithinTermRangePolicy | INSTALLMENTS_OUTSIDE_NEW_TERM_RANGE | D-036 |

## Máquinas de estado

### AuthorizationRequestStateMachine

| Desde \ Evento | OPEN | APPROVE | REJECT | WITHDRAW | EXPIRE |
|---|---|---|---|---|---|
| (sin estado) | PENDING | — | — | — | — |
| PENDING | — | APPROVED | REJECTED | WITHDRAWN | EXPIRED |
| APPROVED | — | — | — | — | — |
| REJECTED | — | — | — | — | — |
| WITHDRAWN | — | — | — | — | — |
| EXPIRED | — | — | — | — | — |

Transiciones válidas: 5 de 30. Pares inválidos: (sin estado) × APPROVE; (sin estado) × REJECT; (sin estado) × WITHDRAW; (sin estado) × EXPIRE; PENDING × OPEN; APPROVED × OPEN; APPROVED × APPROVE; APPROVED × REJECT; APPROVED × WITHDRAW; APPROVED × EXPIRE; REJECTED × OPEN; REJECTED × APPROVE; REJECTED × REJECT; REJECTED × WITHDRAW; REJECTED × EXPIRE; WITHDRAWN × OPEN; WITHDRAWN × APPROVE; WITHDRAWN × REJECT; WITHDRAWN × WITHDRAW; WITHDRAWN × EXPIRE; EXPIRED × OPEN; EXPIRED × APPROVE; EXPIRED × REJECT; EXPIRED × WITHDRAW; EXPIRED × EXPIRE.

### ConfigurationVersionStateMachine

| Desde \ Evento | PROPOSE | PUBLISH_FOR_FUTURE | PUBLISH_FOR_TODAY | APPROVE_FOR_FUTURE | APPROVE_FOR_TODAY | REJECT | WITHDRAW | EXPIRE | REACH_START | CANCEL | SUPERSEDE |
|---|---|---|---|---|---|---|---|---|---|---|---|
| (sin estado) | PROPOSED | SCHEDULED | EFFECTIVE | — | — | — | — | — | — | — | — |
| PROPOSED | — | — | — | SCHEDULED | EFFECTIVE | REJECTED | WITHDRAWN | EXPIRED | — | — | — |
| SCHEDULED | — | — | — | — | — | — | — | — | EFFECTIVE | CANCELLED | — |
| EFFECTIVE | — | — | — | — | — | — | — | — | — | — | SUPERSEDED |
| SUPERSEDED | — | — | — | — | — | — | — | — | — | — | — |
| REJECTED | — | — | — | — | — | — | — | — | — | — | — |
| WITHDRAWN | — | — | — | — | — | — | — | — | — | — | — |
| EXPIRED | — | — | — | — | — | — | — | — | — | — | — |
| CANCELLED | — | — | — | — | — | — | — | — | — | — | — |

Transiciones válidas: 11 de 99. Pares inválidos: (sin estado) × APPROVE_FOR_FUTURE; (sin estado) × APPROVE_FOR_TODAY; (sin estado) × REJECT; (sin estado) × WITHDRAW; (sin estado) × EXPIRE; (sin estado) × REACH_START; (sin estado) × CANCEL; (sin estado) × SUPERSEDE; PROPOSED × PROPOSE; PROPOSED × PUBLISH_FOR_FUTURE; PROPOSED × PUBLISH_FOR_TODAY; PROPOSED × REACH_START; PROPOSED × CANCEL; PROPOSED × SUPERSEDE; SCHEDULED × PROPOSE; SCHEDULED × PUBLISH_FOR_FUTURE; SCHEDULED × PUBLISH_FOR_TODAY; SCHEDULED × APPROVE_FOR_FUTURE; SCHEDULED × APPROVE_FOR_TODAY; SCHEDULED × REJECT; SCHEDULED × WITHDRAW; SCHEDULED × EXPIRE; SCHEDULED × SUPERSEDE; EFFECTIVE × PROPOSE; EFFECTIVE × PUBLISH_FOR_FUTURE; EFFECTIVE × PUBLISH_FOR_TODAY; EFFECTIVE × APPROVE_FOR_FUTURE; EFFECTIVE × APPROVE_FOR_TODAY; EFFECTIVE × REJECT; EFFECTIVE × WITHDRAW; EFFECTIVE × EXPIRE; EFFECTIVE × REACH_START; EFFECTIVE × CANCEL; SUPERSEDED × PROPOSE; SUPERSEDED × PUBLISH_FOR_FUTURE; SUPERSEDED × PUBLISH_FOR_TODAY; SUPERSEDED × APPROVE_FOR_FUTURE; SUPERSEDED × APPROVE_FOR_TODAY; SUPERSEDED × REJECT; SUPERSEDED × WITHDRAW; SUPERSEDED × EXPIRE; SUPERSEDED × REACH_START; SUPERSEDED × CANCEL; SUPERSEDED × SUPERSEDE; REJECTED × PROPOSE; REJECTED × PUBLISH_FOR_FUTURE; REJECTED × PUBLISH_FOR_TODAY; REJECTED × APPROVE_FOR_FUTURE; REJECTED × APPROVE_FOR_TODAY; REJECTED × REJECT; REJECTED × WITHDRAW; REJECTED × EXPIRE; REJECTED × REACH_START; REJECTED × CANCEL; REJECTED × SUPERSEDE; WITHDRAWN × PROPOSE; WITHDRAWN × PUBLISH_FOR_FUTURE; WITHDRAWN × PUBLISH_FOR_TODAY; WITHDRAWN × APPROVE_FOR_FUTURE; WITHDRAWN × APPROVE_FOR_TODAY; WITHDRAWN × REJECT; WITHDRAWN × WITHDRAW; WITHDRAWN × EXPIRE; WITHDRAWN × REACH_START; WITHDRAWN × CANCEL; WITHDRAWN × SUPERSEDE; EXPIRED × PROPOSE; EXPIRED × PUBLISH_FOR_FUTURE; EXPIRED × PUBLISH_FOR_TODAY; EXPIRED × APPROVE_FOR_FUTURE; EXPIRED × APPROVE_FOR_TODAY; EXPIRED × REJECT; EXPIRED × WITHDRAW; EXPIRED × EXPIRE; EXPIRED × REACH_START; EXPIRED × CANCEL; EXPIRED × SUPERSEDE; CANCELLED × PROPOSE; CANCELLED × PUBLISH_FOR_FUTURE; CANCELLED × PUBLISH_FOR_TODAY; CANCELLED × APPROVE_FOR_FUTURE; CANCELLED × APPROVE_FOR_TODAY; CANCELLED × REJECT; CANCELLED × WITHDRAW; CANCELLED × EXPIRE; CANCELLED × REACH_START; CANCELLED × CANCEL; CANCELLED × SUPERSEDE.

### InvoiceDocumentStatusStateMachine

| Desde \ Evento | DRAFT_CREATED | DRAFT_CONTENT_CHANGED | DRAFT_SUBMITTED_FOR_PAYMENT | INVOICE_ISSUED | DRAFT_DISCARDED |
|---|---|---|---|---|---|
| (sin estado) | DRAFT | — | — | — | — |
| DRAFT | — | DRAFT | AWAITING_PAYMENT | ISSUED | DISCARDED |
| AWAITING_PAYMENT | — | — | — | ISSUED | DISCARDED |
| ISSUED | — | — | — | — | — |
| DISCARDED | — | — | — | — | — |

Transiciones válidas: 7 de 25. Pares inválidos: (sin estado) × DRAFT_CONTENT_CHANGED; (sin estado) × DRAFT_SUBMITTED_FOR_PAYMENT; (sin estado) × INVOICE_ISSUED; (sin estado) × DRAFT_DISCARDED; DRAFT × DRAFT_CREATED; AWAITING_PAYMENT × DRAFT_CREATED; AWAITING_PAYMENT × DRAFT_CONTENT_CHANGED; AWAITING_PAYMENT × DRAFT_SUBMITTED_FOR_PAYMENT; ISSUED × DRAFT_CREATED; ISSUED × DRAFT_CONTENT_CHANGED; ISSUED × DRAFT_SUBMITTED_FOR_PAYMENT; ISSUED × INVOICE_ISSUED; ISSUED × DRAFT_DISCARDED; DISCARDED × DRAFT_CREATED; DISCARDED × DRAFT_CONTENT_CHANGED; DISCARDED × DRAFT_SUBMITTED_FOR_PAYMENT; DISCARDED × INVOICE_ISSUED; DISCARDED × DRAFT_DISCARDED.

### InvoicePaymentStatusStateMachine

| Desde \ Evento | ISSUED_WITH_AMOUNT_DUE | ISSUED_WITH_ZERO_TOTAL | BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME | BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED | BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED | FULL_PAYMENT_ALLOCATED | PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED | PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED | DUE_DATE_PASSED | CANCELLATION_CREDIT_NOTE_APPLIED | DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE | PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED | CREDIT_NOTE_APPLIED_TO_PAID_INVOICE |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| (sin estado) | PENDING | PAID | — | — | — | — | — | — | — | — | — | — | — |
| PENDING | — | — | PAYMENT_UNDER_REVIEW | — | — | PAID | — | — | OVERDUE | CANCELLED | PAID | PENDING | — |
| PAYMENT_UNDER_REVIEW | — | — | — | PENDING | OVERDUE | PAID | — | — | — | — | — | — | — |
| PAID | — | — | — | — | — | — | PENDING | OVERDUE | — | — | — | — | PAID |
| OVERDUE | — | — | — | — | — | PAID | — | — | — | CANCELLED | PAID | OVERDUE | — |
| CANCELLED | — | — | — | — | — | — | — | — | — | — | — | — | — |

Transiciones válidas: 18 de 78. Pares inválidos: (sin estado) × BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME; (sin estado) × BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED; (sin estado) × BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED; (sin estado) × FULL_PAYMENT_ALLOCATED; (sin estado) × PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED; (sin estado) × PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED; (sin estado) × DUE_DATE_PASSED; (sin estado) × CANCELLATION_CREDIT_NOTE_APPLIED; (sin estado) × DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE; (sin estado) × PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED; (sin estado) × CREDIT_NOTE_APPLIED_TO_PAID_INVOICE; PENDING × ISSUED_WITH_AMOUNT_DUE; PENDING × ISSUED_WITH_ZERO_TOTAL; PENDING × BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED; PENDING × BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED; PENDING × PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED; PENDING × PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED; PENDING × CREDIT_NOTE_APPLIED_TO_PAID_INVOICE; PAYMENT_UNDER_REVIEW × ISSUED_WITH_AMOUNT_DUE; PAYMENT_UNDER_REVIEW × ISSUED_WITH_ZERO_TOTAL; PAYMENT_UNDER_REVIEW × BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME; PAYMENT_UNDER_REVIEW × PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED; PAYMENT_UNDER_REVIEW × PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED; PAYMENT_UNDER_REVIEW × DUE_DATE_PASSED; PAYMENT_UNDER_REVIEW × CANCELLATION_CREDIT_NOTE_APPLIED; PAYMENT_UNDER_REVIEW × DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE; PAYMENT_UNDER_REVIEW × PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED; PAYMENT_UNDER_REVIEW × CREDIT_NOTE_APPLIED_TO_PAID_INVOICE; PAID × ISSUED_WITH_AMOUNT_DUE; PAID × ISSUED_WITH_ZERO_TOTAL; PAID × BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME; PAID × BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED; PAID × BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED; PAID × FULL_PAYMENT_ALLOCATED; PAID × DUE_DATE_PASSED; PAID × CANCELLATION_CREDIT_NOTE_APPLIED; PAID × DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE; PAID × PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED; OVERDUE × ISSUED_WITH_AMOUNT_DUE; OVERDUE × ISSUED_WITH_ZERO_TOTAL; OVERDUE × BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME; OVERDUE × BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED; OVERDUE × BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED; OVERDUE × PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED; OVERDUE × PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED; OVERDUE × DUE_DATE_PASSED; OVERDUE × CREDIT_NOTE_APPLIED_TO_PAID_INVOICE; CANCELLED × ISSUED_WITH_AMOUNT_DUE; CANCELLED × ISSUED_WITH_ZERO_TOTAL; CANCELLED × BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME; CANCELLED × BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED; CANCELLED × BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED; CANCELLED × FULL_PAYMENT_ALLOCATED; CANCELLED × PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED; CANCELLED × PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED; CANCELLED × DUE_DATE_PASSED; CANCELLED × CANCELLATION_CREDIT_NOTE_APPLIED; CANCELLED × DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE; CANCELLED × PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED; CANCELLED × CREDIT_NOTE_APPLIED_TO_PAID_INVOICE.

## Operación CANCEL_CONFIGURATION_VERSION

- Validaciones (momentos): CONFIGURATION_VERSION_CANCELLATION
- Pre-procesamiento (en orden): —
- Eventos que publica: ConfigurationVersionCancelledEvent
- Post-procesamiento de ConfigurationVersionCancelledEvent: BillingOperationNotificationListener#notifyConfigurationVersionCancelled

## Operación CHANGE_BILLING_CONFIGURATION

- Validaciones (momentos): BILLING_CONFIGURATION_CHANGE, BILLING_CONFIGURATION_PROPOSAL, CONFIGURATION_PROPOSAL_APPROVAL, YEAR_BILLING_CONFIGURATION, YEAR_BILLING_CONFIGURATION_FREEZE, LATE_PAYMENT_TERMS, ISSUANCE_SETTINGS, ANNUAL_UPFRONT_SETTINGS
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: ConfigurationChangeDecidedEvent
- Post-procesamiento de ConfigurationChangeDecidedEvent: BillingOperationNotificationListener#notifyConfigurationChangeDecided

## Operación CREATE_LATE_PAYMENT_SETTINGS

- Validaciones (momentos): LATE_PAYMENT_SETTINGS
- Pre-procesamiento (en orden): —
- Eventos que publica: LatePaymentSettingsCreatedEvent
- Post-procesamiento de LatePaymentSettingsCreatedEvent: BillingOperationNotificationListener#notifyLatePaymentSettingsCreated

## Operación ISSUE_INVOICE

- Validaciones (momentos): INVOICE_ISSUING
- Pre-procesamiento (en orden): InvoiceDiscountEnrichmentHandler
- Eventos que publica: InvoiceIssuedDomainEvent, InvoicePaidDomainEvent
- Post-procesamiento de InvoiceIssuedDomainEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: —
