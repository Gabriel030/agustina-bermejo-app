# Célula de agentes

Equipo fijo de agentes que ejecuta tickets de punta a punta. El rol vive en archivos del repo (`.claude/agents/`, `.claude/skills/`); la sesión que lo toma es descartable.

## Roles

| Rol | Ficha | Modelo | Qué hace |
|---|---|---|---|
| Líder técnico | `.claude/agents/tech-lead.md` | Opus | Ticket => plan, contratos, asignación, orden de merge, preguntas al usuario |
| Backend (backend-1, backend-2) | `.claude/agents/backend.md` | Sonnet | Una subtarea de backend en el módulo que indique el ticket |
| Frontend (frontend-1) | `.claude/agents/frontend.md` | Sonnet | Una subtarea de front, con diseño aprobado |
| Revisor (reviewer-1, reviewer-2) | `.claude/agents/reviewer.md` | Sonnet | Revisión independiente del diff; nunca de su propio trabajo |
| Mecánico | `.claude/agents/mechanic.md` | Haiku | Traer develop, conflictos de docs/logs, tablero |

Skills: `cell-plan` (ticket => plan), `cell-worker` (tomar subtarea), `cell-review` (revisar PR), `cell-report` (formatos de reporte y mensajes).

## Cómo arranca una sesión

Una línea por sesión:
- "Sos backend-1: cargá `.claude/agents/backend.md` y la skill `cell-worker`; tu subtarea es `<ticket>#<n>`."
- "Sos frontend-1: cargá `.claude/agents/frontend.md` y la skill `cell-worker`; tu subtarea es `<ticket>#<n>`."
- "Sos reviewer-1: cargá `.claude/agents/reviewer.md` y la skill `cell-review`; revisá el PR #N."
- "Sos el mecánico: cargá `.claude/agents/mechanic.md`; tarea: ..."
- "Sos el líder: cargá `.claude/agents/tech-lead.md` y la skill `cell-plan`; ticket: ..."

Cada sesión trabaja en su propio worktree/checkout, con rama propia desde `origin/develop`.

## Flujo de un ticket

1. Líder: `cell-plan` => `docs/cell/plans/<ticket>.md` (plantilla en `docs/cell/plans/TEMPLATE.md`), con contratos, migraciones reservadas, subtareas ≤100k tokens, archivos sin solapamiento y orden de merge.
2. Líder pregunta al usuario, en un solo mensaje, lo que bloquea (negocio, diseño). Front: mockup aprobado antes de codificar.
3. Líder lanza workers con una línea de brief; workers corren `cell-worker` y abren PR contra `develop`.
4. Cada PR pasa por un revisor que no lo escribió (`cell-review`); plata/contrato/seguridad, por los dos. Una ronda de revisión y una de correcciones.
5. Líder decide el orden de merge (PRs apilados en orden estricto); mecánico trae develop y actualiza el tablero. Auto-merge solo para lo que no toca plata/seguridad, con `backend` y `frontend` en verde; releases a `master` los hace el usuario.

## Coordinación

- Hub-and-spoke: todo pasa por el líder.
- Fuente de verdad en archivos: plan (con tablero Estado), contrato (`contracts/openapi.json` + sección Contratos), `docs/cell/handoffs/`.
- Mensajes entre sesiones: solo `TERMINÉ`, `BLOQUEADO`, `NECESITO DECISIÓN` (formatos en `.claude/skills/cell-report/SKILL.md`). Avance: una línea al minuto 10 y luego cada 20.

## Presupuesto

- Planificación del líder ~120k, corte 150k con handoff. Workers: subtarea ≤100k, tope duro 150k (tokens de plataforma; la propia cuenta subestima ~2,2x). Revisión ≤100k. Mecánico ≤80k.
- Al tope no se reanuda: handoff y agente nuevo.

## Local vs nube

| | Local (Mac) | Nube (claude.ai/code) |
|---|---|---|
| Entorno | Cargada; máx. 2 agentes con Maven, Vitest con `--maxWorkers=2`, mirar `uptime` | Ubuntu, Java 21, Docker apagado al inicio |
| Arranque | Normal | Hook `SessionStart` ejecuta `scripts/cloud-session-bootstrap.sh`: toolchains JDK 21 y `dockerd` |
| Semáforo de Maven | `~/.claude/epica18/ci-slot.sh` si existe (`backend/dev-test` lo usa) | No existe ni hace falta |
| Archivos de usuario | `~/.claude` disponible | No visible: todo lo necesario está en el repo |

En ambos casos: tests focalizados con `backend/dev-test`; suite completa y PIT en GitHub Actions.

## Qué leer

- Reglas de código y proceso: `CLAUDE.md`, `docs/context/workflow.md`, `docs/context/testing.md`.
- Backend: `docs/context/backend.md`, `docs/context/modules.md`. Frontend: `docs/context/frontend.md`, `docs/context/components.md`, `docs/context/design-system.md`.
- Billing: `docs/billing/decision-registry.md`.
