# Auditoría 24.4b: cobertura de los `/me/**` sin id y de los «6 endpoints» (E24, #1697 S1)

Estado: vigente. Fecha de base: 28-nov-2026, `origin/develop`. Solo lectura: sin cambios de producción, de matriz ni de contrato. Insumo de S2 (tests de aislamiento) del Issue #1697.

## Método

1. Rutas: filas de `backend/src/test/resources/security/authorization-matrix.csv` cuyo `path` es `/me` o empieza con `/me/` y no contiene `{`. Son **66 filas** (el Issue hablaba de unas 51 más `GET|PUT /me`: la cuenta real incluye también los métodos de escritura y las rutas de matriz agregadas después).
2. Controller, método y fuente del principal: lectura de las anotaciones `@*Mapping` y de los servicios `Current*` / `CurrentActorFinder` que usa el controller o su caso de uso.
3. Test que cubre: se partió de `git grep -F '"<ruta>"'` en `backend/src/test` fuera de las matrices y se leyó el nombre del método de test que afirma el aislamiento. La columna *Nivel* dice qué tan fuerte es la cobertura (ver abajo). **Una referencia literal no es cobertura**: los tests que arman la ruta con constantes no aparecen en el grep.

Niveles:

- **A**: existe un test con dos personas (la dueña y otra) cuyo nombre y aserción afirman que la otra no ve ni modifica lo de la primera. Leído por nombre de método; la lectura completa del cuerpo queda como primer paso de S2.
- **F**: solo hay tests funcionales del caso feliz o de roles, sin segunda persona (o no se pudo confirmar que el test de ajeno ejecute *esta* ruta).
- **0**: ninguna referencia en `backend/src/test` fuera de las matrices; la matriz (`AuthorizationMatrixTest`, `MatrixKnownResponses`) solo verifica el código de estado por rol, no que el filtro por principal exista.

Veredicto de código: la ruta resuelve el principal desde el contexto de seguridad (`CurrentStudentService`, `CurrentInstructorService`, `CurrentPersonService`, `CurrentActorFinder` o un lector equivalente) y **nunca recibe un id de persona por parámetro**. Ninguna ruta de la lista toma `studentId`/`personId` del request. Eso no prueba que el filtro del especialista esté bien; ese es el objetivo de S2.

## Inventario de las 66 rutas

