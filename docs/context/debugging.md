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

## Crear un usuario de prueba para probar login manualmente

No hay seed data. Para crear una persona con rol y contraseña sin tocar el hash
de bcrypt a mano, usar el flujo real de reset:

```bash
# 1. Insertar address + person + person_role directo en la DB (ver docker-compose.yml
#    para el nombre del contenedor de postgres)
# 2. Disparar forgot-password (202) y leer el token generado en el log del backend
curl -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" -d '{"email":"prueba@example.com"}'
# el log imprime: "Token de recuperación de password generado para person N: <token>"
# 3. Setear la contraseña con ese token (mínimo 8 caracteres, letra + dígito)
curl -X POST http://localhost:8080/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<token>","newPassword":"Test1234"}'
```

Borrar el usuario de prueba de la DB al terminar — no dejar datos de prueba en el
Postgres local compartido del repo.

## Ver también

[docs/context/testing.md](testing.md) para cómo correr los tests una vez que todo
esto está levantado.
