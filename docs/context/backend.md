# Backend — convenciones (Spring Boot)

Leé esto antes de tocar `backend/`. Todo acá está verificado contra código real del
repo, no es aspiracional.

## Estructura: package-by-feature + Use Case

**Migración completa desde 2026-09-11** (arrancó con PR #47, `identity/`).
El backend quedó reorganizado de "todo en un paquete por capa" (`entity/`,
`service/`, `controller/` planos en la raíz) a **un paquete por feature de
dominio** — `identity`, `billing`, `academicstructure`, `enrollment`,
`evaluation`, `attendance`, `graduation`, `notification` — y dentro de cada
feature, **un caso de uso por operación** en vez de un `Service` con muchos
métodos no relacionados entre sí, ahí donde el service realmente mezclaba
capacidades de actores distintos (no en todos lados — ver el criterio de
alcance en cada sección de feature más abajo). `identity/` es la
referencia completa del patrón — mirala antes de tocar código nuevo.

```
<feature>/
  usecase/<subdominio>/
    <Verbo><Sustantivo>UseCase.java      interfaz — un solo método de negocio
    <Verbo><Sustantivo>Service.java      implementación, anotada @UseCase
    <Verbo><Sustantivo>Command.java      input, solo si son 2+ parámetros
  service/        colaboradores compartidos entre varios use cases (NO son
                   ellos mismos un caso de uso — ver más abajo)
  infrastructure/ técnico puro, no toca reglas de negocio (JWT, rate
                   limiting, verificación de tokens externos)
  entity/  controller/  repository/  dto/  exception/
```

**Raíz del paquete (`com.red_escuela.backend`)**: solo `BackendApplication`,
`config/`, `shared/`, los paquetes de feature y `academicrecord/` (composición:
solo el controller `MeAcademicRecordController`, sin entidades). No hay
`entity/`, `service/`, `dto/`, `controller/`, `repository/`, `util/` ni
`exception/` sueltos en la raíz.

`shared/` contiene lo realmente compartido entre features: `domain/` (Value
Objects `Money`, `Percentage`, `Grade`, etc., más `StatusType` y `Address`),
`persistence/` (convertidores JPA), `serialization/`, `validation/`,
`exception/` (bases `BadRequestException`, etc., más `ErrorCodeResponse` y
`GlobalExceptionHandler`), `storage/` (`FileStorageService`,
`TransactionBoundFileCleanup`, `UnsupportedFileTypeException`) y `mail/`
(`EmailService`, `BrevoEmailService`).

- **`@UseCase`** (`shared/UseCase.java`) es una anotación real (meta-anotada
  `@Component`), no una convención de carpeta — patrón de Tom Hombergs
  ([buckpal](https://github.com/thombergs/buckpal), *Get Your Hands Dirty on
  Clean Architecture*). Cada operación de negocio real (algo que un actor
  pide hacer, con su propio endpoint) es una interfaz `XxxUseCase` + una
  clase `XxxService implements XxxUseCase` anotada `@UseCase`. El método de
  la interfaz es igual al nombre de la interfaz sin el sufijo, en minúscula
  inicial (`ApproveBankTransferPaymentUseCase.approveBankTransferPayment(command)`),
  nunca un `execute()` genérico. Los casos de uso existentes no se renombran.
- **`Command`** solo cuando el caso de uso necesita 2+ parámetros — un solo
  `Long id` o un DTO existente que ya calza (`CreateStaffRequest`,
  `RegisterRequest`) se pasa directo, sin wrapper artificial.
- **Colaboradores en `service/`** (sin `@UseCase`): lógica compartida entre
  varios use cases del mismo subdominio que no es en sí "algo que un actor
  pide hacer" — ver `AuthTokenIssuer`, `PasswordPolicyValidator`,
  `StaffRoleAssignmentPolicy`, `CurrentPersonService` en `identity/service/`.
  Si un helper se usa desde un solo use case, va como método privado ahí
  adentro — no se extrae un colaborador "por las dudas".
- **`infrastructure/`**: lo que no representa una regla de negocio (firmar
  JWT, verificar un idToken de Google, rate limiting en memoria) — ver
  `identity/infrastructure/`.
- **Los Controllers NO se explotan uno por use case.** Siguen siendo uno por
  `@RequestMapping` de recurso (`StudentController`, `AuthController`), cada
  endpoint inyecta el/los `UseCase` que necesita — decisión explícita tomada
  al diseñar el patrón, no un olvido.
- **La persona autenticada nunca se resuelve de un id de la URL.** Se resuelve
  del `sub` del JWT vía `CurrentPersonService.resolveCurrentPerson()`
  (`identity/service/`) — reusalo, no dupliques esa resolución en un use case
  nuevo.
- **Autorización de rol grueso vía `@PreAuthorize("hasRole('X')")`** a nivel
  de clase o método del controller. Cuando la regla depende de datos (ej.
  "Administrativo puede crear Docente/Preceptor pero no Administrativo"), esa
  regla fina vive en un colaborador de `service/` — ver
  `StaffRoleAssignmentPolicy` (mapa `actorRole -> rolesQuePuedeCrear`), nunca
  en la anotación ni en el controller.

DTOs de request/response en `dto/` como `record`, nunca se expone una
`entity` directo en un controller. Cada DTO de response tiene un factory
estático `from(Entity)` (ver `PersonResponse.from`, `AuthResponse.from`).

## Paquete por feature: `billing`

El dominio de facturación (`Invoice`, `Payment`, `PriceRevision`, `Scholarship`,
`Promotion`, `LatePaymentSettings`, `ProgramCohort`, `ProgramYearBillingTemplate`,
etc. — todo lo de Fase 6.x del roadmap) vive en su propio paquete
`com.red_escuela.backend.billing.*`. Es ~25% de todo el backend, bastante
autocontenido — si otro dominio (ej. evaluación y asistencia) llega a crecer
igual de grande, este es el precedente a seguir, no antes de eso. El
acoplamiento cruzado es esperado, no un smell: `billing.*` importa
`Person`/`Course`/`Program`/`CourseEnrollment`/`ProgramEnrollment` (raíz), y
`CourseEnrollmentService`/`ProgramEnrollmentService` (raíz) importan use
cases de `billing` para el flujo de baja.

**Migrado al patrón Use Case (2026-09-08)**: `billing/usecase/<subdominio>/`
(`invoice`, `payment`, `paymentreceipt`, `creditnote`, `scholarship`,
`promotion`, `paymentplan`, `billingperiod`, `latepaymentsettings`, `cohort`)
tiene un `XxxUseCase`+`XxxService`(`@UseCase`) por cada operación que un
controller invoca directo. Lo que queda en `billing/service/` son
colaboradores de una sola responsabilidad, no use cases — tres categorías:
1. **Compartidos entre varios use cases** del mismo subdominio (evitan
   duplicar lógica): `PaymentPlanSelectionGuard`, `PromotionRedeemer`,
   `ScholarshipGrantSupport`, `PaymentReceiptFinder`, etc.
2. **Ya cohesivos de por sí**, no tenían el problema de "cajón de sastre"
   (varios métodos, pero todos sobre el mismo concepto — mismo criterio que
   `PersonFactory`/`CurrentPersonService` en `identity`): `InvoiceFactory`
   (Factory pattern), `PriceRevisionService`, `InvoiceQueryService`.
3. **Jobs programados** (sin actor HTTP detrás, se disparan por cron):
   `InvoiceOverdueSweepService`, `EnrollmentBillingComplianceSweepService`,
   `ProgramEnrollmentBillingScheduler` y sus `*RowProcessor`.

Antes de agregar una operación nueva a `billing`, mirá si encaja en un
subdominio existente de `usecase/` o si necesita uno nuevo — no la agregues
como método suelto a un colaborador de `service/`.

### Motor de facturación v2: subdominio primero (D-173)

El código nuevo de facturación nace con el subdominio como primer nivel del
paquete (`billing.configuration`, `billing.operation`, y los que cree cada fase
V2), con la misma estructura interna en todos: `domain/`, `policy/`,
`operation/`, `usecase/`, `service/`, `repository/`, `controller/`, `dto/`,
`exception/`. Lo existente en `billing/usecase/<subdominio>/`,
`billing/service/` y `billing/entity/` se mueve en V2-02; hasta entonces
conviven los dos mapas. Reglas que ArchUnit hace cumplir en los paquetes V2
(`ArchitectureRules`): las excepciones de negocio solo salen de la evaluación
de Policies; los casos de uso dependen solo de repositorios, Policies, dominio,
servicios y DTO de su subdominio; un subdominio no usa el repositorio de otro;
las entidades no tienen setters públicos y sus montos son `NonNegativeMoney`;
toda `Policy` declara `@PolicyRule` (códigos de violación y decisiones);
`academicstructure` no depende de `billing`. `UseCaseBranchingTest` prohíbe
`if`, `switch` y ternarios en los `*Service` `@UseCase` de esos paquetes.

Pipeline de operación (V2-00c2): toda operación que cambia configuración o
estado económico implementa `BillingOperation` y corre por
`BillingOperationPipeline.runOperation` (validación, pre-procesamiento con
handlers registrados en `OperationPreHandlers`, procesamiento, y publicación de
eventos de `billing.events` dentro de la misma transacción). Los efectos de post
son `@ApplicationModuleListener` idempotentes: usan `ProcessedEffectGuard`
(tabla `processed_effect`, evento `DeduplicableDomainEvent`) porque el registro
de Spring Modulith entrega al menos una vez. Un handler de pre que pide
autorización abre un `AuthorizationRequest` (`billing.authorization`) y la
operación no se procesa hasta que se aprueba.

Puertas de calidad (todas en `./mvnw clean verify`, salvo PIT):
`BillingDecisionCoverageTest` exige un `@Tag("D-xxx")` por cada decisión
`implementada`/`parcial` de `docs/billing/decision-registry.md` (fuente de
verdad, columna Implementación); `PolicyCatalogTest` compara
`docs/billing/policy-catalog.md` con las Policies declaradas (regenerar con
`./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true`); jqwik para
propiedades de Value Objects (semilla fija); PIT con
`./mvnw -P mutation test-compile org.pitest:pitest-maven:mutationCoverage`,
umbral 90 % solo en paquetes de dominio puro, exclusiones únicamente en
`docs/billing/mutation-exclusions.md`. Los tests de integración heredan
`@Tag("integration")` de `AbstractIntegrationTest` y PIT los excluye.
Detalle: Obsidian, "Arquitectura y patrones - Motor de facturación v2" (§1, §2,
§4) y "Estrategia de pruebas - Motor de facturación v2".

## Paquete por feature: `academicstructure`

Institución/Programa/Curso/Materia/Comisión/Período (`Institution`, `Program`,
`ProgramSubject`, `Course`, `CourseSubject`, `CourseSubjectSection`,
`SubjectSection`, `ElectiveGroup`, `Term`, `Holiday`, `Instructor`, etc.)
viven en `com.red_escuela.backend.academicstructure.*`.

**Acá la mayoría de los services quedan cohesivos, sin Use Case** —
`CourseService`, `CourseSubjectService`, `CourseSectionService`,
`ProgramService`, `ProgramSubjectService`, `SubjectService`,
`SubjectSectionService`, `ElectiveGroupService`. Cada uno mezcla CRUD del
recurso con una operación relacionada del mismo concepto (ej.
`CourseSectionService` = CRUD de comisión + asignar/quitar docente de esa
misma comisión; `ProgramSubjectService` = CRUD de materia de plan +
correlatividades de esa materia) — no es un "cajón de sastre" de
capacidades no relacionadas, así que no se fuerza la descomposición (mismo
criterio que `InvoiceFactory`/`PriceRevisionService` en billing). Los tres
controllers que son solo lectura pegan directo al repository, sin service
intermedio: `InstitutionController`, `InstructorController`,
`HolidayController`.

**Única excepción real, migrada al patrón Use Case**:
`academicstructure/usecase/term/` — el viejo `TermController`/`TermService`
sí mezclaba dos capacidades no relacionadas: CRUD de período (crear/editar/
activar/desactivar/listar/detalle) y gestión de la ventana de inscripción
(abrir/cerrar inscripción y quitar override, sobre
`TermEnrollmentOverride`) — un actor y motivo distinto del CRUD del período
en sí. Se descompuso en 9 use cases (`CreateTermUseCase`,
`UpdateTermUseCase`, `ListTermsUseCase`, `GetTermUseCase`,
`DeactivateTermUseCase`, `ActivateTermUseCase`, `OpenTermEnrollmentUseCase`,
`CloseTermEnrollmentUseCase`, `ClearTermEnrollmentOverrideUseCase`). El
`findOrThrow` compartido por 7 de los 9 quedó extraído en un colaborador
`TermFinder` (`academicstructure/service/`), sin `@UseCase` — no es en sí
una operación que un actor pide hacer.

`StudyProgram`/`StudyProgramCourse` no se movieron: no tienen repository,
service ni controller — parecen legacy sin uso, quedaron en
`com.red_escuela.backend.entity` a la espera de una decisión explícita
sobre si se eliminan.

## Paquete por feature: `enrollment`

Matriculación a curso/carrera y sus solicitudes (`CourseEnrollment`,
`ProgramEnrollment`, `CourseEnrollmentApplication`,
`ProgramEnrollmentApplication`, `SubjectSectionEnrollment`,
`DirectorApprovalRequest`, alta de prospectos) viven en
`com.red_escuela.backend.enrollment.*`. `StudentDocument`/`DocumentType`
entraron acá también — pese al nombre, su único uso real es como adjunto de
`ProspectService`/`ProgramEnrollmentApplicationService`, no es un concepto
de `identity`.

**La mayoría de los services quedan cohesivos, sin Use Case**:
`CourseEnrollmentService`, `CourseEnrollmentApplicationService`,
`CourseSectionEnrollmentService`, `ProgramEnrollmentService`,
`ProspectService`, `SubjectSectionEnrollmentService` — mismo criterio que
`academicstructure`, cada uno es CRUD/lifecycle de un solo concepto sin
mezclar actores. `CourseEnrollmentWaitlistExpiryJob` es un job programado,
tampoco es use case.

**Dos excepciones reales, migradas al patrón Use Case** — mismo criterio
que el viejo `PaymentReceiptService` de billing: dos actores con motivos
distintos mezclados en un mismo service/controller:

1. `enrollment/usecase/directorapproval/` — el viejo
   `DirectorApprovalRequestService` mezclaba Preceptor/Administrativo
   *solicitando* una excepción con Director *aprobando/rechazando/
   listando*. 5 use cases, con `DirectorApprovalRequestFinder` (findOrThrow
   compartido) y `DirectorApprovalRequestDecisionRecorder` (lógica de
   aprobar/rechazar compartida entre `ApproveDirectorApprovalRequestService`
   y `RejectDirectorApprovalRequestService`) como colaboradores en
   `enrollment/service/`.
2. `enrollment/usecase/programenrollmentapplication/` — el viejo
   `ProgramEnrollmentApplicationService` mezclaba autoservicio del Alumno
   (`solicitar con pago`/`ver las propias`/`agregar documento`) con
   decisión de Preceptor/Administrativo (`listar pendientes`/`aprobar`/
   `rechazar`). 7 use cases, con `ProgramEnrollmentApplicationFinder` como
   colaborador del findOrThrow compartido.

## Paquete por feature: `evaluation`

Mesas de examen, notas, esquemas de evaluación y configuración de
evaluación de comisión (`ExamBoard`, `ExamBoardRegistration`, `GradeItem`,
`EvaluationScheme`, `SectionEvaluationConfig`) viven en
`com.red_escuela.backend.evaluation.*`.

**La mayoría de los services quedan cohesivos, sin Use Case**:
`EvaluationSchemeService`, `ExamBoardService` (CRUD de mesa + asignar
tribunal — mismo concepto, mismo criterio que `CourseSectionService`),
`ExamBoardRegistrationService` (inscribir/dar de baja, usado tanto por
staff como por autoservicio del alumno — es la misma operación invocada
por actores distintos, no un workflow de decisión), `GradeItemService`
(cargar nota es el Docente, ver notas es Preceptor/Administrativo/Director
— lectura vs. escritura del mismo recurso, no dos procesos de negocio
distintos). `SectionEvaluationConfigReminderJob` es un job programado.

**Única excepción real, migrada al patrón Use Case**:
`SectionEvaluationConfigService` sí mezclaba autoservicio del Docente
(crear borrador/guardar borrador/enviar a aprobación) con decisión de
Director/Administrativo (aprobar/rechazar/listar) — mismo patrón que
`ProgramEnrollmentApplicationService` en enrollment. 8 use cases en
`evaluation/usecase/sectionevaluationconfig/`, con tres colaboradores en
`evaluation/service/`:
- `SectionEvaluationConfigFinder` — findOrThrow compartido.
- `SectionEvaluationConfigInstructorAccess` — validación de pertenencia/
  liderazgo de comisión y armado de la lista de docentes de la respuesta;
  se inyecta directo en los controllers para las consultas de soporte de
  UI (mismo patrón que `CurrentPersonService`/`CurrentInstructorService`),
  no todo tiene que pasar por un Use Case.
- `SectionEvaluationConfigDraftInitializer` — `createInitialDraft`, se
  invoca desde `academicstructure.service.SubjectSectionService` al crear
  una comisión (colaborador cross-feature, no un Use Case porque no lo
  dispara un actor con su propio endpoint).

## Paquete por feature: `attendance`

Toma de asistencia, justificación, workflow de solicitudes de cambio y
ciclo de vida de la "clase" puntual (`Attendance`, `AttendanceChangeRequest`,
`ClassSession`) viven en `com.red_escuela.backend.attendance.*`.

**Caso real de "cajón de sastre"** — a diferencia de `academicstructure`/
`enrollment`/`evaluation` (donde casi todo quedó cohesivo), acá el viejo
`AttendanceService` (21 métodos públicos) mezclaba genuinamente 5
capacidades no relacionadas con actores distintos:
1. **Cargar asistencia** (Docente) → `LoadProgramSectionAttendanceUseCase`,
   `LoadCourseSectionAttendanceUseCase`.
2. **Workflow de justificación** (Docente/Alumno *justifica* vs.
   Preceptor/Administrativo *aprueba/rechaza* — mismo patrón que
   `ProgramEnrollmentApplicationService`) → `JustifyAttendanceUseCase`,
   `SubmitAttendanceProofUseCase` (autoservicio del alumno),
   `ApproveAttendanceJustificationUseCase`,
   `RejectAttendanceJustificationUseCase`.
3. **Consultas** → `GetAttendanceUseCase`,
   `List{Program,Course}EnrollmentAttendanceUseCase`,
   `Calculate{Program,Course}EnrollmentAttendanceRiskUseCase`.
4. **Reportes/roster** → `Get{Program,Course}SectionRosterUseCase`,
   `Get{Program,Course}SectionAbsenceReportUseCase`,
   `Get{Program,Course}SectionAttendanceHistoryUseCase`.
5. **Ciclo de vida de la clase** → `Get{Program,Course}SectionClassSessionUseCase`,
   `Cancel{Program,Course}SectionClassSessionUseCase`.

Todos en `attendance/usecase/attendance/` (21 use cases), con colaboradores
en `attendance/service/`: `AttendanceFinder`, `AttendanceSectionAccess`
(pertenencia de Docente a comisión + resolución de `Instructor`),
`AttendanceRiskCalculator`, `SectionRosterBuilder`, `AttendanceHistoryBuilder`,
`ClassSessionUpserter`. `AttendanceExportService` queda cohesivo (exportar a
PDF/XLS es un solo concepto) y consume los use cases de historial/reporte.

`AttendanceChangeRequestService` (5 métodos) es la segunda excepción real,
mismo patrón: Docente *solicita* un cambio vs. Preceptor/Administrativo
*decide*. 5 use cases en `attendance/usecase/attendancechangerequest/`, con
`AttendanceChangeRequestFinder` como colaborador (findOrThrow +
`requirePending`).

La resolución de la persona autenticada para justificar/aprobar/rechazar
reusa `CurrentPersonService` (identity) en vez de leer
`SecurityContextHolder` directo — el service viejo lo hacía manualmente,
duplicando el mismo mecanismo.

## Paquete por feature: `graduation`

Trámite de egreso y constancias (`GraduationRequest`, `CertificateIssuance`)
viven en `com.red_escuela.backend.graduation.*`.

**`CertificateService` queda cohesivo, sin Use Case** — emitir constancia
regular o de materias aprobadas son la misma operación (generar un
documento a pedido, nunca persistido) para el mismo actor (el Alumno,
autoservicio vía `MeCertificateController`).

**`GraduationRequestService` sí es una excepción real** — mismo patrón que
`ProgramEnrollmentApplicationService`/`SectionEvaluationConfigService`/
`AttendanceChangeRequestService`: Alumno *solicita* el trámite de egreso,
Administrativo *verifica deuda*, Director *aprueba*, Administrativo/Director
*rechaza*. 7 use cases en `usecase/graduationrequest/`
(`RequestGraduationUseCase`, `VerifyGraduationRequestDebtUseCase`,
`ApproveGraduationRequestUseCase`, `RejectGraduationRequestUseCase`,
`List{ByProgramEnrollment,ByStatus}GraduationRequestsUseCase`,
`GetGraduationRequestUseCase`), con `GraduationRequestFinder` como
colaborador del findOrThrow compartido.

`createGraduationRequestIfEligible` (se dispara solo al aprobarse una
materia, ver `evaluation.service.GradeItemService`) no es un use case — nadie
lo pide desde un endpoint propio, es un efecto automático de otro flujo.
Quedó como el colaborador `GraduationRequestAutoRequester`
(`graduation/service/`), que internamente invoca `RequestGraduationUseCase`
— un colaborador plano puede depender de un Use Case de su propia feature,
no hace falta que todo pase por otro Use Case.

## Paquete por feature: `notification`

**Última feature del roadmap package-by-feature** — a diferencia del resto,
acá ya existía de antes un paquete `notification` (singular) con la
infraestructura de eventos de dominio: `NotificationDispatcher`, los
`NotificationChannel` (`InAppNotificationChannel`), tópicos, destinatarios
(`PersonRecipient`/`RoleRecipient`) y los eventos de cada feature
(`GraduationRequestApprovedEvent`, `SectionEvaluationConfigRejectedEvent`,
etc. — cada feature publica los suyos, cross-package, mismo patrón que
`ApplicationEventPublisher` ya usado en todo el backend). Ese paquete
YA escribía directo en la entidad `Notification` (`InAppNotificationChannel`
→ `NotificationRepository`) — la bandeja de notificaciones del alumno y la
infraestructura de eventos son, en los hechos, la misma feature.

Por eso acá NO se creó un paquete `notifications` (plural) nuevo — hubiera
quedado un choque de nombres confuso con el `notification` ya existente.
Se movieron `Notification`/`NotificationType`/`NotificationService`/
`MeNotificationController`/`NotificationRepository` como
`entity/service/controller/repository` **dentro** de `notification/`,
junto a los eventos y el dispatcher que ya vivían ahí en la raíz del
paquete (mismo patrón que `shared/UseCase.java` conviviendo con
subcarpetas de cada feature). `NotificationService` queda cohesivo, sin
Use Case (2 métodos, un solo actor: el alumno viendo/marcando como leídas
sus propias notificaciones).

`BatchExecutionLog`/`BatchExecutionStatus` NO son de esta feature pese al
nombre genérico — su comentario original decía explícitamente que existen
solo para que `InvoiceOverdueSweepService`/
`EnrollmentBillingComplianceSweepService` (billing) dejen rastro de cada
corrida. Se movieron a `billing/entity`/`billing/repository`, no a
`notification/`.

`Events.java` (entity) quedó sin mover — igual que `StudyProgram`/
`StudyProgramCourse` en `academicstructure`, no tiene repository, service
ni controller, parece legacy sin uso real. Pendiente una decisión explícita
sobre si se elimina.

`EmailService`/`BrevoEmailService`/`FileStorageService` **no** entraron a
esta feature — son infraestructura genérica usada cross-feature
(`EmailService` solo lo llama `identity.usecase.auth.RequestPasswordResetService`,
nada que ver con el sistema de eventos; `FileStorageService` lo usan
enrollment/evaluation/billing/identity) y viven en `shared/mail` y
`shared/storage`.

## Reglas de arquitectura

Tests ArchUnit en `backend/src/test/java/com/red_escuela/backend/architecture/` (`ArchitectureRules` define las reglas, `ArchitectureTest` las ejecuta sobre las clases de `main`). Fallan el build (`verify`):

- Ningún campo de `..entity..` ni `..dto..` es `BigDecimal`/`Double`/`Float`/`double`/`float` si su nombre sugiere plata, porcentaje o nota (`amount`, `price`, `fee`, `grade`, `score`, ...): usar `Money`, `Percentage` o `Grade`. **Congelada** hoy con las violaciones existentes (precios y notas aún sin migrar).
- Un campo `Percentage` en un `dto` termina en `Percent` o `Rate`.
- `shared` no depende de ningún paquete de feature. **Congelada** (hoy: `shared/mail` y `GlobalExceptionHandler`).
- Un `*Service` en `..usecase..` lleva `@UseCase`; una clase en `..service..` no.

Ciclos entre features, imports de repositorios de otra feature y dependencias de controllers no van con ArchUnit: los cubre Spring Modulith.

Las reglas congeladas usan `FreezingArchRule`; el almacén está en `backend/src/test/resources/archunit-frozen/` (config en `archunit.properties`, con `allowStoreUpdates=false`: una violación nueva falla, una arreglada también hasta actualizar el almacén). Para descongelar: corregir el código y borrar del almacén las líneas de esa violación (o poner `freeze.refreeze=true` en `archunit.properties`, correr el test una vez y volverlo a `false`). Una violación nueva no se congela: se arregla.

## Excepciones

Una excepción de dominio por caso, con nombre descriptivo del problema
(`InvalidPasswordPolicyException`, `RoleNotAssignableByActorException`, nunca un
`BusinessException` genérico con un string). Cada una extiende una de las siete
bases abstractas de `com.red_escuela.backend.shared.exception`, que definen el
status HTTP:

| Base | Status | Body |
|---|---|---|
| `BadRequestException` | 400 | `{ "code": "..." }` |
| `UnauthorizedException` | 401 | vacío |
| `ForbiddenException` | 403 | vacío |
| `NotFoundException` | 404 | vacío |
| `ConflictException` | 409 | `{ "code": "..." }` |
| `TooManyRequestsException` | 429 | vacío |
| `ExternalServiceException` | 502 | vacío |

`shared/exception/GlobalExceptionHandler.java` tiene un `@ExceptionHandler` por base:
una excepción nueva se agrega extendiendo la base que corresponda, **sin tocar el
handler**. Ninguna excepción de dominio extiende `RuntimeException` directo; la
única con handler propio es `InvalidEmailVerificationTokenException` (400 sin
body, a diferencia del resto de los 400).

**Body de error**: desde #123 los 400 y 409 devuelven `{ "code": "..." }`
(`ErrorCodeResponse`), derivado del nombre de la clase concreta
(`InvalidTermDatesException` -> `INVALID_TERM_DATES`). Los 401, 403, 404, 429 y 502
no devuelven body. El frontend arma el mensaje al usuario a partir del status y,
cuando existe, del `code`. No cambies un `code` ni agregues body a otros status sin
confirmar con el usuario: es contrato HTTP.

**Logs**: los 409 (incluidos `IllegalStateException`, `DataIntegrityViolationException`
y `ObjectOptimisticLockingFailureException`) y los `IllegalArgumentException`
loguean en `warn`, los 502 en `error`, con el `code` y el `requestId`/`userId` del MDC.
Los 400, 401, 403, 404 y 429 no loguean. `IllegalStateException` responde 409.

## Migraciones (Flyway)

`src/main/resources/db/migration/V{N}__descripcion_en_snake_case.sql`, `N`
correlativo: `git fetch` y mirá el último `V*.sql` en `origin/develop` **y** en
`origin/master` (y PRs abiertos) antes de crear uno — ya hubo colisiones que rompieron el
CI. Una migración ya aplicada en `master` nunca se renumera. Ver
[workflow.md](workflow.md), sección "Migraciones". `spring.jpa.hibernate.ddl-auto=validate`: el schema SIEMPRE se define en
la migración, nunca se deja que Hibernate lo genere. Constraints de integridad
(NOT NULL, unique, índices parciales de Postgres para reglas tipo "solo un X activo
por Y") van en la migración SQL, no solo a nivel de validación Java.

## Seguridad

`SecurityConfig.java`: todo exige JWT salvo `/auth/**` y `POST /prospects`
(altas públicas). CORS restringido a `app.cors.allowed-origins` (default
`http://localhost:5173`, el puerto de Vite). Rate limiting en memoria (Bucket4j)
solo en `/auth/login` y `/auth/forgot-password` — no hay rate limiting genérico de
API, el resto de los endpoints ya exige JWT válido como barrera.

`JWT_SECRET` no tiene default en `application.properties` a propósito — el boot
falla explícito si falta. Para correr local ver
[docs/context/debugging.md](debugging.md).

## Dominio y DDD

El proyecto ya sigue objetos de dominio reales (`Person`, `Role`,
`SubjectSection`, `AcademicOfferingCycle`-tipo de conceptos), no DTOs anémicos
con lógica en un "manager". Antes de agregar un service nuevo, primero mirá si la
regla de negocio pertenece a un método de la entidad (invariante propio) o si
necesita coordinar varias entidades (ahí sí es un domain service). Para elegir
qué patrón táctico DDD aplicar en un caso concreto, usá la skill `design-patterns`.

## Antes de asumir que un endpoint existe

Varias specs de roadmap ya se escribieron asumiendo endpoints que después
resultaron no existir (ejemplo real: `GET /me` no existía, solo había `PUT /me`,
se agregó recién en la Fase 1 del frontend). **Grepeá el controller real antes de
escribir contra un endpoint** — no confíes en lo que diga una spec vieja de
Obsidian sin verificar.
