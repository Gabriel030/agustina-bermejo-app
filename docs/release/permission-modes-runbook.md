# Modos de la grilla de permisos: operación y rollback

Todo lo de `/permissions/*` es solo para el Director. El modo es un dato por rol y módulo; lo que no está cargado se asume `LEGACY`.

| Modo | Quién decide | Qué registra |
|---|---|---|
| `LEGACY` | La regla vieja (`legacyRoles`) | Nada |
| `SHADOW` | La regla vieja | Cada vez que la grilla habría decidido distinto, con su dirección |
| `ENFORCE` (en pantalla "Activo") | La grilla | Nada |

## Volver un módulo a LEGACY sin deploy

`PUT /permissions/modes` con un motivo obligatorio (hasta 500 caracteres) y el cambio por rol y módulo:

```json
{"reason": "Se rompió el acceso de Docentes a avisos", "changes": [{"role": "DOCENTE", "module": "announcement", "mode": "LEGACY"}]}
```

Aplica al instante (se invalida la caché de la grilla al confirmar) y deja una entrada de auditoría `PERMISSION_GRID_CHANGED` con el modo anterior, el nuevo y el motivo. Un cambio que no modifica nada no audita. El rol `DIRECTOR` no se edita.

## Leer las discrepancias

- `GET /permissions/discrepancies/summary`: por módulo, cantidad por dirección, total y última vez.
- `GET /permissions/discrepancies?module=<módulo>`: detalle por rol y permiso con `direction` y `count`. Un módulo inexistente responde 422 `UNKNOWN_PERMISSION_MODULE`.

Direcciones:

- `LEGACY_ALLOWED_GRID_DENIED`: la regla vieja dejó pasar y la grilla habría negado. Pasar a `ENFORCE` le quitaría el acceso a alguien que hoy lo tiene.
- `LEGACY_DENIED_GRID_ALLOWED`: la regla vieja negó y la grilla habría permitido. Pasar a `ENFORCE` le daría acceso a alguien que hoy no lo tiene.

Los contadores se vuelcan a la base cada 30 segundos como máximo; las lecturas fuerzan el volcado antes de responder.

## Criterio para salir de SHADOW

Pasar un módulo a `ENFORCE` solo con cero discrepancias sin explicar durante al menos un ciclo de uso completo del módulo. Una discrepancia está explicada si corresponde a un cambio de grilla deliberado, registrado en la auditoría; el resto se corrige en la grilla (o en la regla vieja) y el conteo se vigila de nuevo.

## Limpieza final de legacyRoles

Quitar `legacyRoles` del código es una limpieza de código, no un cambio de configuración: después de ese deploy ya no existe regla vieja a la que volver y `PUT /permissions/modes` en `LEGACY` deja de tener efecto. Solo se hace con todos los módulos migrados en `ENFORCE` y estables. Revertirla exige un deploy.

## El Director en ENFORCE

El Director conserva todos los permisos de la grilla de forma implícita (decisión de Gabriel). Por eso, al pasar un módulo a Activo, el Director queda habilitado también en las escrituras que hoy le deniega la regla vigente. Es una discrepancia esperada, no un error: en Sombra se registra como divergencia y no hay que corregirla.

### Attendance

Endpoints que hoy deniegan al Director y pasarían a permitirlo en Activo:

- POST /program-sections/{id}/attendance y POST /course-sections/{id}/attendance (attendance.record)
- GET /program-sections/{id}/roster y GET /course-sections/{id}/roster (attendance.record)
- GET /program-sections/{id}/class-sessions/{date} y GET /course-sections/{id}/class-sessions/{date} (attendance.record)
- POST /program-sections/{id}/class-sessions/{date}/cancel y POST /course-sections/{id}/class-sessions/{date}/cancel (attendance.cancel_class_session)
- POST /attendance/{id}/justify (attendance.justify)
- POST /attendance/{id}/justification-proof (attendance.submit_justification_proof)
- GET /attendance/justifications y GET /attendance/{id}/justification-attachment (attendance.read_justifications)
- POST /attendance/{id}/approve-justification y POST /attendance/{id}/reject-justification (attendance.review_justification)
- POST /attendance/{id}/change-requests y GET /me/attendance-change-requests (attendance.request_change)
- GET /attendance/change-requests (attendance.read_change_requests)
- POST /attendance/change-requests/{id}/approve y POST /attendance/change-requests/{id}/reject (attendance.review_change_request)

