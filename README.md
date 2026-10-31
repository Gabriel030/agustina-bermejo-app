# agustina-bermejo-app

Aplicación web de gestión académica y administrativa para institutos de formación: personas, estructura académica, inscripciones, asistencia, evaluación, egresos, comunicados, notificaciones y un motor de facturación (cuotas, matrícula, comprobantes, notas de crédito).

> Verificado: 2026-10-31, commit `cd1ede80`.

## Stack

| Capa | Tecnología |
|---|---|
| Backend (`backend/`) | Java 21, Spring Boot 3.5, Spring Security con JWT, Spring Data JPA, Spring Modulith, Flyway, Maven (`./mvnw`) |
| Base de datos | PostgreSQL 16 |
| Frontend (`frontend/`) | React 19, Vite 7, Tailwind CSS v4, shadcn/ui, JavaScript (sin TypeScript), Vitest |
| Pruebas de sistema (`e2e/`) | Playwright (solo local) |

## Cómo levantarlo

Requisitos: JDK 21, Node.js, Docker (para Postgres y para los tests con Testcontainers).

```bash
docker compose up -d

cd backend
JWT_SECRET="dev-secret-local-solo-para-pruebas" SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

cd frontend
npm install
npm run dev
```

El backend queda en `http://localhost:8080`; el perfil `local` apunta al Postgres de `docker-compose.yml` y siembra usuarios de prueba (ver [docs/context/test-users.md](docs/context/test-users.md)). El frontend lee `VITE_API_BASE_URL` (ver `frontend/.env.example`).

Tests: `cd backend && ./mvnw test` (o `backend/dev-test <Clase|paquete|--changed>` para una corrida focalizada) y `cd frontend && npm test`. Detalle en [docs/context/testing.md](docs/context/testing.md) y [docs/context/debugging.md](docs/context/debugging.md).

## Estructura del repo

| Carpeta | Contenido |
|---|---|
| `backend/` | API Spring Boot, un paquete por módulo (`identity`, `academicstructure`, `enrollment`, `attendance`, `evaluation`, `graduation`, `announcement`, `notification`, `billing`, `config`, `shared`) y migraciones Flyway en `src/main/resources/db/migration` |
| `frontend/` | SPA React, organizada por rol y por funcionalidad |
| `contracts/` | `openapi.json`, contrato HTTP versionado y verificado en CI |
| `docs/` | Documentación técnica; el índice es [docs/README.md](docs/README.md) |
| `e2e/` | Pruebas de sistema con Playwright |
| `qa/`, `load/`, `scripts/` | Colecciones Bruno, pruebas de carga y scripts de apoyo |
| `.github/` | Workflows de CI y scripts de reparto de tests |

## Documentación y proceso

- [docs/README.md](docs/README.md): índice de toda la documentación del repo.
- [CLAUDE.md](CLAUDE.md): reglas de código y proceso (identificadores en inglés, sin comentarios, PRs contra `develop`).
- [docs/adr/](docs/adr/README.md): decisiones de arquitectura.
- [docs/context/workflow.md](docs/context/workflow.md) y [.github/workflows/ci.yml](.github/workflows/ci.yml): ramas, PRs y el flujo de CI.
