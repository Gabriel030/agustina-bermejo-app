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
