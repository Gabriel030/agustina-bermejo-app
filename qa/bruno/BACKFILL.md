# Backfill de cobertura QA — seguimiento

No es documentación de producto, es el checklist de avance del backfill
completo sobre los 155 endpoints existentes (decisión del usuario,
2026-08-27: cubrir todo antes de seguir con fases nuevas del roadmap, no solo
hacia adelante). Se actualiza en cada sesión que agrega cobertura — mirar acá
primero antes de asumir qué falta.

**Dos herramientas, ambas se actualizan** — ver
[docs/context/testing.md](../../docs/context/testing.md): Karate
(`backend/src/test/resources/karate/`) es el gate automático en CI; Bruno
(acá, `qa/bruno/`) es para explorar a mano. Un endpoint "cubierto" tiene
casos en las dos, no en una sola.

Convención: cada fila es un controller. Estado `✅ cubierto` significa happy
path + bordes reales (auth, ownership, validación, duplicados, ids
inexistentes) para cada endpoint, en Karate Y en Bruno — no solo un smoke
test de 200. `🟡 parcial` anota qué falta puntualmente.

## Auth + Autoservicio (Épica 7) — cubierto

| Controller | Endpoints | Karate | Bruno | Notas |
|---|---|---|---|---|
| AuthController | 6 | ✅ | ✅ | login, register, refresh, logout — falta forgot-password/reset-password |
| InstitutionController | 1 | 🟡 (vía fixture, no feature propio) | ✅ | |
| ProgramEnrollmentApplicationController | 6 | 🟡 (flujo principal) | ✅ | falta `agregarDocumento`, `rechazar`, `listarPendientes` |
| MeEnrollmentController | 7 | 🟡 (`course-enrollments`) | 🟡 (`course-enrollments`) | `program-enrollments` GET, `section-enrollments` sin cubrir |
| ProgramController | 6 | 🟡 (crear, vía setup) | 🟡 (GET) | editar/activar/desactivar sin cubrir |
| CourseController | 6 | 🟡 (crear, vía setup) | 🟡 (GET) | editar/activar/desactivar sin cubrir |
| TermController | 9 | 🟡 (crear, vía setup) | — | resto sin cubrir |

## Gestión de personas — cubierto (2026-08-27)

| Controller | Endpoints | Karate | Bruno | Notas |
|---|---|---|---|---|
| PersonController | 2 | ✅ | ✅ | GET/PUT /me, validación de email |
| StaffController | 2 | ✅ | ✅ | reparto Administrativo/Director (RF-AD-05/RF-DI-04), cruzado 403, DNI duplicado (400, no 409 — inconsistencia real documentada) |
| ProspectController | 4 | ✅ | ✅ | ciclo completo prospecto→documentos→confirmar→ALUMNO, tipo de archivo no soportado, reemplazo de documento |
| StudentController | 4 | ✅ | ✅ | legajo (Director R), baja (Preceptor/Administrativo únicamente, Director 403) |

**Bug real encontrado acá** (no de este dominio puntual, sistémico): cualquier
excepción de Spring MVC no manejada explícitamente (JSON malformado,
`MethodArgumentTypeMismatchException` por un path variable con tipo
incorrecto) quedaba enmascarada como `403` vacío en vez del status real —
mismo síntoma que el bug de `/error` de la Fase 2 de Épica 7, pero más
amplio de lo que se había corregido ahí. Fix: `/error` vuelve a ser
`permitAll()` (se había revertido por error junto con un catch-all que sí
era peligroso), más handlers explícitos para `HttpMessageNotReadableException`
y `MethodArgumentTypeMismatchException` en `GlobalExceptionHandler`. Ver el
commit de esta sesión para el detalle completo — incluye por qué un
catch-all de `Exception.class` NO es la solución (rompe `@PreAuthorize`).

## Procesos administrativos y financieros — cubierto (2026-08-28)

