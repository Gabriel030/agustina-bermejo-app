# E18 M5-S2: plan para bajar billing → enrollment y billing → academicstructure

Medición del 26-nov sobre `origin/develop` con `ViolationDumpTest` (`-Dmodulith.violations.dump=<archivo>`), filtrando `Module 'billing' depends on non-exposed type`. Cada fila es una referencia del bytecode (campo, constructor, parámetro, llamada), por eso un repositorio inyectado suma varias.

## Medición inicial

| Destino | Total | Tipo | Cantidad |
|---|---|---|---|
| enrollment | 143 | repository | 112 (`ProgramEnrollmentRepository` 62, `CourseEnrollmentRepository` 45, `EnrollmentFeeExemptionRepository` 5) |
| | | exception | 21 (`ProgramEnrollmentNotFoundException` 11, `CourseEnrollmentNotFoundException` 7, `InvalidWindowConfigurationException` 2, `EnrollmentFeeNotPaidForTermException` 1) |
| | | service | 9 (`ProgramEnrollmentService`) |
| | | dto | 1 (`ProgramProgressResponse`) |
| academicstructure | 41 | repository | 36 (`TermRepository` 16, `ProgramRepository` 12, `CourseRepository` 4, `SubjectRepository` 4) |
| | | exception | 5 (`ProgramNotFound` 2, `TermNotFound` 1, `CourseNotFound` 1, `SubjectNotFound` 1) |

Las entidades `CourseEnrollment`, `ProgramEnrollment`, `Term`, `Program`, etc. ya son tipos expuestos (`@NamedInterface`): solo los repositorios, servicios, excepciones y el DTO son el problema.

## Qué consume billing

- `ProgramEnrollmentRepository`/`CourseEnrollmentRepository`: `findById` (16 sitios), `findByStudentId` (6), `save` (2), `findAllById`, `findAllWith*ByIdIn`, `findDistinctStudentIdsByStatusIn`, `findIdsByProgramIdAndStatusIn`. Clases: `InvoiceViewLoader`, `MeInvoiceController`, `FinancialDashboardReader`, `StudentBillingOverviewReader`, `MonthlyInstallmentRowProcessor`, `RecurringFeeWindowService`, `EnrollmentPaymentStandingService`, `SuspendedEnrollmentClosureService`, `InvoicePlanningContextLoader`, `Own{Course,Program}EnrollmentBillingStatusReader`, `CreateAdHocInvoiceFor*Transaction`, `SelectPaymentPlanFor*Transaction`, `CourseInvoicePlanningContextLoader`, `ProgramEnrollmentBillingSetup`, `YearBillingConfiguredListener`.
- `ProgramEnrollmentService`: `withdrawForNonpayment`, `calculateProgress`, `hasOnlyFinalExamsLeft` (`InvoicePlanningContextLoader`, `SuspendedEnrollmentClosureService`).
- `EnrollmentFeeExemptionRepository` y `EnrollmentFeeNotPaidForTermException`: solo `EnrollmentFeeRequirementService`.
- academicstructure: todos los repositorios se usan con `findById`, `getReferenceById` y `findTermsOfProgram`, que `academicstructure.api.AcademicReferenceFinder` ya expone (`findSubject`, `findTerm`, `findProgram`, `findCourse`, `getProgramReference`, `findTermsOfProgram`).

## Etapas

Patrón común: mover el tipo a `<modulo>.api` si es una excepción o un DTO sin lógica; reemplazar el repositorio por un finder/puerto de `api` si es un repositorio. Sin cambio de comportamiento, contrato ni migración en ninguna etapa. Cada etapa baja la línea base `backend/src/test/resources/modulith-baseline/billing.txt` por el número indicado y regenera `docs/architecture` al final tras `git merge origin/develop`.

