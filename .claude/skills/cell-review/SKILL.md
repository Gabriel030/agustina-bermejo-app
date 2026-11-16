---
name: cell-review
description: Flujo de revisión independiente de un PR de la célula (subagente reviewer-1 o reviewer-2, lanzado por el líder) con veredicto y hallazgos por severidad. Usar cuando te digan tarea del líder, "revisá el PR #N", "cell-review".
---

# cell-review

Prerrequisito: leíste `.claude/agents/reviewer.md`.

## Flujo
0. Primer paso (en la Mac): correr `scripts/update-code-index.sh` y usar el índice de código (skill `code-index`) para medir el impacto del diff; en la nube, grep.
1. Independencia: sos un subagente con contexto limpio que el líder lanza por PR; recibís solo diff + checklist + D-xxx. Si el autor del PR es tu misma sesión, `BLOQUEADO: autor = revisor`.
2. Tipo de PR: ¿plata, contrato o seguridad? Si sí, el líder hace dos invocaciones independientes (reviewer-1 y reviewer-2, sin ver el informe del otro) y leés `docs/billing/decision-registry.md` por las D-xxx del PR.
3. `gh pr diff N` y `gh pr view N --json files,body`. Leé solo lo que el diff obliga. Segunda ronda: solo el diff nuevo.
4. Aplicar el checklist de `reviewer.md`. Verificá con datos (grep, lectura de archivos); lo que no pudiste comprobar va en "No verificado".
5. Emitir el informe con este formato fijo:
```
REVIEW PR #N · <reviewer-1|reviewer-2> · MERGEABLE | MERGEABLE CON MENORES | NO MERGEABLE
Resumen: ...
Hallazgos:
[BLOQUEANTE|MAYOR|MENOR] archivo:línea — qué — escenario — arreglo
Verificado: ...
No verificado: ...
```
6. Publicarlo: `gh pr comment N --body-file <archivo>`; ese comentario es el informe oficial; devolvelo además al líder como resultado del subagente.

## Reglas
- BLOQUEANTE/MAYOR => NO MERGEABLE. Solo MENOR => MERGEABLE CON MENORES. Sin hallazgos inventados.
- Una sola ronda por PR; tope fijo 100k tokens, sin extensiones (al tope: lo que tenés, "revisión incompleta" y relevo). `maxTurns: 30`. El líder mergea con tu MERGEABLE (o CON MENORES corregidos) y CI verde; en plata/contrato/seguridad hace falta el MERGEABLE de ambas invocaciones. No editás ni pusheás ni mergeás.

En sesiones cloud ver docs/context/cell.md#gh-en-sesiones-de-la-nube-sin-graphql (equivalentes REST de `gh`).
