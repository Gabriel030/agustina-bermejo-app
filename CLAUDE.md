# agustina-bermejo-app — índice de contexto

Este archivo es un **índice**, no la documentación en sí. Su único trabajo es decirte
qué archivo leer según el tipo de tarea, para no cargar todo el contexto del proyecto
de una — cada doc de `docs/context/` es acotado y se lee solo si la tarea lo pide.

Backend: Spring Boot 3 + Postgres + Flyway, `backend/`. Frontend: React 19 + Vite +
Tailwind v4 + shadcn/ui, `frontend/`. Roadmap y specs de producto: Obsidian, ver
`docs/context/specs.md` para la ubicación exacta.

## Reglas de código que NO se pueden pasar por alto

Estas dos aplican a **todo código nuevo**, backend y frontend, sin excepción — se
pidieron explícitamente y de forma repetida, así que van acá arriba de todo para
no perderlas en medio de una tarea larga:

1. **Identificadores de código en inglés, siempre.** Clases, métodos, variables,
   endpoints — todo en inglés (`createSection`, `submitForApproval`, no
   `crearComision`/`enviarAAprobacion`). Nunca mezclar idiomas dentro del mismo
   código. Solo aplica a código nuevo — lo que ya existe en castellano (gran
   parte del backend actual) no se toca de forma retroactiva salvo pedido
   explícito. Los mensajes/comentarios que el usuario pida puntualmente sí
   siguen en castellano, y todo lo que ve el usuario final en el frontend
   siempre se traduce a castellano — esto es solo sobre identificadores.
2. **Cero comentarios y cero javadoc en código nuevo.** Ni explicando el "qué"
   ni el "por qué", ni una decisión de diseño sutil — nada. Si hace falta
   explicar un razonamiento, va en la conversación, no en el código. Aplica a
   Java y a JS/JSX por igual. No se hace limpieza retroactiva de comentarios ya
   existentes salvo pedido explícito.

Detalle completo y contexto de por qué en la memoria de la sesión
(`feedback_nombres_de_metodos_en_ingles` y
`feedback_sin_comentarios_y_traducir_estados`) — esto de acá es el resumen que no
se puede perder.

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
