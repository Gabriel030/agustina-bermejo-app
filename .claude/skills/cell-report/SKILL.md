---
name: cell-report
description: Formatos fijos de reporte de progreso, informe final y mensajes entre sesiones de la célula (TERMINÉ, BLOQUEADO, NECESITO DECISIÓN). Usar al reportar avance, cerrar una subtarea, pedir una decisión o escribir un handoff.
---

# cell-report

Comentarios del PR (`gh pr comment N --body ...`) SOLO en: arranque (PR borrador), hitos (compila, tests de dominio verdes, ITs verdes, listo), `BLOQUEADO` / `NECESITO DECISIÓN` y `TERMINÉ`. Sin reportes periódicos: el líder mira commits y comentarios en los hitos (`gh pr view N --comments` / `gh pr list --label cell:blocked`). El estado detallado vive en tu rama: `docs/cell/progress/<issue>-<n>.md` (checklist de criterios de aceptación pasa/no pasa, sin editar tests para hacerlos pasar, y notas: hecho / sigue / decisiones / bloqueos), con commit y push en cada hito. El líder te escribe por SendMessage; tus resultados vuelven por el PR. Revisores y mecánico son subagentes del líder: devuelven su resultado al líder, no comentan avance.

Al arrancar: PR en BORRADOR con un primer commit chico (`gh pr create --draft`), etiquetas `cell:<issue>` y `role:<puesto>`. Al terminar: comentario `TERMINÉ` y `gh pr ready`. `BLOQUEADO` y `NECESITO DECISIÓN`: comentario + etiqueta `cell:blocked` (se saca al resolverse).

## Comentario de hito (una línea)
`[<ticket>#<n>] <HITO> · hecho: ... · falta: ... · bloqueos: ... · tokens: <usados>/<tope>`
HITO: ARRANQUE | COMPILA | DOMINIO-VERDE | ITS-VERDES | LISTO. Aviso explícito al 50 %, 80 % y 100 % del tope de tokens. Al tope o al cambiar de tarea: handoff y frená. Tras 2 intentos fallidos de corregir lo mismo: handoff con diagnóstico y frená (no hay tercer intento); el líder relanza con un brief mejor.

## TERMINÉ
```
TERMINÉ [<ticket>#<n>] <rol-N>
PR: <url> · commit: <sha> · rama: <rama>
Tests: <comando> => <resultado real>
Desvíos: ninguno | ...
Plata/contrato/seguridad: sí|no
Tokens: <usados>/<tope>
Siguiente: <qué espera / qué desbloquea>
```
Informe final ≤ 15 líneas. Parcial: `TERMINÉ (parcial)` + ruta del handoff.

## BLOQUEADO
```
BLOQUEADO [<ticket>#<n>] <rol-N>
Motivo: ...
Desde: <hora> · Probé: ...
Necesito: <acción concreta y de quién>
```

## NECESITO DECISIÓN
```
NECESITO DECISIÓN [<ticket>#<n>] <rol-N>
Pregunta: ...
Opciones: A) ... B) ...
Recomiendo: <opción> porque ...
Bloquea: <subtareas>
```
El líder agrupa estas preguntas en un solo mensaje al usuario.

## Al coordinador (solo líder, SendMessage, una línea)
- `MERGEADO #<pr> (Issue #<n>) · <qué quedó andando, en llano>`
- `CERRADO Issue #<n> · PRs: #a, #b`
- `BLOQUEADO Issue #<n> · <motivo> · necesita: <quién/qué>`
- `DECISIÓN Issue #<n> · <pregunta> · A) ... B) ... · recomiendo <x>`
- `RELEASE · <N> PRs en develop desde el último release`
Sin sesión coordinadora: el mismo texto como comentario del Issue + etiqueta `cell:coordinator`.

## Handoff (`docs/cell/handoffs/<ticket>-<n>.md`)
Secciones fijas: Estado (verde / falta), Archivos tocados, Decisiones tomadas, Cómo seguir (comandos), Tokens gastados. Se commitea en la rama del worker (no en un archivo de estado compartido). Lo lee un agente nuevo sin más contexto.

## Reglas
- Datos reales (comando, exit code, conteo de tests), no "debería andar". Lo no verificado se dice.
- Sin prosa de relleno ni resúmenes que dupliquen la salida.

