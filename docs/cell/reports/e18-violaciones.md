# E18 Fase 3 - M0: punto de partida de la modularización (violaciones de Spring Modulith)

Medido sobre `origin/develop` @ `d9d94b2cf` el 23-nov-2026, con `ApplicationModules.detectViolations()` (Spring Modulith 1.4.13), el mismo verificador que usa `ModularityTest`. Todas las cifras de este documento salen de esa ejecución; nada está estimado.

## Resumen

- **293 violaciones de módulo** (mensajes de `detectViolations()` que no son ciclos) en **65 clases origen**; **50 mensajes de ciclo** (`Cycle detected`) que atraviesan **14 pares de módulos** (tabla (d)).
- **79** de las 293 van **hacia arriba** según las capas de `docs/context/modules.md`; el resto son accesos a internos de un módulo de capa inferior (se arreglan con API pública, no con inversión).
- Concentración: `billing -> enrollment` (143), `enrollment -> billing` (66, todas hacia arriba), `billing -> academicstructure` (41). Entre las tres suman 250 de 293 (85 %).

### Drift de la baseline

La baseline de develop **no coincidía** con la realidad: `ModularityTest` pasaba, pero imprimía entradas sobrantes (la baseline solo impide agregar, no obliga a bajar). Se regeneró en un commit aparte (`test(arquitectura): regenerar línea base de Modulith`), sin agregar ninguna violación:

| entrada | antes | ahora |
|---|---|---|
| billing -> academicstructure | 81 | 41 |
| billing -> identity | 10 | 2 |
| enrollment -> academicstructure | 2 | 0 |
| enrollment -> identity | 9 | 0 |
| graduation -> billing | 21 | 13 |
| ciclos con graduation (6 pares) | presentes | ausentes |

Los archivos vacíos (`academicrecord`, `attendance`, `notification`) los borra el escritor de la baseline; `ViolationBaselineFiles.read` los trata como vacíos.

### Diferencia con las cifras del plan y evolución

