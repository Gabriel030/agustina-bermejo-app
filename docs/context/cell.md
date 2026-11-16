# Célula de agentes

Equipo fijo de agentes que ejecuta tickets de punta a punta. El rol vive en archivos del repo (`.claude/agents/`, `.claude/skills/`); la sesión que lo toma es descartable.

## Roles

| Rol | Ficha | Modelo | Qué hace |
|---|---|---|---|
| Líder técnico | `.claude/agents/tech-lead.md` | Opus | Issue => plan, contratos, asignación, orden de merge, preguntas al usuario |
| Backend (backend-1, backend-2) | `.claude/agents/backend.md` | Sonnet | Una subtarea de backend en el módulo que indique el ticket |
| Frontend (frontend-1) | `.claude/agents/frontend.md` | Sonnet | Una subtarea de front, con diseño aprobado |
| Revisor (reviewer-1, reviewer-2) | `.claude/agents/reviewer.md` | Sonnet | Revisión independiente del diff; nunca de su propio trabajo |
| Mecánico | `.claude/agents/mechanic.md` | Haiku | Traer develop, conflictos de docs/logs |

Skills: `cell-plan` (ticket => plan), `cell-worker` (tomar subtarea), `cell-review` (revisar PR), `cell-report` (formatos de reporte y mensajes).

## Cómo arranca una sesión

Una línea por sesión:
- "Sos backend-1: cargá `.claude/agents/backend.md` y la skill `cell-worker`; tu subtarea es `<issue>#<n>`."
- "Sos frontend-1: cargá `.claude/agents/frontend.md` y la skill `cell-worker`; tu subtarea es `<issue>#<n>`."
- "Sos reviewer-1: cargá `.claude/agents/reviewer.md` y la skill `cell-review`; revisá el PR #N."
- "Sos el mecánico: cargá `.claude/agents/mechanic.md`; tarea: ..."
- "Sos el líder: cargá `.claude/agents/tech-lead.md` y la skill `cell-plan`; ticket: ..."

Cada sesión trabaja en su propio worktree/checkout, con rama propia desde `origin/develop`.

## Flujo de un ticket

1. Los tickets son GitHub Issues. Líder: lee el issue, `cell-plan` => `docs/cell/plans/<issue>.md` (plantilla `docs/cell/plans/TEMPLATE.md`, escrito UNA vez), lo linkea en el issue y crea las sub-tareas (checklist o sub-issues). Contratos primero, subtareas ≤100k con valor observable, un dueño por archivo, dependencias explícitas, backend y frontend en paralelo con mocks del contrato, una subtarea por puesto.
2. Líder pregunta al usuario, en un solo mensaje, lo que bloquea (negocio, diseño). Front: mockup aprobado antes de codificar.
3. Líder lanza workers con una línea de brief; abren PR en BORRADOR con un primer commit chico y trabajan con etiquetas `cell:<issue>` y `role:<puesto>`. Los PRs referencian el issue (`Refs #N`; `Closes #N` solo el último).
4. Cada PR pasa por un revisor que no lo escribió (`cell-review`), alternando reviewer-1/reviewer-2 (etiqueta `review:reviewer-1|2`); el informe es un comentario del PR. Plata/contrato/seguridad: MERGEABLE de ambos. Una ronda de revisión y una de correcciones.
5. El líder mergea automático cuando el revisor asignado da MERGEABLE (o MERGEABLE CON MENORES ya corregidos) y la CI está verde; plata/contrato/seguridad requieren MERGEABLE de reviewer-1 y reviewer-2. PRs apilados en orden estricto; el mecánico trae develop. Releases a `master` los hace el usuario.

## Coordinación

- Hub-and-spoke: todo pasa por el líder.
- Fuente de verdad = GitHub, no archivos del repo (evita conflictos): issue del ticket, PRs con etiquetas `cell:<issue>`, `role:<puesto>`, `review:reviewer-1|2` y `cell:blocked`, y el Project "Célula" (kanban por estado) armado con esos issues/PRs. En el repo solo quedan el plan (una vez), el contrato (`contracts/openapi.json` + sección Contratos) y `docs/cell/handoffs/` (en la rama del worker).
- Reportes de workers de la nube (no pueden mandar mensajes a otras sesiones): PR en borrador desde el primer commit y COMENTARIOS del PR con la línea de `cell-report` + `contexto: <%>`: a los 10 min, luego cada 20 o por hito. Al terminar: `TERMINÉ` y `gh pr ready`. `BLOQUEADO` y `NECESITO DECISIÓN` también como comentario + etiqueta `cell:blocked`. Las sesiones locales pueden además mandar mensaje; la verdad son los comentarios. El líder los lee con `gh pr view N --comments` y `gh pr list --label cell:blocked`.
- Paralelismo: máximo 3 workers (2 backends + 1 frontend) + 2 revisores a la vez.
- Reporte del líder al usuario: al pedido y en cada hito (merge, bloqueo, decisión), con la tabla fija y debajo "Necesito de vos" (preguntas agrupadas, opciones y recomendación):

`| Puesto | Subtarea | PR | Estado | Avance | Tokens | Contexto | Bloqueo |`

## Presupuesto y contexto

- Planificación del líder ~120k, corte 150k con handoff. Topes fijos SIN extensiones: worker 150k, revisor 100k, mecánico 80k (tokens de plataforma; la propia cuenta subestima ~2,2x).
- Cada reporte incluye el % de contexto usado. Al 60 % el worker escribe el handoff (`docs/cell/handoffs/<issue>-<n>.md`, en su rama) y frena; al tope también. El líder lanza uno fresco que lo lee.

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
gh label create "cell:blocked" --color D93F0B --description "Bloqueado o con decisión pendiente"
gh label create "review:reviewer-1" --color 0E8A16
gh label create "review:reviewer-2" --color 0E8A16
gh label create "role:backend-1" --color 1D76DB
gh label create "role:backend-2" --color 1D76DB
gh label create "role:frontend-1" --color 5319E7
gh label create "role:reviewer-1" --color FBCA04
gh label create "role:reviewer-2" --color FBCA04
gh label create "role:mechanic" --color C5DEF5
gh auth refresh -s project
gh project create --owner Gabriel030 --title "Célula"
```

- Por ticket: `gh label create "cell:<issue>" --color BFD4F2` antes de lanzar workers.
- En el Project "Célula" crear el campo Status (Por hacer, En curso, En revisión, Bloqueado, Hecho) y agregar issues y PRs (`gh project item-add <n> --owner Gabriel030 --url <url>`); el líder los mueve por estado al leer los comentarios.

## Qué leer

- Reglas de código y proceso: `CLAUDE.md`, `docs/context/workflow.md`, `docs/context/testing.md`.
- Backend: `docs/context/backend.md`, `docs/context/modules.md`. Frontend: `docs/context/frontend.md`, `docs/context/components.md`, `docs/context/design-system.md`.
- Billing: `docs/billing/decision-registry.md`.
