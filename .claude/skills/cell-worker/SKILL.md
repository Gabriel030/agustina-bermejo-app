---
name: cell-worker
description: Flujo de un worker (backend o frontend) de la célula para tomar una subtarea de un plan, implementarla, testearla, abrir el PR y reportar. Usar cuando te digan "sos backend-N / frontend-1", "tomá la subtarea", "cell-worker".
---

# cell-worker

Prerrequisito: leíste tu ficha (`.claude/agents/backend.md` o `frontend.md`) y tu subtarea `<issue>#<n>` en `docs/cell/plans/<issue>.md` (el estado no está en archivos: vive en el issue y los PRs de GitHub). Corrés en la nube por defecto.

## Flujo
1. Preparar rama en tu worktree propio (nunca en el de otro):
   `git fetch origin && git switch -c <prefijo>/<slug> origin/develop`
   Verificá `git worktree list`, `git status` y `gh pr list` (nadie hizo lo mismo).
2. Confirmar la subtarea: archivos asignados, contrato, D-xxx, migración reservada. Si algo del plan no coincide con el código real, `NECESITO DECISIÓN` (no improvises). Solo tocás tus archivos; si necesitás otro, pedilo al líder.
3. Estimar tokens; si >100k, proponer el corte antes de escribir. Abrir YA el PR en BORRADOR con un primer commit chico (`gh pr create --draft --base develop`, etiquetas `cell:<issue>` y `role:<tu puesto>`) y crear `docs/cell/progress/<issue>-<n>.md` (checklist de criterios de aceptación pasa/no pasa + notas: hecho / sigue / decisiones / bloqueos). Comentarios del PR solo en arranque, hitos (compila, tests de dominio verdes, ITs verdes, listo), `BLOQUEADO` / `NECESITO DECISIÓN` y `TERMINÉ` (cell-report); commit y push del progress en cada hito.
4. Implementar siguiendo las reglas de tu ficha. Un commit por unidad coherente, mensaje en castellano con el "por qué" y trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
5. Testear solo lo afectado: backend con `backend/dev-test` (una invocación de Maven, `-o`), frontend con `npx vitest run <ruta>` + `npm run lint` y verificación en el navegador (preview) de las pantallas tocadas (en local, además semáforo de Maven máx. 2 y `--maxWorkers=2`; en la nube sin semáforo). Suite completa en el CI.
6. Checklist pre-PR de tu ficha. `git fetch` y traer `origin/develop`; reverificar si entró algo.
7. Pushear; completá el PR (sigue en borrador hasta `gh pr ready`): título `tipo(módulo): descripción (<ticket>#<n>)`, cuerpo con `Refs #<issue>` (`Closes #<issue>` solo el último PR del ticket), qué hace, "después de este PR X puede hacer Y", tests y sabotajes, D-xxx tocadas, desvíos, migración (número), plata/contrato/seguridad sí/no; terminado en `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
8. Comentario `TERMINÉ` en el PR (formato `cell-report`) y `gh pr ready`. No esperes el CI ni mergees; si el líder devuelve un CI rojo o una revisión, corregí en UN push.

## Cortes
- Tope fijo 150k tokens de plataforma (tu cuenta x2,2), SIN extensiones; avisos 50/80/100 %. Sin relevo por % de contexto: al tope o al cambiar de tarea, handoff `docs/cell/handoffs/<issue>-<n>.md` en tu rama, push, comentario `TERMINÉ (parcial)` y frená; el líder lanza uno fresco.
- Criterio de parada: tras 2 intentos fallidos de corregir lo mismo (test que no pasa, CI roja por la misma causa) no hay tercer intento: handoff con diagnóstico y el líder relanza con un brief mejor. Los tests no se editan para hacerlos pasar. Opcional: `/compact <foco>` antes de un hito largo.
- Bloqueo de más de 10 min (máquina saturada, semáforo, decisión): `BLOQUEADO` (comentario del PR + etiqueta `cell:blocked`), no gastes tokens esperando.
- Commit/push denegado: no lo rodees; avisá.

En sesiones cloud ver docs/context/cell.md#gh-en-sesiones-de-la-nube-sin-graphql (equivalentes REST de `gh`).
