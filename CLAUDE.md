# agustina-bermejo-app — índice de contexto

Este archivo es un **índice**, no la documentación en sí. Su único trabajo es decirte
qué archivo leer según el tipo de tarea, para no cargar todo el contexto del proyecto
de una — cada doc de `docs/context/` es acotado y se lee solo si la tarea lo pide.

Backend: Spring Boot 3 + Postgres + Flyway, `backend/`. Frontend: React 19 + Vite +
Tailwind v4 + shadcn/ui, `frontend/`. Roadmap y specs de producto: Obsidian, ver
`docs/context/specs.md` para la ubicación exacta.

## Qué leer según la tarea

| Tarea | Leer |
|---|---|
| Programar una feature de **backend** (entidad, servicio, controller, migración) | [docs/context/backend.md](docs/context/backend.md) |
| Programar una feature de **frontend** (componente, pantalla, hook, ruteo) | [docs/context/frontend.md](docs/context/frontend.md) |
| Escribir o revisar **tests** (unitarios, integración, frontend) | [docs/context/testing.md](docs/context/testing.md) |
| **Debuggear** algo que no arranca o falla localmente | [docs/context/debugging.md](docs/context/debugging.md) |
| **Git / commits / PRs / ramas** | [docs/context/workflow.md](docs/context/workflow.md) |
| Escribir una **spec técnica/funcional nueva** antes de implementar | Skill `spec-writing` — no leas un doc, disparala directamente |
| Elegir un **patrón de diseño** (GoF o táctico DDD) para una feature o refactor | Skill `design-patterns` |

Si la tarea no encaja en ninguna fila, seguí con el juicio normal — este índice
cubre lo recurrente, no reemplaza pensar.

## Verdad de una sola fuente

Estos docs describen **convenciones y patrones**, no el estado actual del código ni
del roadmap — para eso siempre el código real y Obsidian (`docs/context/specs.md`)
ganan por sobre lo que diga cualquier doc de esta carpeta si llegan a discrepar.
