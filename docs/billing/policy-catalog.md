# Catálogo de validaciones de facturación

Generado por `PolicyCatalogTest`; no se edita a mano. Para regenerarlo: `./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true` y commitear.

## Momento ACCOUNT_INCONSISTENCY_SCOPE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | InconsistencyStaysLocalPolicy | GLOBAL_ACCOUNT_INCONSISTENCY | D-356, D-489 |

## Momento ANNUAL_UPFRONT_IDEMPOTENCY_KEY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AnnualUpfrontIdempotencyKeyPolicy | IDEMPOTENCY_KEY_INVALID | D-136 |

## Momento ANNUAL_UPFRONT_REQUEST

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AnnualUpfrontEnrollmentActivePolicy | ENROLLMENT_NOT_ACTIVE | D-066, D-053 |
| 2 | AnnualUpfrontRequestWindowPolicy | ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED | D-156 |
| 3 | ScholarshipHolderCannotChooseAnnualUpfrontPolicy | ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP | D-130, D-302, D-388 |
| 4 | AnnualUpfrontBlockedByOverdueDebtPolicy | OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT | D-152, D-292 |
| 5 | OnlyOneAnnualUpfrontRequestPolicy | ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED | D-131 |
| 6 | AnnualUpfrontMonthsStillAvailablePolicy | NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT | D-131 |
| 7 | AnnualUpfrontLineCountSupportedPolicy | ANNUAL_UPFRONT_LINE_COUNT_NOT_SUPPORTED | D-131 |
| 8 | AnnualUpfrontPriceAvailablePolicy | NO_EFFECTIVE_PRICE_FOR_ANNUAL_UPFRONT | D-439 |

## Momento ANNUAL_UPFRONT_SETTINGS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AnnualUpfrontSettingsCoherencePolicy | INVALID_ANNUAL_UPFRONT_SETTINGS | D-155, D-156 |

## Momento ANNUAL_UPFRONT_TERMS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AnnualUpfrontTermsAcceptedPolicy | ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED | D-236 |

## Momento BANK_TRANSFER_RECEIPT_DETAILS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | BankTransferReceiptDetailsRequiredPolicy | BANK_TRANSFER_AMOUNT_INVALID, BANK_TRANSFER_DATE_REQUIRED, BANK_TRANSFER_DATE_IN_FUTURE, BANK_TRANSFER_OPERATION_NUMBER_REQUIRED, BANK_TRANSFER_OPERATION_NUMBER_TOO_LONG, BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED, BANK_TRANSFER_ORIGIN_ACCOUNT_TOO_LONG, BANK_TRANSFER_HOLDER_NAME_REQUIRED, BANK_TRANSFER_HOLDER_NAME_TOO_LONG, BANK_TRANSFER_BANK_NAME_TOO_LONG, BANK_TRANSFER_RECEIPT_FILE_REQUIRED, BANK_TRANSFER_INVOICES_REQUIRED | D-267, D-136, D-097 |

## Momento BANK_TRANSFER_RECEIPT_FILE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | BankTransferReceiptFilePolicy | BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED, BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH | D-267 |

## Momento BANK_TRANSFER_RECEIPT_IDEMPOTENCY_KEY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | IdempotencyKeyFormatPolicy | IDEMPOTENCY_KEY_INVALID | D-136 |

## Momento BANK_TRANSFER_RECEIPT_PROTECTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ReceiptUploadedByDueDatePolicy | INVOICE_ALREADY_OVERDUE, RECEIPT_UPLOADED_AFTER_DUE_DATE, TRANSFER_AFTER_DUE_DATE | D-154, D-186, D-153 |

## Momento BANK_TRANSFER_RECEIPT_REGISTRATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | SelectedInvoicesBelongToAccountPolicy | INVOICE_CURRENCY_MISMATCH, INVOICE_OF_ANOTHER_ACCOUNT, INVOICE_NOT_PAYABLE_BY_RECEIPT | D-097, D-153 |
| 2 | LegacyReceiptNotPendingOnSelectedInvoicesPolicy | LEGACY_RECEIPT_PENDING_ON_INVOICE | D-260 |
| 3 | DuplicateBankTransferPolicy | POSSIBLE_DUPLICATE_BANK_TRANSFER | D-269 |
| 4 | ReceiptCoversOldestInvoicesFirstPolicy | INVOICE_ALREADY_UNDER_REVIEW, RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST | D-153, D-260, D-329 |

## Momento BILLING_CONFIGURATION_CHANGE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ConfigurationChangeReasonRequiredPolicy | CHANGE_REASON_REQUIRED | D-095 |
| 2 | EffectiveDateNotRetroactivePolicy | RETROACTIVE_EFFECTIVE_DATE | D-059, D-111 |

## Momento BILLING_CONFIGURATION_PROPOSAL

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | SingleOpenProposalPerTopicPolicy | PROPOSAL_ALREADY_OPEN | D-116 |

## Momento CASH_PAYMENT_DATING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CashPaymentDateNotInFuturePolicy | PAYMENT_DATE_IN_FUTURE | D-169, D-266 |
| 2 | CashPaymentDateWithinAdministrativeWindowPolicy | CASH_PAYMENT_DATE_TOO_OLD | D-266, D-270 |
| 3 | CashPaymentBackdateReasonPolicy | CASH_PAYMENT_BACKDATE_REASON_REQUIRED | D-266, D-270 |

## Momento CASH_PAYMENT_PREVIEW

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CashPaymentPreviewIsCurrentPolicy | CASH_PAYMENT_PREVIEW_OUTDATED | D-319, D-207 |

## Momento CASH_PAYMENT_PREVIEW_REQUEST

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CashPaymentAmountPolicy | CASH_PAYMENT_AMOUNT_INVALID | D-136, D-097, D-098 |

## Momento CHARGEBACK_REGISTRATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ChargebackReferenceRequiredPolicy | CHARGEBACK_REFERENCE_INVALID | D-206 |
| 2 | ChargebackDateWithinPaymentLifePolicy | CHARGEBACK_DATE_INVALID | D-206 |
| 3 | PaymentMoneyNotRefundedPolicy | PAYMENT_ALREADY_REFUNDED | D-262 |
| 4 | PaymentWithoutPendingRefundPolicy | PAYMENT_HAS_PENDING_REFUND | D-262, D-265 |
| 5 | ChargebackIsForTheFullAmountPolicy | CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT | D-262 |

## Momento COHORT_PROMOTION_ACQUISITION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | OneCohortPromotionPerEnrollmentPolicy | COHORT_PROMOTION_ALREADY_ACQUIRED | D-386 |

## Momento COLLECTION_REMINDER_EFFECTIVE_FROM

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CollectionReminderEffectiveFromNextDayPolicy | EFFECTIVE_FROM_BEFORE_NEXT_DAY | D-350 |

## Momento COLLECTION_REMINDER_ELIGIBILITY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ReminderEligibleInvoicePolicy | INVOICE_NOT_REMINDABLE | D-354, D-242, D-052, D-149, D-131 |
| 2 | ReceiptUnderReviewSuppressesReminderPolicy | RECEIPT_UNDER_REVIEW | D-346, D-186 |
| 3 | PendingCancellationSuppressesReminderPolicy | PENDING_CANCELLATION_REQUEST | D-247, D-354 |

## Momento COLLECTION_REMINDER_PRIOR_DEBT

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | NewInstallmentWaitsForPriorDebtPolicy | NEW_INSTALLMENT_WAITS_FOR_PRIOR_DEBT | D-347 |

## Momento COLLECTION_REMINDER_SCHEDULE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ReminderScheduleSizePolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-368 |
| 2 | ReminderNoticeTimingPolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-368 |
| 3 | ReminderNoticeChannelsPolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-368, D-344 |
| 4 | ReminderIssuanceNoticePolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-368, D-344 |
| 5 | ReminderRepetitionPolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-368 |
| 6 | ReminderNoticeUniquenessPolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-368 |
| 7 | ReminderScheduleFollowsGroupPolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-368 |
| 8 | ReminderSendingOverridesCoherencePolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-369, D-373 |