| Ruta | Roles que la permiten | Controller#método | Fuente del principal | Test que la cubre (clase#método) | Nivel |
|---|---|---|---|---|---|
| `GET /me` | todos los autenticados | PersonController#getOwnProfile | CurrentPersonService | DemoSeedIT; EmailChangeReverificationCharacterizationTest; LegalAcceptanceControllerTest; NoStoreResponseFilterTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `PUT /me` | todos los autenticados | PersonController#updateOwnProfile | CurrentPersonService | DemoSeedIT; EmailChangeReverificationCharacterizationTest; LegalAcceptanceControllerTest; NoStoreResponseFilterTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/announcements` | todos los autenticados | MeAnnouncementController#mine | CurrentStudentService | sin test | 0 |
| `GET /me/annual-upfront-requests` | ALUMNO | AnnualUpfrontRequestViewController#listRequestsOfCurrentStudent | CurrentStudentService | AnnualUpfrontRequestViewIT#theStudentListsOnlyHisOwnRequestsNewestFirstAndEnrollmentListFiltersByEnrollment | A |
| `POST /me/approved-subjects-certificates` | todos los autenticados | MeApprovedSubjectsCertificateController#request | caso de uso | ApprovedSubjectsEndToEndIT; ApprovedSubjectsProcedureEligibilityIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/attendance-change-requests` | DOCENTE | AttendanceChangeRequestController#mine | CurrentStudentService | AttendanceChangeRequestAndRiskCharacterizationTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/avatar` | todos los autenticados | PersonController#getAvatar | CurrentPersonService | PersonControllerTest; UploadedFileContentHttpIT; UploadedFileSizeLimitHttpIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `POST /me/avatar` | todos los autenticados | PersonController#uploadAvatar | CurrentPersonService | PersonControllerTest; UploadedFileContentHttpIT; UploadedFileSizeLimitHttpIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/bank-transfer-receipts` | todos los autenticados | MeInvoiceController#myReceipts | CurrentStudentService | BankTransferReceiptReviewQueueIT#aStudentNeverSeesAnotherStudentsReceiptNorItsDeclaredData | A |
| `POST /me/bank-transfer-receipts` | ALUMNO | BankTransferReceiptSubmissionController#submitOwnReceipt | CurrentStudentService | AbstractBankTransferReceiptHttpIT; BankTransferReceiptReviewQueueIT; CorsPreflightTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/calendar` | todos los autenticados | MeCalendarFeedController#myCalendar | CurrentStudentService | MeCalendarIntegrationTest#anotherStudentNeverSeesTheEventsOfTheOwner | A |
| `GET /me/calendar/export.ics` | todos los autenticados | MeCalendarFeedController#exportMyCalendar | CurrentStudentService | CalendarFeedSecurityIT#feedAndExportContainOnlyTheOwnersEvents | A |
| `GET /me/calendar/feed` | todos los autenticados | MeCalendarFeedController#myCalendarFeed | CurrentStudentService | CalendarFeedSecurityIT#feedAndExportContainOnlyTheOwnersEvents | A |
| `DELETE /me/calendar/feed/token` | todos los autenticados | MeCalendarFeedController#revokeMyCalendarFeedToken | CurrentStudentService | CalendarFeedSecurityIT#aStudentCannotSeeOrRegenerateTheTokenOfAnotherStudent | A |
| `POST /me/calendar/feed/token` | todos los autenticados | MeCalendarFeedController#rotateMyCalendarFeedToken | CurrentStudentService | CalendarFeedSecurityIT#aStudentCannotSeeOrRegenerateTheTokenOfAnotherStudent | A |
| `GET /me/certificates` | todos los autenticados | MeGraduationCertificateController#list | caso de uso `ListOwn…`/`GetOwn…` | MeGraduationCertificateHttpIT#studentListsOnlyTheirOwnCertificatesWithoutTheRevocationReason | A |
| `GET /me/contact-consents` | todos los autenticados | ContactConsentController#listContactConsents | CurrentActorFinder (caso de uso) | AccountCreationConsentIT; MeContactConsentControllerIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `PUT /me/contact-consents` | todos los autenticados | ContactConsentController#updateContactConsent | CurrentActorFinder (caso de uso) | MeContactConsentControllerIT#updatesOnlyOwnConsent | A |
| `GET /me/course-enrollment-applications` | todos los autenticados | MeEnrollmentController#myCourseApplications | CurrentStudentService | sin test | 0 |
| `GET /me/course-enrollment-preview` | todos los autenticados | MeCourseAdmissionPreviewController#previewCourseEnrollment | CurrentStudentService | MeCourseAdmissionPreviewBillingModeIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/course-enrollments` | todos los autenticados | MeCourseEnrollmentController#mine | CurrentStudentService | CourseEnrollmentWithTransferReceiptIT; DemoEducationalSeedIT; MultipartRequestSizeFilterTest; RegistrationFlowTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `POST /me/course-enrollments` | todos los autenticados | MeEnrollmentController#enrollInCourse | CurrentStudentService | CourseEnrollmentWithTransferReceiptIT; DemoEducationalSeedIT; MultipartRequestSizeFilterTest; RegistrationFlowTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/course-section-enrollments` | todos los autenticados | MeCourseSectionEnrollmentController#mine | CurrentStudentService | sin test | 0 |
| `GET /me/course-section-evaluation-configs` | DOCENTE | MeSectionEvaluationConfigController#myCourseSections | CurrentInstructorService | sin test | 0 |
| `GET /me/credit-balance` | ALUMNO | StudentCreditBalanceController#describeOwnCreditBalance | CurrentStudentService | RefundRequestIT#aStudentCannotCompleteNorFailARefundAndAnotherStudentCannotSeeTheirAccountThroughMe (idem) y InvoiceCancellationSupportIT (funcional) | F |
| `GET /me/credit-notes` | ALUMNO | CreditNoteController#listMyCreditNotes | CurrentStudentService | InvoiceCancellationIT; InvoiceCancellationSupportIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/documents` | todos los autenticados | ProgramEnrollmentApplicationController#myDocuments | CurrentStudentService | NoStoreResponseFilterTest; RegistrationFlowTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/enrollment-payment-status` | todos los autenticados | MeInvoiceController#myPaymentStatus | CurrentStudentService | sin test | 0 |
| `GET /me/exam-board-registrations` | todos los autenticados | MeExamBoardRegistrationController#mine | CurrentStudentService | ExamBoardEnrollmentFeeP9CharacterizationTest; MeExamBoardRegistrationControllerTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `POST /me/exam-board-registrations` | todos los autenticados | MeExamBoardRegistrationController#register | CurrentStudentService | ExamBoardEnrollmentFeeP9CharacterizationTest; MeExamBoardRegistrationControllerTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/exam-boards` | todos los autenticados | MeExamBoardRegistrationController#availableExamBoards | CurrentStudentService | sin test | 0 |
| `POST /me/legal-acceptances` | todos los autenticados | LegalDocumentController#acceptCurrentDocuments | caso de uso | LegalAcceptanceControllerTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `POST /me/legal-acceptances/guardian-confirmation/resend` | todos los autenticados | LegalDocumentController#resendGuardianConfirmation | caso de uso | LegalAcceptanceControllerTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/legal-acceptances/pending` | todos los autenticados | LegalDocumentController#getPendingAcceptances | caso de uso | LegalAcceptanceControllerTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/modules` | todos los autenticados | ModulesController#myModules | sin dato personal (rol del token) | PlatformModulesControllerIT (módulos por rol de la instalación; sin datos personales) | F |
| `GET /me/newsletter-preference` | todos los autenticados | LegalDocumentController#getNewsletterPreference | caso de uso | LegalAcceptanceControllerTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `PUT /me/newsletter-preference` | todos los autenticados | LegalDocumentController#updateNewsletterPreference | caso de uso | LegalAcceptanceControllerTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/notification-preferences` | todos los autenticados | PersonController#getNotificationPreferences | CurrentPersonService | sin test | 0 |
| `PUT /me/notification-preferences` | todos los autenticados | PersonController#updateNotificationPreferences | CurrentPersonService | sin test | 0 |
| `GET /me/notifications` | todos los autenticados | MeNotificationController#mine | CurrentActorFinder | sin test | 0 |
| `GET /me/notifications/unread-count` | todos los autenticados | MeNotificationController#unreadCount | CurrentActorFinder | sin test | 0 |
| `PUT /me/password` | todos los autenticados | PersonController#changePassword | CurrentPersonService | AuditedPasswordActionsIT; OwnProfileCharacterizationTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/payment-methods` | todos los autenticados | MyPaymentMethodsController#myPaymentMethods | sin dato personal (institución) | InstituteSettingsControllerIT (medios de pago de la institución; sin datos personales) | F |
| `GET /me/payments` | ALUMNO | StudentPaymentController#listOwnPayments | CurrentStudentService | CashPaymentRegistrationIT#theEndpointsAcceptOnlyTheirRolesAndAStudentSeesOnlyItsOwnPayments | A |
| `GET /me/procedures/approved` | todos los autenticados | MeProcedureController#approved | CurrentActorFinder (caso de uso) | ApprovedProceduresIntegrationTest#approvedListShowsOwnIssuedDocumentsAndNothingOfOtherStudents | A |
| `GET /me/program-enrollment-applications` | todos los autenticados | ProgramEnrollmentApplicationController#mine | CurrentStudentService | RegistrationFlowTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `POST /me/program-enrollment-applications` | todos los autenticados | ProgramEnrollmentApplicationController#request | CurrentStudentService | RegistrationFlowTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `POST /me/program-enrollment-applications/with-documents` | todos los autenticados | ProgramEnrollmentApplicationWithDocumentsController#requestWithDocuments | CurrentStudentService | MultipartRequestSizeFilterTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/program-enrollments` | todos los autenticados | MeEnrollmentController#myProgramEnrollments | CurrentStudentService | DemoEducationalSeedIT; DemoFullSeedIT; DemoFullSeedVolumesAndProfilesIT; DemoSeedIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/refund-eligibility` | ALUMNO | RefundPreviewController#describeOwnRefundEligibility | CurrentStudentService | RefundSupportIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/refund-requests` | ALUMNO | RefundController#listOwnRefundRequests | CurrentStudentService | RefundRequestIT#aStudentCannotCompleteNorFailARefundAndAnotherStudentCannotSeeTheirAccountThroughMe (cubre la cuenta ajena; no se verificó que liste este GET) | F |
| `POST /me/refund-requests` | ALUMNO | RefundController#requestOwnRefund | CurrentStudentService | RefundRequestIT; RefundSupportIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `POST /me/refund-requests/preview` | ALUMNO | RefundPreviewController#previewOwnRefund | CurrentStudentService | RefundSupportIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/refunds` | ALUMNO | RefundController#listOwnRefunds | CurrentStudentService | RefundListingIT#aStudentOnlySeesItsOwnRefundsAndNeverThoseOfAnotherStudent | A |
| `GET /me/regular-certificates` | todos los autenticados | MeRegularCertificateController#state | CurrentStudentService (caso de uso) | StudentRegularCertificateIntegrationTest#b112AnotherStudentCannotSeeRequestDownloadOrCancelWhatIsNotTheirs | A |
| `POST /me/regular-certificates` | todos los autenticados | MeRegularCertificateController#submit | CurrentStudentService (caso de uso) | StudentRegularCertificateIntegrationTest#b112AnotherStudentCannotSeeRequestDownloadOrCancelWhatIsNotTheirs | A |
| `GET /me/regular-certificates/eligibility` | todos los autenticados | MeRegularCertificateController#regularity | CurrentStudentService (caso de uso) | StudentRegularCertificateIntegrationTest#b117AnotherStudentGetsNotFoundWithoutRegularityDataForSomeoneElse | A |
| `GET /me/schedule` | todos los autenticados | MeAcademicRecordController#mySchedule | CurrentStudentService | MeScheduleIntegrationTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/section-enrollments` | todos los autenticados | MeEnrollmentController#mine | CurrentStudentService | DemoEducationalSeedIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `POST /me/section-enrollments` | todos los autenticados | MeEnrollmentController#enroll | CurrentStudentService | DemoEducationalSeedIT (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `GET /me/section-evaluation-configs` | DOCENTE | MeSectionEvaluationConfigController#mySections | CurrentInstructorService | SectionEvaluationConfigCharacterizationTest (referencias literales; funcional, sin segundo usuario confirmado) | F |
| `DELETE /me/sessions` | todos los autenticados | PersonController#revokeAllSessions | CurrentPersonService | sin test de la ruta sin id (OwnershipBoundaryHttpTest cubre solo `DELETE /me/sessions/{id}`) | 0 |
| `GET /me/sessions` | todos los autenticados | PersonController#getActiveSessions | CurrentPersonService | sin test de la ruta sin id (OwnershipBoundaryHttpTest cubre solo `DELETE /me/sessions/{id}`) | 0 |
| `GET /me/student-discounts` | ALUMNO | StudentDiscountController#listMyStudentDiscounts | CurrentStudentDiscountReader (caso de uso) | MyStudentDiscountsControllerIT#anotherStudentsDiscountsAreNeverListed | A |
| `GET /me/teaching-sections` | DOCENTE | MeTeachingSectionController#mine | CurrentInstructorService | sin test | 0 |
| `GET /me/waitlist-positions` | ALUMNO | MeWaitlistController#listOwnPositions | CurrentStudentService | OwnWaitlistPositionsIT#studentWithoutWaitingGetsEmptyListAndNeverOthersRows | A |

