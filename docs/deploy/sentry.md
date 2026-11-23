# Sentry (errores 5xx del backend)

Apagado por defecto. Con `SENTRY_DSN` vacío el SDK no se inicializa y no abre ninguna conexión.

**No activar el DSN en ningún entorno real hasta que Gabriel resuelva tres cosas**: SaaS de Sentry o GlitchTip autoalojado, tunnel o ajuste de CSP para el front, y privacidad con datos de menores de edad.

## Activar (cuando esté resuelto lo anterior)

1. Crear un proyecto Java en Sentry (o GlitchTip) y copiar el DSN.
2. Definir `SENTRY_DSN` en `deploy/.env`. `APP_ENV` (entorno) y `APP_VERSION` (release) se reutilizan.
3. Reiniciar el backend. Probar con un 5xx controlado en staging y revisar en Sentry que el evento llegó depurado.

## Qué se reporta

Solo los errores que el handler global responde como 5xx: el catch-all (`INTERNAL_ERROR`), `DataIntegrityBrokenException` y `ExternalServiceException`. Los 4xx y los 409 no se reportan.

La captura automática (MVC, logback, handler de excepciones no capturadas, sesiones, hilos, client reports) está desactivada. Tracing en 0, sin breadcrumbs, `sendDefaultPii=false`, sin cuerpo de request y sin configuración externa del SDK.

## Qué se conserva (allowlist)

El `SentryEventScrubber` (`beforeSend`) deja el evento solo con:

- Clase de cada excepción y de cada causa, y sus stack frames (clase, método, archivo, línea; sin variables, sin código fuente ni rutas absolutas).
- Como valor de cada excepción y causa (reemplaza al mensaje): `code=<código de error>`; si es una `SQLException`, además `sqlState=<SQLSTATE>`; si es un `PSQLException`, además `constraint=<nombre del constraint>`. Nunca el `Detail` ni el `Failing row contains`.
- El tag `requestId`, solo si tiene formato UUID.
- `environment` y `release`.

Hoy `ExternalServiceException` no expone el status HTTP del tercero, así que solo se conserva su código.

## Qué se descarta

Mensaje del evento y parámetros, request completo (headers, cookies, body, query, URL), usuario, breadcrumbs, extras, contexts, threads, fingerprint, transaction, módulos, debug_meta, nombre del servidor y cualquier otro tag. El mensaje completo sigue en el log local con el mismo `requestId`.

Sobre lo que se conserva se aplica además un enmascarado por patrones (`[redacted]`): JWT, emails, CUIL/CUIT, teléfonos, tokens URL-safe de 32 caracteres o más y DNI (con puntos, espacios o comas), con el texto truncado a 2000 caracteres antes de enmascarar.

## Advertencia de privacidad

La allowlist evita que los mensajes de dominio y de base de datos (que pueden traer nombres de alumnos o familias, incluidos menores) salgan del backend. Aun así, los nombres de clase y de constraint son parte del evento. Antes de activar en producción: confirmar el contrato con el proveedor (región, retención, DPA) y no ampliar lo que se envía sin revisión de seguridad.