## Momento COLLECTION_REMINDER_SCHEDULE_CHANGE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ReminderChangeDoesNotFollowGroupPolicy | INVALID_COLLECTION_REMINDER_SCHEDULE | D-368 |

## Momento COLLECTION_REMINDER_SETTINGS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CollectionReminderSettingsCoherencePolicy | INVALID_COLLECTION_REMINDER_SETTINGS | D-351, D-373 |

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

## Momento CREDIT_NOTE_ISSUING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CreditNoteJustificationRequiredPolicy | CREDIT_NOTE_JUSTIFICATION_REQUIRED | D-165 |
| 2 | CreditNoteReasonAllowedPolicy | INVALID_CREDIT_NOTE_REASON | D-165, D-238 |
| 3 | CreditNoteReferencesIssuedInvoicePolicy | CREDIT_NOTE_INVOICE_REFERENCE_REQUIRED | D-244 |
| 4 | CreditNoteInvoiceNotUnderReviewPolicy | PENDING_BANK_TRANSFER_ON_INVOICE | D-153, D-165 |
| 5 | CreditNoteLinesBelongToInvoicePolicy | CREDIT_NOTE_LINES_INVALID | D-165 |
| 6 | CreditNoteDestinationUnchangedPolicy | CREDIT_NOTE_DISTRIBUTION_MISMATCH | D-165 |
| 7 | CreditNoteRefundWithinPaidAmountPolicy | CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT | D-165 |
| 8 | CreditNoteWithinOutstandingAmountPolicy | CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT | D-165 |
| 9 | CreditNoteLinesWithinCreditableAmountPolicy | CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT | D-165 |
| 10 | CreditNoteFullDebtRequiresCancellationPolicy | CREDIT_NOTE_FULL_DEBT_REQUIRES_CANCELLATION | D-167, D-202, D-127 |

## Momento DEBIT_NOTE_ISSUING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | LateFeeAppliesToInvoiceTypePolicy | LATE_FEE_NOT_APPLICABLE | D-289, D-242, D-149, D-155 |
| 2 | OriginNotUnderPaymentReviewPolicy | ORIGIN_UNDER_PAYMENT_REVIEW | D-153, D-154 |
| 3 | LateFeeChargingEnabledPolicy | LATE_FEE_CHARGING_DISABLED | D-210, D-294 |
| 4 | LateFeeNotFrozenPolicy | LATE_FEE_FROZEN | D-148, D-293 |
| 5 | DebitNoteSameRecipientAsOriginPolicy | DEBIT_NOTE_RECIPIENT_MISMATCH | D-244, D-097 |

## Momento DISCOUNT_DEFINITION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | DiscountDefinitionCoherencePolicy | INVALID_DISCOUNT_DEFINITION | D-075, D-129, D-171, D-240 |
| 2 | DiscountAvailabilityPeriodPolicy | INVALID_DISCOUNT_DEFINITION | D-283 |
| 3 | DiscountScopeMatchesTypePolicy | INVALID_DISCOUNT_DEFINITION | D-240 |
| 4 | CohortPromotionTermsPolicy | INVALID_DISCOUNT_DEFINITION | D-433, D-432, D-326 |
| 5 | ScholarshipOnlyForMonthlyInstallmentsPolicy | SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS | D-157 |
| 6 | DiscountTypeAvailableInCatalogPolicy | DISCOUNT_TYPE_NOT_IN_CATALOG | D-287, D-240 |
| 7 | InstallmentCountOnlyForMonthlyInstallmentsPolicy | INSTALLMENT_COUNT_ONLY_FOR_MONTHLY_INSTALLMENTS | D-075 |
| 8 | DiscountCurrencyMatchesOfferingPolicy | CURRENCY_MISMATCH | D-097 |

## Momento DISCOUNT_DEFINITION_CLOSURE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | DiscountClosureReasonRequiredPolicy | DISCOUNT_CLOSURE_REASON_REQUIRED | D-217, D-036 |

## Momento EMAIL_DELIVERY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PreDueEmailOptOutPolicy | OPTED_OUT | D-340, D-372 |
| 2 | VerifiedEmailRequiredPolicy | NO_VERIFIED_EMAIL | D-354 |

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

## Momento IDEMPOTENCY_KEY_REUSE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | IdempotencyKeyReuseConsistencyPolicy | IDEMPOTENCY_KEY_CONFLICT | D-136 |

## Momento INVOICE_CANCELLATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CancellationJustificationPolicy | CREDIT_NOTE_JUSTIFICATION_REQUIRED | D-165, D-127 |
| 2 | CancellationReasonPolicy | INVALID_CREDIT_NOTE_REASON | D-127, D-238 |
| 3 | SystemInitiatedCancellationPolicy | CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED | D-165, D-229, D-300, D-242, D-311 |
| 4 | CancellationInvoiceNotUnderReviewPolicy | PENDING_BANK_TRANSFER_ON_INVOICE | D-153, D-127 |
| 5 | CancellationByFullCreditNotePolicy | ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION | D-127, D-091 |

## Momento INVOICE_DISCOUNT_SELECTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | DiscountMatchesInvoiceTypePolicy | DISCOUNT_NOT_APPLICABLE_TO_INVOICE_TYPE | D-129 |
| 2 | BestDiscountPerInvoiceLinePolicy | DISCOUNT_OUTBID_BY_A_BETTER_ONE | D-073, D-285, D-295, D-301, D-384, D-385 |

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

## Momento LATE_FEE_ANNULMENT_ON_CANCELLATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | LateFeeDebitNoteNotUnderPaymentReviewPolicy | LATE_FEE_UNDER_PAYMENT_REVIEW | D-153, D-291, D-400 |

## Momento LATE_FEE_CORRECTION_ISSUING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | CorrectionTargetsLiveLateFeeDebitNotePolicy | LATE_FEE_CORRECTION_TARGET_INVALID | D-244, D-375, D-378, D-331 |
| 2 | CorrectionDebitNoteNotUnderPaymentReviewPolicy | PENDING_BANK_TRANSFER_ON_INVOICE | D-153, D-375, D-378 |

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
| 1 | LateFeeSettingsCoherencePolicy | INVALID_LATE_PAYMENT_SETTINGS | D-061, D-147, D-149, D-239, D-330 |

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

## Momento NIGHTLY_BILLING_COMPLETED_TODAY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | NightlyBillingCompletedTodayPolicy | NIGHTLY_BILLING_NOT_COMPLETED | D-055, D-459 |

## Momento NIGHTLY_BILLING_STEP_SEQUENCE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PreviousStepAllowsNextPolicy | PREVIOUS_STEP_FAILED | D-356, D-449 |

## Momento OVERDUE_DETECTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | InvoiceIsPastDueDatePolicy | INVOICE_NOT_PENDING, INVOICE_WITHIN_GRACE_PERIOD | D-057, D-153, D-288 |

## Momento PAYMENT_ALLOCATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PaymentAllocationSameAccountPolicy | PAYMENT_ALLOCATION_ACCOUNT_MISMATCH | D-097 |
| 2 | FullInvoicePaymentOnlyPolicy | PARTIAL_PAYMENT_NOT_ALLOWED | D-136 |
| 3 | OldestOverdueFirstAllocationOrderPolicy | ALLOCATION_ORDER_VIOLATED | D-089, D-128, D-139, D-140, D-329 |

## Momento PAYMENT_ALLOCATION_REVERSAL

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PaymentAllocationBelongsToLockedAccountPolicy | PAYMENT_ALLOCATION_ACCOUNT_MISMATCH | D-094, D-097 |
| 2 | PaymentAllocationReversedOnlyOncePolicy | PAYMENT_ALLOCATION_ALREADY_REVERSED | D-094 |

## Momento PAYMENT_APPROVAL_REVOCATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PaymentMoneyNotRefundedPolicy | PAYMENT_ALREADY_REFUNDED | D-262 |
| 2 | PaymentWithoutPendingRefundPolicy | PAYMENT_HAS_PENDING_REFUND | D-262, D-265 |

## Momento PAYMENT_GATEWAY_CAPABILITY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PaymentGatewayCapabilityPolicy | PAYMENT_GATEWAY_CAPABILITY_NOT_SUPPORTED | D-082, D-097 |

