# Retención de datos operativos

Jobs nocturnos con ShedLock que borran datos operativos viejos en lotes. Nunca tocan datos académicos ni de facturación.

| Job | Hora | Qué borra |
|---|---|---|
| `IdentityDataRetentionJob` | 03:40 | `refresh_token` (incluidos los rotados), `password_reset_token` y `email_verification_token` vencidos (`expires_at < ahora`); `login_attempt` con más de 12 meses |
| `NotificationDataRetentionJob` | 03:50 | `notification_delivery` en estado terminal (`SENT`, `SENT_SIMULATED`, `SUPPRESSED`, `DISCARDED`, `FAILED`) con `scheduled_for` de más de 12 meses; `notification` de más de 12 meses |

## Qué se conserva

- Entregas `QUEUED` y `DEFERRED`: siguen en curso, no se borran nunca por antigüedad.
- Notificaciones sin leer que tienen `related_entity_id` (hay un trámite pendiente: al resolverlo el sistema las borra solo): no se borran. Sí se borran las leídas y las sin leer sin trámite asociado.
- Entregas de la categoría `COLLECTION_REMINDER`: 36 meses (no 12), porque el tablero de recordatorios de cobranza permite ventanas de hasta 36 meses (`MonthWindow.MAXIMUM_MONTHS`) y leería datos truncados.
- No hay clave foránea entre `notification` y `notification_delivery` (se relacionan por `dedup_key`, sin FK): el job borra primero entregas y después notificaciones. Los tokens rotados se borran al vencer; el hijo de un token borrado queda con `parent_id` nulo (`on delete set null`).

## Cómo ajustar los plazos

Propiedades `app.retention.*` (variable de entorno entre paréntesis), con valores por defecto:

| Propiedad | Defecto |
|---|---|
| `app.retention.enabled` (`RETENTION_ENABLED`) | `true`. Con `false` ningún job borra |
| `app.retention.login-attempt-months` (`RETENTION_LOGIN_ATTEMPT_MONTHS`) | 12 |
| `app.retention.notification-months` (`RETENTION_NOTIFICATION_MONTHS`) | 12 |
| `app.retention.notification-delivery-months` (`RETENTION_NOTIFICATION_DELIVERY_MONTHS`) | 12 |
| `app.retention.collection-reminder-delivery-months` (`RETENTION_COLLECTION_REMINDER_DELIVERY_MONTHS`) | 36 |
| `app.retention.batch-size` (`RETENTION_BATCH_SIZE`) | 1000 filas por transacción |
| `app.retention.max-batches-per-run` (`RETENTION_MAX_BATCHES_PER_RUN`) | 1000 lotes por tabla y corrida |

Valores menores a 1 hacen fallar el arranque. Para acortar el plazo de las entregas de cobranza por debajo de 36 meses hay que reducir también `MonthWindow.MAXIMUM_MONTHS`.

## Observabilidad

Contador Micrometer `retention.rows.deleted` (tag `table`) y un log por tabla con la cantidad de filas, sin datos personales. Los índices que sostienen el borrado por rango están en la migración V216.
