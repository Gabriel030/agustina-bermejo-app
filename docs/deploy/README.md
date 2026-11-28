# Despliegue de producción

Un despliegue por instituto, con el mismo código. Funciona en cualquier servidor con Docker y
Docker Compose v2 (un VPS de cualquier proveedor) y las imágenes son estándar, así que también
sirven en plataformas de contenedores.

## Cómo está armado

```
Internet --443/80--> Caddy (imagen "frontend") --/api/*--> backend:8080 --> postgres:5432
                        |
                        +--> estáticos del front (con fallback a index.html)
```

- **Un solo origen.** El navegador habla con `https://<dominio>` para todo. La API vive en
  `/api/*`; Caddy le saca el prefijo `/api` antes de pasarle el pedido al backend, así que los
  contratos del backend no cambian. No hay CORS ni preflight.
- **Solo Caddy está expuesto** (puertos 80 y 443). Backend y Postgres están en una red interna
  de Docker. El backend sale a internet por una red aparte (Brevo, Google, Mercado Pago).
- **Caddy saca y renueva el certificado HTTPS solo** para el dominio de `SITE_ADDRESS`.
- Las cabeceras de seguridad (CSP, HSTS, etc.) y la política de caché las pone Caddy:
  `frontend/Caddyfile`. `index.html`, `sw.js`, `manifest.webmanifest` y `theme-init.js` van con
  `no-cache`; `/assets/*` (con hash en el nombre) va `immutable` por un año.
- Los endpoints internos de actuator (métricas, prometheus) devuelven 404 desde afuera. Solo
  `GET /api/actuator/health` es público.
- La IP real del cliente llega al backend por `X-Forwarded-For`. El backend confía en Caddy por
  `TRUSTED_PROXIES_REGEX` y `FORWARD_HEADERS_STRATEGY=native`, de modo que el bloqueo de
  recuperación de clave y el rate limit cuentan por cliente y no por la IP del proxy.
- Rate limit: lo hace el backend (Bucket4j). Caddy solo limita el tamaño del pedido (12 MB).

Archivos: `docker-compose.prod.yml`, `backend/Dockerfile`, `frontend/Dockerfile`,
`frontend/Caddyfile`, `deploy/.env.example`; para staging, `deploy/staging/`; overlay de
GlitchTip: `deploy/glitchtip/docker-compose.glitchtip.yml`.

## Antes de empezar (una sola vez por instituto)

1. Un servidor con Docker (`docker --version` y `docker compose version` tienen que andar) y los
   puertos 80 y 443 abiertos.
2. Un dominio con un registro DNS tipo A (y AAAA si hay IPv6) apuntando al servidor. Caddy no
   puede sacar el certificado hasta que el DNS resuelva.
3. Credenciales de los servicios externos: Brevo (SMTP), Google Cloud (Client ID de OAuth, con el
   dominio como origen de JavaScript autorizado) y Mercado Pago (access token y secreto del
   webhook). Los dos últimos son opcionales si todavía no se usan.

## Primer despliegue

```bash
git clone <repo> agustina-bermejo-app && cd agustina-bermejo-app
git checkout master            # o la etiqueta de release que se quiera desplegar
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
```

Editar `deploy/.env`. Las que no se pueden dejar con el valor de ejemplo:

| Variable | Qué poner |
|---|---|
| `SITE_ADDRESS` | El dominio, sin `https://` |
| `CORS_ALLOWED_ORIGINS`, `FRONTEND_BASE_URL` | `https://<dominio>` |
| `BACKEND_BASE_URL` | `https://<dominio>/api` |
| `POSTGRES_PASSWORD` | `openssl rand -base64 32 \| tr -d '/+='` |
| `JWT_SECRET` | `openssl rand -base64 48 \| tr -d '\n'` |
| `JWT_SECRET` (paso previo al release 24.4) | Antes de desplegar, verificar que el valor actual tenga al menos 32 caracteres y al menos 10 distintos (`printf %s "$JWT_SECRET" \| fold -w1 \| sort -u \| wc -l`). Si no, el backend no arranca y el log dice `jwt.secret debe tener al menos 10 caracteres distintos`; rotarlo con el procedimiento de `docs/security/secrets-rotation.md` |
| `JWT_ACCEPT_MISSING_AUDIENCE` | Opcional, `true` por defecto. Acepta los access tokens emitidos antes de que existiera `aud` (duran 15 min). Dejarlo en `true` en el release 24.4 y pasarlo a `false` en el siguiente |
| `BROADCAST_UNSUBSCRIBE_SECRET` | `openssl rand -base64 48 \| tr -d '\n'`, distinta de `JWT_SECRET`; sin ella el backend no arranca en prod ni en staging |
| `BREVO_SMTP_USERNAME`, `BREVO_SMTP_KEY`, `MAIL_FROM_ADDRESS` | Los de Brevo |
| `RESILIENCE_ENABLED` | Opcional. Circuit breakers y bulkheads de las integraciones externas; en `prod` y staging es `true` por defecto. Poner `false` solo para apagarlos ante un incidente. Límites en `docs/context/overload-protection.md` |
| `GOOGLE_OAUTH_CLIENT_ID` y `VITE_GOOGLE_CLIENT_ID` | El mismo Client ID en las dos |

