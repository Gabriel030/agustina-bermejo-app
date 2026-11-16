---
name: cell-review
description: Flujo de revisión independiente de un PR de la célula (reviewer-1 o reviewer-2) con veredicto y hallazgos por severidad. Usar cuando te digan "sos reviewer-N", "revisá el PR #N", "cell-review".
---

# cell-review

Prerrequisito: leíste `.claude/agents/reviewer.md`.

## Flujo
1. Confirmar independencia: autor del PR (`gh pr view N --json author,headRefName,labels,comments` y la etiqueta `role:*`) distinto de tu sesión; tu puesto es el de la etiqueta `review:reviewer-1|2` que te asignó el líder. Si no, `BLOQUEADO: autor = revisor`.
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
6. Publicarlo: `gh pr comment N --body-file <archivo>`; ese comentario es el informe oficial (el líder lo lee con `gh pr view N --comments`). En local podés además avisar con `TERMINÉ` (cell-report).

## Reglas
- BLOQUEANTE/MAYOR => NO MERGEABLE. Solo MENOR => MERGEABLE CON MENORES. Sin hallazgos inventados.
- Una sola ronda por PR; tope fijo 100k tokens, sin extensiones (al tope: lo que tenés, "revisión incompleta" y relevo). Al 60 % de contexto emití lo que tenés. El líder mergea con tu MERGEABLE (o CON MENORES corregidos) y CI verde; en plata/contrato/seguridad hace falta el MERGEABLE de ambos revisores. No editás ni pusheás ni mergeás.
