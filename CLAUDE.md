# agustina-bermejo-app — índice de contexto

Este archivo es un **índice**, no la documentación en sí. Su único trabajo es decirte
qué archivo leer según el tipo de tarea, para no cargar todo el contexto del proyecto
de una — cada doc de `docs/context/` es acotado y se lee solo si la tarea lo pide.

Backend: Spring Boot 3 + Postgres + Flyway, `backend/`. Frontend: React 19 + Vite +
Tailwind v4 + shadcn/ui, `frontend/`. Roadmap y specs de producto: Obsidian, ver
`docs/context/specs.md` para la ubicación exacta.

## Reglas de código que NO se pueden pasar por alto

Estas tres aplican a **todo código nuevo**, backend y frontend, sin excepción — se
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
3. **Nombres de tests también en inglés** (`validTokenMarksEmailAsVerified`, no
   `tokenValidoMarcaEmail...`).

Reglas de proceso igual de no negociables (detalle en `docs/context/workflow.md`
y `~/.claude/instructions/workflow-git.md`):

- **CI local antes de abrir cualquier PR**: correr lo mismo que corre el CI y verlo
  pasar. No se abre un PR que pueda romper el CI (GitHub Actions cobra minutos).
- **PRs siempre contra `develop`** (`--base develop` explícito); rama propia por cambio;
  aprobación explícita antes de cada commit y de cada push; el merge lo hace el usuario.
- **Migraciones Flyway**: mirar el último número en `origin/develop` y `origin/master`
  antes de crear una.
- **Todo diseño o mockup pasa por el agente `ux-advisor`** antes de mostrarse, y no se
  codifica sin aprobación del diseño. Ver `docs/context/frontend.md`.
- **Releases y hotfixes**: solo el usuario abre PRs contra `master` (`release/*` desde
  develop, `hotfix/*` desde master). Un push a `master` que no venga de `release/*`
  dispara un PR automático "Backport" (rama `backport/*`, cherry-pick) hacia `develop`, salvo que venga de una rama `release/*`. No existe un PR
  automático develop → master.
- **Mockups: siempre como Artifact** (skill `vidrio-liquido-mockup`), nunca un `.html` suelto.

## Qué leer según la tarea

| Tarea | Leer |
|---|---|
| Programar una feature de **backend** (entidad, servicio, controller, migración) | [docs/context/backend.md](docs/context/backend.md) |
| Programar una feature de **frontend** (componente, pantalla, hook, ruteo) | [docs/context/frontend.md](docs/context/frontend.md) |
| Tocar **límites entre módulos** (Spring Modulith: api/events/model, línea base de violaciones) | [docs/context/modules.md](docs/context/modules.md) |
| Escribir o revisar **tests** (unitarios, integración, frontend) | [docs/context/testing.md](docs/context/testing.md) — empezá por "Mapa de herramientas: qué pregunta responde cada una" |
| **Debuggear** algo que no arranca o falla localmente | [docs/context/debugging.md](docs/context/debugging.md) |
| **Git / commits / PRs / ramas / CI local / migraciones / Backport** | [docs/context/workflow.md](docs/context/workflow.md) |
| Elegir **qué componente usar** / listar componentes en una spec | [docs/context/components.md](docs/context/components.md) |
| **Usuarios de prueba** y qué usuario usar para probar cada flujo | [docs/context/test-users.md](docs/context/test-users.md) |
| Levantar la **demo con datos realistas** (base separada, perfil `demo`, rama `demo/seed-data`) | [docs/context/demo.md](docs/context/demo.md) |
| **Diseñar o mockear** una pantalla (flujo con `ux-advisor`) | [docs/context/frontend.md](docs/context/frontend.md), sección "Flujo de diseño" |
| Escribir una **spec técnica/funcional nueva** antes de implementar | Skill `spec-writing` — no leas un doc, disparala directamente |
| Elegir un **patrón de diseño** (GoF o táctico DDD) para una feature o refactor | Skill `design-patterns` |

Si la tarea no encaja en ninguna fila, seguí con el juicio normal — este índice
cubre lo recurrente, no reemplaza pensar.

## Verdad de una sola fuente

Estos docs describen **convenciones y patrones**, no el estado actual del código ni
del roadmap — para eso siempre el código real y Obsidian (`docs/context/specs.md`)
ganan por sobre lo que diga cualquier doc de esta carpeta si llegan a discrepar.

Regla de mantenimiento: lo que lleve mucho contenido va en su archivo de
`docs/context/` y acá solo se referencia; este archivo se mantiene corto.