Después:

```bash
docker compose --env-file deploy/.env -f docker-compose.prod.yml up -d --build
docker compose --env-file deploy/.env -f docker-compose.prod.yml ps
```

La primera vez construye las imágenes (varios minutos) y Flyway crea todas las tablas en la base
vacía (330 migraciones hasta la V484 en `develop`). Cuando `ps` muestre los tres servicios `healthy`, verificar:

```bash
curl -fsS https://<dominio>/api/actuator/health       # {"status":"UP",...}
curl -sI https://<dominio>/ | grep -i "content-security\|strict-transport"
```

y entrar a `https://<dominio>` desde el navegador.

> Para probar en una máquina sin dominio: `SITE_ADDRESS=:80` y `HTTP_PORT=8088`; queda en HTTP plano
> en `http://localhost:8088` (sin HTTPS, así que el login con Google no funciona).

### Atajo para no repetir flags

```bash
export COMPOSE_FILE=docker-compose.prod.yml
alias dc='docker compose --env-file deploy/.env'
```

El resto de este documento usa `dc` para abreviar.

## Actualizar a una versión nueva (con migraciones)

Las migraciones de base de datos (Flyway) corren solas al arrancar el backend nuevo. Son
irreversibles hacia atrás, por eso se hace copia antes.

1. **Copia de la base** (siempre antes de actualizar):
   ```bash
   dc exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > backup-$(date +%F-%H%M).dump
   ```
   Guardarla fuera del servidor. (Los backups programados tienen su propio runbook: [backup-and-restore.md](backup-and-restore.md).)
2. Anotar la versión actual para poder volver: `git rev-parse --short HEAD`.
3. Traer y desplegar:
   ```bash
   git fetch --tags && git checkout <tag-o-commit>
   export APP_VERSION=$(git rev-parse --short HEAD)
   dc up -d --build
   ```
   Con `APP_VERSION` las imágenes quedan etiquetadas por versión y se pueden volver a usar.
4. Mirar el arranque del backend (ahí aparece Flyway):
   ```bash
   dc logs -f --tail=100 backend
   ```
   Esperar `Started BackendApplication` y que `dc ps` muestre `healthy`.
5. Correr la prueba posterior al despliegue (ver la sección siguiente) y entrar a la app.

## Prueba posterior al despliegue

`deploy/smoke.sh` confirma, contra la URL pública, que la app responde y que las migraciones
corrieron. Se corre desde el checkout del código que se acaba de desplegar (de ahí toma el último
`V###` esperado):

```bash
SMOKE_DB_VERSION_CMD="docker compose --env-file deploy/.env -f docker-compose.prod.yml exec -T postgres sh -c 'psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -Atc \"select max(version::int) from flyway_schema_history where success and version is not null\"'" \
  deploy/smoke.sh --require-migrations https://<dominio>
```

Qué verifica: `/` sirve el `index.html` con las cabeceras de seguridad de Caddy (HSTS, nosniff,
DENY, Referrer-Policy, Permissions-Policy, COOP, CSP con `frame-ancestors 'none'`, sin `Server`),
`/api/actuator/health` en UP, el actuator interno oculto (404), `/api/me` sin sesión en 401 con
`code` `UNAUTHENTICATED` (y no 500) y que la última versión de Flyway aplicada es igual al último
`V###` del código. Sale con código 0 si todo pasa, 1 si algo falla.

