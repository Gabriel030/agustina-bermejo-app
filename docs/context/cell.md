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

## Protocolo de respuesta y flujo continuo

- Todo pedido entre coordinador, líder y workers se cierra con una respuesta explícita con la señal acordada (`PLANIFICADO #N`, `LISTO … PARA NUBE`, `TERMINÉ`, `BLOQUEADO`). El silencio no es respuesta: quien pidió reclama.
- Un plan o refinamiento no se ejecuta solo: el agente avisa al terminar y espera el OK de quien lo pidió. Producto, plata y prioridades entre épicas las decide Gabriel (AskUserQuestion con recomendación); lo técnico chico lo decide el coordinador y lo informa.
- El coordinador es el pivot: mantiene carga paralela alta según la capacidad de la máquina, asigna cada ítem al puesto que corresponde, pide refinamiento y specs con anticipación y divide en rebanadas lanzables para no depender de Gabriel.
- El líder tiene el dominio de los merges (a `develop` y `release/*` a `master`) desde el 17-nov, con los controles de siempre: CI verde, revisión de plata/contrato/seguridad, smoke de migraciones y etiqueta `contract-break-approved` solo con diff seguro.

## Coordinación

- Hub-and-spoke: todo pasa por el líder.
- Fuente de verdad = GitHub, no archivos del repo (evita conflictos): issue del ticket, PRs con etiquetas `cell:<issue>`, `role:<puesto>` y `cell:blocked`, y el Project "Célula" (kanban por estado) armado con esos issues/PRs. En el repo solo quedan el plan (una vez), el contrato (`contracts/openapi.json` + sección Contratos) y `docs/cell/handoffs/` (en la rama del worker).
- Estado en la rama del worker: `docs/cell/progress/<issue>-<n>.md` con (a) checklist de criterios de aceptación con estado pasa/no pasa (los tests no se editan para hacerlos pasar) y (b) notas de progreso (hecho / sigue / decisiones / bloqueos); commit y push en cada hito.
- Comentarios del PR SOLO en: arranque (PR borrador), hitos (compila, tests de dominio verdes, ITs verdes, listo), `BLOQUEADO` / `NECESITO DECISIÓN` (más etiqueta `cell:blocked`) y `TERMINÉ` (más `gh pr ready`). No hay reportes periódicos: el líder mira commits y comentarios en los hitos (`gh pr view N --comments`, `gh pr list --label cell:blocked`).
- El líder envía instrucciones a una sesión de la nube con `claude -p "<mensaje>" --cloud <session-id>`; los resultados vuelven por el PR.
- Paralelismo: máximo 3 workers (2 backends + 1 frontend) + los subagentes revisores necesarios.
- Reporte del líder al usuario: al pedido y en cada hito (merge, bloqueo, decisión), con la tabla fija y debajo "Necesito de vos" (preguntas agrupadas, opciones y recomendación):

`| Puesto | Subtarea | PR | Estado | Avance | Tokens | Bloqueo |`

## Presupuesto y contexto

- Planificación del líder ~120k, corte 150k con handoff. Topes fijos SIN extensiones: worker 150k, revisor 100k, mecánico 80k (tokens de plataforma; la propia cuenta subestima ~2,2x).
- Con ventanas de contexto de 1M el límite real es el tope de tokens, no un % de contexto: el relevo es al tope (worker 150k, sin extensiones) o al cambiar de tarea. El worker escribe el handoff (`docs/cell/handoffs/<issue>-<n>.md`, en su rama) y el líder lanza uno fresco que lo lee. Opcional: `/compact <foco>` antes de un hito largo.
- Criterio de parada: tras 2 intentos fallidos de corregir lo mismo (test que no pasa, CI roja por la misma causa) no hay tercer intento: handoff con diagnóstico y el líder relanza con un brief mejor.
- Brief de cada subtarea (lo escribe el líder en el plan): objetivo, formato de salida esperado, archivos permitidos, FUERA DE ALCANCE explícito, criterios de aceptación verificables y comando de verificación (frontend: además navegador/preview).