Totales: **17 con aislamiento (A)**, **36 solo funcional (F)**, **13 sin test (0)**.

## Lectura de los resultados

- **Sin test (13)**: `GET /me/announcements`, `/me/course-enrollment-applications`, `/me/course-section-enrollments`, `/me/course-section-evaluation-configs`, `/me/enrollment-payment-status`, `/me/exam-boards`, `GET|PUT /me/notification-preferences`, `GET /me/notifications`, `/me/notifications/unread-count`, `GET|DELETE /me/sessions` y `GET /me/teaching-sections`. Son el mínimo de S2. Las dos de docente (`teaching-sections`, `course-section-evaluation-configs`) y `enrollment-payment-status` (plata, lectura) son las de mayor riesgo.
- **Solo funcional (F)**: la mayoría tiene tests del caso feliz o de roles pero no un alumno B que verifique que no ve lo de A. S2 las recorre con el test genérico de dos personas (D2).
- **Con aislamiento (A)**: 17 rutas, sobre todo plata de lectura, constancias, calendario y consentimientos. Confirmado por el nombre y la aserción del método; el sabotaje del filtro por principal no se probó acá (S1 es de solo lectura).
- Ninguna ruta de la lista toma el id de la persona del request; no se encontró una ruta que devuelva datos ajenos por lectura de código. No se descarta nada del filtro del especialista: eso lo prueba S2.