## Momento PAYMENT_GATEWAY_RESOLUTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PaymentGatewayAvailabilityPolicy | PAYMENT_GATEWAY_UNAVAILABLE | D-226 |

## Momento PAYMENT_PLAN_SELECTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AnnualUpfrontOfProgramMustUseRequestEndpointPolicy | ANNUAL_UPFRONT_USE_REQUEST_ENDPOINT | D-131, D-444 |

## Momento PAYMENT_REGISTRATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | IdempotencyKeyFormatPolicy | IDEMPOTENCY_KEY_INVALID | D-136 |
| 2 | CashPaymentAmountPolicy | CASH_PAYMENT_AMOUNT_INVALID | D-136, D-097, D-098 |
| 3 | CashPaymentReceiptNumberPolicy | CASH_PAYMENT_RECEIPT_NUMBER_INVALID | D-136 |
| 4 | CashPaymentBackdateReasonLengthPolicy | CASH_PAYMENT_BACKDATE_REASON_INVALID | D-266 |

## Momento PAYMENT_REJECTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PaymentRejectionReasonRequiredPolicy | PAYMENT_REJECTION_REASON_REQUIRED, PAYMENT_REJECTION_REASON_TOO_LONG | D-139 |

## Momento PAYMENT_REVIEW

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | VerifiedPaymentAmountPolicy | VERIFIED_PAYMENT_AMOUNT_INVALID | D-139, D-208 |
| 2 | VerifiedPaymentCurrencyPolicy | VERIFIED_PAYMENT_CURRENCY_MISMATCH | D-097, D-098 |
| 3 | VerifiedPaymentDatePolicy | PAYMENT_DATE_IN_FUTURE | D-169, D-320 |
| 4 | PaymentReviewNoteLengthPolicy | PAYMENT_REVIEW_NOTE_TOO_LONG | D-139 |

## Momento PRICE_CHANGE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PriceChangeReasonPolicy | CHANGE_REASON_REQUIRED | D-095 |
| 2 | EffectiveDateNotRetroactivePolicy | RETROACTIVE_EFFECTIVE_DATE | D-059, D-111 |

## Momento PRICE_PROPOSAL

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | SingleOpenPriceProposalPolicy | PROPOSAL_ALREADY_OPEN | D-116, D-211 |

## Momento PRICE_PROPOSAL_APPROVAL

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ProposalNotExpiredPolicy | PROPOSAL_EXPIRED | D-180 |

## Momento PRICE_PROPOSAL_REJECTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PriceRejectionReasonPolicy | REJECTION_REASON_REQUIRED | D-114 |

## Momento PRICE_VERSION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PriceCurrencyPolicy | CURRENCY_MISMATCH | D-097, D-240 |
| 2 | PriceAmountWithinLimitPolicy | PRICE_AMOUNT_TOO_LARGE | D-214 |
| 3 | MarketingDiscountNotNegativePolicy | NEGATIVE_MARKETING_DISCOUNT | D-240 |
| 4 | MarketingDiscountFixedNotAboveBasePolicy | MARKETING_DISCOUNT_EXCEEDS_BASE | D-240 |

## Momento PRICE_VERSION_CANCELLATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | PriceCancellationReasonPolicy | CANCELLATION_REASON_REQUIRED | D-115 |
| 2 | ScheduledPriceVersionCancellablePolicy | VERSION_ALREADY_EFFECTIVE | D-115 |

## Momento PROGRAM_INVOICE_PLANNING_ELIGIBILITY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | AcademicallyActiveEnrollmentPolicy | NOT_ACTIVE | D-050 |
| 2 | NotSuspendedOrWithdrawnForNonPaymentPolicy | SUSPENDED_OR_WITHDRAWN | D-066, D-053 |
| 3 | StudyPlanNotCompletedPolicy | PLAN_COMPLETED | D-038 |
| 4 | PaymentPlanChosenPolicy | NO_PAYMENT_PLAN | D-050 |

## Momento RECEIPT_APPROVAL

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | LegacyReceiptNotVerifiablePolicy | LEGACY_RECEIPT_HAS_NO_VERIFIABLE_PAYMENT | D-139 |
| 2 | ReceiptTransferDateMatchesUploadDatePolicy | RECEIPT_TRANSFER_DATE_MISMATCH | D-320, D-243 |

## Momento REENROLLMENT_DEBT

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | OutstandingDebtBlocksReenrollmentPolicy | DEBT_IN_PENDING_CANCELLATION_REQUEST, OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT, OVERDUE_INVOICE | D-065, D-142, D-314 |

## Momento REFUND_AVAILABILITY

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | RefundWithoutOverdueDebtPolicy | REFUND_BLOCKED_BY_OVERDUE_DEBT | D-264 |
| 2 | RefundNotGreaterThanAvailablePolicy | INSUFFICIENT_ACCOUNT_CREDIT, REFUND_EXCEEDS_AVAILABLE_AMOUNT | D-159, D-264 |

## Momento REFUND_COMPLETION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | RefundCompletionPolicy | REFUND_COMPLETION_INVALID | D-159 |
| 2 | RefundSameMethodAsOriginalPolicy | REFUND_METHOD_MISMATCH | D-159, D-209 |

## Momento REFUND_DESTINATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | RefundDestinationRequiredPolicy | REFUND_DESTINATION_REQUIRED | D-267 |

## Momento REFUND_FAILURE

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | RefundFailureReasonPolicy | REFUND_FAILURE_REASON_INVALID | D-159 |

## Momento REFUND_REQUEST_FORMAT

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | RefundAmountPolicy | REFUND_AMOUNT_INVALID | D-159 |
| 2 | RefundDestinationFormatPolicy | REFUND_DESTINATION_INVALID, REFUND_DESTINATION_REQUIRED | D-267 |

## Momento STUDENT_DISCOUNT_GRANTING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | StudentDiscountCoherencePolicy | INVALID_STUDENT_DISCOUNT | D-075, D-384 |
| 2 | ProposalNotExpiredPolicy | STUDENT_DISCOUNT_PROPOSAL_EXPIRED | D-238, D-180 |
| 3 | StudentDiscountDefinitionPolicy | INVALID_STUDENT_DISCOUNT, DISCOUNT_DEFINITION_CLOSED | D-217, D-384 |
| 4 | StudentDiscountInvoiceTypePolicy | DISCOUNT_INVOICE_TYPE_MISMATCH | D-129, D-157, D-384 |
| 5 | StudentDiscountPeriodRangePolicy | INVALID_SCHOLARSHIP_PERIOD | D-282, D-384 |
| 6 | StudentDiscountCurrencyPolicy | CURRENCY_MISMATCH | D-097 |
| 7 | ScholarshipStartsAtNextInstallmentPolicy | SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT | D-238 |
| 8 | ScholarshipNotAvailableWithAnnualUpfrontPolicy | SCHOLARSHIP_NOT_AVAILABLE_WITH_ANNUAL_UPFRONT | D-158, D-388 |
| 9 | StudentDiscountNoOverlapPolicy | STUDENT_DISCOUNT_OVERLAP | D-401 |
| 10 | EnrollmentFeeDiscountBeforeIssuePolicy | ENROLLMENT_FEE_ALREADY_ISSUED | D-402 |

## Momento STUDENT_DISCOUNT_REVOCATION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | RevocationReasonRequiredPolicy | REVOCATION_REASON_REQUIRED | D-284 |
| 2 | RevocationProposalNotExpiredPolicy | STUDENT_DISCOUNT_PROPOSAL_EXPIRED | D-389 |

## Momento WITHDRAWAL_LINE_REDUCTION

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | WithdrawalReductionInvoiceUnpaidPolicy | WITHDRAWAL_REDUCTION_INVOICE_NOT_REDUCIBLE | D-204, D-248, D-236, D-253 |
| 2 | WithdrawalReductionLinesUnchangedPolicy | WITHDRAWAL_REDUCTION_INVOICE_CHANGED | D-204, D-248, D-253 |
| 3 | WithdrawalReductionLeavesDebtPolicy | WITHDRAWAL_REDUCTION_WOULD_CLEAR_DEBT | D-248, D-142, D-127 |

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

### AnnualUpfrontRequestStateMachine

