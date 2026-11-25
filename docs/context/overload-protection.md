# Protección contra sobrecarga (Épica 24, fase 24.3)

Todas las propiedades se pueden sobrescribir por variable de entorno; los defaults sirven para correr local sin proxy.

## IP real detrás de un proxy

| Propiedad | Variable | Default | Efecto |
|---|---|---|---|
| `server.forward-headers-strategy` | `FORWARD_HEADERS_STRATEGY` | `none` | Con `none` se ignora `X-Forwarded-For`: nadie puede falsificar su IP. Con un proxy delante poner `native` |
| `server.tomcat.remoteip.internal-proxies` | `TRUSTED_PROXIES_REGEX` | solo loopback | Regex de IPs de proxies confiables. Tomcat solo respeta `X-Forwarded-For` si el pedido llega desde una de ellas |

Despliegue típico: `FORWARD_HEADERS_STRATEGY=native` y `TRUSTED_PROXIES_REGEX` con la IP (o red) del proxy del instituto. Poner `native` sin acotar la regex a los proxies reales permite falsificar la IP.

**Verificación pública de certificados (`GET /public/certificates/{code}`):** el límite por IP usa `request.getRemoteAddr()` (IPv6 agrupado por /64). Sin `FORWARD_HEADERS_STRATEGY=native` y un `TRUSTED_PROXIES_REGEX` exacto a la IP del proxy, todos los visitantes comparten la IP del proxy y comparten un único bucket: un atacante lo agota y nadie puede verificar (DoS). Con la regex demasiado amplia se puede falsificar la IP y esquivar el límite. Es requisito de despliegue de esta ruta. Los logs de acceso de Tomcat y del proxy registran la URL con el código (es una credencial): desactivarlos o enmascararlos para `/public/certificates/` y `/verificar/`.

## Rate limit (en la app, buckets en memoria por instancia)

Un `429` lleva `Retry-After` (segundos), `code=REQUEST_RATE_LIMIT_EXCEEDED` y `retryable=true`. Capacidad y recarga por minuto:

| Ruta | Clave | Propiedades (`app.rate-limit.*`) | Default |
|---|---|---|---|
| `POST /auth/refresh` | IP | `refresh.capacity`, `refresh.refill-per-minute` | 30 |
| `POST /auth/reset-password` | IP | `reset-password.*` | 10 |
| `POST /prospects` (público) | IP | `prospects.*` | 10 |
| `POST /public/monitoring/tunnel` (solo con `SENTRY_DSN`) | IP, y además un tope global (clave única) tras el de IP | `refresh.*` por IP; `monitoring-tunnel-global.*` (`SENTRY_TUNNEL_GLOBAL_PER_MINUTE`) | 30 por IP; 120 global |
| Pedidos autenticados `GET`/`HEAD` | usuario | `user-read.*` | 600 |
| Pedidos autenticados de escritura | usuario | `user-write.*` | 120 |
| `/auth/login`, `/auth/google`, `/auth/register`, `/auth/forgot-password`, `/auth/verify-reset-code` | IP | `auth.rate-limit.*` (preexistente) | ver `application.properties` |

Memoria: cada limitador guarda como máximo `app.rate-limit.max-tracked-keys` (50000) claves; las inactivas más de 30 minutos se barren cada 10 minutos y, si el mapa se llena de claves activas, las nuevas comparten un bucket de desborde (el mapa no crece). Al haber varias instancias el límite es por instancia.

## Timeouts y pool

| Propiedad | Default | Qué acota |
|---|---|---|
| `spring.datasource.hikari.maximum-pool-size` (`DB_POOL_MAX_SIZE`) | 20 | Conexiones simultáneas |
| `spring.datasource.hikari.connection-timeout` (`DB_POOL_CONNECTION_TIMEOUT_MS`) | 10000 | Espera de una conexión libre |
| `spring.datasource.hikari.validation-timeout` | 5000 | Validación de conexión |
| `spring.datasource.hikari.max-lifetime` | 1740000 | Reciclado de conexiones |
| `spring.jpa.properties.jakarta.persistence.query.timeout` (`DB_QUERY_TIMEOUT_MS`) | 60000 | Consultas JPA (no cubre SQL nativo ni JDBC directo) |
| `data-source-properties.connectTimeout` / `socketTimeout` (`DB_CONNECT_TIMEOUT_SECONDS`, `DB_SOCKET_TIMEOUT_SECONDS`) | 10 / 300 | Conexión TCP y lectura del driver (red de seguridad) |
| `mercadopago.connection-timeout-ms` / `connection-request-timeout-ms` / `socket-timeout-ms` | 3000 / 3000 / 10000 | SDK de Mercado Pago; un timeout termina en `502 MERCADO_PAGO_GATEWAY` con `retryable=true` |
| `spring.mail.properties.mail.smtp.*timeout` | 5000 | Brevo (SMTP); ya existía |
| `app.mail.brevo.connect-timeout-ms` / `read-timeout-ms` | 3000 / 5000 | API HTTP de Brevo (`BrevoTransactionalEmailGateway`); un timeout cuenta como fallo del circuito `mail` |
| `resilience.<nombre>.max-wait` (`RESILIENCE_MAX_WAIT`) | 200ms | Espera de un permiso del bulkhead (ver más abajo) |