Se midió también el commit que cita `docs/context/modules.md` (`cd1ede809`, 31-oct-2026, merge del PR #436) con el mismo `ViolationDumpTest` y el mismo script, en un worktree temporal (con Maven en línea: ese commit usa Spring Boot 3.5.16, que no estaba en la caché local). Compila y corre sin cambios.

| métrica | cd1ede809 (31-oct) | hoy (d9d94b2cf, 23-nov) | plan |
|---|---|---|---|
| mensajes totales | 365 | 343 | - |
| violaciones (mensajes) | 315 | 293 | 3.574 |
| ciclos (mensajes) | 50 | 50 | 100 |
| accesos puntuales (`in (Clase.java:N)`) | 237 | 214 | 3.474 |
| líneas de detalle dentro de ciclos | 2.562 | 2.767 | - |
| detalle total (puntuales + ciclos) | 2.799 | 2.981 | - |
| clases origen distintas | 66 | 65 | 302 |
| hacia arriba | 78 | 79 | - |
| destino repository | 164 | 160 | 572 |
| destino exception | 40 | 41 | 266 |
| destino service | 36 | 28 | 383 |
| destino entity | 29 | 27 | 2.128 |
| destino usecase | 13 | 3 | dto/usecase 121 |
| destino dto | 9 | 8 | dto/usecase 121 |
| destino api | 9 | 13 | - |

Conclusiones:

- Las cifras del plan **no se reproducen ni siquiera en el commit que cita**: ahí el verificador da 315 violaciones y 50 ciclos. Por lo tanto la diferencia no es de fecha: el plan midió otra cosa (con otra definición de "violación", p. ej. un conteo por acceso incluyendo duplicados entre ciclos y violaciones, o un desglose de otra herramienta). Con este verificador ninguna combinación de las columnas medidas da 3.574 ni 3.474.
- Desde el 31-oct a hoy bajaron: `graduation -> billing` 21 a 13 (usecase/service), `enrollment -> identity` 9 a 0, `billing -> identity` 10 a 2, `enrollment -> academicstructure` 2 a 0. Subió `billing -> academicstructure` de 37 a 41. `enrollment -> billing` pasó de 65 a 66; el resto de la matriz de origen-destino no cambió.
- La baseline versionada ya estaba desfasada en el commit del plan (decía `billing -> academicstructure` 81 contra 37 medido), por lo que el drift es anterior a esta medición.

## Cómo regenerar

```bash
cd backend
./mvnw -o -q test -Dtest=ViolationDumpTest -Dmodulith.violations.dump=/ruta/raw.txt
python3 -I ../scripts/modulith-violations-report.py /ruta/raw.txt > /ruta/informe.md
```

`ViolationDumpTest` solo corre con la propiedad `modulith.violations.dump` y escribe un mensaje por línea (saltos de línea reemplazados por ` | `). El script parsea esas líneas: tipo destino = primer subpaquete conocido (`entity`, `repository`, `service`, `exception`, `dto`, `usecase`; si no, el nombre del subpaquete, p. ej. `api`, `events`, `invoice`); "arriba" = la capa del origen es menor que la del destino en `shared < identity < academicstructure < enrollment < attendance < evaluation < billing < graduation < announcement < notification`. Los módulos de composición (`academicrecord`, `dataimport`, `demo`, `config`, `platform`) no aparecen como origen ni destino en la salida actual. La clase origen de los accesos por constructor/campo se toma del archivo del mensaje (`in (Clase.java)`).

Las secciones (a) a (e) de abajo son la salida literal del script (incluye los totales de mensajes, no parseados y líneas de detalle). Para medir otro commit: `git worktree add --detach <dir> <commit>`, copiar `ViolationDumpTest` y correr los mismos comandos.

## (a) a (e) Datos medidos

### Totales

- Mensajes totales: 343 = 293 violaciones + 50 ciclos
- Mensajes no parseados: 0
- Accesos puntuales (líneas `in (Clase.java:N)` de violaciones): 214
- Líneas de detalle dentro de ciclos: 2767
- Detalle total (puntuales + ciclos): 2981
- Violaciones de módulo (mensajes): 293
- Ciclos entre módulos (mensajes Cycle detected): 50
- Clases origen distintas: 65
- Pares (clase origen, tipo destino) distintos: 148
- Hacia arriba: 79

### Por tipo de destino

| tipo | cantidad |
|---|---|
| repository | 160 |
| exception | 41 |
| service | 28 |
| entity | 27 |
| api | 13 |
| dto | 8 |
| invoice | 8 |
| usecase | 3 |
| events | 3 |
| planning | 2 |

### Matriz origen -> destino

| origen | destino | cantidad | sentido | desglose por tipo |
|---|---|---|---|---|
| billing | enrollment | 143 | abajo | repository 112, exception 21, service 9, dto 1 |
| enrollment | billing | 66 | arriba | entity 15, api 11, exception 9, service 8, invoice 8, dto 5, repository 4, usecase 3, events 3 |
| billing | academicstructure | 41 | abajo | repository 36, exception 5 |
| graduation | billing | 13 | abajo | service 9, exception 3, planning 1 |
| announcement | billing | 8 | abajo | repository 4, entity 4 |
| billing | evaluation | 7 | abajo | repository 4, entity 2, exception 1 |
| academicstructure | evaluation | 4 | arriba | entity 4 |
| academicstructure | billing | 2 | arriba | dto 2 |
| billing | identity | 2 | abajo | exception 2 |
| enrollment | attendance | 2 | arriba | api 2 |
| evaluation | billing | 2 | arriba | service 1, planning 1 |
| identity | enrollment | 2 | arriba | entity 2 |
| academicstructure | enrollment | 1 | arriba | service 1 |

### Top 20 clases origen

| módulo | clase | cantidad |
|---|---|---|
| billing | InvoicePlanningContextLoader | 15 |
| billing | SuspendedEnrollmentClosureService | 15 |
| billing | InvoiceViewLoader | 12 |
| billing | AdHocInvoiceRequestResolver | 12 |
| billing | MonthlyInstallmentRowProcessor | 12 |
| enrollment | ProgramEnrollmentService | 11 |
| billing | RecurringFeeWindowService | 10 |
| billing | CreateProgramYearBillingTemplateService | 10 |
| billing | StudentBillingOverviewReader | 9 |
| enrollment | CourseEnrollmentApplicationService | 9 |
| announcement | ScheduledAnnouncementDispatchJob | 8 |
| billing | EnrollmentPaymentStandingService | 8 |
| billing | MeInvoiceController | 8 |
| billing | FinancialDashboardReader | 8 |
| graduation | GraduationDebtChecker | 8 |
| enrollment | ProgramEnrollmentApplicationResponse | 7 |
| billing | EnrollmentFeeRequirementService | 6 |
| billing | CoursePaymentPlanOptionCreation | 5 |
| billing | ProgramPaymentPlanOptionCreation | 5 |
| billing | OwnCourseEnrollmentBillingStatusReader | 5 |

### Ciclos por par de módulos

| par | ciclos que lo atraviesan |
|---|---|
| billing<->evaluation | 22 |
| enrollment<->identity | 22 |
| academicstructure<->billing | 20 |
| billing<->enrollment | 20 |
| academicstructure<->evaluation | 20 |
| academicstructure<->enrollment | 18 |
| attendance<->enrollment | 16 |
| academicstructure<->attendance | 14 |
| attendance<->evaluation | 12 |
| enrollment<->evaluation | 6 |
| evaluation<->identity | 6 |
| attendance<->identity | 5 |
| billing<->identity | 5 |
| academicstructure<->identity | 5 |

### Dependencias hacia arriba (origen, clase, destino, tipo, cantidad)

| módulo origen | clase origen | módulo destino | tipo destino | cantidad |
|---|---|---|---|---|
| academicstructure | CourseController | billing | dto | 1 |
| academicstructure | ProgramController | billing | dto | 1 |
| academicstructure | CourseController | enrollment | service | 1 |
| academicstructure | CourseSubjectSection | evaluation | entity | 1 |
| academicstructure | SubjectSection | evaluation | entity | 1 |
| academicstructure | CourseSectionService | evaluation | entity | 1 |
| academicstructure | SubjectSectionService | evaluation | entity | 1 |
| enrollment | ProgramEnrollmentService | attendance | api | 2 |
| enrollment | ProgramEnrollmentApplicationResponse | billing | invoice | 4 |
| enrollment | ProgramEnrollmentService | billing | exception | 4 |
| enrollment | ProgramEnrollmentController | billing | dto | 3 |
| enrollment | CourseEnrollmentApplicationService | billing | invoice | 3 |
| enrollment | GrantEnrollmentFeeExemptionTransaction | billing | exception | 3 |
| enrollment | BillingEnrollmentFactsFinder | billing | api | 2 |
| enrollment | CourseEnrollmentController | billing | dto | 2 |
| enrollment | CourseEnrollmentApplicationResponse | billing | entity | 2 |
| enrollment | ProgramEnrollmentApplicationResponse | billing | api | 2 |
| enrollment | ProgramEnrollment | billing | entity | 2 |
| enrollment | AdmissionInvoiceReceiptFinder | billing | api | 2 |
| enrollment | CourseEnrollmentApplicationService | billing | service | 2 |
| enrollment | CourseEnrollmentApplicationService | billing | exception | 2 |
| enrollment | ProgramEnrollmentService | billing | repository | 2 |
| enrollment | ProgramEnrollmentService | billing | entity | 2 |
| enrollment | EnrollmentReminderTargetFinder | billing | api | 1 |
| enrollment | EnrollmentReminderTargetFinder | billing | entity | 1 |
| enrollment | CourseEnrollmentController | billing | usecase | 1 |
| enrollment | ProgramEnrollmentController | billing | usecase | 1 |
| enrollment | EnrollmentFeeExemptionResponse | billing | entity | 1 |
| enrollment | EnrollmentFeeObligationResponse | billing | api | 1 |
| enrollment | EnrollmentFeeObligationResponse | billing | service | 1 |
| enrollment | ProgramEnrollmentApplicationResponse | billing | entity | 1 |
| enrollment | CourseEnrollmentApplication | billing | entity | 1 |
| enrollment | EnrollmentFeeExemption | billing | entity | 1 |
| enrollment | ProgramEnrollmentApplication | billing | entity | 1 |
| enrollment | ProgramEnrollmentApplication | billing | invoice | 1 |
| enrollment | CourseEnrollmentApplicationRepository | billing | entity | 1 |
| enrollment | AdmissionEnrollmentStalledNotifier | billing | events | 1 |
| enrollment | ApproveProgramEnrollmentApplicationTransaction | billing | service | 1 |
| enrollment | CourseEnrollmentAdmissionPaymentListener | billing | events | 1 |
| enrollment | CourseEnrollmentApplicationService | billing | usecase | 1 |
| enrollment | CourseEnrollmentApplicationService | billing | entity | 1 |
| enrollment | CourseEnrollmentNonpaymentWithdrawalAdapter | billing | api | 1 |
| enrollment | CourseEnrollmentService | billing | api | 1 |
| enrollment | EnrollmentFeeReader | billing | service | 1 |
| enrollment | GrantEnrollmentFeeExemptionTransaction | billing | repository | 1 |
| enrollment | GrantEnrollmentFeeExemptionTransaction | billing | entity | 1 |
| enrollment | ProgramApplicationRegistrar | billing | service | 1 |
| enrollment | ProgramEnrollmentAdmissionPaymentListener | billing | service | 1 |
| enrollment | ProgramEnrollmentAdmissionPaymentListener | billing | events | 1 |
| enrollment | ProgramEnrollmentService | billing | api | 1 |
| enrollment | SubjectSectionEnrollmentService | billing | repository | 1 |
| enrollment | SubjectSectionEnrollmentService | billing | service | 1 |
| evaluation | ExamBoardRegistrationManager | billing | service | 1 |
| evaluation | ExamBoardRegistrationManager | billing | planning | 1 |
| identity | Student | enrollment | entity | 2 |

### Tipos destino hacia arriba (para agrupar procesos)

| origen | destino | tipo | cantidad |
|---|---|---|---|
| academicstructure | billing | CoursePricingResponse | 1 |
| academicstructure | billing | ProgramPricingResponse | 1 |
| academicstructure | enrollment | CourseEnrollmentService | 1 |
| academicstructure | evaluation | EvaluationScheme | 4 |
| enrollment | attendance | AttendanceStandingReader | 1 |
| enrollment | attendance | AttendanceStanding | 1 |
| enrollment | billing | ProgramYearBillingTemplate | 5 |
| enrollment | billing | ProgramCohort | 4 |
| enrollment | billing | Invoice | 4 |
| enrollment | billing | BankTransferReceiptStatus | 3 |
| enrollment | billing | AdmissionReceiptSummary | 3 |
| enrollment | billing | AdmissionInvoicePaidEvent | 3 |
| enrollment | billing | EnrollmentPaymentStandingQuery | 2 |
| enrollment | billing | CreateAdHocInvoiceRequest | 2 |
| enrollment | billing | InvoiceResponse | 2 |
| enrollment | billing | BankTransferReceipt | 2 |
| enrollment | billing | EnrollmentDebtGuard | 2 |
| enrollment | billing | EnrollmentFeeRequirementService | 2 |
| enrollment | billing | ProgramYearBillingTemplateRepository | 2 |
| enrollment | billing | ProgramYearBillingTemplateNotFoundException | 2 |
| enrollment | billing | ProgramYearBillingTemplateMismatchException | 2 |
| enrollment | billing | EnrollmentPaymentStandingView | 1 |
| enrollment | billing | EnrollmentPaymentStatus | 1 |
| enrollment | billing | CreateAdHocInvoiceForCourseEnrollmentUseCase | 1 |
| enrollment | billing | CreateAdHocInvoiceForProgramEnrollmentUseCase | 1 |
| enrollment | billing | AdvanceBillingYearRequest | 1 |
| enrollment | billing | AdvanceEnrollmentFeeView | 1 |
| enrollment | billing | EnrollmentFeeRequirementService$Obligation | 1 |
| enrollment | billing | InvoiceAmounts | 1 |
| enrollment | billing | InvoicePaymentStatus | 1 |
| enrollment | billing | AdmissionReceiptLookup | 1 |
| enrollment | billing | ProgramAdmissionInvoiceIssuer | 1 |
| enrollment | billing | InvoiceIssuingService | 1 |
| enrollment | billing | SubmitCourseAdmissionReceiptUseCase | 1 |
| enrollment | billing | InvoiceIssuingResult$InvoiceIssuingRejected | 1 |
| enrollment | billing | InvoiceIssuingRequestFactory | 1 |
| enrollment | billing | InvoiceIssuingResult$InvoiceIssued | 1 |
| enrollment | billing | ChargeRejectedException | 1 |
| enrollment | billing | DuplicateChargeException | 1 |
| enrollment | billing | CourseEnrollmentNonpaymentWithdrawalPort | 1 |
| enrollment | billing | InvalidEnrollmentFeeConfigurationException | 1 |
| enrollment | billing | ProgramCohortService | 1 |
| enrollment | billing | ProgramEnrollmentBillingSetup | 1 |
| enrollment | billing | ProgramCohortRepository | 1 |
| enrollment | billing | CohortAdmissionWindowClosedException | 1 |
| enrollment | billing | ProgramCohortNotFoundException | 1 |
| enrollment | billing | InvoiceRepository | 1 |
| evaluation | billing | EnrollmentFeeRequirementService | 1 |
| evaluation | billing | EnrollmentFeeRequirementScope | 1 |
| identity | enrollment | CourseEnrollment | 1 |
| identity | enrollment | ProgramEnrollment | 1 |

## (f) Procesos de negocio que cruzan módulos hacia arriba

Agrupación propuesta a partir de las clases y tipos de la tabla anterior. La columna "corte sugerido" es una hipótesis a validar en el diseño de M3-M5, no una decisión.

| # | Proceso | Cruce (origen -> destino) | Clases implicadas (origen) y tipos destino | Corte sugerido |
|---|---|---|---|---|
| P1 | Alta, admisión y aprobación de inscripción / pago de admisión | enrollment -> billing | `ProgramEnrollmentService`, `CourseEnrollmentApplicationService`, `ProgramApplicationRegistrar`, `ApproveProgramEnrollmentApplicationTransaction`, `ProgramEnrollmentAdmissionPaymentListener`, `CourseEnrollmentAdmissionPaymentListener`, `AdmissionInvoiceReceiptFinder`, `AdmissionEnrollmentStalledNotifier`; destinos: `AdmissionInvoicePaidEvent`, `InvoiceIssuingService`, `ProgramAdmissionInvoiceIssuer`, `SubmitCourseAdmissionReceiptUseCase`, `AdmissionReceiptLookup`, `BankTransferReceipt` | evento de enrollment ("inscripción aprobada/creada") que billing escucha para emitir; puerto de lectura de comprobantes en `enrollment.api` implementado por billing |
| P2 | Baja de alumno / inscripción por falta de pago | enrollment -> billing | `CourseEnrollmentNonpaymentWithdrawalAdapter` (`CourseEnrollmentNonpaymentWithdrawalPort`), `ProgramEnrollmentService` (`EnrollmentDebtGuard`, `EnrollmentPaymentStandingQuery/View`, `EnrollmentPaymentStatus`), `CourseEnrollmentApplicationService`, `EnrollmentReminderTargetFinder` | HECHO (M4-S1): puertos de deuda y de estado de pago en `enrollment.api` (`EnrollmentDebtGuardPort`, `EnrollmentBillingStandingPort`) implementados en billing; `CourseEnrollmentNonpaymentWithdrawalPort` en `shared.module` como paso intermedio (`CourseEnrollmentApplicationService` pertenece a P1/P3, no se tocó) |
| P3 | Alta y cambio de cohorte / plantilla de facturación del año | enrollment -> billing | `ProgramEnrollmentService`, `SubjectSectionEnrollmentService`, `CourseEnrollmentApplicationService`; destinos: `ProgramCohort`, `ProgramCohortService`, `ProgramCohortRepository`, `ProgramYearBillingTemplate(Repository)`, `ProgramEnrollmentBillingSetup`, excepciones `ProgramCohort*`/`ProgramYearBillingTemplate*`, `CohortAdmissionWindowClosedException` | API pública `billing.api` con un caso de consulta/alta de cohorte (devolviendo VO propios) y excepciones de dominio expuestas en `model`; es el mayor tipo repetido (`ProgramYearBillingTemplate` 5, `ProgramCohort` 4) |
| P4 | Matrícula (`EnrollmentFee`): requisito, exención y respuestas | enrollment -> billing, evaluation -> billing | `GrantEnrollmentFeeExemptionTransaction`, `EnrollmentFeeReader`, `EnrollmentFeeExemption`, `EnrollmentFeeExemptionResponse`, `EnrollmentFeeObligationResponse`, `InvalidEnrollmentFeeConfigurationException`; `ExamBoardRegistrationManager` (evaluation) -> `EnrollmentFeeRequirementService/Scope`; destinos: `AdvanceEnrollmentFeeView`, `AdvanceBillingYearRequest` | puerto de matrícula en `billing.api` (ya hay un patrón en `EnrollmentDebtGuard`); los DTO de respuesta de enrollment dejan de incrustar entidades/DTO de billing |
| P5 | Facturas ad hoc, comprobantes y vistas de inscripción | enrollment -> billing (controllers/DTO) | `ProgramEnrollmentController`, `CourseEnrollmentController`, `ProgramEnrollmentApplicationResponse` (incluye `AdmissionInvoiceSummary` con `Invoice`, `InvoiceAmounts`, `InvoicePaymentStatus`), `CourseEnrollmentApplicationResponse`, `CourseEnrollmentApplication`, `ProgramEnrollmentApplication`, `CourseEnrollmentApplicationRepository`; destinos: `CreateAdHocInvoiceRequest`, `InvoiceResponse`, `CreateAdHocInvoiceFor*UseCase`, `InvoiceRepository` | mover esos endpoints a billing (billing ya consume enrollment hacia abajo) o devolver vistas por `billing.api`; las entidades `Invoice`/`BankTransferReceipt` dentro de entidades de enrollment (`CourseEnrollmentApplication`, `ProgramEnrollmentApplication`) se reemplazan por ids |
| P6 | Estructura académica -> precios y evaluación | academicstructure -> billing / enrollment / evaluation | `CourseController`, `ProgramController` (`getPricing` -> `CoursePricingResponse`, `ProgramPricingResponse`), `CourseController` -> `CourseEnrollmentService`, `CourseSubjectSection`, `SubjectSection`, `CourseSectionService`, `SubjectSectionService` -> `EvaluationScheme` | mover los endpoints de pricing a billing; para `EvaluationScheme` (4 entradas) reemplazar la relación por id o evento de configuración |
| P7 | Identity -> inscripciones | identity -> enrollment (2) | `Student` -> `CourseEnrollment`, `ProgramEnrollment` (entidades) | quitar la asociación JPA bidireccional en `Student`; consultar desde enrollment por `studentId` |
| P8 | Inscripción -> asistencia | enrollment -> attendance (2) | `ProgramEnrollmentService` -> `AttendanceStanding`, `AttendanceStandingReader` (ya vía `attendance.api`, prohibido solo por capas) | decidir si `attendance` puede quedar por debajo de `enrollment` o invertir con evento/puerto |
| P9 | Evaluación -> billing | evaluation -> billing (2) | `ExamBoardRegistrationManager` (ver P4) | puerto de matrícula de P4 |

Procesos pedidos que **no aparecen** hacia arriba en la medición actual: evaluación/asistencia -> graduation (0: graduation solo depende hacia abajo) y negocio -> notification (0 violaciones con destino notification). Existe además deuda hacia abajo (accesos a internos, no hacia arriba), que el corte por API pública resuelve sin invertir:

- billing -> enrollment (143: repositorios 112, excepciones 21, servicios 9, dto 1): `InvoicePlanningContextLoader`, `SuspendedEnrollmentClosureService`, `InvoiceViewLoader`, `MonthlyInstallmentRowProcessor`, `RecurringFeeWindowService`, `StudentBillingOverviewReader`, `EnrollmentPaymentStandingService`, `MeInvoiceController`, `FinancialDashboardReader`.
- billing -> academicstructure (41): repositorios (36) y excepciones (5), p. ej. `TermRepository` desde `InvoicePlanningContextLoader`.
- billing -> evaluation (7): `AdHocInvoiceRequestResolver` -> `ExamBoardRegistration`, su repositorio y excepción.
- graduation -> billing (13): `GraduationDebtChecker` (`InvoiceQueryService`, `PendingDebtException`), `ProgramCertificateIssuer` (`EnrollmentFeeRequirementService/Scope`).
- announcement -> billing (8): `ScheduledAnnouncementDispatchJob` -> `BatchExecutionLog`, `BatchExecutionLogRepository`, `BatchExecutionStatus` (candidato a mover el registro de ejecuciones de lotes a `shared`/`platform`).
- billing -> identity (2): `StudentNotFoundException`.

## Hallazgos principales

1. El problema ya no es de entidades: dominan los repositorios ajenos (160 de 293) y casi todo es `billing -> enrollment` (143).
2. Hay un único cruce realmente hacia arriba que importa: `enrollment -> billing` (66 de las 79 hacia arriba), concentrado en 4 clases (`ProgramEnrollmentService`, `CourseEnrollmentApplicationService`, `ProgramEnrollmentApplicationResponse`, `EnrollmentFee*`).
3. El par enrollment <-> billing mantiene 20 ciclos; los pares con más ciclos son billing<->evaluation 22, enrollment<->identity 22, academicstructure<->billing 20 y academicstructure<->evaluation 20; los cortes P1-P7 atacan las aristas que los sostienen (hipótesis a verificar tras cada corte).
4. La baseline de develop tenía drift (entradas sobrantes por 81 -> 41, 10 -> 2, 21 -> 13, etc.); se regeneró aparte.
5. Las cifras del plan (3.574 / 100 / 302) no se reproducen ni en el commit que cita (`cd1ede809`: 315 violaciones, 50 ciclos, 66 clases origen); hoy 293, 50 y 65.
