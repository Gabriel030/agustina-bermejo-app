# Levantar local con dominio y datos sembrados

Runbook para tener en una máquina propia el mismo stack que producción (Caddy + backend + Postgres), con un dominio local y la institución demo sembrada. Complementa a [README.md](README.md) (producción y staging): acá se usa el overlay de staging con perfiles `prod,local,demo`.

> Verificado el 27-nov-2026 sobre `develop` (`b94ac7d68`). Cada paso dice si se **ejecutó** o **no se ejecutó**. Lo no ejecutado lo corre quien tenga Docker con acceso a internet sin proxy intermedio: la VM donde se redactó esto tiene un proxy cuyo certificado los contenedores no aceptan, por lo que `docker compose build` falla ahí con `certificate verify failed` al bajar Maven. No es un defecto del repo ni del Dockerfile y no se parchó.

## Qué hay que tener

- Docker con Compose v2 y puertos libres para `HTTP_PORT` y `HTTPS_PORT` (aquí 8088 y 8443).
- Para un dominio real (no `localhost`): DNS tipo A, puertos 80 y 443 abiertos y credenciales externas. Eso depende de Gabriel: ver la sección «Qué depende de Gabriel».

## 1. Variables

```bash
cp deploy/.env.example deploy/.env.staging
chmod 600 deploy/.env.staging
```

Cargar en `deploy/.env.staging` (los secretos con `openssl`, nunca los de producción):

| Variable | Valor para local con dominio |
|---|---|
| `ENV_FILE` | `deploy/.env.staging` (sin esto el backend lee `deploy/.env`) |
| `COMPOSE_PROJECT_NAME` | `agustina-bermejo-local` (distinto de producción) |
| `SITE_ADDRESS` | `escuela.localhost` |
| `HTTP_PORT` / `HTTPS_PORT` | `8088` / `8443` |
| `CORS_ALLOWED_ORIGINS`, `FRONTEND_BASE_URL` | `https://escuela.localhost:8443` |
| `BACKEND_BASE_URL` | `https://escuela.localhost:8443/api` |
| `POSTGRES_PASSWORD` | `openssl rand -base64 32 \| tr -d '/+='` |
| `JWT_SECRET` | `openssl rand -base64 48 \| tr -d '\n'`: mínimo 32 caracteres y al menos 10 distintos (`JwtSecretPolicy`) |
| `BROADCAST_UNSUBSCRIBE_SECRET` | `openssl rand -base64 48 \| tr -d '\n'`, distinta de `JWT_SECRET`; sin ella el perfil `prod` no arranca |
| `TRANSACTIONAL_MAIL_PROVIDER` | `mock` |
| `STAGING_SPRING_PROFILES` | `prod,local,demo` |
| `STAGING_DEMO_SEED` | `true` |
| `BREVO_SMTP_USERNAME`, `BREVO_SMTP_KEY` | vacías (ver «Mails» más abajo) |

Estado: **ejecutado** (`docker compose ... config` resuelve sin error con este archivo).

El alias del resto del runbook:

```bash
alias sc='docker compose --env-file deploy/.env.staging -f docker-compose.prod.yml -f deploy/staging/docker-compose.staging.yml'
```

## 2. Levantar (compose, Caddy y dominio)

```bash
sc up -d --build
sc ps
sc logs -f backend | grep -E "Successfully applied|Started BackendApplication|Seed demo"
```

Estado: **no ejecutado** (la build falla en la VM de redacción, ver arriba). Lo que sí se ejecutó es el mismo jar, el mismo front y Flyway/seed por fuera de Docker (secciones 3 y 4).

Qué esperar, a confirmar por quien lo corra:

- `escuela.localhost` resuelve a `127.0.0.1` sin tocar `/etc/hosts` en Linux y macOS. Caddy emite un certificado de su CA interna para los hosts `.localhost`, así que el navegador y `curl` avisan de certificado no confiable: `curl -k` o `docker compose exec caddy caddy trust` (y reabrir el navegador).
- Caddy espera a que el backend esté `healthy`. El backend se marca `healthy` cuando termina Flyway y arranca la aplicación, **antes** de que termine el seed: seguir `Seed demo listo` en los logs (con el dataset `full` tardó unos 9 minutos en la medición de la sección 4).

Prueba posterior:

```bash
curl -ksS https://escuela.localhost:8443/api/actuator/health
SMOKE_DB_VERSION_CMD="docker compose --env-file deploy/.env.staging -f docker-compose.prod.yml -f deploy/staging/docker-compose.staging.yml exec -T postgres sh -c 'psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -Atc \"select max(version::int) from flyway_schema_history where success and version is not null\"'" \
  deploy/smoke.sh --require-migrations https://escuela.localhost:8443
```

Estado: **no ejecutado**. Con el certificado interno de Caddy el smoke puede fallar por la verificación TLS de `curl`: eso no dice nada sobre la app (ver `deploy/smoke.sh`, que no lleva `-k`).

## 3. Base limpia: Flyway V1 → V484

