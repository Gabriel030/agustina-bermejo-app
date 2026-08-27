# Backfill de cobertura QA — seguimiento

No es documentación de producto, es el checklist de avance del backfill
completo de `qa/bruno/` sobre los 155 endpoints existentes (decisión del
usuario, 2026-08-27: cubrir todo antes de seguir con fases nuevas del
roadmap, no solo hacia adelante). Se actualiza en cada sesión que agrega
cobertura — mirar acá primero antes de asumir qué falta.

Convención: cada fila es un controller. Estado `✅ cubierto` significa
happy path + bordes reales (auth, ownership, validación, duplicados,
ids inexistentes) para cada endpoint — no solo un smoke test de 200.
`🟡 parcial` anota qué falta puntualmente.

## Auth + Autoservicio (Épica 7) — ya cubierto

| Controller | Endpoints | Estado |
|---|---|---|
| AuthController | 6 | ✅ (login, register, refresh, logout — falta forgot-password/reset-password) |
| InstitutionController | 1 | ✅ |
| ProgramEnrollmentApplicationController | 6 | ✅ (solicitar/listar/aprobar/rechazar/propia — falta `agregarDocumento`) |
| MeEnrollmentController | 7 | 🟡 parcial (`course-enrollments` ✅; `program-enrollments` GET, `section-enrollments` POST/GET/withdraw/change-section sin cubrir) |
| ProgramController | 6 | 🟡 parcial (GET listar/detalle ✅ vía Autoservicio; crear/editar/activar/desactivar sin cubrir) |
| CourseController | 6 | 🟡 parcial (mismo criterio que ProgramController) |

## Gestión de personas — EN PROGRESO (prioridad alta: alta de cuentas, roles)

| Controller | Endpoints | Estado |
|---|---|---|
| PersonController | 2 | pendiente |
| StaffController | 2 | pendiente |
| ProspectController | 4 | pendiente |
| StudentController | 4 | pendiente |

## Procesos administrativos y financieros — prioridad alta (dinero)

| Controller | Endpoints | Estado |
|---|---|---|
| InvoiceController | 5 | pendiente |
| MeInvoiceController | 3 | pendiente |
| MeCertificateController | 2 | pendiente |
| GraduationRequestController | 4 | pendiente |
| MeGraduationRequestController | 1 | pendiente |
| DirectorApprovalRequestController | 4 | pendiente |

## Estructura académica / oferta

| Controller | Endpoints | Estado |
|---|---|---|
| TermController | 9 | pendiente |
| SubjectController | 6 | pendiente |
| ProgramSubjectController | 9 | pendiente |
| CourseSubjectController | 6 | pendiente |
| ElectiveGroupController | 7 | pendiente |
| CourseSectionController | 9 | pendiente |
| SubjectSectionController | 9 | pendiente |

## Inscripciones académicas

| Controller | Endpoints | Estado |
|---|---|---|
| ProgramEnrollmentController | 5 | pendiente |
| CourseEnrollmentController | 5 | pendiente |
| SubjectSectionEnrollmentController | 4 | pendiente |
| CourseSectionEnrollmentController | 2 | pendiente |
| MeAcademicRecordController | 2 | pendiente |

## Evaluación y asistencia

| Controller | Endpoints | Estado |
|---|---|---|
| AttendanceController | 7 | pendiente |
| GradeItemController | 4 | pendiente |
| ExamBoardController | 4 | pendiente |
| ExamBoardRegistrationController | 2 | pendiente |
| MeExamBoardRegistrationController | 2 | pendiente |

## Total

155 endpoints, 33 controllers. Cubiertos con rigor completo (✅): 2 de 33
(AuthController parcial, InstitutionController). El resto: pendiente o
parcial — ver tablas de arriba para el detalle exacto.
