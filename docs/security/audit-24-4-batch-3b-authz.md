# Auditoría 24.4, lote 3b: autorización y ownership

Alcance: `billing` (solo lecturas y autorización, sin tocar reglas de plata), `graduation`, `announcement`, `notification`, `identity` y `platform`. Verificado contra `origin/develop` (652e8d1b7, 26-nov-2026). Rutas de producción relativas a `backend/src/main/java/com/red_escuela/backend/`.

Hay unos 64 controllers con `{id}` en estos paquetes. Este lote cubre los 13 de la capa de ownership más sensible (rutas `/me`, recibos, pedidos de autorización, excepciones de constancia, alumnos). El resto queda como tanda 3c (sección 5).

## 1. Columna `PLATFORM_ADMIN` de `authorization-matrix.csv`

Verificado, sin cambio. Las filas permitidas a `PLATFORM_ADMIN` son solo rutas públicas, `GET /me`, `GET /me/modules`, `GET /public/modules`, `GET /backups/status` y las `/platform/**`. `PlatformAdminIsolationTest` pide 403 en toda ruta mapeada fuera de esas y `matrixNeverAllowsPlatformAdminOutsideItsOwnRoutes` rechaza cualquier fila nueva con `ALLOW` fuera de ellas. `PlatformFeatureController`, `PlatformAuditLogController` y `PlatformProvisioningController` solo permiten `PLATFORM_ADMIN` (filas `/platform/**`: DENY para los otros siete roles). `BackupStatusController` usa `hasAnyRole('DIRECTOR','PLATFORM_ADMIN')`, igual que la matriz.

## 2. Ownership de los `{id}` no públicos

| Endpoint | Resultado | Evidencia |
|---|---|---|
| `POST /me/announcements/{id}/read` | Verificado; se agrega fila de ownership e IT (antes ninguna) | `announcement/specialist/AnnouncementReadRecorder.java`: el alumno ajeno recibe `AnnouncementNotFoundException` (404 sin cuerpo) |
| `GET /me/course-enrollments/{id}/invoices` | Verificado; se agrega fila de ownership e IT (antes ninguna) | `billing/reporting/service/OwnCourseEnrollmentBillingStatusReader.java:26`, `NotOwnEnrollmentException` es 404 |
| `GET /me/program-enrollments/{id}/invoices` | Ídem | `OwnProgramEnrollmentBillingStatusReader.java:26` |
| `POST /me/invoices/{id}/mp-preference`, `/bank-transfer-receipts`, `GET /me/invoices/{id}/price-breakdown` | Verificado, sin cambio | `billing/service/InvoiceQueryService.java:137-142`; filas `invoice` de la matriz y `OwnershipBoundaryHttpTest` |
| `POST /me/bank-transfer-receipts` | Verificado, sin cambio | `SubmitBankTransferReceiptOperation.java:137-141` (A6/B6 de `audit-2026-11-03-status.md`) |
| `/bank-transfer-receipts/{id}/file`, `approve`, `reject` | Verificado, sin cambio | solo personal con `billing.read_bank_transfer_receipts` o `billing.review_bank_transfer_receipt`; el alumno queda en DENY |
| `GET /authorization-requests/{id}`, `POST .../withdraw`, `approve`, `reject` | Verificado, sin cambio | `billing/authorization/service/AuthorizationCallerAccess.java`: lectura y retiro ajenos son 404; `approve` y `reject` solo `ROLE_DIRECTOR`, así que el 403 de `requireMayDecide` no es alcanzable por quien no ve el pedido |
| `GET /me/certificates/{id}/pdf` | Verificado, sin cambio | `graduation/certificate/specialist/OwnGraduationCertificateDownloader.java` filtra por `studentId`; `MeGraduationCertificateHttpIT.anotherStudentCannotDownloadAndNothingIsAudited` |
| `GET /me/regular-certificates/{id}/pdf`, `DELETE /me/regular-certificate-requests/{id}` | Verificado, sin cambio | `StudentRegularCertificateDownloader` y `StudentRegularRequestCanceller` usan `findByIdAndStudentId` |
| `GET/POST /me/regular-certificates` y `/eligibility` (`enrollmentId` por query) | Verificado, sin cambio | `RegularEnrollmentFactsFinder.findOwnedBy(kind, enrollmentId, studentId)` |
| `GET /me/procedures/{type}/eligibility`, `/approved/{documentType}/{id}/pdf`, `POST /me/documents/{type}` | Verificado, sin cambio | las fuentes usan `findByIdAndStudentId` / `findActiveOwned` / `findAttendedOwned` y `OwnProgramEnrollmentResolver.requireOwnedBy`; lo ajeno es `ProcedureDocumentNotFoundException` |
| `GET /certificate-exceptions/{id}`, `POST .../withdraw` | Verificado, sin cambio | `CertificateExceptionReader.get` y `CertificateExceptionDecider.withdraw` devuelven 404 si no es quien lo pidió |
| `POST /subject-sections|course-sections/{sectionId}/audience-preview`, `GET .../audience-segments` | Verificado, sin cambio | `requireCurrentInstructorOf` en `ListAudienceSegmentsService` y `PreviewAudienceService` |
| `GET .../read-receipts`, `GET /announcements/{id}/recipients`, `PUT/DELETE /announcements/{id}` | Verificado, sin cambio | `StaffSectionAnnouncementReader` (instructor de la comisión salvo Director/Preceptor); filas de matriz existentes |
| `POST /me/notifications/{id}/read` | Verificado, sin cambio | `NotificationInboxManager` (A6) |
| `DELETE /me/sessions/{id}` | Verificado, sin cambio | fila `session` de la matriz |
| `/me/refund-requests`, `/me/credit-balance`, `/me/payments`, `/me/credit-notes`, `/me/student-discounts` | Verificado, sin cambio | `@PreAuthorize("hasRole('ALUMNO')")` y alumno resuelto desde el token; no reciben ids ajenos |

