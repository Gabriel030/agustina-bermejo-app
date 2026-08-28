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

## Procesos administrativos y financieros — prioridad alta (dinero)

| Controller | Endpoints | Karate | Bruno | Notas |
|---|---|---|---|---|
| InvoiceController | 5 | ✅ | ✅ | ciclo completo crear→pago parcial→exceder saldo (400)→completar→cancelar bloqueado (409); Preceptor sin acceso (403, decisión 14); Director R sin W (403); alumno inexistente (404) |
| MeInvoiceController | 3 | ✅ | ✅ | autoservicio: `/me/invoices`, `/me/invoices/debt-summary`, `/me/payment-standing` — cubiertos como parte del mismo ciclo de arriba |
| MeCertificateController | 2 | pendiente | pendiente | |
| GraduationRequestController | 4 | pendiente | pendiente | |
| MeGraduationRequestController | 1 | pendiente | pendiente | |
| DirectorApprovalRequestController | 4 | pendiente | pendiente | |

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

## Estructura académica / oferta

| Controller | Endpoints | Karate | Bruno |
|---|---|---|---|
| SubjectController | 6 | pendiente | pendiente |
| ProgramSubjectController | 9 | pendiente | pendiente |
| CourseSubjectController | 6 | pendiente | pendiente |
| ElectiveGroupController | 7 | pendiente | pendiente |
| CourseSectionController | 9 | pendiente | pendiente |
| SubjectSectionController | 9 | pendiente | pendiente |

## Inscripciones académicas

| Controller | Endpoints | Karate | Bruno |
|---|---|---|---|
| ProgramEnrollmentController | 5 | pendiente | pendiente |
| CourseEnrollmentController | 5 | pendiente | pendiente |
| SubjectSectionEnrollmentController | 4 | pendiente | pendiente |
| CourseSectionEnrollmentController | 2 | pendiente | pendiente |
| MeAcademicRecordController | 2 | pendiente | pendiente |

## Evaluación y asistencia

| Controller | Endpoints | Karate | Bruno |
|---|---|---|---|
| AttendanceController | 7 | pendiente | pendiente |
| GradeItemController | 4 | pendiente | pendiente |
| ExamBoardController | 4 | pendiente | pendiente |
| ExamBoardRegistrationController | 2 | pendiente | pendiente |
| MeExamBoardRegistrationController | 2 | pendiente | pendiente |

## Total

155 endpoints, 33 controllers. Con cobertura de rigor completo en ambas
herramientas: 7 de 33 (AuthController; PersonController, StaffController,
ProspectController, StudentController de Gestión de personas;
InvoiceController, MeInvoiceController de Procesos administrativos). El
resto: parcial o pendiente — ver tablas de arriba.