Sin `SMOKE_DB_VERSION_CMD` (por ejemplo, si se corre desde otra máquina sin acceso a la base) las
migraciones quedan como `NO VERIFICADO`, que no cuenta como éxito de ese chequeo; con
`--require-migrations` eso hace fallar el script. Desde un job se usa igual: el comando y el código
de salida son los mismos.

**Si falla:** leer qué chequeo falló, mirar `dc logs --tail=100 backend caddy` y, si la causa no es
evidente y la app no está usable, seguir la sección «Rollback» de este runbook. El script no hace
rollback por sí mismo.

Si el release trae migraciones, mirar antes el PR del release: cualquier ventana de migraciones
se avisa ahí.

## Rollback

**Solo código (sin migraciones nuevas):** volver al commit anterior y reconstruir.

```bash
git checkout <commit-anterior>
export APP_VERSION=<commit-anterior-corto>
dc up -d --build
```

**Con migraciones nuevas:** Flyway no deshace migraciones. El camino seguro es volver al commit
anterior Y restaurar la copia hecha antes de actualizar (se pierde lo cargado desde entonces):

```bash
dc stop caddy backend
dc exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
dc exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' < backup-AAAA-MM-DD-HHMM.dump
git checkout <commit-anterior>
export APP_VERSION=<commit-anterior-corto>
dc up -d --build
```

Si se trabaja con un registro de imágenes, el rollback es poner en `deploy/.env` las
`BACKEND_IMAGE` y `FRONTEND_IMAGE` de la versión anterior y correr `dc pull && dc up -d`.

## Rotación de secretos

Procedimiento completo por secreto (impacto, pasos, verificación, rollback, cadencia y checklist de ensayo en staging): [docs/security/secrets-rotation.md](../security/secrets-rotation.md). Lo que sigue es el resumen.

Todos viven en `deploy/.env`. Después de cambiar uno: `dc up -d` (recrea solo lo que cambió). `dc` solo conoce `docker-compose.prod.yml`: el backup y el monitoreo son overlays aparte y hay que recrearlos con sus propios `-f` (ver el documento de rotación).

| Secreto | Efecto de rotarlo | Cómo |
|---|---|---|
| `JWT_SECRET` | Invalida los access tokens (15 min): el front renueva con el refresh y nadie pierde la sesión. Para cerrar las sesiones hay que revocar también los refresh tokens (ver el documento de rotación) | Generar uno nuevo, `dc up -d backend` |
| `POSTGRES_PASSWORD` | Hay que cambiarla también dentro de Postgres; `dc up -d` recrea `postgres` y las conexiones nuevas fallan entre el cambio y el reinicio del backend. El servicio `backup` se recrea aparte | `\password` interactivo dentro de `psql` (no pasar la clave en `-c`), editar `.env` y `dcb up -d`; pasos completos en el documento de rotación |
| `BREVO_SMTP_KEY` | Sin efecto si se cambia a la vez en Brevo | Crear la clave nueva en Brevo, editar `.env`, `dc up -d backend`, revocar la vieja |
| `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` | Los cobros online usan el nuevo apenas reinicia | Cambiarlos en el panel de Mercado Pago y en `.env`, `dc up -d backend` |
| `ACTUATOR_PASSWORD` | Solo afecta al monitoreo | Cambiar en `.env` y en el monitoreo |
| `GLITCHTIP_SECRET_KEY` | Cierra las sesiones de la interfaz de GlitchTip; no afecta los DSN | Cambiar en `.env` y recrear `glitchtip` con el overlay (`docs/deploy/glitchtip.md`) |

`GOOGLE_OAUTH_CLIENT_ID` / `VITE_GOOGLE_CLIENT_ID` no son secretos, pero el segundo se fija al
construir: si cambia, `dc up -d --build`.

## Ver logs y estado

```bash
dc ps                                   # estado y salud
dc logs -f --tail=200 backend           # backend (en producción salen en JSON/ECS)
dc logs -f --tail=100 caddy             # proxy y certificados
dc logs --since 1h postgres             # base de datos
dc stats --no-stream                    # memoria y CPU por contenedor
```

## Enlace de calendario: el token viaja en la URL

`GET /api/public/calendar/<token>.ics` es el feed suscribible del calendario del alumno: el token (256 bits) es la única credencial y va en el path. Estado verificado en este repositorio:

- El `frontend/Caddyfile` **no define la directiva `log`**: Caddy no escribe access log por defecto, así que el path (y el token) no queda guardado en el proxy. Si alguien agrega `log` o un `access_log`, hay que enmascarar el path `/api/public/calendar/*` (filtro `replace` sobre `request>uri`) antes de habilitarlo.
- El backend no activa el access log de Tomcat ni loguea URIs de pedidos; el nivel raíz es INFO (WARN en un perfil) y el de `com.red_escuela.backend` INFO, sin DEBUG de Spring MVC. Hay un test (`CalendarFeedSecurityIT.tokenNeverAppearsInApplicationLogs`) que falla si el token aparece en un log de aplicación.
- Si el servidor está detrás de otro proxy o balanceador (CDN, WAF), ese componente puede guardar el path: configurar el enmascarado ahí.
- Un token filtrado se invalida regenerándolo o revocándolo desde la cuenta del alumno; el servidor deja de aceptar el token anterior de inmediato. Esto no cuenta la caché del cliente: la respuesta lleva `Cache-Control: private, max-age=900` y el feed declara `REFRESH-INTERVAL` de 1 h, así que un cliente (navegador, Google, Apple) puede seguir mostrando o pidiendo el feed viejo entre 15 minutos y 1 hora después de revocar.
- El límite por IP de pedidos con token inexistente (los que dan 404) usa la IP del cliente: requiere `FORWARD_HEADERS_STRATEGY=native` con `TRUSTED_PROXIES_REGEX` cubriendo la red del proxy (ver `docs/context/overload-protection.md`). Sin eso todos los clientes comparten la IP del proxy y los pedidos inválidos de un atacante bloquean los pedidos inexistentes de todos. Los pedidos con token válido nunca se cortan por IP, solo por el límite del propio token (120 por hora).
- Si no hay ningún bean `MyCalendarProvider`, el feed y la exportación responden 503 y el arranque deja un WARN `No MyCalendarProvider bean is registered`.

## Problemas típicos

- **Caddy no saca el certificado:** el DNS todavía no apunta al servidor o hay un firewall
  bloqueando 80/443. Mirar `dc logs caddy`. Para no agotar los límites de Let's Encrypt, no
  reiniciar en bucle: arreglar el DNS y esperar.
- **El backend no arranca con `Could not resolve placeholder 'JWT_SECRET'`:** falta la variable
  en `deploy/.env`.
- **El backend queda `unhealthy`:** mirar `dc logs backend`; lo más común es una contraseña de
  Postgres distinta a la que ya tiene el volumen (la contraseña se fija al crear el volumen).
- **Todos los usuarios comparten el bloqueo de rate limit:** el backend está viendo la IP del
  proxy. Revisar `TRUSTED_PROXIES_REGEX` contra la red de Docker (`docker network inspect`) y
  que `FORWARD_HEADERS_STRATEGY` sea `native`.
- **Hay un balanceador o CDN delante de Caddy:** poner sus rangos en `CADDY_TRUSTED_PROXIES`.

## Entorno de ensayo (staging)

Un segundo despliegue igual al de producción, con datos de prueba, para ensayar importaciones,
actualizaciones con migraciones y restauraciones antes de tocar producción. Usa el mismo
`docker-compose.prod.yml` y las mismas imágenes; `deploy/staging/docker-compose.staging.yml` solo
cambia el perfil de Spring, el modo inicial de facturación y la cookie de sesión.

**Qué lo separa de producción** (todo sale de `COMPOSE_PROJECT_NAME`, que en staging es
`agustina-bermejo-staging`):

| Qué | Producción | Staging |
|---|---|---|
| Volúmenes (base, `/data`, Caddy) | `agustina-bermejo_*` | `agustina-bermejo-staging_*` |
| Contenedores y redes | `agustina-bermejo-*` | `agustina-bermejo-staging-*` |
| Puertos publicados | 80 / 443 | `HTTP_PORT` / `HTTPS_PORT` (por defecto 8080 / 8443) |
| Dominio | `SITE_ADDRESS` de producción | otro dominio o subdominio |
| Archivo de variables | `deploy/.env` | `deploy/.env.staging` |
| Secretos | los reales | nuevos, nunca reutilizados |
| Correo | Brevo | `TRANSACTIONAL_MAIL_PROVIDER=mock`: no sale ningún mail |

Atajo (el resto de la sección usa `sc`):

