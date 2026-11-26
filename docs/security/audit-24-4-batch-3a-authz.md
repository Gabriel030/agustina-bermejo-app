# E24 24.4 lote 3a: autorización y ownership (H14)

Alcance: controllers de `academicstructure`, `enrollment`, `attendance`, `evaluation` y `academicrecord`, acotado a 12 endpoints con `{id}` sin fila `ownership` en `authorization-matrix.csv`. Nota: `pentest-informe-2026-11.md` no existe en develop; se usó `audit-2026-11-03-status.md` y `pentest-interno.md`.

## Resultado

| endpoint | chequeo en código | resultado |
|---|---|---|
| `POST /course-sections/{id}/attendance` | `AttendanceSectionAccessChecker.requireOwnCourseSection` | verificado, sin cambio |
| `GET /course-sections/{id}/roster` | `GetCourseSectionRosterService` → `requireOwnCourseSection` | verificado, sin cambio |
| `GET /course-sections/{id}/class-sessions/{date}` | `GetCourseSectionClassSessionService` → `requireOwnCourseSection` | verificado, sin cambio |
| `POST /course-sections/{id}/class-sessions/{date}/cancel` | transacción de cancelación con `instructorId` | verificado, sin cambio |
| `GET /course-sections/{id}/grade-grid` | `TeacherGradeGridBuilder` → `requireTeaches` | verificado, sin cambio |
| `POST /course-sections/{id}/grade-items/publish` | `SectionGradeItemPublisher` → `requireTeaches` | verificado, sin cambio |
| `POST /course-sections/{id}/closure/confirm` | cierre de comisión | verificado, sin cambio |
| `PUT /course-sections/{id}/closure/students/{enrollmentId}/observation` | cierre de comisión | verificado, sin cambio |
| `PUT /exam-boards/{id}/final-grades` | `ExamBoardFinalGradesRecorder.requireOpenBoardOfTribunalMember` | verificado, sin cambio |

No se encontró ningún hueco de ownership: no hay cambio de código de producción.

## Cambios

- `authorization-matrix.csv`: 9 filas pasan de `ALLOW` a `OWNER` con columna `ownership` (`course-section-instructor` y `exam-board-tribunal`). `AuthorizationMatrixOwnershipTest` genera por cada una el caso docente de otra comisión → 404 y docente dueño → ni 403 ni 404.
- `OwnershipFailuresLookLikeMissingResourcesTest`: el 404 de un docente fuera del tribunal tiene el mismo cuerpo que el de una mesa inexistente (no filtra existencia).
- Sabotaje: sin `requireOwnCourseSection` en el roster y sin `requireTeaches` en la grilla de comisión, `AuthorizationMatrixOwnershipTest` falla en `GET /course-sections/{id}/roster` y `GET /course-sections/{id}/grade-grid`.

## Pendiente (no cubierto en este lote)

- `POST /grade-items/{id}/recovery` y `/me/section-evaluation-configs/{sectionId}`: faltan tipos de ownership en `OwnershipKind` y fixtures.
- `/me/course-enrollments/{id}/invoices`, `/me/program-enrollments/{id}/invoices`, `/me/exam-board-registrations/{id}/withdraw`, `/me/section-enrollments/{id}/change-section`: alumno `ALLOW` sin fila `ownership`.
- A7: `POST /attendance/{id}/justify` y `/announcements/{id}` (seguimiento E24 ya declarado).
- La columna `PLATFORM_ADMIN` figura en todas las filas revisadas y `AuthorizationMatrixTest` (5336 casos) pasa.

## Lote 3a-2 (rama apilada sobre #1577)

| endpoint | chequeo en código | resultado |
|---|---|---|
| `GET /me/course-enrollments/{id}/invoices` | `OwnCourseEnrollmentBillingStatusReader` (dueño, si no `NotOwnEnrollmentException` 404) | verificado, sin cambio; fila `ownership` `course-enrollment` |
| `GET /me/program-enrollments/{id}/invoices` | `OwnProgramEnrollmentBillingStatusReader` | verificado, sin cambio; fila `program-enrollment` |
| `POST /me/section-enrollments/{id}/change-section` | cambio de comisión del alumno | verificado, sin cambio; fila `section-enrollment` |

Sabotaje: sin el chequeo de dueño en `OwnCourseEnrollmentBillingStatusReader`, `AuthorizationMatrixOwnershipTest` falla en `GET /me/course-enrollments/{id}/invoices`.

Sigue pendiente (requiere tipos nuevos en `OwnershipKind` y fixtures): `POST /grade-items/{id}/recovery`, `/me/section-evaluation-configs/{sectionId}`, `POST /me/exam-board-registrations/{id}/withdraw`.
