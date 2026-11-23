# Sentry (errores 5xx del backend)

Apagado por defecto. Con `SENTRY_DSN` vacío el SDK no se inicializa y no abre ninguna conexión.

## Activar

1. Crear un proyecto Java en Sentry y copiar el DSN.
2. Definir `SENTRY_DSN` en `deploy/.env`. `APP_ENV` (entorno) y `APP_VERSION` (release) se reutilizan.
3. Reiniciar el backend. Probar con un 5xx controlado en staging y revisar en Sentry que el evento llegó depurado.

## Qué se reporta

Solo los errores que el handler global responde como 5xx: el catch-all (`INTERNAL_ERROR`), `DataIntegrityBrokenException` y `ExternalServiceException`. Los 4xx y los 409 no se reportan. Cada evento lleva el tag `requestId`, el mismo del log y del header `X-Request-Id`.

La captura automática (MVC, logback, handler de excepciones no capturadas, sesiones) está desactivada para no duplicar eventos ni saltear el depurado. Tracing en 0, sin breadcrumbs, `sendDefaultPii=false`, sin cuerpo de request y sin configuración externa del SDK.

## Qué se depura antes de enviar (`SentryEventScrubber`)

- Se eliminan: cuerpo del request, cookies, query string completa (y todo lo que sigue a `?` en la URL), usuario, breadcrumbs y nombre del servidor.
- Se eliminan los headers `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`, `X-Auth-Token`, `X-CSRF-Token` y `X-XSRF-Token`.
- Se enmascaran con `[redacted]` en el mensaje, los valores de excepción, la URL y los headers restantes: JWT, emails, tokens largos URL-safe (32 caracteres o más, por ejemplo los de `/auth/activate`, `/auth/reset-password` y `/verificar-email`) y DNI (7 u 8 dígitos, con o sin puntos).

## Advertencia de privacidad

El depurado es por patrones y no puede garantizar que un mensaje de dominio no contenga nombres u otros datos personales, en especial de menores. Antes de activar en producción: confirmar el contrato con Sentry (región, retención, DPA), revisar los mensajes de excepción que incluyen datos de personas y no ampliar el envío (usuario, cuerpo, headers) sin revisión de seguridad.