| Controller | Endpoints | Karate | Bruno | Notas |
|---|---|---|---|---|
| InvoiceController | 5 | ✅ | ✅ | ciclo completo crear→pago parcial→exceder saldo (400)→completar→cancelar bloqueado (409); Preceptor sin acceso (403, decisión 14); Director R sin W (403); alumno inexistente (404) |
| MeInvoiceController | 3 | ✅ | ✅ | autoservicio: `/me/invoices`, `/me/invoices/debt-summary`, `/me/payment-standing` — cubiertos como parte del mismo ciclo de arriba |
| MeCertificateController | 2 | pendiente | pendiente | |
| GraduationRequestController | 4 | pendiente | pendiente | |
| MeGraduationRequestController | 1 | pendiente | pendiente | |
| DirectorApprovalRequestController | 4 | ✅ | ✅ | solicitar (Preceptor/Administrativo), listar pendientes/aprobar/rechazar (Director), 403 Alumno, 409 decidir dos veces, 404 matriculación inexistente |
| MeCertificateController | 2 | ✅ | ✅ | regular (400 sin cursada activa), materias-aprobadas (200 aun sin materias), 404 matriculación ajena |
| GraduationRequestController | 4 | ✅ | ✅ | ciclo REQUESTED→ADMIN_VERIFIED→APPROVED (matriculación pasa a COMPLETED en la misma transacción), 400 no elegible por créditos, 409 ya activo / deuda pendiente (decisión 10, sigue en REQUESTED) / decidir dos veces, 403 Preceptor no aprueba, 404 inexistente |
| MeGraduationRequestController | 1 | ✅ | ✅ | mismo ciclo de arriba, disparado por el propio Alumno |

**Nota de setup (Bruno/Karate, Egreso y excepciones)**: la matriculación se
arma directo vía `POST /students/{id}/program-enrollments`
(Preceptor/Administrativo), no vía el flujo de solicitud+aprobación de
`ProgramEnrollmentApplicationController` (ya cubierto aparte). Una carrera
con `totalCreditsRequired: 0` y sin `ProgramSubject` hace
`elegibleParaCompletar` trivialmente verdadero (mismo criterio que
`GraduationRequestServiceTest.persistProgramSinRequisitos` en el backend) —
evita tener que armar materias/cursada completa solo para probar el flujo
de egreso.

**Nota de setup (Bruno)**: el alumno de prueba se crea vía `POST
/auth/register` (no `/prospects`) porque hace falta loguearse como ese
alumno para probar `/me/invoices` — `/prospects` deja `password` en null.
`/auth/register` no devuelve `studentId`, así que se resuelve con `GET
/students?onboardingStatus=CONFIRMADO` filtrando por `username` en el
script de Bruno (`StudentResponse.username`, dto ya expone ese campo).

**Nota sobre el rate limiter, confirmada acá**: la colección completa
(`bru run qa/bruno -r`) pega justo en el límite de `auth.rate-limit.login`
(capacidad 10/min) — Auth + Autoservicio + Gestión de personas ya consumen
los 10 logins antes de llegar a esta carpeta. Correr esta carpeta sola
(`bru run "Procesos administrativos y financieros" -r`) da 25/25 verde;
correr todo junto sin reiniciar el backend entre medio pega 429 a partir de
acá — no es un bug nuevo, es el límite ya documentado arriba en este mismo
archivo ("Cuidado con el rate limiter" en `docs/context/testing.md`).

## Estructura académica / oferta — cubierto (2026-08-28)

| Controller | Endpoints | Karate | Bruno | Notas |
|---|---|---|---|---|
| SubjectController | 6 | ✅ | ✅ | ciclo completo, nombre duplicado (409), 404, Preceptor sin acceso (403) |
| ProgramSubjectController | 9 | ✅ | ✅ | alta/edición/baja, duplicado (409), correlatividades (agregar/listar/quitar), prereq de sí misma y circular (400), correlatividad entre carreras distintas (400, solo Karate) |
| ElectiveGroupController | 7 | ✅ | ✅ | ciclo completo, rango inválido (400), no se puede tagear sin asignar a la carrera (decisión 8, 400), en uso no se puede quitar (409) |
| CourseSubjectController | 6 | ✅ | ✅ | ciclo completo, curso/módulo inexistente (404) |
| CourseSectionController | 9 | ✅ | ✅ (sin asignación de docente) | ciclo completo, docente ya asignado (409, solo Karate), quitar asignación de otra comisión (404, solo Karate), Alumno sin acceso (403) |
| SubjectSectionController | 9 | ✅ | ✅ (sin asignación de docente) | ciclo completo, regla de diseño "una comisión por materia, no por carrera" (solo Karate), quitar asignación ajena (404, solo Karate) |

