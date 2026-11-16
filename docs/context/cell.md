# Célula de agentes

Equipo fijo de agentes que ejecuta tickets de punta a punta. El rol vive en archivos del repo (`.claude/agents/`, `.claude/skills/`); la sesión que lo toma es descartable.

## Roles

| Rol | Ficha | Modelo | Qué hace |
|---|---|---|---|
| Coordinador | `.claude/agents/coordinator.md` | Opus, local | Backlog en Obsidian, prioridades, Issues `cell` para el líder, reporte de épicas a Gabriel; único que escribe en Obsidian |
| Líder técnico | `.claude/agents/tech-lead.md` | Opus | Issue => plan, contratos, asignación, orden de merge, preguntas al usuario |
| Backend (backend-1, backend-2) | `.claude/agents/backend.md` | Sonnet | Una subtarea de backend en el módulo que indique el ticket |
| Frontend (frontend-1) | `.claude/agents/frontend.md` | Sonnet | Una subtarea de front, con diseño aprobado |
| Revisor (subagente) | `.claude/agents/reviewer.md` | Sonnet, `maxTurns` 30 | Revisión independiente del diff; lo lanza el líder por PR con contexto limpio |
| Mecánico (subagente) | `.claude/agents/mechanic.md` | Haiku, `maxTurns` 25 | Traer develop, conflictos de docs/logs; lo lanza el líder por tarea |

Skills: `cell-backlog` (backlog en Obsidian ↔ Issues, solo coordinador), `cell-plan` (ticket => plan), `cell-design` (ticket => mockup aprobado), `cell-contract` (mockup => PR de contrato), `cell-worker` (tomar subtarea), `cell-review` (revisar PR), `cell-report` (formatos de reporte y mensajes), `code-index` (índice de código del MCP `codebase-memory`, proyecto `agustina-develop`, solo en la Mac; `scripts/update-code-index.sh` lo deja en `origin/develop` al abrir sesión y tras cada merge del líder).

## Cómo arranca una sesión

Una línea por sesión (workers y líder). Revisores y mecánico no son sesiones: el líder los lanza como subagentes con la herramienta Agent (`reviewer` / `mechanic`), con contexto limpio y solo lo necesario (revisor: diff + checklist + D-xxx).
- "Sos backend-1: cargá `.claude/agents/backend.md` y la skill `cell-worker`; tu subtarea es `<issue>#<n>`."
- "Sos frontend-1: cargá `.claude/agents/frontend.md` y la skill `cell-worker`; tu subtarea es `<issue>#<n>`."
- "Sos el líder: cargá `.claude/agents/tech-lead.md` y la skill `cell-plan`; ticket: ..."
- "Sos el coordinador: cargá `.claude/agents/coordinator.md` y la skill `cell-backlog`." (solo en la Mac)

Cada sesión trabaja en su propio worktree/checkout, con rama propia desde `origin/develop`.

## Flujo de un ticket

Tipo de ticket (lo fija el líder en el plan): A solo backend (contrato => backend); B cambia una pantalla existente (mockup => OK de Gabriel => contrato => backend y frontend en paralelo); C pantalla nueva (mockup completo => `ux-advisor` => OK de Gabriel => contrato => backend y frontend en paralelo). Gate: con pantalla, sin OK de Gabriel no arrancan contrato ni frontend; el backend que no depende de la pantalla (dominio, migraciones, reglas, tests) puede arrancar antes. El contrato es del líder (`cell-contract`) y se mergea primero; los cambios a mitad de camino solo entran vía él.

