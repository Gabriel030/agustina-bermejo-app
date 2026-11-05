# Billing por modo: lecturas en MIRROR, escrituras en FULL

Billing tiene tres modos (`BillingMode`): `OFF`, `MIRROR` y `FULL`. Con `OFF` todos los endpoints de billing responden 404 salvo el webhook de Mercado Pago. Con `MIRROR` (modo espejo: se observa, no se factura ni se cobra) siguen disponibles las lecturas y las escrituras responden 404; con `FULL` funciona todo.

## Semántica de `@RequiresModule`

- `RequiresModuleInterceptor` busca la anotación primero en el método y, si no hay, en la clase: la del método reemplaza a la de la clase, no se combinan.
- Cada controller de billing declara `@RequiresModule(RequiresModule.BILLING)` en la clase (`minMode` implícito `MIRROR`).
- Cada método de escritura declara además `@RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)`.
- Si `ModuleGuard.permits` devuelve falso, el interceptor lanza `NoResourceFoundException`: en `MIRROR` una escritura responde el mismo 404 de "no disponible" que en `OFF`.

## Criterio

- Lectura (`READ`): todo `GET`, y los `POST` de vista previa, validación o simulación que no persisten (verificado en el servicio: transacción de solo lectura o ninguna escritura).
- Escritura (`WRITE`): todo `POST`/`PUT`/`PATCH`/`DELETE` que factura, cobra, emite, aprueba, descarta, corrige, reembolsa, genera corridas, otorga descuentos, registra pagos o modifica configuración.
- Excepción (`EXCEPTION_OFF`): el webhook de Mercado Pago, disponible siempre.

`BillingEndpointModeClassificationTest` falla si un endpoint de billing no figura en esta tabla, si la tabla tiene filas de endpoints que no existen, o si la anotación del método no coincide con la clasificación.

## Clasificación

