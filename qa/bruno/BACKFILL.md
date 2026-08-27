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

## Gestión de personas — prioridad alta (alta de cuentas, roles)

| Controller | Endpoints | Karate | Bruno |
|---|---|---|---|
| PersonController | 2 | pendiente | pendiente |
| StaffController | 2 | pendiente | pendiente |
| ProspectController | 4 | pendiente | pendiente |
| StudentController | 4 | pendiente | pendiente |

## Procesos administrativos y financieros — prioridad alta (dinero)

| Controller | Endpoints | Karate | Bruno |
|---|---|---|---|
| InvoiceController | 5 | pendiente | pendiente |
| MeInvoiceController | 3 | pendiente | pendiente |
| MeCertificateController | 2 | pendiente | pendiente |
| GraduationRequestController | 4 | pendiente | pendiente |
| MeGraduationRequestController | 1 | pendiente | pendiente |
| DirectorApprovalRequestController | 4 | pendiente | pendiente |

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
herramientas: 1 de 33 (AuthController). El resto: parcial o pendiente — ver
tablas de arriba.