## Límites de entrada

| Propiedad | Default |
|---|---|
| `spring.servlet.multipart.max-file-size` (`MULTIPART_MAX_FILE_SIZE`) | 5MB |
| `spring.servlet.multipart.max-request-size` (`MULTIPART_MAX_REQUEST_SIZE`) | 10MB (techo de Spring; ver `MultipartRequestSizeFilter`) |
| `server.tomcat.max-swallow-size` | 2MB |
| `server.tomcat.max-http-form-post-size` | 2MB |
| `server.max-http-request-header-size` | 16KB |
| `spring.data.web.pageable.max-page-size` (`MAX_PAGE_SIZE`) | 200 |

Un archivo mayor al límite responde `400 MAX_UPLOAD_SIZE_EXCEEDED`. Spring solo admite un límite global de multipart, así que `MultipartRequestSizeFilter` aplica el tope por ruta usando `Content-Length`: 6 MB por solicitud multipart en todo el sistema y 10 MB en `POST /me/program-enrollment-applications/with-documents` (tres documentos, máximo 3 MB cada uno: `400 APPLICATION_DOCUMENT_TOO_LARGE`). Los endpoints que reciben `page` y `size` a mano acotan el tamaño en su servicio (`MAX_PAGE_SIZE`); la propiedad de Spring cubre los que usen `Pageable` como parámetro.

## Cola de eventos de Modulith

`EventPublicationResubmissionJob` reintenta cada 5 minutos las publicaciones incompletas con más de 5 minutos. Cada reintento se cuenta en `event_publication_attempt`. Al llegar a `app.event-publication.resubmission.max-attempts` (`EVENT_RESUBMISSION_MAX_ATTEMPTS`, default 100, unas 8 horas) la publicación se mueve a `event_publication_dead_letter` (con el evento serializado, para reinyectarlo a mano) y se registra un `ERROR` con id, listener y tipo. Con facturación en `OFF`/`MIRROR` los listeners de billing se saltean y completan (`ModuleGuardAspect`), por lo que no acumulan publicaciones.

## Circuit breakers

Propiedades por nombre: `resilience.<nombre>.window`, `failure-threshold`, `wait-open`; `enabled` hereda `resilience.default.enabled` (`RESILIENCE_ENABLED`). El default base es `false` (local y tests), pero los perfiles `prod` y `staging` (`application-prod.properties`; staging usa `STAGING_SPRING_PROFILES=prod`) lo arrancan en `true`. Con `enabled=false` el circuit breaker y el bulkhead son pass-through.

| Nombre | Propiedades | Cuenta como fallo | Respuesta con el circuito abierto |
|---|---|---|---|
| `mercadopago` | `RESILIENCE_MERCADOPAGO_WINDOW` (10), `_FAILURE_THRESHOLD` (5), `_WAIT_OPEN` (30s) | `MPException` (red/timeout) y `MPApiException` 5xx o 429; los 4xx no cuentan | `503 EXTERNAL_SERVICE_UNAVAILABLE` + `Retry-After` |
| `google` | `RESILIENCE_GOOGLE_WINDOW` (10), `_FAILURE_THRESHOLD` (5), `_WAIT_OPEN` (30s) | `IOException` al verificar el token (`502 GOOGLE_SERVICE_UNAVAILABLE`); un token inválido no cuenta | `503 EXTERNAL_SERVICE_UNAVAILABLE` + `Retry-After` |
| `mail` | Sin propiedades propias (hereda `resilience.default.*`) | Fallo de envío por SMTP o por la API HTTP de Brevo (red/timeout/5xx) | `503 EXTERNAL_SERVICE_UNAVAILABLE` + `Retry-After`; en jobs de notificación el envío queda para reintento |
| `whatsapp-meta` | `RESILIENCE_WHATSAPP_META_WINDOW` (10), `_FAILURE_THRESHOLD` (5), `_WAIT_OPEN` (30s) | Red/timeout y 5xx o 429 de la API de Meta; los 4xx no cuentan | `503 EXTERNAL_SERVICE_UNAVAILABLE` + `Retry-After`; los envíos quedan para reintento |

## Bulkhead