Los endpoints de lectura de historial, informe de ausencias, exportación y asistencia por inscripción ya permiten al Director y no cambian.

### Graduation

Endpoint que hoy deniega al Director y pasaría a permitirlo en Activo:

- POST /graduation-requests/{id}/verify-debt (graduation.verify_debt)

Los permisos certificate.* usados por los certificados de egreso y sus plantillas pertenecen al módulo certificate: su modo se configura aparte del de graduation, pero conviene prender graduation y certificate juntos en Activo, porque el egreso y sus certificados se usan en un mismo circuito. Las constancias de #1103, las rutas de alumno y las públicas no usan la grilla. Los permisos certificate.revoke, certificate.issue y certificate.templates_manage pasan a ser sensibles.

### Identity

Endpoints que hoy deniegan al Director y pasarían a permitirlo en Activo:

- POST /students/{id}/reset-password (identity.reset_student_password): hoy solo lo permiten Preceptor y Administrativo (discrepancia esperada, #1216).

El resto de los endpoints migrados de identity ya permiten al Director y no cambian.

### Enrollment

Endpoints que hoy deniegan al Director y pasarían a permitirlo en Activo:

- GET /program-enrollment-applications, /pending, /history, /{id}, /{id}/approval-check y /{id}/documents/{documentId}/file (enrollment.read_program_applications)
- POST /program-enrollment-applications/{id}/approve y /reject (enrollment.review_program_application)
- POST /program-enrollments/{id}/advance-billing-year (enrollment.advance_billing_year)
- POST /program-enrollments/{id}/invoices/ad-hoc y POST /course-enrollments/{id}/invoices/ad-hoc (enrollment.create_ad_hoc_invoice)
- POST /director-approval-requests (enrollment.request_director_approval)

Los endpoints del alumno (/me/*), el alta pública de interesados y las decisiones sobre pedidos de aprobación (listar, ver, aprobar, rechazar) quedan fuera de la grilla.

### Billing (tanda A2)

Lecturas y previews sin efecto del staff sobre entidades con plata (16 permisos `billing.*`, V381, 40 endpoints). Las escrituras, la cobranza, los recordatorios, los reportes y las corridas quedan en otras tandas, y el módulo sigue en LEGACY. Ningún permiso es sensible. Alumno (`/me/*`), webhook de Mercado Pago, configuración de pagos, plan de pagos, cohortes y los términos del pago anual `current` y `versions/{n}` (mixtos) quedan fuera.

Ningún endpoint de esta tanda deniega hoy al Director: las reglas vigentes son Administrativo y Director, o solo Director. Pasar el módulo a Activo no cambia su resultado.

Permisos que hoy solo permite el Director y no se siembran para ningún otro rol (se delegan desde la grilla):

- POST /payments/{id}/approval-revocation/preview (billing.preview_approval_revocation)
- GET /annual-upfront-terms/versions (billing.read_annual_upfront_terms_versions)

Detalle de la migración: las reglas de los services (`@PreAuthorize`) de los métodos de staff de estos endpoints se movieron a `legacyRoles` del controller; en Activo un `@PreAuthorize` en el service anularía la grilla. Los métodos de alumno de los services compartidos conservan su regla.

### Academicstructure

Endpoints que hoy deniegan al Director y pasarían a permitirlo en Activo:

- POST /programs, PUT /programs/{id}, POST /programs/{id}/subjects, PUT /program-subjects/{id}, POST y DELETE de prerrequisitos y correquisitos de materias de carrera, POST/PUT /elective-groups y POST/DELETE /programs/{id}/elective-groups (academicstructure.manage_programs)
- POST /courses, PUT /courses/{id}, POST /courses/{id}/subjects y PUT /course-subjects/{id} (academicstructure.manage_courses)
- POST activate/deactivate de carreras y materias de carrera (academicstructure.change_program_status)
- POST activate/deactivate de cursos y materias de curso (academicstructure.change_course_status)
- PUT /programs/{id}/catalog-visibility y PUT /courses/{id}/catalog-visibility (academicstructure.change_catalog_visibility)
- POST /holidays y PUT /holidays/{id} (academicstructure.manage_holidays); DELETE /holidays/{id} (academicstructure.delete_holiday)
- POST /subjects, PUT /subjects/{id} y POST /subjects/{id}/submit-for-approval (academicstructure.manage_subjects)
- POST /academic-offering-cycles, POST /academic-offering-cycles/{id}/sections y POST /academic-offering-cycles/{id}/submit (academicstructure.manage_offering_cycle)
- POST /classrooms y POST /classrooms/{id}/deactivate (academicstructure.manage_classrooms)
- POST /subject-sections/{id}/modification-request y /modification-request/preview (academicstructure.request_section_modification)
- POST /subjects/{id}/sections, PUT /program-sections/{id}, POST /course-subjects/{id}/sections y PUT /course-sections/{id} (academicstructure.manage_sections)
- POST activate/deactivate de comisiones de materias y de cursos (academicstructure.change_section_status)
- POST /program-sections/{id}/instructors y /course-sections/{id}/instructors (academicstructure.assign_section_instructor)
- DELETE /program-sections/{id}/instructors/{assignmentId} y /course-sections/{id}/instructors/{assignmentId} (academicstructure.remove_section_instructor)

El resto de los endpoints migrados de academicstructure ya permite al Director y no cambia. Las decisiones exclusivas del Director (aprobar o rechazar ciclos de oferta, materias y pedidos de cambio de horario, ver los pedidos pendientes y definir los rangos de turnos) quedan como permisos sin concesiones a otros roles: el Director las conserva.

Las lecturas abiertas a cualquier usuario autenticado (listados y detalles de carreras, cursos, materias, comisiones, ciclos, aulas, feriados, períodos y rangos de turnos) y GET /me/teaching-sections quedan fuera de la grilla.

### Evaluation

Endpoints que hoy deniegan al Director y pasarían a permitirlo en Activo:

- POST /exam-boards, POST /exam-boards/{id}/tribunal, POST /exam-boards/{id}/reschedule, POST /exam-boards/{id}/registrations y POST /exam-board-registrations/{id}/withdraw (evaluation.manage_exam_boards)
- POST /exam-boards/{id}/cancel (evaluation.cancel_exam_board): hoy el Director no puede cancelar mesas; en Activo sí
- POST /exam-boards/{id}/close (evaluation.close_exam_board)
- PUT /exam-boards/{id}/final-grades (evaluation.record_exam_board_final_grades)
- POST /section-enrollments/{id}/grade-items y POST /course-section-enrollments/{id}/grade-items (evaluation.load_grade_item)
- POST /program-sections/{id}/grade-items/publish y POST /course-sections/{id}/grade-items/publish (evaluation.publish_grades)
- POST /grade-items/{id}/recovery (evaluation.record_exam_recovery)
- GET /program-sections/{id}/grade-grid y GET /course-sections/{id}/grade-grid (evaluation.read_grade_grid)
- POST /program-sections/{id}/closure/confirm, POST /course-sections/{id}/closure/confirm y los PUT closure/students/{enrollmentId}/observation (evaluation.confirm_commission_closure)
- POST /program-sections/{id}/closure/approve y POST /course-sections/{id}/closure/approve (evaluation.approve_commission_closure)

Los endpoints del alumno (/me/*) y la configuración de evaluación del docente (/me/section-evaluation-configs) quedan fuera de la grilla. El resto de los endpoints migrados de evaluation ya permiten al Director y no cambian.

De esos endpoints, load_grade_item, publish_grades, record_exam_recovery, read_grade_grid, confirm_commission_closure (confirmar y observación) y record_exam_board_final_grades dejan pasar al Director en Activo, pero el handler resuelve el instructor actual y, si el Director no tiene ficha de instructor, falla con InstructorNotFoundException (404): en la práctica sigue sin poder hacer acciones de docente salvo que tenga esa ficha. Esto viene de la resolución del instructor actual (currentInstructorId): el Director sin ficha de instructor no pasa en Activo lo que dependa de ella. requireCurrentInstructorOf, en cambio, deja pasar al Director sin ficha, así que no entra en este caso.

### Billing (tanda A1: lecturas, cobranza y configuración de recordatorios)

Todos los endpoints migrados permiten hoy al Director (reglas AD, solo Director o Administrativo, Director y Preceptor), así que ningún endpoint de esta tanda cambia de resultado para el Director en Activo. El módulo sigue en Legado y billing no está completo: el resto de los endpoints (plata, corridas con efecto, alumno /me, webhook, planes de pago, términos de pago anual) llegan en las tandas siguientes.

Migrados (33 endpoints, permisos billing.*): payment-standing de inscripciones (read_payment_standing, con Preceptor), su historial, contact-list, impact, configuración y cronogramas de recordatorios (read_reminder_config y manage_reminder_config), métricas, tablero financiero, deudores (read_debtors, solo Director, sin grant a otros roles), billing-overview de un alumno, alertas de configuración, corridas nocturnas y mensuales (lecturas, read_billing_runs), simulación de corrida mensual (simulate_monthly_run, persiste una corrida simulada sin emitir facturas) y late-payment-settings/current.

manage_reminder_config queda no sensible porque solo propone y valida cambios: la aprobación, que sí tiene efecto, queda fuera de esta tanda y no se puede autoaprobar. Pasaría a ser sensible solo si una tanda futura pusiera la publicación directa bajo ese permiso.

### Billing (tanda B2: corridas, descuentos, promociones, precios, plantillas, recargos, pasarela y aprobaciones)

Los 29 endpoints migrados (V410, 20 permisos billing.*) mueven plata o cambian su configuración, por eso los 20 permisos son sensibles: delegarlos exige la confirmación de sensible. El Director conserva todos en Activo: ningún endpoint de la tanda le está denegado hoy, así que no cambia de resultado en ninguno. Alumno y administrador de plataforma quedan fuera de la grilla; el Subdirector hereda lo de Administrativo.

Seed a Administrativo (reglas AD): retry_draft_invoice_items, approve_draft_invoices, discard_draft_invoices, manage_discount_definition (crear y cerrar), grant_student_discount (carrera y curso), revoke_student_discount, propose_price_change (POST /prices/{id}/versions), manage_year_billing_cohorts (crear cohorte y descuento de matrícula), withdraw_authorization_request y request_advance_enrollment_fee (mostrador, no el /me del alumno).

Decisiones exclusivas del Director, sin grants (solo se delegan con confirmación de sensible): run_billing (POST /billing-runs/executions), issue_monthly_invoices_manually, manage_promotion (crear y cerrar), cancel_price_version, manage_payment_plan_options, manage_year_billing_template (crear y configurar), set_late_payment_settings, manage_payment_gateway (PUT /payment-config y QR), decide_authorization_request (aprobar y rechazar) y publish_annual_upfront_terms.

decide_authorization_request es exclusivo del Director (fixed_to_director, V426): PUT /permissions/grid rechaza delegarlo con 422 FIXED_TO_DIRECTOR_PERMISSION_NOT_EDITABLE y la pantalla de permisos lo muestra sin casilla. El servicio además exige el rol Director para decidir y que quien decide no sea quien pidió; la bandeja completa también es del Director (un Administrativo solo ve las suyas).

Delegar billing.manage_payment_gateway (CBU y QR), billing.set_late_payment_settings o billing.approve_draft_invoices hace que el delegado aplique el cambio directo, sin segunda aprobación. manage_payment_gateway, run_billing e issue_monthly_invoices_manually son solo del Director y se dejan sin grants. Lo que decide si algo se aplica directo o queda como solicitud pendiente depende hoy del rol Director real (resolvers de actor), no de la grilla: un delegado no Director de manage_year_billing_template o manage_year_billing_cohorts genera una solicitud pendiente. Por eso billing se queda en Legado: no pasar a Sombra real ni a Activo hasta resolver el Issue #1254.

Fuera de esta tanda: /me/*, webhook de Mercado Pago, GET payment-config/qr-image, POST payment-plan (Administrativo o Alumno), cohortes y términos de pago anual de lectura (mixtos con Alumno), previews y lecturas (A1/A2) y cobros y devoluciones (B1).

### Billing (tanda B1: cobros, reversas, reembolsos, notas de crédito y anulaciones)

Los 13 permisos de esta tanda son SENSIBLES: delegarlos o revocarlos desde la grilla exige confirmación explícita y queda auditado como evento destacado. Migrados (14 endpoints): pago en efectivo (register_cash_payment), pago anual adelantado en efectivo (register_annual_upfront_cash_payment), carga de comprobante por el staff (submit_bank_transfer_receipt), aprobar y rechazar comprobantes (review_bank_transfer_receipt), revocar aprobación de un pago (revoke_payment_approval, solo Director en Legado, sin grant a otros roles), contracargos y su reversa (register_chargeback, reverse_chargeback), reembolsos (request_refund, complete_refund, fail_refund), nota de crédito (issue_credit_note), anulación de factura (cancel_invoice) y aplicación del saldo a favor (allocate_account_credit).

Ningún endpoint de esta tanda cambia de resultado para el Director en Activo: todas las reglas vigentes lo permiten. Quedan fuera los previews (tanda A2), /me, el webhook de la pasarela y la tanda B2. Se quitó el @PreAuthorize de los métodos de staff de los services; los métodos de alumno de SubmitBankTransferReceiptService y RequestRefundService conservan el suyo. El módulo sigue en Legado.

## Billing: estado

Efecto fail-safe de los permisos de dinero delegados (Issue #1254): los resolvers de actor deciden por el rol Director real, así que un rol con un permiso delegado actúa como Administrativo: nunca aplica directo. Descuentos (definir, cerrar, otorgar, revocar), versiones de precio, reembolsos, notas de crédito, anulaciones y el descuento de matrícula de cohortes y la configuración de plantillas dejan una solicitud pendiente de aprobación del Director; el pago en efectivo de más de 5 días atrás se rechaza. Cada descripción de permiso afectado lo dice (V426). decide_authorization_request no es delegable. `DelegatedBillingRoleFailSafeIT` lo verifica en Legado y Activo para Director, Administrativo y un rol delegado. Quedan sin red de seguridad (un delegado aplica directo, no hay solicitud): los permisos solo del Director sin flujo de aprobación (run_billing, issue_monthly_invoices_manually, manage_promotion, cancel_price_version, manage_payment_plan_options, crear plantilla, set_late_payment_settings, manage_payment_gateway, publish_annual_upfront_terms, revoke_payment_approval). Billing sigue en Legado hasta resolverlo.

Billing queda en LEGACY hasta resolver el Issue #1254 (el resultado de aplicar directo o dejar pendiente depende del rol Director real y no de la grilla): no pasar a Sombra real ni a Activo antes. Sus 33 permisos monetarios sensibles son los 13 de B1 más los 20 de B2; los de lectura (A1 y A2) no. Todo endpoint de billing sin `@RequiresPermission` está en una lista explícita por endpoint de `PermissionGateCoverageTest` y son 33 endpoints: `/me/*` de alumno, webhook de Mercado Pago, mixtos (ADMINISTRATIVO o ALUMNO, cohorts GET, términos del pago anual `current` y `versions/{n}` con ALUMNO, ADMINISTRATIVO o DIRECTOR) y lecturas sin rol (payment-config GET, qr-image y listProgramCohorts). El test falla si aparece uno nuevo sin gate.

GET /payment-config y GET /payment-config/qr-image son visibles para cualquier usuario autenticado salvo el administrador de plataforma, por decisión de producto (el alumno necesita CBU y QR para pagar); por eso están en `BILLING_ENDPOINTS_WITHOUT_GRID_RULE` y no llevan `@RequiresPermission`. Cambiar CBU o QR sigue siendo `billing.manage_payment_gateway`, solo Director. `PaymentConfigReadRuleTest` fija esta regla contra la matriz de autorización.

## Academicrecord

El módulo no tiene permisos en la grilla: sus cinco controllers son del alumno (/me/*) o públicos con token (/public/calendar/{token}.ics). No hay migración de permisos ni cambio de resultado para el Director en ENFORCE.

## Notas operativas

- Los contadores de discrepancias anteriores a la migración V288 no guardaban la dirección: se les asignó una según la grilla de ese momento y es aproximada. Para un criterio de salida limpio, conviene reiniciar los contadores de un módulo al pasarlo a Sombra.
- Los cambios de modo se auditan con la acción `PERMISSION_GRID_CHANGED` y el tipo `PERMISSION`, pero el identificador de la entidad es el de la fila de `permission_module_mode`, no el de un permiso del catálogo. El rol, el módulo y el modo anterior y nuevo están en los valores de antes y después, y el motivo en el motivo.
- Pasar un rol a Activo con su grilla vacía en ese módulo le cierra el acceso a todo ese módulo. El sistema no lo impide: revisá la grilla y las discrepancias antes, y recordá que volver a Legado siempre es posible y no depende de la grilla.
- Dos cambios de modo simultáneos sobre el mismo rol y módulo sin fila previa pueden hacer que el segundo responda 409: se repite el pedido. Solo el Director cambia modos, así que es poco probable.
