# Backend — convenciones (Spring Boot)

Leé esto antes de tocar `backend/`. Todo acá está verificado contra código real del
repo, no es aspiracional.

## Estructura: package-by-feature + Use Case

**Convención vigente desde 2026-09-08** (PR #47, `identity/`; en curso para
`billing/`). El backend se está migrando de "todo en un paquete por capa"
(`entity/`, `service/`, `controller/` planos en la raíz) a **un paquete por
feature de dominio**, y dentro de cada feature, **un caso de uso por
operación** en vez de un `Service` con muchos métodos no relacionados entre
sí. `identity/` es la referencia completa — mirala antes de replicar el
patrón en una feature nueva.

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

- **`@UseCase`** (`shared/UseCase.java`) es una anotación real (meta-anotada
  `@Component`), no una convención de carpeta — patrón de Tom Hombergs
  ([buckpal](https://github.com/thombergs/buckpal), *Get Your Hands Dirty on
  Clean Architecture*). Cada operación de negocio real (algo que un actor
  pide hacer, con su propio endpoint) es una interfaz `XxxUseCase` + una
  clase `XxxService implements XxxUseCase` anotada `@UseCase`. El método de
  la interfaz tiene nombre de dominio (`login`, `withdraw`, `create`), nunca
  un `execute()` genérico.
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
`Promotion`, `CollectionsPolicy`, `ProgramCohort`, `ProgramYearBillingTemplate`,
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
`promotion`, `paymentplan`, `billingperiod`, `collectionspolicy`, `cohort`)
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
(`abrirInscripcion`/`cerrarInscripcion`/`quitarOverride`, sobre
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

## Excepciones

Una excepción de dominio por caso, con nombre descriptivo del problema
(`InvalidPasswordPolicyException`, `RoleNotAssignableByActorException`, nunca un
`BusinessException` genérico con un string). Todas se registran en
`exception/GlobalExceptionHandler.java`, agrupadas por status HTTP
(`handleBadRequest`, `handleNotFound`, `handleConflict`, `handleForbidden`, etc.).

**Los handlers no devuelven body — solo fijan el status.** No hay un DTO de error
con `message` en el JSON de respuesta; el cliente (frontend) arma el mensaje al
usuario a partir del código HTTP, no de un body. No agregues un body de error sin
antes confirmar con el usuario que se quiere ese cambio de contrato — es una
decisión tomada, no un olvido.

## Migraciones (Flyway)

`src/main/resources/db/migration/V{N}__descripcion_en_snake_case.sql`, `N`
correlativo (mirá el último `V*.sql` existente antes de crear uno nuevo — no asumas
el número). `spring.jpa.hibernate.ddl-auto=validate`: el schema SIEMPRE se define en
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