1. Los tickets son GitHub Issues. Líder: lee el issue, `cell-plan` => `docs/cell/plans/<issue>.md` (plantilla `docs/cell/plans/TEMPLATE.md`, escrito UNA vez), lo linkea en el issue y crea las sub-tareas (checklist o sub-issues). Contratos primero, subtareas ≤100k con valor observable, un dueño por archivo, dependencias explícitas, backend y frontend en paralelo con mocks del contrato, una subtarea por puesto.
2. Líder pregunta al usuario, en un solo mensaje, lo que bloquea (negocio, diseño). Front: mockup aprobado antes de codificar.
3. Líder lanza workers con una línea de brief; abren PR en BORRADOR con un primer commit chico y trabajan con etiquetas `cell:<issue>` y `role:<puesto>`. Los PRs referencian el issue (`Refs #N`; `Closes #N` solo el último).
4. Cada PR pasa por el subagente revisor (`cell-review`), una invocación; el informe es un comentario del PR. Plata/contrato/seguridad: dos invocaciones independientes (reviewer-1 y reviewer-2), MERGEABLE de ambas. Una ronda de revisión y una de correcciones.
5. El líder mergea automático cuando el revisor asignado da MERGEABLE (o MERGEABLE CON MENORES ya corregidos) y la CI está verde; plata/contrato/seguridad requieren MERGEABLE de las dos invocaciones. PRs apilados en orden estricto; el mecánico trae develop. Releases a `master` los hace el usuario.

## Coordinador y líder

| | Coordinador | Líder técnico |
|---|---|---|
| Dueño de | Backlog en Obsidian, épicas, prioridades | Plan, contratos, workers, revisión y merge |
| Entrada | Gabriel | Issues `cell` del coordinador |
| Salida | Issues `cell`, Obsidian actualizado | PRs mergeados, avisos al coordinador |
| Reporta a Gabriel | Avance de épicas y tareas | Estado de la célula (puestos, PRs, tokens) |
| Escribe en Obsidian | Sí, el único | Nunca |

Circuito: Gabriel prioriza => el coordinador convierte el ítem de Obsidian en un Issue `cell` y avisa al líder => la célula ejecuta => el líder avisa al coordinador por SendMessage en cada hito (formatos en `cell-report`) => el coordinador actualiza Obsidian (`~/.claude/epica18/on-merge.py` + índice de la épica). Sin sesión coordinadora abierta, los avisos quedan como comentario en el Issue con etiqueta `cell:coordinator`.

## Coordinación

- Hub-and-spoke: todo pasa por el líder.
- Fuente de verdad = GitHub, no archivos del repo (evita conflictos): issue del ticket, PRs con etiquetas `cell:<issue>`, `role:<puesto>` y `cell:blocked`, y el Project "Célula" (kanban por estado) armado con esos issues/PRs. En el repo solo quedan el plan (una vez), el contrato (`contracts/openapi.json` + sección Contratos) y `docs/cell/handoffs/` (en la rama del worker).
- Estado en la rama del worker: `docs/cell/progress/<issue>-<n>.md` con (a) checklist de criterios de aceptación con estado pasa/no pasa (los tests no se editan para hacerlos pasar) y (b) notas de progreso (hecho / sigue / decisiones / bloqueos); commit y push en cada hito.
- Comentarios del PR SOLO en: arranque (PR borrador), hitos (compila, tests de dominio verdes, ITs verdes, listo), `BLOQUEADO` / `NECESITO DECISIÓN` (más etiqueta `cell:blocked`) y `TERMINÉ` (más `gh pr ready`). No hay reportes periódicos: el líder mira commits y comentarios en los hitos (`gh pr view N --comments`, `gh pr list --label cell:blocked`).
- El líder lanza los workers como subagentes locales (herramienta Agent, con `isolation: worktree` o con un worktree propio creado con `git worktree add`) o como sesiones locales; los resultados vuelven por el PR y por SendMessage.
- Paralelismo: máximo 2 workers de código a la vez (según capacidad de la máquina) + los subagentes revisores necesarios.
- Reporte del líder al usuario: al pedido y en cada hito (merge, bloqueo, decisión), con la tabla fija y debajo "Necesito de vos" (preguntas agrupadas, opciones y recomendación):

`| Puesto | Subtarea | PR | Estado | Avance | Tokens | Bloqueo |`

## Presupuesto y contexto