## Dónde corren

| | Nube (claude.ai/code), por defecto | Local (Mac de Gabriel) |
|---|---|---|
| Quién | Workers (backends y frontend) | Líder y lo que necesite algo local: base demo 5433, preview en navegador, mockups/Obsidian |
| Entorno | Cada sesión con su propia VM: 4 vCPU, 16 GB, Docker/Postgres y Vitest propios | Cargada: mirar `uptime` |
| Concurrencia | Sin semáforo | Semáforo de Maven máx. 2 (`~/.claude/epica18/ci-slot.sh`, `backend/dev-test` lo toma solo); Vitest `--maxWorkers=2` |
| Arranque | Hook `SessionStart` ejecuta `scripts/cloud-session-bootstrap.sh`: toolchains JDK 21 y `dockerd` | Normal |
| Archivos de usuario | `~/.claude` no visible: todo está en el repo | `~/.claude` disponible |

En ambos casos: tests focalizados con `backend/dev-test`; suite completa y PIT en GitHub Actions.

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

## `gh` en sesiones de la nube (sin GraphQL)

Las sesiones cloud de Claude Code bloquean GraphQL (HTTP 403 "GitHub GraphQL is not available from Claude Code sessions"). Ahí no andan `gh issue view|list|comment|create`, `gh pr view|list|checks|create|comment|edit|merge` ni `gh label list`; sí andan `gh pr diff` y `gh api` (REST). En la Mac los comandos normales siguen valiendo. En la tabla, `R=repos/Gabriel030/agustina-bermejo-app`.

| Para qué | Equivalente REST | Estado |
|---|---|---|
| Ver issue | `gh api $R/issues/N` | validado en la nube |
| Listar issues por etiqueta | `gh api "$R/issues?labels=cell&state=all&per_page=50"` (filtrar `.pull_request == null`) | validado en la nube |
| Listar PRs | `gh api "$R/pulls?state=open&per_page=50"` | validado en la nube |
| Ver PR y sus archivos | `gh api $R/pulls/N`, `gh api $R/pulls/N/files`, `gh pr diff N` | validado en la nube |
| Comentar PR o issue | `gh api $R/issues/N/comments -F body=@archivo.md` | validado en la nube |
| Checks de un commit | `gh api $R/commits/<sha>/check-runs` | validado en la nube |
| Crear PR | `gh api $R/pulls -f title=... -f head=<rama> -f base=develop -F draft=true -F body=@cuerpo.md` (o `mcp__github__create_pull_request`) | SIN VALIDAR |
| Etiquetar | `gh api $R/issues/N/labels -f 'labels[]=cell:915'` | SIN VALIDAR |
| Sacar etiqueta | `gh api -X DELETE $R/issues/N/labels/<etiqueta>` | SIN VALIDAR |
| Crear etiqueta | `gh api $R/labels -f name=... -f color=...` | SIN VALIDAR |
| Mergear | `gh api -X PUT $R/pulls/N/merge -f merge_method=squash` | SIN VALIDAR |
| Pasar a ready / volver a borrador | `POST $R/pulls/N/ccr/ready_for_review` y `POST $R/pulls/N/ccr/convert_to_draft` | SIN VALIDAR |
| Crear issue | `gh api $R/issues -f title=... -F body=@archivo.md -f 'labels[]=cell'` | SIN VALIDAR |

## Qué leer

- Reglas de código y proceso: `CLAUDE.md`, `docs/context/workflow.md`, `docs/context/testing.md`.
- Backend: `docs/context/backend.md`, `docs/context/modules.md`. Frontend: `docs/context/frontend.md`, `docs/context/components.md`, `docs/context/design-system.md`.
- Billing: `docs/billing/decision-registry.md`.