**Gap real encontrado (no un bug, una limitación documentada)**: no existe
ningún endpoint que exponga el `id` de un `Instructor` — `StaffResponse.id`
es el id de `Person`, no el de `Instructor` (comentario explícito en
`StaffResponse.java`). Karate lo resuelve con un fixture Java nuevo
(`KarateFixtures.crearInstructor()`); Bruno **no puede** probar la
asignación de docente a una comisión sin ese id — por eso las carpetas de
Bruno de este dominio cubren todo el ciclo de la comisión (crear, editar,
baja/alta) pero **no** el alta/baja de `SectionInstructor`. Si en algún
momento se agrega un endpoint de listado de instructores, revisar esto.

**Otro gap real (limitación del producto, no de este test)**:
`UpdateProgramSubjectRequest.electiveGroupId` con JSON `null` se ignora —
`editarMateria` solo pisa campos no-nulos (`ProgramSubjectService.java:121`),
es "PATCH parcial", nunca "borrar este campo". No hay forma vía API de
destagear una materia de un grupo electivo ya asignado.

**Nota de setup (Bruno)**: el `type` de `/terms` (`CreateTermRequest.type`)
es un `String` libre, no un enum — se usa `'ANUAL'` por convención ya
establecida en el resto de la colección, no porque el backend lo exija.

**Cobertura de Bruno reducida a propósito** (documentado también en
`qa/bruno/Estructura academica y oferta/folder.bru`): 46 endpoints en 6
controllers con dependencias combinatorias — Bruno cubre flujos
principales y reglas de negocio clave (54 requests), Karate tiene el
detalle exhaustivo de casos borde (28 escenarios).

## Inscripciones académicas — cubierto (2026-08-28)

| Controller | Endpoints | Karate | Bruno | Notas |
|---|---|---|---|---|
| ProgramEnrollmentController | 5 | ✅ | ✅ | matricular, listar, detalle, progreso, baja — completa lo que había quedado parcial en el backfill de Facturación |
| CourseEnrollmentController | 5 | ✅ | ✅ | alta automática "todo o nada" en todos los módulos (decisión 15), duplicado (409), módulo sin comisión (409), módulo ambiguo con sectionOverrides (400→201), ventana cerrada (400), baja con cascada |
| CourseSectionEnrollmentController | 2 | ✅ | ✅ | baja puntual de un módulo, doble baja (409), listar por módulo |
| SubjectSectionEnrollmentController | 4 | ✅ | ✅ | correlatividad no cumplida (400), materia fuera del plan (400), ya inscripto en otra comisión (409), cambio de comisión (200, crea fila nueva y da de baja la origen), cupo lleno con lista de espera FIFO y promoción automática al dar de baja (solo Karate) |
| MeAcademicRecordController | 2 | ✅ | pendiente | ownership 404 (no 403), caso vacío y caso con datos reales (asistencia+nota cargada por un Docente) — ver gap de `EvaluationScheme` abajo |

**Gap real encontrado (bloqueante de UI, no solo de testing)**: no existe
ningún endpoint que exponga o permita crear un `EvaluationScheme` — sin
uno asignado a la comisión, `GradeItemService` rechaza cualquier carga de
nota. Karate lo resolvió con un fixture Java nuevo
(`KarateFixtures.crearEvaluationScheme()`, vía un repository de solo
test, `EvaluationSchemeTestRepository`) — Bruno no tiene forma de
replicarlo, así que `MeAcademicRecordController` queda sin colección
Bruno por ahora. Detalle completo en "Deuda técnica y gaps de QA"
(Obsidian), entrada 5.

**Hallazgo operativo importante**: agregar los 3 features de Karate de
este dominio (23 escenarios) hizo que la suite completa (`mvn test`, una
sola JVM) fallara con `429` en `/auth/register` — el rate limiter
(`auth.rate-limit.register.capacity=20`) no tiene override en el perfil
`test`, y todos los `POST /auth/register` de toda la suite comparten el
mismo bucket. Se corrigió reemplazando los `/auth/register` de
`mi-cursada.feature` (que no necesitaban el endpoint real) por
`KarateFixtures.crearAlumnoConfirmado(...)`. Ver entrada 6 de "Deuda
técnica y gaps de QA" — **a tener en cuenta en cualquier backfill
futuro**: preferir el fixture Java sobre `/auth/register` real salvo que
el escenario testee específicamente ese endpoint.

## Evaluación y asistencia — cubierto (2026-08-28)