```bash
alias sc='docker compose --env-file deploy/.env.staging -f docker-compose.prod.yml -f deploy/staging/docker-compose.staging.yml'
```

### Levantar

```bash
cp deploy/.env.example deploy/.env.staging
chmod 600 deploy/.env.staging
```

Copiar al final de `deploy/.env.staging` lo de `deploy/staging/.env.staging.example` (o ajustar esas
variables en su lugar), completar los secretos con valores nuevos y levantar:

```bash
sc up -d --build
sc ps
```

Si staging comparte servidor con producción, los puertos tienen que ser distintos y, si hay otro
proxy delante, apuntarlo a `HTTP_PORT`. Sin dominio: `SITE_ADDRESS=:80`, `REFRESH_COOKIE_SECURE=false`
y entrar por `http://<servidor>:<HTTP_PORT>` (sin HTTPS el login con Google no funciona).

### Cargar datos de prueba

Elegir una de las dos:

- **Base vacía (como producción).** Es el valor por defecto (`STAGING_SPRING_PROFILES=prod`). Sirve
  para ensayar la importación de datos históricos desde cero.
- **Institución demo.** En `deploy/.env.staging`: `STAGING_SPRING_PROFILES=prod,local,demo` y
  `STAGING_DEMO_SEED=true`, y `sc up -d`. El backend siembra la institución demo hablando con su
  propia API (idempotente: si ya hay datos no repite). Crea las cuentas `*.demo` con clave
  `Test1234` (ver `docs/context/test-users.md`): **no dejar ese staging abierto a internet sin una
  barrera delante** (VPN, lista de IP o autenticación del proxy). Para volver a una base vacía hay
  que destruir los volúmenes (ver «Destruir»).

Con el perfil `demo` el reloj de la app es el `DemoClock`, que sin ninguna orden de viaje en el tiempo
(`travelTo`: no hay llamadas en el código principal) devuelve la hora real. Esto se verificó leyendo el
código. El seed `full` sí se corrió (27-nov-2026, Postgres 16 y jar de `develop` fuera de Docker): Flyway
aplica 330 migraciones hasta la V484 en unos 4 s y el seed tarda unos 9 minutos, y el backend queda
`healthy` **antes** de que termine; esperar `Seed demo listo` en `sc logs backend` antes de ensayar.
Con `TRANSACTIONAL_MAIL_PROVIDER=mock` los mails de autenticación igual intentan salir por SMTP: dejar
`BREVO_SMTP_*` vacías en un staging de prueba. Paso a paso, con la salida medida y lo que no se ejecutó:
[local-con-dominio-y-seed.md](local-con-dominio-y-seed.md).

### Correr la prueba posterior al despliegue

Es la misma de producción contra la URL de staging:

```bash
SMOKE_DB_VERSION_CMD="docker compose --env-file deploy/.env.staging -f docker-compose.prod.yml -f deploy/staging/docker-compose.staging.yml exec -T postgres sh -c 'psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -Atc \"select max(version::int) from flyway_schema_history where success and version is not null\"'" \
  deploy/smoke.sh --require-migrations https://<dominio-de-staging>
```

Si staging corre por HTTP plano (sin dominio), los chequeos de cabeceras HTTPS pueden fallar: eso es
esperable y no dice nada sobre producción.

### Ensayar antes de producción

1. **Actualización con migraciones:** desplegar en staging la etiqueta candidata siguiendo
   «Actualizar a una versión nueva» (con `sc` en lugar de `dc`) y correr la prueba posterior.
2. **Restauración:** restaurar en staging una copia de producción con el procedimiento de
   `docs/deploy/backup-and-restore.md` y contrastar los conteos. Una copia real tiene datos
   personales: staging queda con la misma protección que producción y los mails en `mock`.
3. **Importación:** ensayar la carga de datos históricos completa en staging y revisar el resultado
   antes de repetirla en producción.

### Promover a producción

Staging no promueve nada solo: lo que se ensayó es una etiqueta o commit del repo.

1. Anotar la etiqueta o el commit exacto que pasó el ensayo y la prueba posterior.
2. En producción, desplegar exactamente eso con «Actualizar a una versión nueva» (copia de la base
   primero) y correr la prueba posterior.
3. Si el ensayo falló, no se promueve: se corrige en una rama, se vuelve a desplegar en staging y
   se repite.

### Destruir