| Desde \ Evento | REQUESTED | PAYMENT_INITIATED | PAYMENT_APPROVED | PAYMENT_REJECTED | DEADLINE_PASSED | WITHDRAWN | CANCELLED_BY_STUDENT |
|---|---|---|---|---|---|---|---|
| (sin estado) | AWAITING_PAYMENT | — | — | — | — | — | — |
| AWAITING_PAYMENT | — | PAYMENT_UNDER_REVIEW | — | — | DISCARDED | DISCARDED | DISCARDED |
| PAYMENT_UNDER_REVIEW | — | — | CONFIRMED | DISCARDED | — | — | — |
| CONFIRMED | — | — | — | — | — | — | — |
| DISCARDED | — | — | — | — | — | — | — |

Transiciones válidas: 7 de 35. Pares inválidos: (sin estado) × PAYMENT_INITIATED; (sin estado) × PAYMENT_APPROVED; (sin estado) × PAYMENT_REJECTED; (sin estado) × DEADLINE_PASSED; (sin estado) × WITHDRAWN; (sin estado) × CANCELLED_BY_STUDENT; AWAITING_PAYMENT × REQUESTED; AWAITING_PAYMENT × PAYMENT_APPROVED; AWAITING_PAYMENT × PAYMENT_REJECTED; PAYMENT_UNDER_REVIEW × REQUESTED; PAYMENT_UNDER_REVIEW × PAYMENT_INITIATED; PAYMENT_UNDER_REVIEW × DEADLINE_PASSED; PAYMENT_UNDER_REVIEW × WITHDRAWN; PAYMENT_UNDER_REVIEW × CANCELLED_BY_STUDENT; CONFIRMED × REQUESTED; CONFIRMED × PAYMENT_INITIATED; CONFIRMED × PAYMENT_APPROVED; CONFIRMED × PAYMENT_REJECTED; CONFIRMED × DEADLINE_PASSED; CONFIRMED × WITHDRAWN; CONFIRMED × CANCELLED_BY_STUDENT; DISCARDED × REQUESTED; DISCARDED × PAYMENT_INITIATED; DISCARDED × PAYMENT_APPROVED; DISCARDED × PAYMENT_REJECTED; DISCARDED × DEADLINE_PASSED; DISCARDED × WITHDRAWN; DISCARDED × CANCELLED_BY_STUDENT.

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

### EnrollmentPaymentStatusStateMachine

| Desde \ Evento | STANDING_OPENED | OVERDUE_DEBT_DETECTED | OVERDUE_DEBT_CLEARED | MONTH_CLOSED_WITH_UNPAID_INSTALLMENT | ACCOUNT_SETTLED | TERM_CLOSED_WHILE_SUSPENDED | COURSE_ENDED_WHILE_SUSPENDED |
|---|---|---|---|---|---|---|---|
| (sin estado) | UP_TO_DATE | — | — | — | — | — | — |
| UP_TO_DATE | — | OVERDUE | — | — | — | — | — |
| OVERDUE | — | — | UP_TO_DATE | SUSPENDED | — | — | — |
| SUSPENDED | — | — | — | — | UP_TO_DATE | WITHDRAWN_FOR_NONPAYMENT | WITHDRAWN_FOR_NONPAYMENT |
| WITHDRAWN_FOR_NONPAYMENT | — | — | — | — | — | — | — |

Transiciones válidas: 7 de 35. Pares inválidos: (sin estado) × OVERDUE_DEBT_DETECTED; (sin estado) × OVERDUE_DEBT_CLEARED; (sin estado) × MONTH_CLOSED_WITH_UNPAID_INSTALLMENT; (sin estado) × ACCOUNT_SETTLED; (sin estado) × TERM_CLOSED_WHILE_SUSPENDED; (sin estado) × COURSE_ENDED_WHILE_SUSPENDED; UP_TO_DATE × STANDING_OPENED; UP_TO_DATE × OVERDUE_DEBT_CLEARED; UP_TO_DATE × MONTH_CLOSED_WITH_UNPAID_INSTALLMENT; UP_TO_DATE × ACCOUNT_SETTLED; UP_TO_DATE × TERM_CLOSED_WHILE_SUSPENDED; UP_TO_DATE × COURSE_ENDED_WHILE_SUSPENDED; OVERDUE × STANDING_OPENED; OVERDUE × OVERDUE_DEBT_DETECTED; OVERDUE × ACCOUNT_SETTLED; OVERDUE × TERM_CLOSED_WHILE_SUSPENDED; OVERDUE × COURSE_ENDED_WHILE_SUSPENDED; SUSPENDED × STANDING_OPENED; SUSPENDED × OVERDUE_DEBT_DETECTED; SUSPENDED × OVERDUE_DEBT_CLEARED; SUSPENDED × MONTH_CLOSED_WITH_UNPAID_INSTALLMENT; WITHDRAWN_FOR_NONPAYMENT × STANDING_OPENED; WITHDRAWN_FOR_NONPAYMENT × OVERDUE_DEBT_DETECTED; WITHDRAWN_FOR_NONPAYMENT × OVERDUE_DEBT_CLEARED; WITHDRAWN_FOR_NONPAYMENT × MONTH_CLOSED_WITH_UNPAID_INSTALLMENT; WITHDRAWN_FOR_NONPAYMENT × ACCOUNT_SETTLED; WITHDRAWN_FOR_NONPAYMENT × TERM_CLOSED_WHILE_SUSPENDED; WITHDRAWN_FOR_NONPAYMENT × COURSE_ENDED_WHILE_SUSPENDED.

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

### DiscountDefinitionStateMachine

| Desde \ Evento | DEFINITION_CREATED | DEFINITION_CLOSED |
|---|---|---|
| (sin estado) | ACTIVE | — |
| ACTIVE | — | CLOSED |
| CLOSED | — | — |

Transiciones válidas: 2 de 6. Pares inválidos: (sin estado) × DEFINITION_CLOSED; ACTIVE × DEFINITION_CREATED; CLOSED × DEFINITION_CREATED; CLOSED × DEFINITION_CLOSED.

### StudentDiscountStateMachine

| Desde \ Evento | DISCOUNT_GRANTED | LAST_INSTALLMENT_CONSUMED | CONSUMPTION_RETURNED | DISCOUNT_REVOKED | ENROLLMENT_ENDED |
|---|---|---|---|---|---|
| (sin estado) | ACTIVE | — | — | — | — |
| ACTIVE | — | EXHAUSTED | ACTIVE | REVOKED | ENDED |
| EXHAUSTED | — | — | ACTIVE | — | — |
| REVOKED | — | — | — | — | — |
| ENDED | — | — | — | — | — |

Transiciones válidas: 6 de 25. Pares inválidos: (sin estado) × LAST_INSTALLMENT_CONSUMED; (sin estado) × CONSUMPTION_RETURNED; (sin estado) × DISCOUNT_REVOKED; (sin estado) × ENROLLMENT_ENDED; ACTIVE × DISCOUNT_GRANTED; EXHAUSTED × DISCOUNT_GRANTED; EXHAUSTED × LAST_INSTALLMENT_CONSUMED; EXHAUSTED × DISCOUNT_REVOKED; EXHAUSTED × ENROLLMENT_ENDED; REVOKED × DISCOUNT_GRANTED; REVOKED × LAST_INSTALLMENT_CONSUMED; REVOKED × CONSUMPTION_RETURNED; REVOKED × DISCOUNT_REVOKED; REVOKED × ENROLLMENT_ENDED; ENDED × DISCOUNT_GRANTED; ENDED × LAST_INSTALLMENT_CONSUMED; ENDED × CONSUMPTION_RETURNED; ENDED × DISCOUNT_REVOKED; ENDED × ENROLLMENT_ENDED.

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

### PaymentStatusStateMachine

