# Debugging — correr todo local

## Backend

Necesita Postgres. Dos formas:
- **Tests** (`./mvnw test`): Testcontainers levanta su propio Postgres
  descartable — solo necesitás **Docker corriendo** (`docker info` para
  chequear; si no, `open -a Docker` y esperar a que levante).
- **Correr la app** (`./mvnw spring-boot:run`): usa el Postgres de
  `docker-compose.yml` en la raíz del repo — `docker compose up -d` antes de
  arrancar. Además necesita `JWT_SECRET` seteada (no tiene default a propósito,
  ver `docs/context/backend.md`) — para desarrollo local alcanza cualquier
  string largo:
  ```bash
  JWT_SECRET="dev-secret-local-solo-para-pruebas" ./mvnw spring-boot:run
  ```

Errores típicos:
- `Could not resolve placeholder 'JWT_SECRET'` → falta la env var de arriba.
- `Could not find a valid Docker environment` (al correr tests) → Docker Desktop
  no está levantado.
- Puerto 8080 ocupado → probablemente quedó un proceso viejo, `pkill -f
  "spring-boot:run"`.

## Frontend

`npm run dev` en `frontend/` (puerto 5173, hardcodeado en CORS del backend —
`app.cors.allowed-origins`). Variable `VITE_API_BASE_URL` (ver `.env.example`)
apunta al backend, default `http://localhost:8080` — no hay proxy de Vite, el
frontend le pega directo al backend por CORS ya configurado.

Para levantar el preview en el navegador de Claude Code: `.claude/launch.json` ya
tiene la config `frontend` (`npm --prefix frontend run dev`).

## Datos de prueba — perfil `local`

`LocalSeedRunner` (`identity/specialist/LocalSeedRunner.java`, `@Profile("local")`) crea 5
cuentas ya logueables, una por rol, la primera vez que arranca la app con ese
perfil activo — idempotente, no duplica en arranques siguientes:

```bash
JWT_SECRET="dev-secret-local-solo-para-pruebas" SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

| username | password | rol |
|---|---|---|
| `alumno.demo` | `Test1234` | ALUMNO |
| `docente.demo` | `Test1234` | DOCENTE |
| `preceptor.demo` | `Test1234` | PRECEPTOR |
| `administrativo.demo` | `Test1234` | ADMINISTRATIVO |
| `director.demo` | `Test1234` | DIRECTOR |

**Alcance a propósito chico**: solo `Person` + rol, sin `Student`/`Instructor`
ni datos académicos (programas, cursos, inscripciones) — alcanza para probar
login/layout/ruteo por rol (Épica 1). Cuando las épicas de negocio (2, 3, 4)
necesiten datos reales para probar sus pantallas (un alumno con materias
inscriptas, un curso con comisiones, etc.), ese seeder es el lugar donde
sumarlos — no crear otro mecanismo aparte.

Sin el perfil `local` activo (`./mvnw spring-boot:run` a secas, o cualquier
otro perfil), el seeder no corre — nunca se activa en `test` ni en
prod/staging por accidente, no tiene ninguna referencia en
`application.properties`.

**Para un usuario de prueba que no sea una de estas 5 cuentas fijas** (ej. un
Docente con `Instructor`, que el seeder no crea): el flujo de recuperación
**ya no sirve** para esto. Manda un código por email —que en local falla— y en
`password_reset_token` guarda solo el hash, así que el código no se puede
recuperar ni del log ni de la base.

La vía que funciona es crear la persona por API y después copiarle a mano un
hash de password conocido:

```bash
# 1. crear la persona con su rol (como administrativo.demo)
curl -X POST http://localhost:8080/staff -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN" \
  -d '{"nombre":"Ana","apellido":"Docente","email":"ana@example.com","dni":"90001","role":"DOCENTE"}'
# el username que queda es el DNI

# 2. darle la misma password que las cuentas demo (Test1234)
docker compose exec -T postgres psql -U postgres -d agustina_bermejo -c \
  "update person set password = (select password from person where username='docente.demo'),
   status='ACTIVE' where username='90001';"
