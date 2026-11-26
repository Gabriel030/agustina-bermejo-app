# Auditoría 24.4, lote 3c: autorización y ownership (rutas de personal)

Alcance: la tanda 3c que dejó pendiente `audit-24-4-batch-3b-authz.md` (sección 5): `billing` de personal, `graduation` de personal, `notification` (difusiones y plantillas) e `identity` (`StaffController`, `PermissionController`, `LegalDocumentController`, `AuditLogController`). Rutas de producción relativas a `backend/src/main/java/com/red_escuela/backend/`. Sin migración, sin cambio de contrato y sin tocar reglas de plata.

Resultado: no se encontró un hallazgo nuevo que corregir. Todo lo revisado queda como «verificado, sin cambio»; se agrega un IT de ownership donde no había ninguno.

## 1. `identity`

| Endpoint | Resultado | Evidencia |
|---|---|---|
| `PUT /staff/{id}`, `POST /staff/{id}/withdraw`, `reactivate`, `reset-password` | Verificado, sin cambio | `WithdrawStaffTransaction`, `StaffReactivator`, `UpdateStaffTransaction` y `ResetStaffPasswordService` resuelven el rol de personal del destino y exigen `StaffRoleAssignmentPolicy.requireCanAssign` / `requireManageableOrNotFound`. Un DIRECTOR no está en `STAFF_ROLES`, así que un ADMINISTRATIVO recibe 404 sobre él; sobre otro ADMINISTRATIVO o un SUBDIRECTOR, 404 o 403 según la jerarquía de `CREATABLE_ROLES_BY_ROLE` |
| `GET /staff/{id}` | Verificado, sin cambio | `GetStaffRecordService` filtra por `visibleRolesForCurrentActor` |
| `/permissions/**` | Verificado, sin cambio | sin `{id}`; `@PreAuthorize` por rol (DIRECTOR; lectura también SUBDIRECTOR) |
| `/audit-log` | Verificado, sin cambio | sin `{id}`; permiso `audit.read` (DIRECTOR) |
| `/legal/**`, `/me/legal-acceptances/**`, `/legal-acceptances` | Verificado, sin cambio | las rutas `/me` toman la persona del token; el listado de personal exige `identity.read_legal_acceptances` |

Doble rol: el único rol que la API puede sumar a una persona existente es `ALUMNO` (`StudentRoleGranter`); `CreateStaffTransaction` crea una persona con un solo rol de personal. Por eso no se alcanza el caso «personal con dos roles de personal» por la API, y el caso persona de personal con ficha de alumno ya lo cerró H-3b-1 en las rutas `/students/{id}`.

## 2. `billing` de personal

| Endpoint | Resultado | Evidencia |
|---|---|---|
| `POST /students/{studentId}/annual-upfront-requests/{requestId}/cash-payment` | Verificado; se agrega IT (antes ninguno para el alumno ajeno) | `RegisterAnnualUpfrontCashPaymentOperation:98-103` compara el alumno de la cuenta de la factura con el del path y responde `AnnualUpfrontRequestNotFoundException` (404); `AnnualUpfrontCashPaymentIT.chargingTheRequestUnderAnotherStudentIsNotFoundAndChargesNothing` |
| `/students/{studentId}/cash-payments`, `/refund-requests`, `/credit-balance`, `/current-account/allocations`, `/student-discounts`, `/credit-notes` | Verificado, sin cambio | el único id es el del path; los cuerpos (`RegisterCashPaymentRequest`, `RequestRefundRequest`, `CashPaymentPreviewRequest`) no traen ids de otros recursos |
| `POST /monthly-invoice-runs/{monthlyRunId}/items/{itemId}/retries` | Verificado, sin cambio | `FailedMonthlyInvoiceItemRetryProcessor.retryItemManually` filtra el ítem por la corrida del path |
| `POST /monthly-invoice-runs/{monthlyRunId}/draft-invoices/approvals` y descarte | Verificado, sin cambio | `DraftInvoiceLocking.lockPendingDraftsOfRun` llama a `Invoice.requirePendingDraftOfRun(monthlyRunId)` por cada id del cuerpo |
| `/collection-reminder-schedules/{group}/exceptions/{targetId}/**` y `/billing-runs/{nightlyRunId}/steps/{step}/items` | Verificado, sin cambio | rutas de configuración y lectura de personal con permiso por código; sin ownership por alumno |

## 3. `graduation` de personal

| Endpoint | Resultado | Evidencia |
|---|---|---|
| `/certificate-templates/{kind}/images/{id}` | Verificado, sin cambio | `CertificateTemplateReader.imageOf` usa `findByIdAndTemplateId`: una imagen de otro tipo de plantilla es 404 |
| `/certificate-exceptions/{id}/approve|reject` | Verificado, sin cambio | solo DIRECTOR (`certificate.exceptions_approve|reject`); `withdraw` solo de quien lo pidió |
| `/regular-certificates/{id}`, `/revoke`, `/pdf`, `/graduation-certificates/{id}/**`, `/exam-certificates/{id}/**`, `/approved-subjects-certificates/{id}/revoke`, `/certificate-signatories/{id}/**`, `/graduation-certificate-reviews/{graduationRequestId}/**` | Verificado, sin cambio | recursos de la institución, sin alcance por persona; permiso por código en cada ruta |
| Cuerpos con `enrollmentId`/`registrationIds` | Verificado, sin cambio | son operaciones de personal sobre cualquier matrícula; no hay un segundo id contra el cual cruzar |

## 4. `notification`

`/broadcasts/{id}/confirm|cancel|recipients`, `/message-templates/{id}` y `/institute-settings/notifications/whatsapp/templates`: recursos institucionales protegidos por permiso (`notification.confirm_broadcast`, `cancel_broadcast`, `read_broadcasts`, `*_message_template`, `*_whatsapp_template`); verificado, sin cambio. `InvitationWaveController` ya se había verificado en el lote 3b.

## 5. Columna `PLATFORM_ADMIN`

Sin cambio respecto del lote 3b: las filas nuevas de la matriz de este lote no se tocaron y `PlatformAdminIsolationTest` sigue pidiendo 403 fuera de las rutas propias.

## 6. Cobertura de la matriz

No se agregan filas a `authorization-matrix.csv`: ninguna ruta revisada en este lote tiene un dueño por recurso. La comprobación del caso del cobro anual con alumno ajeno es el IT de la sección 2.

## 7. Pendiente

Sin cortes: las cuatro áreas listadas por 3b quedan revisadas. Queda para el lote 4 lo que el líder ya tiene asignado aparte (H13 y H19).