| Desde \ Evento | CASH_PAYMENT_REGISTERED | GATEWAY_PAYMENT_REGISTERED_AS_APPROVED | PENDING_ONLINE_PAYMENT_CREATED | PENDING_BANK_TRANSFER_CREATED | PAYMENT_HOLD_AUTHORIZED | PAYMENT_APPROVED | PAYMENT_REJECTED | PENDING_PAYMENT_CANCELLED | PARTIAL_REFUND_REGISTERED | FULL_REFUND_REGISTERED | CHARGEBACK_REGISTERED | CHARGEBACK_REVERSED | BANK_TRANSFER_APPROVAL_REVOKED |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| (sin estado) | APPROVED | APPROVED | PENDING | PENDING | — | — | — | — | — | — | — | — | — |
| PENDING | — | — | — | — | AUTHORIZED | APPROVED | REJECTED | CANCELLED | — | — | — | — | — |
| AUTHORIZED | — | — | — | — | — | APPROVED | — | CANCELLED | — | — | — | — | — |
| APPROVED | — | — | — | — | — | — | — | — | APPROVED | REFUNDED | CHARGED_BACK | — | REJECTED |
| REJECTED | — | — | — | — | — | — | — | — | — | — | — | — | — |
| REFUNDED | — | — | — | — | — | — | — | — | — | — | — | — | — |
| CHARGED_BACK | — | — | — | — | — | — | — | — | — | — | — | APPROVED | — |
| CANCELLED | — | — | — | — | — | — | — | — | — | — | — | — | — |

Transiciones válidas: 15 de 104. Pares inválidos: (sin estado) × PAYMENT_HOLD_AUTHORIZED; (sin estado) × PAYMENT_APPROVED; (sin estado) × PAYMENT_REJECTED; (sin estado) × PENDING_PAYMENT_CANCELLED; (sin estado) × PARTIAL_REFUND_REGISTERED; (sin estado) × FULL_REFUND_REGISTERED; (sin estado) × CHARGEBACK_REGISTERED; (sin estado) × CHARGEBACK_REVERSED; (sin estado) × BANK_TRANSFER_APPROVAL_REVOKED; PENDING × CASH_PAYMENT_REGISTERED; PENDING × GATEWAY_PAYMENT_REGISTERED_AS_APPROVED; PENDING × PENDING_ONLINE_PAYMENT_CREATED; PENDING × PENDING_BANK_TRANSFER_CREATED; PENDING × PARTIAL_REFUND_REGISTERED; PENDING × FULL_REFUND_REGISTERED; PENDING × CHARGEBACK_REGISTERED; PENDING × CHARGEBACK_REVERSED; PENDING × BANK_TRANSFER_APPROVAL_REVOKED; AUTHORIZED × CASH_PAYMENT_REGISTERED; AUTHORIZED × GATEWAY_PAYMENT_REGISTERED_AS_APPROVED; AUTHORIZED × PENDING_ONLINE_PAYMENT_CREATED; AUTHORIZED × PENDING_BANK_TRANSFER_CREATED; AUTHORIZED × PAYMENT_HOLD_AUTHORIZED; AUTHORIZED × PAYMENT_REJECTED; AUTHORIZED × PARTIAL_REFUND_REGISTERED; AUTHORIZED × FULL_REFUND_REGISTERED; AUTHORIZED × CHARGEBACK_REGISTERED; AUTHORIZED × CHARGEBACK_REVERSED; AUTHORIZED × BANK_TRANSFER_APPROVAL_REVOKED; APPROVED × CASH_PAYMENT_REGISTERED; APPROVED × GATEWAY_PAYMENT_REGISTERED_AS_APPROVED; APPROVED × PENDING_ONLINE_PAYMENT_CREATED; APPROVED × PENDING_BANK_TRANSFER_CREATED; APPROVED × PAYMENT_HOLD_AUTHORIZED; APPROVED × PAYMENT_APPROVED; APPROVED × PAYMENT_REJECTED; APPROVED × PENDING_PAYMENT_CANCELLED; APPROVED × CHARGEBACK_REVERSED; REJECTED × CASH_PAYMENT_REGISTERED; REJECTED × GATEWAY_PAYMENT_REGISTERED_AS_APPROVED; REJECTED × PENDING_ONLINE_PAYMENT_CREATED; REJECTED × PENDING_BANK_TRANSFER_CREATED; REJECTED × PAYMENT_HOLD_AUTHORIZED; REJECTED × PAYMENT_APPROVED; REJECTED × PAYMENT_REJECTED; REJECTED × PENDING_PAYMENT_CANCELLED; REJECTED × PARTIAL_REFUND_REGISTERED; REJECTED × FULL_REFUND_REGISTERED; REJECTED × CHARGEBACK_REGISTERED; REJECTED × CHARGEBACK_REVERSED; REJECTED × BANK_TRANSFER_APPROVAL_REVOKED; REFUNDED × CASH_PAYMENT_REGISTERED; REFUNDED × GATEWAY_PAYMENT_REGISTERED_AS_APPROVED; REFUNDED × PENDING_ONLINE_PAYMENT_CREATED; REFUNDED × PENDING_BANK_TRANSFER_CREATED; REFUNDED × PAYMENT_HOLD_AUTHORIZED; REFUNDED × PAYMENT_APPROVED; REFUNDED × PAYMENT_REJECTED; REFUNDED × PENDING_PAYMENT_CANCELLED; REFUNDED × PARTIAL_REFUND_REGISTERED; REFUNDED × FULL_REFUND_REGISTERED; REFUNDED × CHARGEBACK_REGISTERED; REFUNDED × CHARGEBACK_REVERSED; REFUNDED × BANK_TRANSFER_APPROVAL_REVOKED; CHARGED_BACK × CASH_PAYMENT_REGISTERED; CHARGED_BACK × GATEWAY_PAYMENT_REGISTERED_AS_APPROVED; CHARGED_BACK × PENDING_ONLINE_PAYMENT_CREATED; CHARGED_BACK × PENDING_BANK_TRANSFER_CREATED; CHARGED_BACK × PAYMENT_HOLD_AUTHORIZED; CHARGED_BACK × PAYMENT_APPROVED; CHARGED_BACK × PAYMENT_REJECTED; CHARGED_BACK × PENDING_PAYMENT_CANCELLED; CHARGED_BACK × PARTIAL_REFUND_REGISTERED; CHARGED_BACK × FULL_REFUND_REGISTERED; CHARGED_BACK × CHARGEBACK_REGISTERED; CHARGED_BACK × BANK_TRANSFER_APPROVAL_REVOKED; CANCELLED × CASH_PAYMENT_REGISTERED; CANCELLED × GATEWAY_PAYMENT_REGISTERED_AS_APPROVED; CANCELLED × PENDING_ONLINE_PAYMENT_CREATED; CANCELLED × PENDING_BANK_TRANSFER_CREATED; CANCELLED × PAYMENT_HOLD_AUTHORIZED; CANCELLED × PAYMENT_APPROVED; CANCELLED × PAYMENT_REJECTED; CANCELLED × PENDING_PAYMENT_CANCELLED; CANCELLED × PARTIAL_REFUND_REGISTERED; CANCELLED × FULL_REFUND_REGISTERED; CANCELLED × CHARGEBACK_REGISTERED; CANCELLED × CHARGEBACK_REVERSED; CANCELLED × BANK_TRANSFER_APPROVAL_REVOKED.

### RefundStatusStateMachine

| Desde \ Evento | REFUND_REQUESTED | REFUND_APPROVED | REFUND_REJECTED | REFUND_COMPLETED | REFUND_FAILED |
|---|---|---|---|---|---|
| (sin estado) | REQUESTED | — | — | — | — |
| REQUESTED | — | APPROVED | REJECTED | — | — |
| APPROVED | — | — | — | COMPLETED | FAILED |
| COMPLETED | — | — | — | — | — |
| FAILED | — | — | — | — | — |
| REJECTED | — | — | — | — | — |

Transiciones válidas: 5 de 30. Pares inválidos: (sin estado) × REFUND_APPROVED; (sin estado) × REFUND_REJECTED; (sin estado) × REFUND_COMPLETED; (sin estado) × REFUND_FAILED; REQUESTED × REFUND_REQUESTED; REQUESTED × REFUND_COMPLETED; REQUESTED × REFUND_FAILED; APPROVED × REFUND_REQUESTED; APPROVED × REFUND_APPROVED; APPROVED × REFUND_REJECTED; COMPLETED × REFUND_REQUESTED; COMPLETED × REFUND_APPROVED; COMPLETED × REFUND_REJECTED; COMPLETED × REFUND_COMPLETED; COMPLETED × REFUND_FAILED; FAILED × REFUND_REQUESTED; FAILED × REFUND_APPROVED; FAILED × REFUND_REJECTED; FAILED × REFUND_COMPLETED; FAILED × REFUND_FAILED; REJECTED × REFUND_REQUESTED; REJECTED × REFUND_APPROVED; REJECTED × REFUND_REJECTED; REJECTED × REFUND_COMPLETED; REJECTED × REFUND_FAILED.

