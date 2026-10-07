# Catálogo de validaciones de facturación

Generado por `PolicyCatalogTest`; no se edita a mano. Para regenerarlo: `./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true` y commitear.

## Momento INVOICE_ISSUING

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | ReasonFeeTypePolicy | FEE_TYPE_NOT_ALLOWED_FOR_REASON | D-050 |
| 2 | DueDateNotBeforeIssueDatePolicy | DUE_DATE_BEFORE_ISSUE_DATE | D-057 |
| 3 | EnrollmentFeeSlotRequiredPolicy | ENROLLMENT_FEE_SLOT_REQUIRED | D-002, D-009 |
| 4 | OfferingCurrencyPolicy | CURRENCY_MISMATCH | D-097 |
| 5 | NotAlreadyIssuedPolicy | ALREADY_ISSUED | D-009 |
| 6 | EnrollmentFeeSlotWithinTemplateCountPolicy | ENROLLMENT_FEE_SLOT_NOT_CONFIGURED | D-009, D-036 |

## Momento LATE_PAYMENT_SETTINGS

| Orden | Policy | Códigos de violación | Decisiones |
|---|---|---|---|
| 1 | GracePeriodNotNegativePolicy | NEGATIVE_GRACE_PERIOD | D-147 |
| 2 | SuspensionDaysNotNegativePolicy | NEGATIVE_SUSPENSION_DAYS | D-147 |
| 3 | GracePeriodWithinSuspensionDaysPolicy | GRACE_PERIOD_EXCEEDS_SUSPENSION_DAYS | D-147 |
| 4 | SuspensionInvoiceCountAtLeastOnePolicy | SUSPENSION_INVOICE_COUNT_BELOW_ONE | D-147 |
| 5 | EffectiveDateNotRetroactivePolicy | RETROACTIVE_EFFECTIVE_DATE | D-059 |

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
