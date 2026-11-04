# Protección contra sobrecarga (Épica 24, fase 24.3)

Todas las propiedades se pueden sobrescribir por variable de entorno; los defaults sirven para correr local sin proxy.

## IP real detrás de un proxy

| Propiedad | Variable | Default | Efecto |
|---|---|---|---|
| `server.forward-headers-strategy` | `FORWARD_HEADERS_STRATEGY` | `none` | Con `none` se ignora `X-Forwarded-For`: nadie puede falsificar su IP. Con un proxy delante poner `native` |
| `server.tomcat.remoteip.internal-proxies` | `TRUSTED_PROXIES_REGEX` | solo loopback | Regex de IPs de proxies confiables. Tomcat solo respeta `X-Forwarded-For` si el pedido llega desde una de ellas |

Despliegue típico: `FORWARD_HEADERS_STRATEGY=native` y `TRUSTED_PROXIES_REGEX` con la IP (o red) del proxy del instituto. Poner `native` sin acotar la regex a los proxies reales permite falsificar la IP.

## Rate limit (en la app, buckets en memoria por instancia)

Un `429` lleva `Retry-After` (segundos), `code=REQUEST_RATE_LIMIT_EXCEEDED` y `retryable=true`. Capacidad y recarga por minuto:

| Ruta | Clave | Propiedades (`app.rate-limit.*`) | Default |
|---|---|---|---|
| `POST /auth/refresh` | IP | `refresh.capacity`, `refresh.refill-per-minute` | 30 |
| `POST /auth/reset-password` | IP | `reset-password.*` | 10 |
| `POST /prospects` (público) | IP | `prospects.*` | 10 |
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

## Límites de entrada

| Propiedad | Default |
|---|---|
| `spring.servlet.multipart.max-file-size` (`MULTIPART_MAX_FILE_SIZE`) | 5MB |
| `spring.servlet.multipart.max-request-size` (`MULTIPART_MAX_REQUEST_SIZE`) | 16MB (tres documentos de 5MB más campos; el alta de carrera los envía juntos) |
| `server.tomcat.max-swallow-size` | 2MB |
| `server.tomcat.max-http-form-post-size` | 2MB |
| `server.max-http-request-header-size` | 16KB |
| `spring.data.web.pageable.max-page-size` (`MAX_PAGE_SIZE`) | 200 |

Un archivo mayor al límite responde `400 MAX_UPLOAD_SIZE_EXCEEDED`. Los endpoints que reciben `page` y `size` a mano acotan el tamaño en su servicio (`MAX_PAGE_SIZE`); la propiedad de Spring cubre los que usen `Pageable` como parámetro.

## Cola de eventos de Modulith

`EventPublicationResubmissionJob` reintenta cada 5 minutos las publicaciones incompletas con más de 5 minutos. Cada reintento se cuenta en `event_publication_attempt`. Al llegar a `app.event-publication.resubmission.max-attempts` (`EVENT_RESUBMISSION_MAX_ATTEMPTS`, default 100, unas 8 horas) la publicación se mueve a `event_publication_dead_letter` (con el evento serializado, para reinyectarlo a mano) y se registra un `ERROR` con id, listener y tipo. Con facturación en `OFF`/`MIRROR` los listeners de billing se saltean y completan (`ModuleGuardAspect`), por lo que no acumulan publicaciones.