### PriceVersionStateMachine

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

### BillingRunStateMachine

| Desde \ Evento | STEP_STARTED | STEP_FINISHED_CLEAN | STEP_FINISHED_WITH_ERRORS | STEP_ABORTED | STEP_DECLARED_NOT_APPLICABLE |
|---|---|---|---|---|---|
| (sin estado) | RUNNING | — | — | — | NOT_APPLICABLE |
| RUNNING | — | COMPLETED | COMPLETED_WITH_ERRORS | FAILED | — |
| COMPLETED | — | — | — | — | — |
| COMPLETED_WITH_ERRORS | — | — | — | — | — |
| FAILED | — | — | — | — | — |
| NOT_APPLICABLE | — | — | — | — | — |

Transiciones válidas: 5 de 30. Pares inválidos: (sin estado) × STEP_FINISHED_CLEAN; (sin estado) × STEP_FINISHED_WITH_ERRORS; (sin estado) × STEP_ABORTED; RUNNING × STEP_STARTED; RUNNING × STEP_DECLARED_NOT_APPLICABLE; COMPLETED × STEP_STARTED; COMPLETED × STEP_FINISHED_CLEAN; COMPLETED × STEP_FINISHED_WITH_ERRORS; COMPLETED × STEP_ABORTED; COMPLETED × STEP_DECLARED_NOT_APPLICABLE; COMPLETED_WITH_ERRORS × STEP_STARTED; COMPLETED_WITH_ERRORS × STEP_FINISHED_CLEAN; COMPLETED_WITH_ERRORS × STEP_FINISHED_WITH_ERRORS; COMPLETED_WITH_ERRORS × STEP_ABORTED; COMPLETED_WITH_ERRORS × STEP_DECLARED_NOT_APPLICABLE; FAILED × STEP_STARTED; FAILED × STEP_FINISHED_CLEAN; FAILED × STEP_FINISHED_WITH_ERRORS; FAILED × STEP_ABORTED; FAILED × STEP_DECLARED_NOT_APPLICABLE; NOT_APPLICABLE × STEP_STARTED; NOT_APPLICABLE × STEP_FINISHED_CLEAN; NOT_APPLICABLE × STEP_FINISHED_WITH_ERRORS; NOT_APPLICABLE × STEP_ABORTED; NOT_APPLICABLE × STEP_DECLARED_NOT_APPLICABLE.

### MonthlyInvoiceRunItemStateMachine

| Desde \ Evento | RETRY_ISSUED | RETRY_ALREADY_ISSUED | RETRY_NOT_APPLICABLE | RETRY_FAILED_WITH_WAIT | RETRY_FAILED_WITHOUT_WAIT |
|---|---|---|---|---|---|
| (sin estado) | — | — | — | — | — |
| ISSUED | — | — | — | — | — |
| NOT_APPLICABLE | — | — | — | — | — |
| ALREADY_ISSUED | — | — | — | — | — |
| FAILED | ISSUED | ALREADY_ISSUED | NOT_APPLICABLE | FAILED | RETRIES_EXHAUSTED |
| RETRIES_EXHAUSTED | ISSUED | ALREADY_ISSUED | NOT_APPLICABLE | FAILED | RETRIES_EXHAUSTED |

Transiciones válidas: 10 de 30. Pares inválidos: (sin estado) × RETRY_ISSUED; (sin estado) × RETRY_ALREADY_ISSUED; (sin estado) × RETRY_NOT_APPLICABLE; (sin estado) × RETRY_FAILED_WITH_WAIT; (sin estado) × RETRY_FAILED_WITHOUT_WAIT; ISSUED × RETRY_ISSUED; ISSUED × RETRY_ALREADY_ISSUED; ISSUED × RETRY_NOT_APPLICABLE; ISSUED × RETRY_FAILED_WITH_WAIT; ISSUED × RETRY_FAILED_WITHOUT_WAIT; NOT_APPLICABLE × RETRY_ISSUED; NOT_APPLICABLE × RETRY_ALREADY_ISSUED; NOT_APPLICABLE × RETRY_NOT_APPLICABLE; NOT_APPLICABLE × RETRY_FAILED_WITH_WAIT; NOT_APPLICABLE × RETRY_FAILED_WITHOUT_WAIT; ALREADY_ISSUED × RETRY_ISSUED; ALREADY_ISSUED × RETRY_ALREADY_ISSUED; ALREADY_ISSUED × RETRY_NOT_APPLICABLE; ALREADY_ISSUED × RETRY_FAILED_WITH_WAIT; ALREADY_ISSUED × RETRY_FAILED_WITHOUT_WAIT.

### MonthlyInvoiceRunStateMachine

| Desde \ Evento | RUN_STARTED | RUN_BLOCKED_BY_NIGHT | RUN_FINISHED_CLEAN | RUN_FINISHED_WITH_FAILURES | RUN_ABORTED |
|---|---|---|---|---|---|
| (sin estado) | RUNNING | FAILED | — | — | — |
| RUNNING | — | — | COMPLETED | COMPLETED_WITH_FAILURES | FAILED |
| COMPLETED | — | — | — | — | — |
| COMPLETED_WITH_FAILURES | — | — | — | — | — |
| FAILED | — | — | — | — | — |

Transiciones válidas: 5 de 25. Pares inválidos: (sin estado) × RUN_FINISHED_CLEAN; (sin estado) × RUN_FINISHED_WITH_FAILURES; (sin estado) × RUN_ABORTED; RUNNING × RUN_STARTED; RUNNING × RUN_BLOCKED_BY_NIGHT; COMPLETED × RUN_STARTED; COMPLETED × RUN_BLOCKED_BY_NIGHT; COMPLETED × RUN_FINISHED_CLEAN; COMPLETED × RUN_FINISHED_WITH_FAILURES; COMPLETED × RUN_ABORTED; COMPLETED_WITH_FAILURES × RUN_STARTED; COMPLETED_WITH_FAILURES × RUN_BLOCKED_BY_NIGHT; COMPLETED_WITH_FAILURES × RUN_FINISHED_CLEAN; COMPLETED_WITH_FAILURES × RUN_FINISHED_WITH_FAILURES; COMPLETED_WITH_FAILURES × RUN_ABORTED; FAILED × RUN_STARTED; FAILED × RUN_BLOCKED_BY_NIGHT; FAILED × RUN_FINISHED_CLEAN; FAILED × RUN_FINISHED_WITH_FAILURES; FAILED × RUN_ABORTED.

### NightlyBillingRunStateMachine

| Desde \ Evento | NIGHT_STARTED | ALL_STEPS_FINISHED_CLEAN | ALL_STEPS_FINISHED_WITH_ERRORS | STEP_ABORTED |
|---|---|---|---|---|
| (sin estado) | RUNNING | — | — | — |
| RUNNING | — | COMPLETED | COMPLETED_WITH_ERRORS | FAILED |
| COMPLETED | — | — | — | — |
| COMPLETED_WITH_ERRORS | — | — | — | — |
| FAILED | — | — | — | — |

Transiciones válidas: 4 de 20. Pares inválidos: (sin estado) × ALL_STEPS_FINISHED_CLEAN; (sin estado) × ALL_STEPS_FINISHED_WITH_ERRORS; (sin estado) × STEP_ABORTED; RUNNING × NIGHT_STARTED; COMPLETED × NIGHT_STARTED; COMPLETED × ALL_STEPS_FINISHED_CLEAN; COMPLETED × ALL_STEPS_FINISHED_WITH_ERRORS; COMPLETED × STEP_ABORTED; COMPLETED_WITH_ERRORS × NIGHT_STARTED; COMPLETED_WITH_ERRORS × ALL_STEPS_FINISHED_CLEAN; COMPLETED_WITH_ERRORS × ALL_STEPS_FINISHED_WITH_ERRORS; COMPLETED_WITH_ERRORS × STEP_ABORTED; FAILED × NIGHT_STARTED; FAILED × ALL_STEPS_FINISHED_CLEAN; FAILED × ALL_STEPS_FINISHED_WITH_ERRORS; FAILED × STEP_ABORTED.

