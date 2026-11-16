---
name: cell-worker
description: Flujo de un worker (backend o frontend) de la célula para tomar una subtarea de un plan, implementarla, testearla, abrir el PR y reportar. Usar cuando te digan "sos backend-N / frontend-1", "tomá la subtarea", "cell-worker".
---

# cell-worker

Prerrequisito: leíste tu ficha (`.claude/agents/backend.md` o `frontend.md`) y tu subtarea `<ticket>#<n>` en `docs/cell/plans/<ticket>.md`.

## Flujo
1. Preparar rama en tu worktree propio (nunca en el de otro):
   `git fetch origin && git switch -c <prefijo>/<slug> origin/develop`
   Verificá `git worktree list`, `git status` y `gh pr list` (nadie hizo lo mismo).
2. Confirmar la subtarea: archivos asignados, contrato, D-xxx, migración reservada. Si algo del plan no coincide con el código real, `NECESITO DECISIÓN` (no improvises). Solo tocás tus archivos; si necesitás otro, pedilo al líder.
3. Estimar tokens; si >100k, proponer el corte antes de escribir.
4. Implementar siguiendo las reglas de tu ficha. Un commit por unidad coherente, mensaje en castellano con el "por qué" y trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
5. Testear solo lo afectado: backend con `backend/dev-test` (una invocación de Maven, `-o`), frontend con `npx vitest run <ruta> --maxWorkers=2` + `npm run lint`. Suite completa en el CI.
6. Checklist pre-PR de tu ficha. `git fetch` y traer `origin/develop`; reverificar si entró algo.
7. Pushear y `gh pr create --base develop` (no borrador), título `tipo(módulo): descripción (<ticket>#<n>)`, cuerpo con: qué hace, "después de este PR X puede hacer Y", tests y sabotajes, D-xxx tocadas, desvíos, migración (número), plata/contrato/seguridad sí/no; terminado en `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
8. Reportar `TERMINÉ` al líder con el formato de `cell-report`. No esperes el CI ni mergees; si el líder devuelve un CI rojo o una revisión, corregí en UN push.

## Cortes
- Tope 150k tokens de plataforma (tu cuenta x2,2); avisos 50/80/100 %. Al 100 %: PR con lo verde o estado sin PR + `docs/cell/handoffs/<ticket>-<n>.md` y `TERMINÉ (parcial)`.
- Bloqueo de más de 10 min (máquina saturada, semáforo, decisión): `BLOQUEADO`, no gastes tokens esperando.
- Commit/push denegado: no lo rodees; avisá.