Borra contenedores y **todos** los volúmenes de staging (base, archivos y certificados), nada de producción:

```bash
sc down -v
```

Antes de correrlo confirmar con `echo $COMPOSE_PROJECT_NAME` (o mirando `sc config | head -1`) que el
nombre es el de staging.

### Pendiente de A2 (proveedor y dominio)

Parametrizado y sin definir: `SITE_ADDRESS` y las URLs del front y del backend de staging, el
servidor donde corre (y si comparte máquina con producción), y el registro DNS. Mientras A2 no se
resuelva el ejemplo usa `staging.escuela.example.com`.

## Prueba de envío masivo con Brevo (punta a punta)

Ensayo en **staging** de un masivo real: dos personas adultas, cada una con una casilla propia,
reciben el mismo envío con `TRANSACTIONAL_MAIL_PROVIDER=brevo`. Sirve para comprobar, con mails
que llegan de verdad, el remitente, la autenticación del dominio, la cabecera `List-Unsubscribe`,
el enlace `/baja`, el cupo diario y que el correo no caiga en spam. Antes de esto no se manda
ningún masivo a personas reales.

Alcance: solo correo masivo institucional. No prueba WhatsApp, no prueba avisos promocionales
(con `app.consent.promotional-enabled=false` no hay consentimiento promocional) y no prueba el
tratamiento automático de rebotes y quejas (no existe; los rebotes se miran en el panel de Brevo).

### Antes de empezar (lo prepara Gabriel, en Brevo)

- Remitente verificado (la misma dirección que `MAIL_FROM_ADDRESS`) y dominio autenticado con
  SPF y DKIM en estado verificado. Desde el repo no se puede saber si está hecho: **sin verificar**.
- Una API key v3 y una clave SMTP de la cuenta. Son secretas: no se pegan en chats, issues ni commits.
- Límite diario real del plan. El código asume **300 mails por día con 100 reservados** para
  transaccionales (`BROADCAST_BREVO_DAILY_LIMIT=300`, `BROADCAST_BREVO_RESERVED_FOR_TRANSACTIONAL=100`).
  Ese número es un supuesto: **el límite real lo informa Gabriel** y reemplaza a los valores de
  abajo antes de ensayar.
- Dos casillas propias de personas adultas, distintas de cualquier cuenta de producción.

### Variables a cargar en `deploy/.env.staging`

Los valores reales no van en este documento ni en el repo. Nombres verificados contra
`backend/src/main/resources/application.properties` y `deploy/.env.example`.

| Variable | Qué poner | Para qué |
|---|---|---|
| `TRANSACTIONAL_MAIL_PROVIDER` | `brevo` (en el ensayo; al final vuelve a `mock`) | Elige el gateway HTTP de Brevo para masivos e invitaciones |
| `BREVO_API_KEY` | API key v3 (secreta) | Sin ella el backend no arranca con `brevo` |
| `BREVO_SMTP_USERNAME` | Usuario SMTP de Brevo | Mails de autenticación por SMTP (`spring.mail`) |
| `BREVO_SMTP_KEY` | Clave SMTP (secreta) | Idem |
| `BREVO_SENDER_NAME` | Nombre visible del remitente (opcional) | Se envía como `sender.name` |
| `MAIL_FROM_ADDRESS` | El remitente verificado en Brevo | Se envía como `sender.email` en cada mail |
| `BROADCAST_UNSUBSCRIBE_SECRET` | Valor nuevo, distinto de `JWT_SECRET` (`openssl rand -base64 48 \| tr -d '\n'`) | Firma los enlaces de baja. Staging corre con perfil `prod`: sin ella no arranca |
| `FRONTEND_BASE_URL` | URL pública de staging, por ejemplo `https://staging.escuela.example.com` | Arma el enlace `<FRONTEND_BASE_URL>/baja?token=...`. Si queda en `localhost` el enlace no funciona |
| `BROADCAST_BREVO_DAILY_LIMIT` | El límite real del plan (por defecto 300) | Tope diario de la cuenta |
| `BROADCAST_BREVO_RESERVED_FOR_TRANSACTIONAL` | Reserva para transaccionales (por defecto 100) | Lo que los masivos no pueden tocar |
| `BROADCAST_DISPATCH_MAX_PER_RUN` | 100 (por defecto) | Máximo de mails por corrida del job (corre cada minuto) |
| `BROADCAST_DISPATCH_WINDOW_START` / `_END` | `09:00` / `20:00` (por defecto) | Franja de envío, en `APP_TIMEZONE` (por defecto `America/Argentina/Buenos_Aires`) |

