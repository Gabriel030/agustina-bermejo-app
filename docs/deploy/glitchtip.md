# GlitchTip autoalojado (reporte de errores)

Proveedor de errores decidido por Gabriel el 25-nov-2026 (Issue #1462): GlitchTip en el servidor propio, para que los datos de menores no salgan de la infraestructura del instituto. El SDK de Sentry es el mismo, así que activar es cargar un DSN (ver `docs/deploy/sentry.md`). Este documento cubre el overlay `deploy/glitchtip/docker-compose.glitchtip.yml`. **Ningún DSN real está cargado en el repo ni en ningún ejemplo; no hay servidor ni dominio definidos.**

## Versión y fuente de los nombres

- Versión elegida: **GlitchTip 7.0.0**, imagen `glitchtip/glitchtip:7.0.0` (tag explícito; `latest` está prohibido y `GLITCHTIP_IMAGE` no tiene default). Es el último tag estable de Docker Hub al 25-nov-2026 (publicado el 9-nov-2026). Un tag no es inmutable: para fijarla del todo, usar el digest (`glitchtip/glitchtip:7.0.0@sha256:...`) tomado en el servidor.
- **NO VERIFICADO**: la documentación oficial de instalación (`https://glitchtip.com/documentation/install`) no pudo abrirse desde el entorno donde se armó el overlay (403 del proxy). Los nombres salen del **código fuente del tag `v7.0.0`** del repositorio oficial `gitlab.com/glitchtip/glitchtip-backend` y de Docker Hub:

| Dato | Fuente |
|---|---|
| Imagen, tags | Docker Hub `glitchtip/glitchtip` (tags `7.0.0`, `7.0`, `7`) |
| `SECRET_KEY`, `DATABASE_URL`, `ALLOWED_HOSTS`, `GLITCHTIP_DOMAIN`, `ENABLE_USER_REGISTRATION`, `ENABLE_ORGANIZATION_CREATION`, `EMAIL_BACKEND`, `GLITCHTIP_RETENTION_DAYS`, `GLITCHTIP_ENABLE_UPTIME`, `VALKEY_URL` | `glitchtip/settings.py` del tag `v7.0.0` |
| Un solo proceso (web + worker + scheduler), puerto 8000, usuario `app` | `bin/start.sh`, `bin/run-all-in-one.sh` y `Dockerfile` del tag `v7.0.0` |
| Healthcheck `python healthcheck.py` (`GET /_health/` en el 8000) | `healthcheck.py` del tag `v7.0.0` |
| Postgres 18 con `max_locks_per_transaction=512` | `compose.yml` de desarrollo del repositorio (su comentario dice que coincide con lo que la documentación recomienda a quien se autoaloja) |

Antes del primer arranque real, **confirmar cada nombre contra la documentación oficial** (p. ej. si `ENABLE_ORGANIZATION_CREATION=false` impide crear la primera organización con la cuenta de administración).

## Qué levanta el overlay

- `glitchtip`: **un solo servicio**. En 7.0.0 el mismo proceso atiende HTTP, consume la cola de tareas y corre el programador (`bin/start.sh` rechaza `SERVER_ROLE` de versiones viejas). No hay servicio `worker` aparte.
- `glitchtip-postgres`: Postgres propio (D3) con el volumen `glitchtip_postgres_data`, solo en la red `internal`. No comparte base, usuario ni volumen con el Postgres de la app, ni entra en el backup ni en el rollback de la app.
- **Sin Valkey/Redis**: con `VALKEY_URL` vacío GlitchTip usa la base como caché y cola de tareas (`settings.py`, rama «Fallback to database cache»). Se evita un contenedor más; si el volumen de eventos creciera, es lo primero a revisar (**NO MEDIDO**).
- Hecho a propósito: sin `env_file` (GlitchTip no ve el `.env` de la app), `GLITCHTIP_ENABLE_UPTIME=false` (sin chequeos salientes), sin DSN propio de GlitchTip (no se reporta a sí mismo), `DEBUG=false`.

## Acceso (D1, D2, D9)

- La interfaz se publica solo en `127.0.0.1:8001` (`GLITCHTIP_BIND`). Se entra con túnel SSH: `ssh -L 8001:127.0.0.1:8001 usuario@servidor` y luego `http://localhost:8001`. No hay entrada de Caddy ni cambio de CSP.
- Desvío a revisar: para publicar el puerto, `glitchtip` está en `internal` **y** en `default` (igual que `backend` y `caddy`), porque Docker no publica puertos de una red `internal: true`. La base está solo en `internal`. Consecuencia: `glitchtip` técnicamente podría salir a internet; no necesita hacerlo (sin mail, sin uptime, sin integraciones).
- DSN interno (D2=A): el backend postea desde la red `internal` al host `glitchtip` (puerto 8000). **NO VERIFICADO en staging (S6)**: el DSN que muestra la interfaz se arma con `GLITCHTIP_DOMAIN`; hay que reemplazar el host por `glitchtip:8000` (`http://<clave>@glitchtip:8000/<id>`) antes de cargarlo en `SENTRY_DSN`. `SentryTunnelTarget` acepta `http` y cualquier host.
- Quién accede (D9=A): Gabriel y una cuenta de operación. Registro de usuarios cerrado (`ENABLE_USER_REGISTRATION=false`) y creación de organizaciones cerrada (`ENABLE_ORGANIZATION_CREATION=false`); no se abren por variable.
- Sin mail (D9): `EMAIL_BACKEND` es el de consola. Las invitaciones y avisos que GlitchTip «enviaría» salen en el log (`dcg logs glitchtip`); el enlace de invitación se copia de ahí. **NO VERIFICADO.**

## Primer arranque

Con `dcg` = `docker compose --env-file deploy/.env -f docker-compose.prod.yml -f deploy/glitchtip/docker-compose.glitchtip.yml` (mismo `deploy/.env`; en staging, con su `COMPOSE_PROJECT_NAME` y secretos propios):

1. En `deploy/.env` definir `GLITCHTIP_IMAGE`, `GLITCHTIP_SECRET_KEY` y `GLITCHTIP_POSTGRES_PASSWORD` (`openssl rand -base64 48 | tr -d '/+='` y `-base64 32`). Los valores de `.env.example` son marcadores, no usarlos.
2. `dcg up -d glitchtip-postgres glitchtip` y esperar `healthy` en `dcg ps` (la primera vez crea las tablas; puede tardar).
3. Crear la cuenta de Gabriel: `dcg exec glitchtip python manage.py createsuperuser` (comando estándar de Django; **NO VERIFICADO** que 7.0.0 lo permita con el registro cerrado, es lo esperado).
4. Entrar por el túnel SSH, crear la organización y un proyecto (plataforma Java para el backend; el front usa el mismo DSN, ver `sentry.md`). **El DSN se obtiene en la configuración del proyecto, «Client Keys (DSN)».**
5. Invitar a la cuenta de operación desde la organización (enlace en el log, ver arriba).
6. Recién con el servidor definido y en la ventana de Gabriel (rebanada S6): cargar `SENTRY_DSN` y `VITE_SENTRY_DSN` idénticos y reconstruir (`--build`).

## Retención y datos (D4, D5)

- Retención: 30 días (`GLITCHTIP_RETENTION_DAYS`, aplica a eventos, transacciones y demás tipos).
- Sin backups (D5=A): es telemetría reproducible. Si se pierde el volumen, se pierde la historia, no datos de negocio. Para descartar todo: `dcg stop glitchtip glitchtip-postgres && dcg rm -f glitchtip glitchtip-postgres && docker volume rm <proyecto>_glitchtip_postgres_data`.
- Nada de esto entra en `deploy/backup/` ni en el restore de la app.

## Actualizar la versión

1. Leer las notas de la versión nueva y confirmar que los nombres de variables de este documento siguen vigentes.
2. Cambiar `GLITCHTIP_IMAGE` por el tag nuevo (explícito) en `deploy/.env`; `dcg pull glitchtip && dcg up -d glitchtip` (las migraciones corren al arrancar, `init_database`).
3. Probar en staging primero. Rollback: volver al tag anterior; si la migración ya corrió, descartar los datos (D5) en vez de restaurar.
4. El cambio de la imagen de Postgres (`postgres:18-alpine`) entre versiones mayores no se hace en el lugar: es descartar y recrear (telemetría).

## Rotar la clave secreta

`GLITCHTIP_SECRET_KEY` firma sesiones y tokens: rotarla cierra las sesiones de la interfaz y invalida enlaces de invitación pendientes. No afecta los DSN de los SDK. Cambiar el valor en `deploy/.env` y `dcg up -d glitchtip`. `GLITCHTIP_POSTGRES_PASSWORD` solo se rota cambiándola también dentro de Postgres (`\password` en `psql`) y recreando `glitchtip`.

## Checklist de privacidad

- Sin IP: por el túnel del backend el servidor solo ve la IP del backend (`sentry.md`); el DSN es interno y no hay ingesta pública.
- Sin PII por defecto: `sendDefaultPii=false`, sin cookies, sin usuario, sin cuerpo; el depurado vive en el SDK (`SentryEventScrubber` y `scrubClientEvent`), **no se depende del servidor para depurar**.
- Interfaz solo en loopback, registro cerrado, sin mail, retención 30 días, base propia.
- Pendientes de otras rebanadas (si sus PRs no mergearon al leer esto): tope global del túnel (S1), tope de eventos del backend (S2) y del front (S3), política de mensajes del front para nombres de menores (S4, D8).

## Recursos

- **NO MEDIDOS.** La documentación consultada no publica mínimos. Los valores por defecto del overlay son iniciales y propios: `glitchtip` 768 MB / 1 CPU, `glitchtip-postgres` 512 MB / 0.5 CPU (`GLITCHTIP_MEMORY_LIMIT`, `GLITCHTIP_CPU_LIMIT`, `GLITCHTIP_POSTGRES_MEMORY_LIMIT`, `GLITCHTIP_POSTGRES_CPU_LIMIT`).
- Se miden en staging (S6) con `dcg stats --no-stream` y se anotan acá. Hasta entonces, no dimensionar el servidor contando con ellos como definitivos: se suman a Postgres 1 g + backend 1 g + Caddy 256 m, más backup y monitoreo.
