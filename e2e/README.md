# E2E (Playwright)

Dos configuraciones, cada una con su propio backend de sistema (Postgres de Testcontainers nuevo en cada arranque; requiere Docker):

| Config | Backend | Specs | Comando |
|---|---|---|---|
| `playwright.config.js` | `SystemTestApplication`: 5 cuentas `.demo` + 2 cuotas abiertas de `alumno.demo` | `specs/smoke`, `specs/flows` | `scripts/system-tests.sh` |
| `playwright.demo.config.js` | `DemoSystemTestApplication`: perfil `local,demo` (seed educativo demo: carrera, ciclo, comisiones, alumnos con asistencia) | `specs/demo` | `scripts/system-tests.sh --config playwright.demo.config.js` |

`scripts/system-tests.sh` toma el semáforo de Maven, elige puertos libres, instala dependencias y corre Chromium. Para iterar con servidores ya levantados: `E2E_REUSE_SERVERS=1 npx playwright test --config playwright.demo.config.js` (backend en `E2E_BACKEND_PORT`, default 8090, y `vite preview` en `E2E_FRONTEND_PORT`, default 4173).

## Datos y repetibilidad

El seed demo es idempotente y los specs de `specs/demo` no dependen de su estado: cada test crea sus propios datos por API (por ejemplo `fixtures/attendance.js` registra una inasistencia nueva con una fecha de clase única) y puede correrse varias veces contra el mismo backend.

## CI

No hay job de E2E en el workflow. Cómo sería: un job con Docker, `node 20`, JDK 21 y `scripts/system-tests.sh --config playwright.demo.config.js` (el flujo de asistencia tarda ~25 s; el arranque en frío del backend demo y el build del front suman ~3 a 4 min en una máquina cargada), con `retries: 1` (ya activo con `CI`) y el reporte HTML (`playwright-report/`) como artefacto.