```

Para asignar ese docente a una comisión hace falta el id de `Instructor`, que
**ningún endpoint expone** (ver `Deuda técnica y gaps de QA` en Obsidian):
`select i.id from instructor i join person p on p.id = i.person_id where
p.username = '90001';`.

## Duplicados " 2.ext" sueltos en el árbol de archivos

Si `./mvnw compile` falla con `duplicate class` en archivos con nombre
`Algo 2.java`, no es un problema de código — es basura de sincronización (el
repo vive bajo `Documents/`, probablemente iCloud Drive, que duplica archivos
como "Algo 2.ext" cuando hay un conflicto de sync, ej. mientras un `git
checkout` escribe archivos al mismo tiempo que iCloud sincroniza). Verificar
que son duplicados idénticos antes de borrar (`diff` o hash, no asumir) y
borrarlos — no son parte del código, no se commitean nunca:

```bash
find . -iname "* 2.*" -not -path "*/node_modules/*" -not -path "*/target/*" -not -path "*/.git/*"
```

## Síntomas típicos de correo masivo (Brevo)

Procedimiento completo del ensayo en `docs/deploy/README.md`, «Prueba de envío masivo con Brevo».
Los logs se miran con `sc logs backend` (staging) o `dc logs backend` (producción).

- **El backend no arranca con `TRANSACTIONAL_MAIL_PROVIDER=brevo`:** falta `BREVO_API_KEY`. Es a
  propósito: el gateway de Brevo exige la clave al crearse (`BrevoTransactionalEmailGateway`).
- **El backend no arranca en prod o en staging (perfil `prod`):** falta `BROADCAST_UNSUBSCRIBE_SECRET`
  o es igual a `JWT_SECRET`. Es a propósito: los enlaces de baja no se firman con el secreto del JWT.
  Cargarla con un valor nuevo (`openssl rand -base64 48 | tr -d '\n'`).
- **El masivo quedó confirmado pero no sale ningún mail:** fuera de la franja `BROADCAST_DISPATCH_WINDOW_START`
  – `_END` (hora de `APP_TIMEZONE`) los mails quedan en cola. Esperar a la franja; no es un error.
  El job corre cada minuto y envía como máximo `BROADCAST_DISPATCH_MAX_PER_RUN` por corrida.
- **Los mails no salen y en la base aparecen `SENT_SIMULATED`:** el proveedor es `mock`
  (`TRANSACTIONAL_MAIL_PROVIDER=mock`, el valor de staging). Es el comportamiento esperado fuera del ensayo.
- **Un destinatario queda SKIPPED con `MISSING_TEMPLATE_VARIABLE`:** la plantilla usa `{{carrera}}`,
  `{{materia}}` o `{{comision}}`, que hoy no tienen fuente por persona, o un dato vacío. Quitar la variable
  de la plantilla o esperar a que se implemente la fuente.
- **Un destinatario queda SKIPPED con `NO_VERIFIED_EMAIL`:** su mail no está verificado. El filtro
  funciona bien; hay que verificar la casilla de la persona.
- **Un destinatario queda SKIPPED con `OPTED_OUT` o `NO_CONSENT`:** la persona se dio de baja o no
  consintió. Si la baja fue una prueba, restablecer el consentimiento antes de seguir probando con esa casilla.
- **Mail con `{{linkBaja}}` que lleva a `localhost`:** `FRONTEND_BASE_URL` quedó con el valor por defecto.
  Setearla con la URL pública del entorno y reenviar.
- **Log `Brevo rechazo el mail para ... status=4xx`:** Brevo rechazó el envío (`TransactionalEmailRejectedException`,
  no se reintenta). Con 401 o 403 revisar `BREVO_API_KEY`; con otros 4xx mirar el panel de Brevo
  (el remitente de `MAIL_FROM_ADDRESS` tiene que estar verificado; el texto exacto de Brevo: sin verificar).
- **Log `Brevo fallo con status 5xx` o `Brevo no respondio a tiempo`:** error reintentable. El envío vuelve
  a la cola y se reintenta; no hace falta tocar nada si no se repite.
- **Log `Mail enviado por Brevo a ...` no aparece aunque el masivo esté confirmado:** revisar primero la
  franja horaria y el cupo restante (`límite − máximo(transaccionales, reserva) − masivos` del día). Con el
  cupo en 0 los mails quedan en cola hasta el día siguiente.
- **El mail llega a spam:** el dominio no está autenticado en Brevo (SPF o DKIM sin verificar). Con SPF y DKIM
  en `pass` y aun así en spam, es un tema de contenido de la plantilla, no de configuración del backend.
- **El mail no trae la cabecera `List-Unsubscribe`:** Brevo la está quitando o no la reenvía. Procedimiento
  de comprobación en la misma sección de `docs/deploy/README.md`.

## Ver también

[docs/context/testing.md](testing.md) para cómo correr los tests una vez que todo
esto está levantado.
