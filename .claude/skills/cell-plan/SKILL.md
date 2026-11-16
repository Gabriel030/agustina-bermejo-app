---
name: cell-plan
description: Convierte un ticket en un plan de la célula en docs/cell/plans/<ticket>.md (subtareas, contratos, asignación, orden de merge). Usar cuando el líder técnico recibe un ticket nuevo o dice "planificá", "armá el plan", "dividí este ticket", "cell-plan".
---

# cell-plan (solo líder técnico)

Entrada: ticket (texto, spec, o ruta). Salida: `docs/cell/plans/<ticket>.md` creado desde `docs/cell/plans/TEMPLATE.md`.

## Pasos
1. `git fetch origin`; `gh pr list --state open --json number,title,headRefName` (duplicados y migraciones en vuelo).
2. Definition of Ready: contrastar ticket con el código (grep contra `origin/develop`) y, si toca billing, con `docs/billing/decision-registry.md`. Contradicción, caso inalcanzable o pregunta abierta => anotar en "Preguntas al usuario" con opciones y recomendación; no resolver solo.
3. Cortar en subtareas ≤100k tokens, cada una con valor observable ("después, X puede hacer Y") y cómo probarla.
4. Contratos primero (sección Contratos): endpoints, `contracts/openapi.json`, migración con número reservado (último `V*.sql` en develop, master y PRs abiertos), eventos, puertos `<modulo>.api`, mocks del front.
5. Por subtarea: archivos/paquetes SIN solapamiento con otras, dependencias, D-xxx aplicables, tests esperados, rol/modelo, tope de tokens, si es plata/contrato/seguridad (=> dos revisores).
6. Orden de merge y paralelismo (máx. 2 con Maven en local).
7. Frontend: la subtarea queda bloqueada hasta tener mockup aprobado (ux-advisor + Artifact + OK del usuario).
8. Guardar el plan, commitear en rama `docs/plan-<ticket>` y avisar al usuario con las preguntas agrupadas.

## Reglas
- Plan conciso: listas y tablas, sin prosa. Identificadores en inglés, texto en castellano.
- No copiar specs enteras: referenciar ruta y sección.
- Un desvío se pregunta, no se decide.