- Planificación del líder ~120k, corte 150k con handoff. Topes fijos SIN extensiones: worker 150k, revisor 100k, mecánico 80k (tokens de plataforma; la propia cuenta subestima ~2,2x).
- Con ventanas de contexto de 1M el límite real es el tope de tokens, no un % de contexto: el relevo es al tope (worker 150k, sin extensiones) o al cambiar de tarea. El worker escribe el handoff (`docs/cell/handoffs/<issue>-<n>.md`, en su rama) y el líder lanza uno fresco que lo lee. Opcional: `/compact <foco>` antes de un hito largo.
- Criterio de parada: tras 2 intentos fallidos de corregir lo mismo (test que no pasa, CI roja por la misma causa) no hay tercer intento: handoff con diagnóstico y el líder relanza con un brief mejor.
- Brief de cada subtarea (lo escribe el líder en el plan): objetivo, formato de salida esperado, archivos permitidos, FUERA DE ALCANCE explícito, criterios de aceptación verificables y comando de verificación (frontend: además navegador/preview).

## Dónde corren

Todo corre en local (Mac de Gabriel: 8 núcleos, 16 GB). La nube se descartó el 16-nov-2026 (ver la nota al final de esta sección).

| Regla | Detalle |
|---|---|
| Quién | Líder, workers (subagentes o sesiones locales), revisores y mecánico |
| Workers de código | Máximo 2 a la vez |
| Maven | Semáforo de máx. 2 (`~/.claude/epica18/ci-slot.sh`; `backend/dev-test` lo toma solo) |
| Vitest | Siempre `--maxWorkers=2` |
| Aislamiento | Nunca dos agentes en el mismo worktree |
| Capacidad | Antes de lanzar código correr `python3 ~/.claude/orquestacion/watch.py` y respetar el veredicto |
| Veredicto 🟢 | Se puede lanzar código |
| Veredicto 🟡 | Solo investigación y specs |
| Veredicto 🔴 | No se lanza nada |
| Recursos locales | El código local puede usar el índice de código, `~/.claude`, la base demo 5433 y el preview en navegador |

Tests focalizados con `backend/dev-test`; suite completa y PIT en GitHub Actions. En 🟡/🔴 solo se hacen specs, investigación y revisión.

Descartado (16-nov): la célula no usa sesiones de la nube. `scripts/cloud-session-bootstrap.sh` no se usa: la nube quedó descartada el 16-nov.

## Setup inicial de GitHub (lo corre el líder la primera vez)

```
gh label create "cell" --color 0E8A16 --description "Ticket delegado a la célula por el coordinador"
gh label create "cell:coordinator" --color FBCA04 --description "Aviso del líder pendiente para el coordinador"
gh label create "cell:blocked" --color D93F0B --description "Bloqueado o con decisión pendiente"
gh label create "role:backend-1" --color 1D76DB
gh label create "role:backend-2" --color 1D76DB
gh label create "role:frontend-1" --color 5319E7
gh auth refresh -s project
gh project create --owner Gabriel030 --title "Célula"
```

- Por ticket: `gh label create "cell:<issue>" --color BFD4F2` antes de lanzar workers.
- En el Project "Célula" crear el campo Status (Por hacer, En curso, En revisión, Bloqueado, Hecho) y agregar issues y PRs (`gh project item-add <n> --owner Gabriel030 --url <url>`); el líder los mueve por estado al leer los comentarios.

## `gh` y la nube

Descartado (16-nov): la célula no usa sesiones de la nube; las sesiones cloud bloquean GraphQL de GitHub y cobran tokens aparte. En local `gh` funciona normal.

## Qué leer

- Reglas de código y proceso: `CLAUDE.md`, `docs/context/workflow.md`, `docs/context/testing.md`.
- Backend: `docs/context/backend.md`, `docs/context/modules.md`. Frontend: `docs/context/frontend.md`, `docs/context/components.md`, `docs/context/design-system.md`.
- Billing: `docs/billing/decision-registry.md`.