| Etapa | Qué | Baja | Riesgo | Puerto o api |
|---|---|---|---|---|
| E1 (hecha en este PR) | Mover a `enrollment.api` `CourseEnrollmentNotFoundException`, `ProgramEnrollmentNotFoundException`, `InvalidWindowConfigurationException`; a `academicstructure.api` `TermNotFoundException`, `SubjectNotFoundException` | enrollment 143→123 (−20), academicstructure 41→39 (−2) | Bajo: solo cambia el paquete; el código de error y el handler no cambian; 74 archivos con imports | las propias excepciones |
| E2 | Reemplazar `ProgramRepository`, `TermRepository`, `CourseRepository`, `SubjectRepository` por `AcademicReferenceFinder` en `CreateProgramYearBillingTemplateTransaction`, `SlotWindowResolver`, `InvoicePlanningContextLoader`, `SuspendedEnrollmentClosureService`, `ProgramPaymentPlanOptionCreation`, `CoursePaymentPlanOptionCreation`, `BillingConfigurationAlertService`, `AdHocInvoiceRequestResolver` | academicstructure −36 (queda 3: `ProgramNotFound` 2, `CourseNotFound` 1) | Bajo: el finder ya existe; las excepciones que lanzaban los `orElseThrow` pasan a lanzarlas el finder (mismas clases, mismo código); sin plata nueva | `AcademicReferenceFinder` existente |
| E3 | `EnrollmentFeeExemptionRepository` + `EnrollmentFeeNotPaidForTermException` en `EnrollmentFeeRequirementService`; `ProgramProgressResponse` y `ProgramEnrollmentService` en `InvoicePlanningContextLoader` y `SuspendedEnrollmentClosureService` | enrollment −16 (5 + 1 + 1 + 9) | Medio: `withdrawForNonpayment` tiene efectos; usar `EnrollmentWithdrawalBillingPort` y `ProgramProgressReader` existentes o un puerto nuevo mínimo | `enrollment.api` (`ProgramProgressView`, puerto de retiro, puerto de exención) |
| E4 | Lecturas de cursadas: `findById`/`findByStudentId`/`findAllWith*ByIdIn`/`findDistinct*` en `InvoiceViewLoader`, `MeInvoiceController`, `FinancialDashboardReader`, `StudentBillingOverviewReader`, `Own*BillingStatusReader` | enrollment ≈ −55 | Medio: muchos sitios pero todos de solo lectura; hay que ampliar `CourseEnrollmentFinder`/`ProgramEnrollmentFinder` con consultas por lote y mantener la cantidad de sentencias (hay tests de conteo) | `enrollment.api.CourseEnrollmentFinder`, `ProgramEnrollmentFinder` |
| E5 | Escrituras y planificación: `save` en `ProgramEnrollmentBillingSetup`/`YearBillingConfiguredListener`, `MonthlyInstallmentRowProcessor`, `RecurringFeeWindowService`, `EnrollmentPaymentStandingService`, `CreateAdHocInvoiceFor*Transaction`, `SelectPaymentPlanFor*Transaction`, `CourseInvoicePlanningContextLoader` | enrollment ≈ −32 (el resto) | Alto: toca emisión mensual, estado de pago y plan de pago (plata); cada sitio por separado con tests de caracterización | puertos de escritura en `enrollment.api` o evento; decidir con Gabriel si `save` sobre la entidad es aceptable |
| E6 | `ProgramNotFoundException` y `CourseNotFoundException` | academicstructure −3 | Bajo, pero aparecen en `archunit-frozen/6c808b55…`: moverlas invalida esas entradas congeladas; hacerlo junto con el cambio de `SetProgramCatalogVisibilityService`/`SetCourseCatalogVisibilityService` o con la política de borrar entradas obsoletas | las propias excepciones |

Orden recomendado: E2 (mayor baja de academicstructure y sin riesgo), E3, E4, E5, E6. E2 y E3 son independientes y lanzables en paralelo (archivos disjuntos); E4 y E5 comparten `InvoicePlanningContextLoader` y `SuspendedEnrollmentClosureService` con E3, así que van después.

## Ciclos

E2 a E5 cortan la mitad `billing → enrollment`/`billing → academicstructure` de los pares `billing <-> enrollment` (20) y `academicstructure <-> billing` (20) del informe `e18-violaciones.md`; al quedar solo `enrollment → billing` y `academicstructure → billing` hacia arriba, esos ciclos desaparecen.

## Primera rebanada (este PR)

E1. Patrón: mover la excepción de dominio al paquete `api` del módulo dueño (mismo patrón de `CohortAdmissionWindowClosedException`, #1439). Verificado: `ModularityTest` con la línea base en `billing.txt` (143→123 y 41→39), `ArchitectureTest`, `QualityRulesTest`, `ModulePatternRulesTest`, `JavadocOnlyInPackageInfoTest`, `OpenApiSnapshotTest` y `ErrorCatalogTest`, más `PaymentPlanOptionServiceTest`, `CourseEnrollmentFinderTest` y `TermEnrollmentWindowFinderTest`.