## Los «6 endpoints del audit 24.4b»

El listado original no está en el repo. Se cubren las lecturas posibles (decisión D1 del Issue #1697).

### Lectura 1: la sección «Pendiente» de `audit-24-4-batch-3a-authz.md`

| # | Endpoint | Fila `ownership` hoy | Test negativo (ajeno) | ¿404 ajeno idéntico al de id inexistente? |
|---|---|---|---|---|
| 1 | `POST /grade-items/{id}/recovery` | `grade-item-instructor` | `AuthorizationMatrixOwnershipTest#instructorOfAnotherSectionGets404` (parametrizado por fila) | Probado solo con status 404. `OwnershipBoundaryHttpTest` compara ajeno contra inexistente, pero su `Resource` no incluye este endpoint: **sin test de igualdad de cuerpo** |
| 2 | `GET\|PUT /me/section-evaluation-configs/{sectionId}` y `/submit` | `section-instructor` | `AuthorizationMatrixOwnershipTest#instructorOfAnotherSectionGets404`; `OwnershipFailuresLookLikeMissingResourcesTest#notSectionInstructorIsIndistinguishableFromMissingSection` (usa rutas de un controller de prueba, no este endpoint) | **Sin test de igualdad de cuerpo sobre la ruta real** |
| 3 | `GET /me/course-enrollments/{id}/invoices` | `course-enrollment` | `AuthorizationMatrixOwnershipTest#ownerCanAccessAndNonOwnerGets404` | Sí: `OwnershipBoundaryHttpTest#foreignResourceLooksIdenticalToMissingOneForStudent` (`COURSE_ENROLLMENT_INVOICES`) |
| 4 | `GET /me/program-enrollments/{id}/invoices` | `program-enrollment` | idem | Sí: idem (`PROGRAM_ENROLLMENT_INVOICES`) |
| 5 | `POST /me/exam-board-registrations/{id}/withdraw` | `exam-board-registration` | `AuthorizationMatrixOwnershipTest#ownerCanAccessAndNonOwnerGets404` | **Sin test de igualdad de cuerpo** (`OwnershipFailuresLookLikeMissingResourcesTest` cubre mesas de examen, no registraciones) |
| 6 | `POST /me/section-enrollments/{id}/change-section` | `section-enrollment` | `EnrollmentControllerTest#anotherStudentGets404ChangingAnEnrollmentThatIsNotTheirs` y `AuthorizationMatrixOwnershipTest#ownerCanAccessAndNonOwnerGets404` | **Sin test de igualdad de cuerpo** |

Los 6 tienen fila `ownership` y negativo de status 404. La brecha restante de esta lectura es la igualdad de cuerpo en 1, 2, 5 y 6 (candidato a S2 o a un ítem aparte; no hay hallazgo de fuga de datos).

### Lectura 2: las rutas `/me/**` con `{}` y `ownership` vacío, más `POST /me/documents/{type}`

| Endpoint | Test negativo de ajeno encontrado |
|---|---|
| `POST /me/program-enrollment-applications/{id}/documents` | No se encontró por búsqueda literal (los tests arman la ruta con otra forma); verificar en S2 |
| `GET /me/regular-certificates/{id}/pdf` | `StudentRegularCertificateIntegrationTest#b112AnotherStudentCannotSeeRequestDownloadOrCancelWhatIsNotTheirs` |
| `DELETE /me/regular-certificate-requests/{id}` | idem (cancelar lo ajeno) |
| `GET /me/certificates/{id}/pdf` | `MeGraduationCertificateHttpIT#anotherStudentCannotDownloadAndNothingIsAudited` |
| `GET /me/procedures/approved/{documentType}/{id}/pdf` | `ApprovedProceduresIntegrationTest#anotherStudentGetsNotFoundWhenDownloadingSomeoneElsesDocument` |
| `POST /me/documents/{type}` | Sin test negativo propio: el tipo es un enum, no hay id de recurso en la URL; las ITs `OwnRegularDocumentIntegrationTest`, `OwnExamCertificateIT` y `ExamRegistrationIssuanceIT` lo usan en el caso feliz y por elegibilidad |

### Lectura 3 (nueva, del estado de auditoría)

`docs/security/audit-2026-11-03-status.md`, hallazgo A7 (abierto parcial), nombra como sin fila `ownership` a `POST /attendance/{id}/justify`, `POST /me/attendance/{id}/justify` y `/announcements/{id}` (PUT, DELETE, recipients). Nota: en `develop` actual `POST /me/attendance/{id}/justify` ya tiene `attendance-student` y `OwnershipBoundaryHttpTest#strangerStudentGetsNotFoundOnSelfServiceJustifyOfAForeignAttendanceSameAsMissing` lo cubre; el estado del A7 parece desactualizado. Es la tercera candidata a los «6».

### Cuál lectura es la correcta

No se puede decidir con lo que hay en el repo. La lectura 1 es la única que da exactamente 6 y es la que `audit-24-4-batch-3a-authz.md` deja explícitamente pendiente, así que se la toma como la probable. Pedido a Gabriel: confirmar o pasar el listado (decisión 1 del Issue #1697). Las tres quedaron cubiertas en este documento.

## Verificación de la lista de rutas

```
awk -F, 'NR>1 && ($2=="/me" || $2 ~ /^\/me\//) && $2 !~ /\{/ {print $1" "$2}' backend/src/test/resources/security/authorization-matrix.csv | sort > /tmp/csv-routes.txt
grep -oE '^\| `[A-Z]+ /me[^`]*`' docs/security/audit-24-4b-me-coverage.md | sed -E 's/^\| `//;s/`$//' | grep -v '{' | sort > /tmp/doc-routes.txt
comm -3 /tmp/csv-routes.txt /tmp/doc-routes.txt
```

La salida de `comm -3` está vacía.