Cupo: el código calcula lo que queda del día como
`límite − máximo(transaccionales_de_hoy, reserva) − masivos_de_hoy`, con piso en 0
(`BroadcastDailyQuota`). Las invitaciones cuentan como transaccionales. Con los supuestos de arriba
y sin transaccionales ese día, un masivo puede usar hasta 200 mails por día (300 − 100).

### Orden de pasos

Cada paso dice qué hacer y qué tiene que pasar antes de seguir. Si un paso no da el resultado
esperado, no se sigue: ver `docs/context/debugging.md`, sección de correo masivo.

1. **Verificar remitente y dominio en Brevo.** Resultado: el remitente figura verificado y SPF y
   DKIM del dominio están en verificado. (Los nombres exactos de las pantallas de Brevo: sin verificar.)
2. **Cargar las variables** de la tabla en `deploy/.env.staging` y `chmod 600 deploy/.env.staging`.
   Resultado: `sc config > /dev/null` termina sin error.
3. **Levantar staging con la nueva configuración:** `sc up -d` y luego `sc logs backend | tail -n 100`.
   Resultado: el backend queda `healthy` (`sc ps`), no aparece ningún error por `BREVO_API_KEY` ni por
   `BROADCAST_UNSUBSCRIBE_SECRET`.
4. **Prueba posterior al despliegue:** la de «Correr la prueba posterior al despliegue», contra la
   URL de staging. Resultado: pasa. Si los chequeos de HTTPS fallan por ser staging sin dominio, es
   esperable (ver esa sección).
5. **Crear dos personas de prueba** con sus casillas propias, en la base de staging (vacía o con demo,
   según «Cargar datos de prueba»). Verificar el mail de cada una por el flujo de activación de la app (el paso exacto en pantalla: sin verificar).
   Resultado: las dos personas aparecen con mail verificado. Si no, el masivo las excluye con
   `NO_VERIFIED_EMAIL` (es el resultado correcto del filtro, no un error del envío).
6. **Crear el masivo** en `comunicaciones/nuevo` (roles administrativo y director) con un segmento que
   incluya solo esas dos personas, categoría institucional y una plantilla corta que use `{{nombre}}`
   y `{{linkBaja}}`. No usar `{{carrera}}`, `{{materia}}` ni `{{comision}}`: hoy no tienen fuente por
   persona y dejan a la persona como SKIPPED con `MISSING_TEMPLATE_VARIABLE`.
   Resultado: la vista previa muestra 2 destinatarios y 0 excluidos.
7. **Confirmar el masivo.** Resultado: queda confirmado. Si se confirma fuera de 09:00–20:00 (hora de
   Buenos Aires), los mails quedan en cola hasta la próxima apertura de la franja.
8. **Esperar uno o dos minutos y mirar el envío.**
   - `sc logs backend | grep "Mail enviado por Brevo"`: una línea por destinatario, con la casilla
     enmascarada.
   - En la base, abrir psql dentro del contenedor y correr la consulta:
     ```bash
     sc exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
     ```
     ```sql
     select kind, status, count(*) from notification_delivery
     where scheduled_for = (now() at time zone 'America/Argentina/Buenos_Aires')::date
     group by kind, status;
     ```
     Resultado: los mails del masivo en `SENT` (no `SENT_SIMULATED`, que es lo que deja el modo `mock`),
     con `provider_message_id` no vacío (el id de Brevo, que no empieza con `mock-`).
9. **Recibir en las dos casillas.** Resultado: llegan con el asunto y el cuerpo con el nombre de cada
   una, no con `{{nombre}}` literal. Si un mail no llega, ver el paso 12 (spam) y el panel de Brevo
   (sección «Eventos» o registro de transaccionales, nombre sin verificar).
10. **Ver la cabecera `List-Unsubscribe`.** Ver la sección de abajo. Resultado: está presente y
    apunta a `<URL de staging>/baja?token=...`.
11. **Probar el enlace de baja** desde la casilla A: abrir el enlace del cuerpo, confirmar la baja.
    Resultado: la página `/baja` confirma la baja y la URL es la de staging, no `localhost`.
