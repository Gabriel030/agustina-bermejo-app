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

`LocalSeedRunner` (`config/LocalSeedRunner.java`, `@Profile("local")`) crea 5
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

**Para un usuario de prueba que no sea una de estas 5 cuentas fijas** (ej.
probar el flujo real de alta+primer-ingreso), seguís pudiendo usar el flujo
real de reset a mano:

```bash
curl -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" -d '{"email":"<email de una person existente>"}'
# el log imprime: "Token de recuperación de password generado para person N: <token>"
curl -X POST http://localhost:8080/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<token>","newPassword":"Test1234"}'
```

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

## Ver también

[docs/context/testing.md](testing.md) para cómo correr los tests una vez que todo
esto está levantado.