Un proveedor lento que no devuelve errores no abre el circuit breaker y ocupa hilos y conexiones hasta el timeout. El bulkhead (`shared/resilience/Bulkhead`) acota las llamadas simultáneas a cada integración con un semáforo: si no consigue permiso en `max-wait` rechaza con `CircuitOpenException` (`503 EXTERNAL_SERVICE_UNAVAILABLE` + `Retry-After`, sin código de error nuevo). El permiso se libera siempre en `finally`, aunque la llamada lance.

Propiedades por nombre: `resilience.<nombre>.max-concurrent` y `resilience.<nombre>.max-wait`, con fallback a `resilience.default.*` (`BulkheadFactory`, mismo patrón que `CircuitBreakerFactory`). `enabled` es el mismo `resilience.default.enabled`.

| Nombre | Variable | Default |
|---|---|---|
| `mail` | `RESILIENCE_MAIL_MAX_CONCURRENT` | 4 |
| `whatsapp-meta` | `RESILIENCE_WHATSAPP_META_MAX_CONCURRENT` | 4 |
| `mercadopago` | `RESILIENCE_MERCADOPAGO_MAX_CONCURRENT` | 4 |
| `google` | `RESILIENCE_GOOGLE_MAX_CONCURRENT` | 8 |
| resto (`default`) | `RESILIENCE_MAX_CONCURRENT` / `RESILIENCE_MAX_WAIT` | 8 / 200ms |

Composición: el bulkhead va por fuera del circuit breaker, con `Bulkhead.executeProtected(bulkhead, breaker, call[, countsAsFailure])`. Un rechazo del bulkhead ocurre antes de entrar al breaker, así que no cuenta como fallo del proveedor ni abre el circuito.

Métricas: `resilience.bulkhead.rejected{name}` (contador) y `resilience.bulkhead.available{name}` (permisos libres). Alerta sugerida: `rate(resilience_bulkhead_rejected_total[5m]) > 0` sostenido indica un proveedor lento o un límite chico.

Dónde está aplicado (S3-d):

| Punto | Comportamiento con el bulkhead lleno |
|---|---|
| `EmailDeliveryChannel.dispatchDelivery` (API HTTP de Brevo, nombre `mail`) | La entrega queda `QUEUED`/diferida sin consumir intento, igual que con el circuito abierto |
| `CircuitBreakingMailSender` (SMTP, `BrevoEmailService`, nombre `mail`) | `CircuitOpenException` => `503` + `Retry-After`; no toca SMTP ni cuenta como fallo |
| `WhatsAppDeliverySender.sendQueued` (`whatsapp-meta`) | La entrega queda sin consumir intento |
| `GoogleIdTokenService.verify` (`google`, `POST /auth/google`) | `503 EXTERNAL_SERVICE_UNAVAILABLE` + `Retry-After` (el mapeo de `CircuitOpenException` lo cubre `GlobalExceptionHandlerTest`; no hay MockMvc propio de `/auth/google` para no sumar un contexto de Spring); un token inválido sigue sin contar como fallo del circuito (`countsAsFailure`) |
| `SdkMercadoPagoPaymentFetcher.fetchApproved` (`mercadopago`) | `CircuitOpenException`; solo el fetcher de pagos, `createPreference` queda fuera (B4, D-226). El webhook (`MercadoPagoWebhookRegistration.register`, llamado desde `MpWebhookController`) no captura la excepción: sale como `503` + `Retry-After` y no se registra ningún pago, igual que con el circuito abierto; Mercado Pago reintenta las notificaciones que no reciben `2xx`, y el registro es idempotente por `mpPaymentId` |

Comportamiento conocido: en `BroadcastWhatsAppDispatchJob` con el bulkhead lleno el lote sigue iterando y cada entrega espera hasta `max-wait` (200ms) antes de quedar sin consumir intento; solo suma latencia y infla el contador `attempted`, no pierde ni consume entregas.

`mail` comparte un único bulkhead entre la API HTTP y SMTP (son dos transportes del mismo proveedor y nunca se anidan). Pendiente: S3-e (sacar la llamada HTTP de la transacción) y un pool propio para los jobs de envío.

## Señales de fallo

- `api.errors{status,code}`: contador de respuestas 5xx y de los 409/400 que salen de `IllegalStateException` / `IllegalArgumentException` (sospechosos de enmascarar bugs). El `code` sale del nombre de la excepción, así que la cardinalidad es acotada.
- `scheduled.job.failures{job,cause}`: fallos de cada job con `@Scheduled` + `@SchedulerLock`.
- `scheduled.job.last.success.epoch.seconds{job}`: instante de la última ejecución exitosa; permite detectar jobs que no corren.
- `scheduled.job.duration{job,outcome}`: duración por resultado (`success` / `failure`).
- Regla de alerta sugerida: `time() - scheduled_job_last_success_epoch_seconds{job="NightlyBillingJob"} > 90000`.
- `ScheduledJobCoverageTest` falla si aparece un `@Scheduled` sin `@SchedulerLock` fuera de los barridos por instancia declarados en el test.