Ejecutado en el host con Postgres 16.15 y el jar de `develop` (`./mvnw -B -q -o package -DskipTests`, y `npm ci && VITE_API_BASE_URL=/api VITE_CSP_IN_HEADER=true npm run build` en `frontend/`, ambos con código de salida 0), arrancando el backend con los perfiles y variables de esta guía sobre una base vacía:

```
Successfully applied 330 migrations to schema "public", now at version v484 (execution time 00:04.150s)
Started BackendApplication in 33.409 seconds
```

```
$ psql -Atc "select max(version::int), count(*) from flyway_schema_history where success and version is not null"
484|330
```

Son 330 archivos `V*.sql` con versiones hasta la 484 (hay huecos de numeración: no hace falta que cada número exista).

`docs/release/pre-migration-checks.sql` sobre esa base ya migrada (`psql -h localhost -U agustina -d agustina_bermejo -At -f docs/release/pre-migration-checks.sql`): código de salida 0, `Version actual de la base: 484`, ningún bloque `[V<n>]` corre salvo los `[V470]`, que son los que corren siempre. Este script sirve **antes** de migrar una base ya cargada; sobre una base nueva solo confirma que no rompe. El procedimiento completo con datos está en [../release/migration-smoke.md](../release/migration-smoke.md).

## 4. Datos sembrados (perfil `demo`)

Con `SPRING_PROFILES_ACTIVE=prod,local,demo` y `DEMO_SEED_ENABLED=true` (el overlay lo toma de `STAGING_DEMO_SEED`), el backend siembra después de arrancar hablando con su propia API. El perfil `local` es el que crea las cuentas `*.demo`; sin él el seed no tiene con qué autenticarse.

Medido (dataset `full`): 

```
Seed demo: personas ok en 6927 ms
Seed demo: oferta académica ok en 291005 ms
Seed demo: inscripciones ok en 119585 ms
Seed demo: asistencia ok en 1431 ms
Seed demo: notas ok en 120112 ms
Seed demo listo: dataset=full, pasos=5, 539065 ms
```

Contenido: 56 personas, 44 alumnos, 40 inscripciones a carrera, 117 inscripciones a materia. Login con `Test1234` verificado: `director.demo`, `alumno.demo` y `lucia.gomez` responden 200 en `POST /auth/login` con `{"username": ..., "password": "Test1234"}`. Usuarios y qué probar con cada uno: [../context/test-users.md](../context/test-users.md).

Es idempotente: si ya hay datos no vuelve a sembrar. Para repetir desde cero: `sc down -v` (borra los volúmenes de **este** proyecto; confirmar `COMPOSE_PROJECT_NAME` antes).

### Mails

Con `TRANSACTIONAL_MAIL_PROVIDER=mock` no sale ningún mail de masivos ni invitaciones, pero los mails de autenticación (recuperación de clave) van por SMTP (`BrevoEmailService`) aunque el proveedor sea `mock`. Durante el seed se emitieron 8 códigos de recuperación y los 8 envíos fallaron con `Authentication failed` porque `BREVO_SMTP_*` estaba vacío: ruido esperable y sin efecto en los datos. Con credenciales SMTP reales cargadas, esos mails sí saldrían: en una máquina de prueba dejarlas vacías.

## Qué dejó este recorrido

- Entorno donde se ejecutó: Postgres 16 y jar en el host, sin Docker build. **No ejecutado**: `sc up -d --build`, Caddy con el dominio y su certificado, `deploy/smoke.sh` contra Caddy, `sc` con el seed corriendo dentro del contenedor (límite de memoria 1 g del backend: el seed `full` se midió sin ese límite).
- Para correr esos pasos en la máquina de Gabriel: la sección 2, tal cual.

## Qué depende de Gabriel

| Qué | Por qué |
|---|---|
| Dominio y DNS (registro A, y AAAA si hay IPv6) | Caddy no emite el certificado de un dominio real hasta que resuelva al servidor. Para local alcanza `escuela.localhost` |
| Servidor con 80 y 443 abiertos y proveedor de hosting | Sigue pendiente de A2 ([README.md](README.md), «Pendiente de A2») |
| Segundo disco del servidor | Destino de los backups (`BACKUP_DIR=/ruta/absoluta`, distinto del disco de Postgres): [backup-and-restore.md](backup-and-restore.md) |
| `JWT_ACCEPT_MISSING_AUDIENCE=false` | El valor por defecto del código es `true` (`jwt.accept-missing-audience`). Pasarlo a `false` es una decisión de release: después de 15 minutos de desplegado el release 24.4 ya no quedan access tokens sin `aud` |
| `JWT_SECRET` válido | Al menos 32 caracteres y al menos 10 distintos; si no, el backend no arranca (`jwt.secret debe tener al menos 10 caracteres distintos`). Verificarlo: `printf %s "$JWT_SECRET" \| fold -w1 \| sort -u \| wc -l` |
| `BROADCAST_UNSUBSCRIBE_SECRET`, claves de Brevo, Google OAuth y Mercado Pago | Credenciales propias de la cuenta; ver tabla de [README.md](README.md) |