| Controller | Endpoints | Karate | Bruno | Notas |
|---|---|---|---|---|
| AttendanceController | 7 | ✅ | ❌ (gap) | comisión propia (403 NotOwnSectionException), justificar ausencia, listar por Preceptor/Administrativo/Director, riesgo de libre |
| GradeItemController | 4 | ✅ | ❌ (gap) | recálculo automático de status verificado end-to-end: 3 parciales aprobados → REGULAR, promedio insuficiente → FAILED sin llegar al final, final aprobado/desaprobado → APPROVED/FAILED |
| ExamBoardController | 4 | ✅ | ✅ | crear (organizada por Subject+Term, no por comisión — decisión 7), asignar tribunal, cerrar acta (idempotente), listar inscriptos |
| ExamBoardRegistrationController | 2 | ✅ | ✅ (parcial) | no-regular (400), materia no coincide (400, solo Karate), mesa cerrada (409), ventana vencida (400), doble inscripción (409, solo Karate), reinscripción tras baja reactiva la fila (solo Karate) |
| MeExamBoardRegistrationController | 2 | ✅ | ✅ (parcial) | mismo service que la versión staff; ownership es 404 no 403 (solo Karate) |

**Gap real, no un descuido**: `AttendanceController` y
`GradeItemController` quedan **sin ninguna cobertura Bruno** — ambos
exigen un Docente autenticado que integre la comisión, y no hay forma de
conseguir eso por HTTP puro (ver entradas 5 y 7 de "Deuda técnica y gaps
de QA": no hay endpoint para `EvaluationScheme`, y no hay forma de
loguearse como Docente sin completar el flujo real de
`forgot-password`/`reset-password`, que exige leer un token de un
email). Karate lo resuelve con fixtures Java
(`crearDocenteConToken()`, `crearEvaluationScheme()`).

También documentado: `Subject.maxFinalExamAttempts` no es configurable
por API (`CreateSubjectRequest`/`UpdateSubjectRequest` no lo exponen) —
la regla de tope de reintentos está implementada y probada a nivel
service, pero nunca se puede activar desde ninguna pantalla hoy. Entrada
8 de "Deuda técnica y gaps de QA".

## Total

155 endpoints, 33 controllers.

- **Cobertura de rigor completo en ambas herramientas: 24 de 33** —
  AuthController; PersonController, StaffController, ProspectController,
  StudentController (Gestión de personas); InvoiceController,
  MeInvoiceController, DirectorApprovalRequestController,
  MeCertificateController, GraduationRequestController,
  MeGraduationRequestController (Procesos administrativos);
  SubjectController, ProgramSubjectController, ElectiveGroupController,
  CourseSubjectController, CourseSectionController,
  SubjectSectionController (Estructura académica — estos 2 últimos con
  Bruno acotado, sin asignación de docente); ProgramEnrollmentController,
  CourseEnrollmentController, CourseSectionEnrollmentController,
  SubjectSectionEnrollmentController (Inscripciones académicas);
  ExamBoardController, ExamBoardRegistrationController,
  MeExamBoardRegistrationController (Evaluación y asistencia — estos 2
  últimos con Bruno parcial).
- **Karate completo pero sin Bruno: 3** —
  `MeAcademicRecordController`, `AttendanceController`,
  `GradeItemController`. Mismo tipo de gap real en los 3 (entidad sin
  endpoint HTTP de alta, o rol sin forma de loguearse por HTTP puro —
  ver "Deuda técnica y gaps de QA" en Obsidian, entradas 5 y 7).
- **Pendiente, sin backfill todavía: 6** — del dominio original "Auth +
  Autoservicio (Épica 7)", quedaron en `🟡 parcial` desde antes de que
  arrancara este backfill sistemático y nunca se retomaron:
  `InstitutionController`, `ProgramEnrollmentApplicationController`,
  `MeEnrollmentController`, `ProgramController`, `CourseController`,
  `TermController` — ver el detalle puntual de qué falta de cada uno en
  la tabla "Auth + Autoservicio" arriba (ej. `TermController` solo tiene
  cubierto el alta usada como setup por otros dominios, el resto de sus
  9 endpoints sin tocar).

Con esto se cierra el backfill de los 5 dominios grandes trabajados en
esta ronda de sesiones (Gestión de personas, Procesos administrativos,
Estructura académica, Inscripciones académicas, Evaluación y
asistencia) — el dominio original de Auth + Autoservicio queda como el
único con deuda pendiente real, ya acotada y explícita arriba.
