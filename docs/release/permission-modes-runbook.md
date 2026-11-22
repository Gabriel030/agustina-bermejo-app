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

## Notas operativas

- Los contadores de discrepancias anteriores a la migración V288 no guardaban la dirección: se les asignó una según la grilla de ese momento y es aproximada. Para un criterio de salida limpio, conviene reiniciar los contadores de un módulo al pasarlo a Sombra.
- Los cambios de modo se auditan con la acción `PERMISSION_GRID_CHANGED` y el tipo `PERMISSION`, pero el identificador de la entidad es el de la fila de `permission_module_mode`, no el de un permiso del catálogo. El rol, el módulo y el modo anterior y nuevo están en los valores de antes y después, y el motivo en el motivo.
- Pasar un rol a Activo con su grilla vacía en ese módulo le cierra el acceso a todo ese módulo. El sistema no lo impide: revisá la grilla y las discrepancias antes, y recordá que volver a Legado siempre es posible y no depende de la grilla.
- Dos cambios de modo simultáneos sobre el mismo rol y módulo sin fila previa pueden hacer que el segundo responda 409: se repite el pedido. Solo el Director cambia modos, así que es poco probable.