### DeliveryStatusStateMachine

| Desde \ Evento | QUEUE_FOR_DISPATCH | DELIVER_IN_APP | SUPPRESS | SEND_VIA_PROVIDER | SEND_VIA_SIMULATOR | DEFER | DISCARD | GIVE_UP |
|---|---|---|---|---|---|---|---|---|
| (sin estado) | QUEUED | SENT | SUPPRESSED | — | — | — | — | — |
| QUEUED | — | — | SUPPRESSED | SENT | SENT_SIMULATED | DEFERRED | DISCARDED | FAILED |
| SENT | — | — | — | — | — | — | — | — |
| SENT_SIMULATED | — | — | — | — | — | — | — | — |
| SUPPRESSED | — | — | — | — | — | — | — | — |
| DEFERRED | — | — | SUPPRESSED | SENT | SENT_SIMULATED | DEFERRED | DISCARDED | FAILED |
| DISCARDED | — | — | — | — | — | — | — | — |
| FAILED | — | — | — | — | — | — | — | — |

Transiciones válidas: 15 de 64. Pares inválidos: (sin estado) × SEND_VIA_PROVIDER; (sin estado) × SEND_VIA_SIMULATOR; (sin estado) × DEFER; (sin estado) × DISCARD; (sin estado) × GIVE_UP; QUEUED × QUEUE_FOR_DISPATCH; QUEUED × DELIVER_IN_APP; SENT × QUEUE_FOR_DISPATCH; SENT × DELIVER_IN_APP; SENT × SUPPRESS; SENT × SEND_VIA_PROVIDER; SENT × SEND_VIA_SIMULATOR; SENT × DEFER; SENT × DISCARD; SENT × GIVE_UP; SENT_SIMULATED × QUEUE_FOR_DISPATCH; SENT_SIMULATED × DELIVER_IN_APP; SENT_SIMULATED × SUPPRESS; SENT_SIMULATED × SEND_VIA_PROVIDER; SENT_SIMULATED × SEND_VIA_SIMULATOR; SENT_SIMULATED × DEFER; SENT_SIMULATED × DISCARD; SENT_SIMULATED × GIVE_UP; SUPPRESSED × QUEUE_FOR_DISPATCH; SUPPRESSED × DELIVER_IN_APP; SUPPRESSED × SUPPRESS; SUPPRESSED × SEND_VIA_PROVIDER; SUPPRESSED × SEND_VIA_SIMULATOR; SUPPRESSED × DEFER; SUPPRESSED × DISCARD; SUPPRESSED × GIVE_UP; DEFERRED × QUEUE_FOR_DISPATCH; DEFERRED × DELIVER_IN_APP; DISCARDED × QUEUE_FOR_DISPATCH; DISCARDED × DELIVER_IN_APP; DISCARDED × SUPPRESS; DISCARDED × SEND_VIA_PROVIDER; DISCARDED × SEND_VIA_SIMULATOR; DISCARDED × DEFER; DISCARDED × DISCARD; DISCARDED × GIVE_UP; FAILED × QUEUE_FOR_DISPATCH; FAILED × DELIVER_IN_APP; FAILED × SUPPRESS; FAILED × SEND_VIA_PROVIDER; FAILED × SEND_VIA_SIMULATOR; FAILED × DEFER; FAILED × DISCARD; FAILED × GIVE_UP.

## Operación ACQUIRE_COHORT_PROMOTION

- Validaciones (momentos): COHORT_PROMOTION_ACQUISITION
- Pre-procesamiento (en orden): —
- Eventos que publica: DiscountQuotaExhaustedEvent
- Post-procesamiento de DiscountQuotaExhaustedEvent: BillingOperationNotificationListener#notifyDiscountQuotaExhausted

## Operación ALLOCATE_ACCOUNT_CREDIT

- Validaciones (momentos): PAYMENT_ALLOCATION
- Pre-procesamiento (en orden): —
- Eventos que publica: AccountCreditAllocatedEvent, InvoicePaidDomainEvent
- Post-procesamiento de AccountCreditAllocatedEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación APPROVE_BANK_TRANSFER_RECEIPT_PAYMENT

- Validaciones (momentos): —
- Pre-procesamiento (en orden): —
- Eventos que publica: PaymentApprovedEvent, AccountCreditAllocatedEvent, InvoicePaidDomainEvent
- Post-procesamiento de PaymentApprovedEvent: —
- Post-procesamiento de AccountCreditAllocatedEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación APPROVE_PENDING_BANK_TRANSFER_PAYMENT

- Validaciones (momentos): PAYMENT_REVIEW
- Pre-procesamiento (en orden): —
- Eventos que publica: PaymentApprovedEvent, AccountCreditAllocatedEvent, InvoicePaidDomainEvent
- Post-procesamiento de PaymentApprovedEvent: —
- Post-procesamiento de AccountCreditAllocatedEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación CANCEL_CONFIGURATION_VERSION

- Validaciones (momentos): CONFIGURATION_VERSION_CANCELLATION
- Pre-procesamiento (en orden): —
- Eventos que publica: ConfigurationVersionCancelledEvent
- Post-procesamiento de ConfigurationVersionCancelledEvent: BillingOperationNotificationListener#notifyConfigurationVersionCancelled

## Operación CANCEL_INVOICE

- Validaciones (momentos): INVOICE_CANCELLATION
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: CreditNoteIssuedEvent
- Post-procesamiento de CreditNoteIssuedEvent: CreditNoteNotificationListener#notifyRequester, CreditNoteNotificationListener#notifyStudent

## Operación CANCEL_INVOICES_AFTER_WITHDRAWAL

- Validaciones (momentos): WITHDRAWAL_LINE_REDUCTION, INVOICE_CANCELLATION
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: CreditNoteIssuedEvent
- Post-procesamiento de CreditNoteIssuedEvent: CreditNoteNotificationListener#notifyRequester, CreditNoteNotificationListener#notifyStudent

## Operación CANCEL_PRICE_VERSION

- Validaciones (momentos): PRICE_VERSION_CANCELLATION
- Pre-procesamiento (en orden): —
- Eventos que publica: —

## Operación CHANGE_BILLING_CONFIGURATION

- Validaciones (momentos): BILLING_CONFIGURATION_CHANGE, BILLING_CONFIGURATION_PROPOSAL, CONFIGURATION_PROPOSAL_APPROVAL, YEAR_BILLING_CONFIGURATION, YEAR_BILLING_CONFIGURATION_FREEZE, LATE_PAYMENT_TERMS, ISSUANCE_SETTINGS, ANNUAL_UPFRONT_SETTINGS, COLLECTION_REMINDER_SETTINGS, COLLECTION_REMINDER_EFFECTIVE_FROM, COLLECTION_REMINDER_SCHEDULE, COLLECTION_REMINDER_SCHEDULE_CHANGE
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: ConfigurationChangeDecidedEvent
- Post-procesamiento de ConfigurationChangeDecidedEvent: BillingOperationNotificationListener#notifyConfigurationChangeDecided

## Operación CHANGE_PRICE

- Validaciones (momentos): PRICE_CHANGE, PRICE_VERSION, PRICE_PROPOSAL, PRICE_PROPOSAL_APPROVAL, PRICE_PROPOSAL_REJECTION
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: PriceChangeDecidedEvent
- Post-procesamiento de PriceChangeDecidedEvent: BillingOperationNotificationListener#notifyPriceChangeDecided

## Operación CLOSE_DISCOUNT_DEFINITION

- Validaciones (momentos): DISCOUNT_DEFINITION_CLOSURE
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: —

## Operación COMPLETE_REFUND

- Validaciones (momentos): REFUND_COMPLETION
- Pre-procesamiento (en orden): —
- Eventos que publica: RefundCompletedEvent
- Post-procesamiento de RefundCompletedEvent: —