| Controller | Método | Verbo | Ruta | Clasificación | Motivo |
|---|---|---|---|---|---|
| AnnualUpfrontQuoteController | quoteForCurrentStudent | GET | `/me/program-enrollments/{id}/annual-upfront/quote` | READ | Lectura |
| AnnualUpfrontQuoteController | quoteForStaff | GET | `/program-enrollments/{id}/annual-upfront/quote` | READ | Lectura |
| AnnualUpfrontRequestController | discardAnnualUpfrontRequest | POST | `/me/annual-upfront-requests/{id}/discard` | WRITE | Crea o descarta un pedido de pago anual adelantado |
| AnnualUpfrontRequestController | requestAnnualUpfront | POST | `/me/program-enrollments/{id}/annual-upfront-requests` | WRITE | Crea o descarta un pedido de pago anual adelantado |
| AnnualUpfrontRequestViewController | searchRequestsForStaff | GET | `/annual-upfront-requests` | READ | Lectura |
| AnnualUpfrontRequestViewController | viewRequestForStaff | GET | `/annual-upfront-requests/{id}` | READ | Lectura |
| AnnualUpfrontRequestViewController | listRequestsOfCurrentStudent | GET | `/me/annual-upfront-requests` | READ | Lectura |
| AnnualUpfrontRequestViewController | viewRequestOfCurrentStudent | GET | `/me/annual-upfront-requests/{id}` | READ | Lectura |
| AnnualUpfrontRequestViewController | listRequestsOfEnrollmentOfCurrentStudent | GET | `/me/program-enrollments/{id}/annual-upfront-requests` | READ | Lectura |
| AnnualUpfrontRequestViewController | listRequestsOfEnrollmentForStaff | GET | `/program-enrollments/{id}/annual-upfront-requests` | READ | Lectura |
| AuthorizationRequestController | list | GET | `/authorization-requests` | READ | Lectura |
| AuthorizationRequestController | get | GET | `/authorization-requests/{id}` | READ | Lectura |
| AuthorizationRequestController | approve | POST | `/authorization-requests/{id}/approve` | WRITE | Aprueba, rechaza o retira un pedido que ejecuta o descarta una operación de facturación |
| AuthorizationRequestController | reject | POST | `/authorization-requests/{id}/reject` | WRITE | Aprueba, rechaza o retira un pedido que ejecuta o descarta una operación de facturación |
| AuthorizationRequestController | withdraw | POST | `/authorization-requests/{id}/withdraw` | WRITE | Aprueba, rechaza o retira un pedido que ejecuta o descarta una operación de facturación |
| BankTransferReceiptController | listPending | GET | `/bank-transfer-receipts` | READ | Lectura |
| BankTransferReceiptController | approve | POST | `/bank-transfer-receipts/{id}/approve` | WRITE | Aprueba o rechaza un comprobante: registra o descarta un cobro |
| BankTransferReceiptController | file | GET | `/bank-transfer-receipts/{id}/file` | READ | Lectura |
| BankTransferReceiptController | reject | POST | `/bank-transfer-receipts/{id}/reject` | WRITE | Aprueba o rechaza un comprobante: registra o descarta un cobro |
| BankTransferReceiptSubmissionController | submitOwnReceipt | POST | `/me/bank-transfer-receipts` | WRITE | Carga un comprobante de transferencia: inicia el registro de un cobro |
| BankTransferReceiptSubmissionController | submitReceiptOnBehalfOfStudent | POST | `/students/{studentId}/bank-transfer-receipts` | WRITE | Carga un comprobante de transferencia: inicia el registro de un cobro |
| BillingConfigurationAlertController | list | GET | `/billing/configuration-alerts` | READ | Lectura |
| BillingMetricsController | getFirstMonth | GET | `/billing/metrics/first-month` | READ | Lectura |
| BillingMetricsController | getPanel | GET | `/billing/metrics/{panel}` | READ | Lectura |
| BillingPeriodController | create | POST | `/billing-periods` | WRITE | Configuración: crea el período de facturación |
| BillingPeriodController | current | GET | `/billing-periods/current` | READ | Lectura |
| BillingRunAlertController | list | GET | `/billing-run-alerts` | READ | Lectura |
| BillingRunController | list | GET | `/billing-runs` | READ | Lectura |
| BillingRunController | executeTodaysNightlyRun | POST | `/billing-runs/executions` | WRITE | Ejecuta la corrida nocturna de facturación |
| BillingRunController | find | GET | `/billing-runs/{nightlyRunId}` | READ | Lectura |
| BillingRunController | listItems | GET | `/billing-runs/{nightlyRunId}/steps/{step}/items` | READ | Lectura |
| CollectionReminderImpactController | getImpact | POST | `/collection-reminders/impact` | READ | Simulación de impacto de recordatorios: GetCollectionReminderImpactService solo calcula, no persiste |
| CollectionReminderScheduleController | listPolicies | GET | `/billing-configuration/collection-reminder-schedules` | READ | Lectura |
| CollectionReminderScheduleController | proposeGroupChange | POST | `/billing-configuration/collection-reminder-schedules/{group}/changes` | WRITE | Configuración: propone o quita cambios del cronograma de recordatorios de cobranza |
| CollectionReminderScheduleController | proposeExceptionChange | POST | `/billing-configuration/collection-reminder-schedules/{group}/exceptions/{targetId}/changes` | WRITE | Configuración: propone o quita cambios del cronograma de recordatorios de cobranza |
| CollectionReminderScheduleController | proposeExceptionRemoval | POST | `/billing-configuration/collection-reminder-schedules/{group}/exceptions/{targetId}/removal` | WRITE | Configuración: propone o quita cambios del cronograma de recordatorios de cobranza |
| CollectionReminderScheduleController | validateExceptionChange | POST | `/billing-configuration/collection-reminder-schedules/{group}/exceptions/{targetId}/validation` | READ | Validación de un cambio de cronograma: ValidateReminderScheduleChangeService no persiste |
| CollectionReminderScheduleController | listExceptionVersions | GET | `/billing-configuration/collection-reminder-schedules/{group}/exceptions/{targetId}/versions` | READ | Lectura |
| CollectionReminderScheduleController | validateGroupChange | POST | `/billing-configuration/collection-reminder-schedules/{group}/validation` | READ | Validación de un cambio de cronograma: ValidateReminderScheduleChangeService no persiste |
| CollectionReminderScheduleController | listGroupVersions | GET | `/billing-configuration/collection-reminder-schedules/{group}/versions` | READ | Lectura |
| CollectionReminderSettingsController | proposeOrPublishChange | POST | `/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS/changes` | WRITE | Configuración: propone o publica cambios de los recordatorios de cobranza |
| CollectionReminderSettingsController | validateChange | POST | `/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS/validation` | READ | Validación de un cambio de configuración: ValidateBillingConfigurationChangeService no persiste |
| CollectionReminderSettingsController | listVersions | GET | `/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS/versions` | READ | Lectura |
| CreditNoteController | issueOrRequestCreditNote | POST | `/invoices/{invoiceId}/credit-notes` | WRITE | Emite o solicita una nota de crédito |
| CreditNoteController | listMyCreditNotes | GET | `/me/credit-notes` | READ | Lectura |
| CreditNoteController | listCreditNotesOfStudent | GET | `/students/{studentId}/credit-notes` | READ | Lectura |
| CurrentAccountAllocationController | allocateCredit | POST | `/students/{studentId}/current-account/allocations` | WRITE | Aplica saldo a favor a facturas |
| DiscountDefinitionController | listDiscountDefinitions | GET | `/discount-definitions` | READ | Lectura |
| DiscountDefinitionController | createDiscountDefinition | POST | `/discount-definitions` | WRITE | Configuración: crea o cierra una promoción |
| DiscountDefinitionController | validateDiscountDefinition | POST | `/discount-definitions/validation` | READ | Validación de una promoción: ValidateDiscountDefinitionService no persiste |
| DiscountDefinitionController | findDiscountDefinition | GET | `/discount-definitions/{id}` | READ | Lectura |
| DiscountDefinitionController | closeDiscountDefinition | POST | `/discount-definitions/{id}/closure` | WRITE | Configuración: crea o cierra una promoción |
| EnrollmentPaymentStandingController | getCourseEnrollmentStanding | GET | `/course-enrollments/{id}/payment-standing` | READ | Lectura |
| EnrollmentPaymentStandingController | listCourseEnrollmentStandingHistory | GET | `/course-enrollments/{id}/payment-standing/history` | READ | Lectura |
| EnrollmentPaymentStandingController | getProgramEnrollmentStanding | GET | `/program-enrollments/{id}/payment-standing` | READ | Lectura |
| EnrollmentPaymentStandingController | listProgramEnrollmentStandingHistory | GET | `/program-enrollments/{id}/payment-standing/history` | READ | Lectura |
| FinancialDashboardController | getDashboard | GET | `/financial-dashboard` | READ | Lectura |
| FinancialDashboardController | getDebtors | GET | `/financial-dashboard/debtors` | READ | Lectura |
| FinancialDashboardController | getDebtorsPage | GET | `/financial-dashboard/debtors/page` | READ | Lectura |
| InvoiceCancellationController | cancelOrRequestInvoiceCancellation | POST | `/invoices/{invoiceId}/cancellations` | WRITE | Anula o solicita anular una factura |
| InvoiceCancellationController | previewInvoiceCancellation | POST | `/invoices/{invoiceId}/cancellations/preview` | READ | Vista previa de una anulación: PreviewInvoiceCancellationService es de solo lectura |
| InvoiceController | byCourse | GET | `/course-enrollments/{id}/invoices` | READ | Lectura |
| InvoiceController | priceBreakdown | GET | `/invoices/{id}/price-breakdown` | READ | Lectura |
| InvoiceController | byProgram | GET | `/program-enrollments/{id}/invoices` | READ | Lectura |
| LatePaymentSettingsController | create | POST | `/late-payment-settings` | WRITE | Configuración: crea la regla de mora |
| LatePaymentSettingsController | current | GET | `/late-payment-settings/current` | READ | Lectura |
| ManualMonthlyInvoiceIssuanceController | issueMonthlyInvoicesManually | POST | `/monthly-invoice-runs/manual-issuances` | WRITE | Emite facturas mensuales de forma manual |
| MeInvoiceController | myReceipts | GET | `/me/bank-transfer-receipts` | READ | Lectura |
| MeInvoiceController | myCourseInstallments | GET | `/me/course-enrollments/{id}/invoices` | READ | Lectura |
| MeInvoiceController | myPaymentStatus | GET | `/me/enrollment-payment-status` | READ | Lectura |
| MeInvoiceController | uploadReceipt | POST | `/me/invoices/{id}/bank-transfer-receipts` | WRITE | Genera la preferencia de pago de Mercado Pago o carga un comprobante: inicia un cobro |
| MeInvoiceController | createMpPreference | POST | `/me/invoices/{id}/mp-preference` | WRITE | Genera la preferencia de pago de Mercado Pago o carga un comprobante: inicia un cobro |
| MeInvoiceController | priceBreakdown | GET | `/me/invoices/{id}/price-breakdown` | READ | Lectura |
| MeInvoiceController | myProgramInstallments | GET | `/me/program-enrollments/{id}/invoices` | READ | Lectura |
| MonthlyInvoiceRunController | list | GET | `/monthly-invoice-runs` | READ | Lectura |
| MonthlyInvoiceRunController | find | GET | `/monthly-invoice-runs/{monthlyRunId}` | READ | Lectura |
| MonthlyInvoiceRunController | listDraftInvoiceGroups | GET | `/monthly-invoice-runs/{monthlyRunId}/draft-invoices` | READ | Lectura |
| MonthlyInvoiceRunController | listItems | GET | `/monthly-invoice-runs/{monthlyRunId}/items` | READ | Lectura |
| MonthlyInvoiceRunController | retryItem | POST | `/monthly-invoice-runs/{monthlyRunId}/items/{itemId}/retries` | WRITE | Reintenta un ítem de una corrida de facturación mensual |
| MonthlyInvoiceSimulationController | simulateMonthlyInvoices | POST | `/monthly-invoice-runs/simulations` | WRITE | Persiste una corrida de simulación (borra la anterior del período) aunque no emite facturas; igual que MonthlyInvoiceSimulationJob, que es FULL |
| MpWebhookController | webhook | POST | `/payments/mp-webhook` | EXCEPTION_OFF | Webhook de Mercado Pago: disponible en cualquier modo, guarda el evento crudo (PR 468) |
| OverdueContactListController | list | GET | `/collection-reminders/contact-list` | READ | Lectura |
| PaymentGatewayConfigController | getConfig | GET | `/payment-config` | READ | Lectura |
| PaymentGatewayConfigController | updateConfig | PUT | `/payment-config` | WRITE | Configuración: actualiza la pasarela de cobro o su QR |
| PaymentGatewayConfigController | uploadQr | POST | `/payment-config/qr` | WRITE | Configuración: actualiza la pasarela de cobro o su QR |
| PaymentGatewayConfigController | getQrImage | GET | `/payment-config/qr-image` | READ | Lectura |
| PaymentPlanOptionController | createForCourse | POST | `/courses/{id}/payment-plan-options` | WRITE | Configuración: crea opciones de plan de pago |
| PaymentPlanOptionController | createForProgram | POST | `/programs/{id}/payment-plan-options` | WRITE | Configuración: crea opciones de plan de pago |
| PaymentPlanSelectionController | selectForCourse | POST | `/course-enrollments/{id}/payment-plan` | WRITE | Elige el plan de pago de una matrícula: define cómo se factura |
| PaymentPlanSelectionController | selectForProgram | POST | `/program-enrollments/{id}/payment-plan` | WRITE | Elige el plan de pago de una matrícula: define cómo se factura |
| PaymentReversalController | reverseChargeback | POST | `/chargebacks/{chargebackId}/reversal` | WRITE | Revoca una aprobación o registra o revierte un contracargo |
| PaymentReversalController | revokeBankTransferApproval | POST | `/payments/{paymentId}/approval-revocation` | WRITE | Revoca una aprobación o registra o revierte un contracargo |
| PaymentReversalController | registerChargeback | POST | `/payments/{paymentId}/chargebacks` | WRITE | Revoca una aprobación o registra o revierte un contracargo |
| PaymentReversalPreviewController | previewChargebackReversal | POST | `/chargebacks/{chargebackId}/reversal/preview` | READ | Vista previa de la reversión de un contracargo: PreviewPaymentReversalService es de solo lectura |
| PaymentReversalPreviewController | previewApprovalRevocation | POST | `/payments/{paymentId}/approval-revocation/preview` | READ | Vista previa de la revocación de una aprobación: PreviewPaymentReversalService es de solo lectura |
| PaymentReversalPreviewController | previewChargebackRegistration | POST | `/payments/{paymentId}/chargebacks/preview` | READ | Vista previa de un contracargo: PreviewPaymentReversalService es de solo lectura |
| PriceController | cancelScheduledPriceVersion | POST | `/price-versions/{priceVersionId}/cancellation` | WRITE | Configuración: publica o cancela una versión de precio |
| PriceController | listPrices | GET | `/prices` | READ | Lectura |
| PriceController | listPriceVersionHistory | GET | `/prices/{priceId}/versions` | READ | Lectura |
| PriceController | publishPriceVersion | POST | `/prices/{priceId}/versions` | WRITE | Configuración: publica o cancela una versión de precio |
| PriceController | getPriceChangeImpact | POST | `/prices/{priceId}/versions/impact` | READ | Impacto de un cambio de precio: GetPriceChangeImpactService solo calcula, no persiste |
| PriceController | validatePriceChange | POST | `/prices/{priceId}/versions/validation` | READ | Validación de un cambio de precio: ValidatePriceChangeService es de solo lectura |
| ProgramYearBillingTemplateController | updateEntryFeeDiscount | PATCH | `/cohorts/{id}/entry-fee-discount` | WRITE | Configuración: crea o modifica plantillas y cohortes de facturación anual |
| ProgramYearBillingTemplateController | listProgramCohorts | GET | `/programs/{id}/cohorts` | READ | Lectura |
| ProgramYearBillingTemplateController | listBillingTemplates | GET | `/programs/{id}/year-billing-templates` | READ | Lectura |
| ProgramYearBillingTemplateController | create | POST | `/programs/{id}/year-billing-templates` | WRITE | Configuración: crea o modifica plantillas y cohortes de facturación anual |
| ProgramYearBillingTemplateController | listCohorts | GET | `/year-billing-templates/{id}/cohorts` | READ | Lectura |
| ProgramYearBillingTemplateController | createCohort | POST | `/year-billing-templates/{id}/cohorts` | WRITE | Configuración: crea o modifica plantillas y cohortes de facturación anual |
| ProgramYearBillingTemplateController | updateConfiguration | PATCH | `/year-billing-templates/{id}/configuration` | WRITE | Configuración: crea o modifica plantillas y cohortes de facturación anual |
| PromotionController | createForCourse | POST | `/courses/{id}/promotions` | WRITE | Configuración: crea o cierra una promoción de curso o programa |
| PromotionController | createForProgram | POST | `/programs/{id}/promotions` | WRITE | Configuración: crea o cierra una promoción de curso o programa |
| PromotionController | findActive | GET | `/promotions` | READ | Lectura |
| PromotionController | close | POST | `/promotions/{id}/close` | WRITE | Configuración: crea o cierra una promoción de curso o programa |
| PromotionController | lostBenefits | GET | `/reports/billing/promotions/lost-benefits` | READ | Lectura |
| RefundController | listOwnRefundRequests | GET | `/me/refund-requests` | READ | Lectura |
| RefundController | requestOwnRefund | POST | `/me/refund-requests` | WRITE | Solicita, completa o falla un reembolso |
| RefundController | listOwnRefunds | GET | `/me/refunds` | READ | Lectura |
| RefundController | completeRefund | POST | `/refunds/{refundId}/completion` | WRITE | Solicita, completa o falla un reembolso |
| RefundController | failRefund | POST | `/refunds/{refundId}/failure` | WRITE | Solicita, completa o falla un reembolso |
| RefundController | listRefundRequestsOfStudent | GET | `/students/{studentId}/refund-requests` | READ | Lectura |
| RefundController | requestRefundOnBehalfOfStudent | POST | `/students/{studentId}/refund-requests` | WRITE | Solicita, completa o falla un reembolso |
| RefundController | listRefundsOfStudent | GET | `/students/{studentId}/refunds` | READ | Lectura |
| RefundPreviewController | describeOwnRefundEligibility | GET | `/me/refund-eligibility` | READ | Lectura |
| RefundPreviewController | previewOwnRefund | POST | `/me/refund-requests/preview` | READ | Vista previa de un reembolso: PreviewRefundService es de solo lectura |
| RefundPreviewController | describeRefundEligibilityOfStudent | GET | `/students/{studentId}/refund-eligibility` | READ | Lectura |
| RefundPreviewController | previewRefundOnBehalfOfStudent | POST | `/students/{studentId}/refund-requests/preview` | READ | Vista previa de un reembolso: PreviewRefundService es de solo lectura |
| StudentBillingOverviewController | billingOverview | GET | `/students/{id}/billing-overview` | READ | Lectura |
| StudentCreditBalanceController | describeOwnCreditBalance | GET | `/me/credit-balance` | READ | Lectura |
| StudentCreditBalanceController | describeCreditBalanceOfStudent | GET | `/students/{studentId}/credit-balance` | READ | Lectura |
| StudentDiscountController | listForCourseEnrollment | GET | `/course-enrollments/{id}/student-discounts` | READ | Lectura |
| StudentDiscountController | grantForCourseEnrollment | POST | `/course-enrollments/{id}/student-discounts` | WRITE | Otorga o revoca un descuento a un alumno: modifica lo que se factura |
| StudentDiscountController | previewForCourseEnrollment | POST | `/course-enrollments/{id}/student-discounts/preview` | READ | Vista previa de un descuento: PreviewStudentDiscountGrantService es de solo lectura |
| StudentDiscountController | listMyStudentDiscounts | GET | `/me/student-discounts` | READ | Lectura |
| StudentDiscountController | listForProgramEnrollment | GET | `/program-enrollments/{id}/student-discounts` | READ | Lectura |
| StudentDiscountController | grantForProgramEnrollment | POST | `/program-enrollments/{id}/student-discounts` | WRITE | Otorga o revoca un descuento a un alumno: modifica lo que se factura |
| StudentDiscountController | previewForProgramEnrollment | POST | `/program-enrollments/{id}/student-discounts/preview` | READ | Vista previa de un descuento: PreviewStudentDiscountGrantService es de solo lectura |
| StudentDiscountController | searchStudentDiscounts | GET | `/student-discounts` | READ | Lectura |
| StudentDiscountController | revokeStudentDiscount | POST | `/student-discounts/{id}/revocation` | WRITE | Otorga o revoca un descuento a un alumno: modifica lo que se factura |
| StudentDiscountController | listForStudent | GET | `/students/{studentId}/student-discounts` | READ | Lectura |
| StudentPaymentController | listOwnPayments | GET | `/me/payments` | READ | Lectura |
| StudentPaymentController | registerCashPayment | POST | `/students/{studentId}/cash-payments` | WRITE | Registra un pago en efectivo |
| StudentPaymentController | previewCashPayment | POST | `/students/{studentId}/cash-payments/preview` | READ | Vista previa de un pago en efectivo: PreviewCashPaymentService es de solo lectura |
| StudentPaymentController | listPaymentsOfStudent | GET | `/students/{studentId}/payments` | READ | Lectura |