## 3. Hallazgo corregido

### H-3b-1 (Alta): las rutas de alumno cambiaban, daban de baja o reseteaban a personal con ficha de alumno

`PUT /students/{id}`, `POST /students/{id}/withdraw`, `POST /students/{id}/reactivate` y `POST /students/{id}/reset-password` operan sobre la persona de la ficha de alumno sin mirar sus otros roles. Con una persona que además es DIRECTOR, DOCENTE u otro rol de personal y tiene ficha de alumno (caso previsto por la matriz: `multirole.teacher.student`):

- `PUT /students/{id}` con `identity.withdraw_student` (ADMINISTRATIVO o DIRECTOR) cambia el email de la persona de inmediato (`identity/service/UpdateStudentTransaction.java` → `StudentEmailChanger.changeByStaff`), sin la aprobación del dueño que A4 exige en `PUT /staff/{id}`. Luego `POST /auth/forgot-password` sobre el email nuevo entrega la cuenta del DIRECTOR a un ADMINISTRATIVO: escalada de privilegios.
- `POST /students/{id}/withdraw` pone a la persona en `DELETED` y revoca sus sesiones: un ADMINISTRATIVO deja sin acceso a un DIRECTOR.
- `reactivate` y `reset-password` tampoco validaban el rol del destino.

Corrección: `identity/specialist/StudentOnlyTargetPolicy.requireStudentOnly(studentId)`; si la persona tiene algún rol distinto de `ALUMNO` lanza `StudentNotFoundException` (404, igual que un alumno inexistente). Se aplica en `UpdateStudentService`, `WithdrawStudentWithEnrollmentGuardService`, `ReactivateStudentService` y `ResetStudentPasswordService`, antes de cualquier efecto. Las lecturas (`GET /students/{id}`) no cambian. Sin migración ni cambio de contrato (los cuatro endpoints ya documentan 404).

Test que lo fija: `identity/controller/StudentRoutesStaffTargetIT` (cuatro casos: email de un DIRECTOR con ficha de alumno, baja de ese DIRECTOR, reactivación y reseteo sobre un DOCENTE con ficha, y el caso de control de un alumno solo-ALUMNO que sigue editable). Sabotaje: sin el cableado de la política los tres primeros fallan (`expected: 404`).

Decisión de producto a confirmar: los datos de un docente o directivo que también cursa se gestionan desde `/staff/{id}` (con aprobación del dueño para el email), no desde las rutas de alumno. Si Gabriel quiere permitir a la dirección editar alumnos con rol docente, se acota a una jerarquía (por ejemplo la de `StaffRoleAssignmentPolicy`) sin reabrir el cambio de email.

## 4. Ownership agregado a la matriz

Filas nuevas con columna `ownership` (el dueño pasa y el ajeno recibe 404, ejecutadas por `AuthorizationMatrixOwnershipTest`):

- `POST /me/announcements/{id}/read` → `announcement-recipient` (nuevo `OwnershipKind` y fixture con destinatario).
- `GET /me/course-enrollments/{id}/invoices` → `course-enrollment`.
- `GET /me/program-enrollments/{id}/invoices` → `program-enrollment`.

`OwnershipBoundaryHttpTest.foreignResourceLooksIdenticalToMissingOneForStudent` suma los tres casos (el ajeno y el inexistente devuelven lo mismo).

## 5. Corte y tanda 3c

Quedan sin revisar, por ser rutas de personal con permiso por rol sin ownership por recurso (se verifican con la matriz y `PermissionGateCoverageTest`): `billing` (`/students/{studentId}/**`, descuentos, promociones, precios, plantillas por año, corridas, notas de crédito, anulaciones, reversas, `annualupfront` de personal, `collections`, `configuration`, `reporting`), `graduation` de personal (plantillas, firmantes, `RegularCertificateController`, `GraduationRequestController`, `GraduationCertificateController`, `GraduationCertificateReviewController`, `DocumentDebtExceptionController`, `ApprovedSubjectsCertificateController`), `notification` (`InvitationWaveController`, `BroadcastLifecycleController`, plantillas), e `identity` (`StaffController`, `PermissionController`, `LegalDocumentController`, `AuditLogController`). Los lotes de ownership ya están en la matriz; lo pendiente es verificar que ninguno acepte ids de recursos de otro alcance en el cuerpo.
