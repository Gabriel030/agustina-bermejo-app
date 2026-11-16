---
name: cell-plan
description: Convierte un GitHub Issue en un plan de la célula en docs/cell/plans/<issue>.md (subtareas, contratos, asignación, orden de merge). Usar cuando el líder técnico recibe un ticket nuevo o dice "planificá", "armá el plan", "dividí este ticket", "cell-plan".
---

# cell-plan (solo líder técnico)

Entrada: GitHub Issue (`gh issue view N`) y su spec. Salida: `docs/cell/plans/<issue>.md` desde `docs/cell/plans/TEMPLATE.md`, escrito UNA vez (sin sección de estado) y linkeado en el issue.

## Pasos
1. `git fetch origin`; `gh pr list --state open --json number,title,headRefName` (duplicados y migraciones en vuelo).
2. Definition of Ready: contrastar ticket con el código (grep contra `origin/develop`) y, si toca billing, con `docs/billing/decision-registry.md`. Contradicción, caso inalcanzable o pregunta abierta => anotar en "Preguntas al usuario" con opciones y recomendación; no resolver solo.
3. Contratos primero (sección Contratos), antes de cortar: endpoints/OpenAPI (`contracts/openapi.json`), migraciones con número reservado (último `V*.sql` en develop, master y PRs abiertos), eventos entre módulos, puertos `<modulo>.api`, mocks del front.
4. Cortar en subtareas ≤100k tokens, una por puesto, cada una con valor observable ("después, X puede hacer Y") y cómo probarla. Backend y frontend en paralelo con mocks del contrato.
5. Brief de cada subtarea: objetivo, formato de salida esperado, archivos permitidos (un dueño por archivo, sin solapamiento), FUERA DE ALCANCE explícito, criterios de aceptación verificables, comando de verificación (frontend: además navegador/preview), dependencias explícitas (qué va en paralelo y qué espera), D-xxx aplicables, tests esperados, puesto/modelo, tope de tokens, si es plata/contrato/seguridad (=> dos invocaciones independientes del revisor).
6. Orden de merge y paralelismo: máximo 3 workers (2 backends + 1 frontend) + los subagentes revisores necesarios.
7. Frontend: la subtarea queda bloqueada hasta tener mockup aprobado (ux-advisor + Artifact + OK del usuario).
8. Guardar el plan, commitear en rama `docs/plan-<issue>`, linkearlo en el issue y crear las sub-tareas (checklist en el issue o sub-issues); los PRs usarán `Refs #N` y `Closes #N` solo el último. Avisar al usuario con las preguntas agrupadas.

## Reglas
- Plan conciso: listas y tablas, sin prosa. Identificadores en inglés, texto en castellano.
- No copiar specs enteras: referenciar ruta y sección.
- Un desvío se pregunta, no se decide.