## Operación CREATE_DISCOUNT_DEFINITION

- Validaciones (momentos): DISCOUNT_DEFINITION
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: DiscountDefinitionCreatedEvent
- Post-procesamiento de DiscountDefinitionCreatedEvent: —

## Operación CREATE_LATE_PAYMENT_SETTINGS

- Validaciones (momentos): LATE_PAYMENT_SETTINGS
- Pre-procesamiento (en orden): —
- Eventos que publica: LatePaymentSettingsCreatedEvent
- Post-procesamiento de LatePaymentSettingsCreatedEvent: BillingOperationNotificationListener#notifyLatePaymentSettingsCreated

## Operación DISCARD_ANNUAL_UPFRONT_REQUEST

- Validaciones (momentos): —
- Pre-procesamiento (en orden): —
- Eventos que publica: —

## Operación FAIL_REFUND

- Validaciones (momentos): REFUND_FAILURE
- Pre-procesamiento (en orden): —
- Eventos que publica: —

## Operación GRANT_STUDENT_DISCOUNT

- Validaciones (momentos): STUDENT_DISCOUNT_GRANTING
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: —

## Operación ISSUE_CREDIT_NOTE

- Validaciones (momentos): CREDIT_NOTE_ISSUING
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: CreditNoteIssuedEvent, InvoicePaidDomainEvent
- Post-procesamiento de CreditNoteIssuedEvent: CreditNoteNotificationListener#notifyRequester, CreditNoteNotificationListener#notifyStudent
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación ISSUE_INVOICE

- Validaciones (momentos): INVOICE_ISSUING
- Pre-procesamiento (en orden): InvoiceDiscountEnrichmentHandler
- Eventos que publica: InvoiceIssuedDomainEvent, InvoicePaidDomainEvent
- Post-procesamiento de InvoiceIssuedDomainEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación ISSUE_LATE_FEE_CORRECTION_CREDIT_NOTE

- Validaciones (momentos): LATE_FEE_CORRECTION_ISSUING
- Pre-procesamiento (en orden): —
- Eventos que publica: CreditNoteIssuedEvent, LateFeeAnnulledByPaymentDateEvent, LateFeeCorrectionIssuedAfterFiscalWindowEvent
- Post-procesamiento de CreditNoteIssuedEvent: CreditNoteNotificationListener#notifyRequester, CreditNoteNotificationListener#notifyStudent
- Post-procesamiento de LateFeeAnnulledByPaymentDateEvent: LateFeeCorrectionNotificationListener#notifyStudentOfTheAnnulmentByPaymentDate
- Post-procesamiento de LateFeeCorrectionIssuedAfterFiscalWindowEvent: LateFeeCorrectionNotificationListener#notifySecretariatWhenIssuedAfterFiscalWindow

## Operación ISSUE_LATE_FEE_DEBIT_NOTE

- Validaciones (momentos): DEBIT_NOTE_ISSUING
- Pre-procesamiento (en orden): —
- Eventos que publica: LateFeeIssuedEvent
- Post-procesamiento de LateFeeIssuedEvent: LateFeeNotificationListener#notifySecretariatWhenIssuedAfterFiscalWindow, LateFeeNotificationListener#notifyStudent

## Operación ISSUE_REFUND

- Validaciones (momentos): REFUND_REQUEST_FORMAT, REFUND_AVAILABILITY, REFUND_DESTINATION
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: RefundApprovedEvent
- Post-procesamiento de RefundApprovedEvent: —

## Operación REGISTER_CHARGEBACK

- Validaciones (momentos): CHARGEBACK_REGISTRATION
- Pre-procesamiento (en orden): —
- Eventos que publica: PaymentChargedBackEvent, PaymentAllocationReversedEvent, AccountCreditAllocatedEvent, InvoicePaidDomainEvent
- Post-procesamiento de PaymentChargedBackEvent: PaymentChargedBackNotificationListener#notifyAdministration
- Post-procesamiento de PaymentAllocationReversedEvent: LateFeeOnPaymentAllocationReversedListener#reconcileLateFeesOfInvoiceWithReversedPayment
- Post-procesamiento de AccountCreditAllocatedEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación REGISTER_GATEWAY_PAYMENT

- Validaciones (momentos): —
- Pre-procesamiento (en orden): —
- Eventos que publica: PaymentApprovedEvent, GatewayPaymentArrivedWithGatewayDisabledEvent, PaymentLeftAsAccountCreditEvent, AccountCreditAllocatedEvent, InvoicePaidDomainEvent
- Post-procesamiento de PaymentApprovedEvent: —
- Post-procesamiento de GatewayPaymentArrivedWithGatewayDisabledEvent: GatewayPaymentArrivedWithGatewayDisabledNotificationListener#notifySecretariat
- Post-procesamiento de PaymentLeftAsAccountCreditEvent: PaymentLeftAsAccountCreditNotificationListener#notifySecretariat, PaymentLeftAsAccountCreditNotificationListener#notifyStudent
- Post-procesamiento de AccountCreditAllocatedEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación REGISTER_PAYMENT

- Validaciones (momentos): PAYMENT_REGISTRATION, CASH_PAYMENT_DATING, CASH_PAYMENT_PREVIEW
- Pre-procesamiento (en orden): IdempotencyKeyHandler
- Eventos que publica: PaymentApprovedEvent, AccountCreditAllocatedEvent, InvoicePaidDomainEvent
- Post-procesamiento de PaymentApprovedEvent: —
- Post-procesamiento de AccountCreditAllocatedEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación REJECT_PENDING_BANK_TRANSFER_PAYMENT

- Validaciones (momentos): —
- Pre-procesamiento (en orden): —
- Eventos que publica: —

## Operación REQUEST_ANNUAL_UPFRONT

- Validaciones (momentos): ANNUAL_UPFRONT_IDEMPOTENCY_KEY, ANNUAL_UPFRONT_TERMS, ANNUAL_UPFRONT_REQUEST
- Pre-procesamiento (en orden): IdempotencyKeyHandler
- Eventos que publica: —

## Operación REVERSE_CHARGEBACK

- Validaciones (momentos): —
- Pre-procesamiento (en orden): —
- Eventos que publica: ChargebackReversedEvent, AccountCreditAllocatedEvent, InvoicePaidDomainEvent
- Post-procesamiento de ChargebackReversedEvent: —
- Post-procesamiento de AccountCreditAllocatedEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación REVOKE_BANK_TRANSFER_APPROVAL

- Validaciones (momentos): PAYMENT_REJECTION, PAYMENT_APPROVAL_REVOCATION
- Pre-procesamiento (en orden): —
- Eventos que publica: BankTransferApprovalRevokedEvent, PaymentAllocationReversedEvent, AccountCreditAllocatedEvent, InvoicePaidDomainEvent
- Post-procesamiento de BankTransferApprovalRevokedEvent: —
- Post-procesamiento de PaymentAllocationReversedEvent: LateFeeOnPaymentAllocationReversedListener#reconcileLateFeesOfInvoiceWithReversedPayment
- Post-procesamiento de AccountCreditAllocatedEvent: —
- Post-procesamiento de InvoicePaidDomainEvent: LateFeeCorrectionOnPaymentReviewDecidedListener#retryCorrectionOfDebitNoteWhoseReceiptWasApproved, LateFeeOnInvoiceSettledListener#reconcileLateFeesOfSettledInvoice

## Operación REVOKE_STUDENT_DISCOUNT

- Validaciones (momentos): STUDENT_DISCOUNT_REVOCATION
- Pre-procesamiento (en orden): DirectorAuthorizationHandler
- Eventos que publica: —

## Operación SUBMIT_BANK_TRANSFER_RECEIPT

- Validaciones (momentos): BANK_TRANSFER_RECEIPT_IDEMPOTENCY_KEY, BANK_TRANSFER_RECEIPT_DETAILS, BANK_TRANSFER_RECEIPT_REGISTRATION, BANK_TRANSFER_RECEIPT_PROTECTION
- Pre-procesamiento (en orden): IdempotencyKeyHandler
- Eventos que publica: BankTransferReceiptSubmittedEvent
- Post-procesamiento de BankTransferReceiptSubmittedEvent: —
