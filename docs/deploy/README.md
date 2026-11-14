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
`frontend/Caddyfile`, `deploy/.env.example`.

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
| `BREVO_SMTP_USERNAME`, `BREVO_SMTP_KEY`, `MAIL_FROM_ADDRESS` | Los de Brevo |
| `GOOGLE_OAUTH_CLIENT_ID` y `VITE_GOOGLE_CLIENT_ID` | El mismo Client ID en las dos |

Después:

```bash
docker compose --env-file deploy/.env -f docker-compose.prod.yml up -d --build
docker compose --env-file deploy/.env -f docker-compose.prod.yml ps
```

La primera vez construye las imágenes (varios minutos) y Flyway crea todas las tablas en la base
vacía. Cuando `ps` muestre los tres servicios `healthy`, verificar:

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
   Guardarla fuera del servidor. (Los backups programados tienen su propio runbook.)
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
5. Probar `curl -fsS https://<dominio>/api/actuator/health` y entrar a la app.

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

Todos viven en `deploy/.env`. Después de cambiar uno: `dc up -d` (recrea solo lo que cambió).

| Secreto | Efecto de rotarlo | Cómo |
|---|---|---|
| `JWT_SECRET` | Cierra todas las sesiones abiertas (todos tienen que volver a entrar) | Generar uno nuevo, `dc up -d backend` |
| `POSTGRES_PASSWORD` | Hay que cambiarla también dentro de Postgres | `dc exec postgres psql -U <usuario> -c "ALTER USER <usuario> PASSWORD 'nueva'"`, luego editar `.env` y `dc up -d` |
| `BREVO_SMTP_KEY` | Sin efecto si se cambia a la vez en Brevo | Crear la clave nueva en Brevo, editar `.env`, `dc up -d backend`, revocar la vieja |
| `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` | Los cobros online usan el nuevo apenas reinicia | Cambiarlos en el panel de Mercado Pago y en `.env`, `dc up -d backend` |
| `ACTUATOR_PASSWORD` | Solo afecta al monitoreo | Cambiar en `.env` y en el monitoreo |

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

## Qué NO hace este paquete

- No publica imágenes en ningún registro (el CI solo las construye para validar).
- No incluye backups ni monitoreo (viven en `deploy/backup/` y `deploy/monitoring/`).
- No define el proveedor de hosting ni el dominio.