12. **Probar que la baja se respeta.** Crear otro masivo solo con A y B. Resultado: A queda SKIPPED con
    motivo `OPTED_OUT` y B recibe el mail. Después de este paso A queda sin consentimiento institucional:
    para seguir probando con A, usar una tercera casilla propia o restablecer el consentimiento desde
    la matriz de consentimiento (sin verificar la pantalla exacta).
13. **Revisar el cupo.** Con los dos envíos hechos, el cupo restante del día baja en 2 respecto de lo
    que había antes del ensayo, según la fórmula de arriba. Resultado: el número coincide con
    `límite − máximo(transaccionales, reserva) − masivos` (medido con el SQL del paso 8 y el panel de
    Brevo). Si no coincide, no se ensaya más hasta entender la diferencia.
14. **Volver a `mock` y cerrar el ensayo.** En `deploy/.env.staging`: `TRANSACTIONAL_MAIL_PROVIDER=mock`,
    luego `sc up -d`. Resultado: los envíos siguientes quedan como `SENT_SIMULATED` y no sale ningún mail.
    Se deja una nota en el Issue con fecha, resultado de cada paso y las casillas usadas enmascaradas.

### Autenticación del dominio: SPF, DKIM y spam

- **Panel de Brevo:** el dominio figura autenticado (SPF y DKIM verificados). Sin eso el mail sale
  con firma de Brevo y cae en spam con más frecuencia. (Brevo no se consulta desde el repo: sin verificar.)
- **Casilla de destino:** en Gmail, abrir el mail, «Mostrar original». Buscar `spf=pass`,
  `dkim=pass` y `dmarc=pass` en `Authentication-Results`. Resultado: los tres en `pass`.
- **Spam:** el mail tiene que aparecer en «Recibidos», no en «Spam» ni «Promociones» de la casilla
  de prueba. Si aparece en spam con SPF y DKIM en `pass`, anotarlo y revisar el contenido de la
  plantilla antes del siguiente ensayo (no hay forma de medirlo desde el repo: sin verificar).

### Cómo comprobar que Brevo respeta `List-Unsubscribe`

El backend manda el enlace en la cabecera `List-Unsubscribe` de cada masivo (el gateway lo pasa como
`headers` de la API v3). Lo que no está verificado es que Brevo lo reenvíe tal cual al destinatario
(ver `docs/cell/progress/933-3f.md`, «Pendiente de verificar a mano»). Para verificarlo:

1. En la casilla de prueba, abrir el mail del masivo y elegir «Mostrar original».
2. Buscar la línea `List-Unsubscribe:`. Resultado esperado: `<https://<staging>/baja?token=...>`.
   El backend manda solo esa URL (sin `List-Unsubscribe-Post`), así que **no** hay garantía de que
   aparezca el botón de baja en un clic de Gmail: si aparece, es comportamiento de Gmail, no del envío.
3. Comparar el token de la cabecera con el del enlace del cuerpo. Ambos son válidos (el del cuerpo se
   firma al confirmar, el de la cabecera al enviar), pero no tienen que ser iguales.
4. Abrir el enlace de la cabecera y comprobar que llega a `/baja`.

Si la cabecera no aparece en el mail recibido, Brevo la está quitando o la cuenta no la permite: no se
pasa a masivos reales hasta resolverlo, y se anota en el Issue de #933.

### Menores

Decisión de Gabriel (D2): un masivo a una persona menor de edad va **solo a su casilla propia**. Si no
tiene casilla propia, o si la casilla coincide con la del tutor, esa persona queda SKIPPED. Hoy el código
**no aplica esta regla**: en `notification/broadcast` no hay lógica de edad ni de tutor (la implementación
es la rebanada R6 de #933; sin verificar en el momento de escribir esto). Por eso el ensayo usa solo
personas adultas. No se incluye a ningún menor en el segmento de prueba.

### Si algo sale mal

Ver «Síntomas típicos de correo masivo» en `docs/context/debugging.md`.

## Qué NO hace este paquete

- No publica imágenes en ningún registro (el CI solo las construye para validar).
- No incluye backups ni monitoreo (viven en `deploy/backup/` y `deploy/monitoring/`).
- No levanta GlitchTip (el reporte de errores autoalojado vive en el overlay `deploy/glitchtip/`, apagado hasta tener servidor; ver `docs/deploy/glitchtip.md`).
- No define el proveedor de hosting ni el dominio.
