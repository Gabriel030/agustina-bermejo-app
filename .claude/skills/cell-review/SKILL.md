---
name: cell-review
description: Flujo de revisión independiente de un PR de la célula (reviewer-1 o reviewer-2) con veredicto y hallazgos por severidad. Usar cuando te digan "sos reviewer-N", "revisá el PR #N", "cell-review".
---

# cell-review

Prerrequisito: leíste `.claude/agents/reviewer.md`.

## Flujo
1. Confirmar independencia: autor del PR (`gh pr view N --json author,headRefName` y la tabla Estado del plan) distinto de tu sesión. Si no, `BLOQUEADO: autor = revisor`.
2. Tipo de PR: ¿plata, contrato o seguridad? Si sí, es revisión doble (reviewer-1 y reviewer-2, sin leer el informe del otro antes del propio) y leés `docs/billing/decision-registry.md` por las D-xxx del PR.
3. `gh pr diff N` y `gh pr view N --json files,body`. Leé solo lo que el diff obliga. Segunda ronda: solo el diff nuevo.
4. Aplicar el checklist de `reviewer.md`. Verificá con datos (grep, lectura de archivos); lo que no pudiste comprobar va en "No verificado".
5. Emitir el informe con este formato fijo:
```
REVIEW PR #N · <reviewer-N> · MERGEABLE | MERGEABLE CON MENORES | NO MERGEABLE
Resumen: ...
Hallazgos:
[BLOQUEANTE|MAYOR|MENOR] archivo:línea — qué — escenario — arreglo
Verificado: ...
No verificado: ...
```
6. Publicarlo: `gh pr comment N --body-file <archivo>` y avisar al líder con `TERMINÉ` (cell-report).

## Reglas
- BLOQUEANTE/MAYOR => NO MERGEABLE. Solo MENOR => MERGEABLE CON MENORES. Sin hallazgos inventados.
- Una sola ronda por PR; tope 100k tokens. No editás ni pusheás ni mergeás.
