---
name: cell-report
description: Formatos fijos de reporte de progreso, informe final y mensajes entre sesiones de la célula (TERMINÉ, BLOQUEADO, NECESITO DECISIÓN). Usar al reportar avance, cerrar una subtarea, pedir una decisión o escribir un handoff.
---

# cell-report

Reportes: SOLO avance, `TERMINÉ`, `BLOQUEADO` y `NECESITO DECISIÓN`. Los workers de la nube no pueden mandar mensajes a otras sesiones: los publican como COMENTARIOS del PR (`gh pr comment N --body ...`), que es la fuente de verdad; las sesiones locales pueden además mandar mensaje. El líder los lee con `gh pr view N --comments` / `gh pr list --label cell:blocked`. El detalle va en un handoff (`docs/cell/handoffs/`), no en el reporte.

Al arrancar: PR en BORRADOR con un primer commit chico (`gh pr create --draft`), etiquetas `cell:<issue>` y `role:<puesto>`. Al terminar: comentario `TERMINÉ` y `gh pr ready`. `BLOQUEADO` y `NECESITO DECISIÓN`: comentario + etiqueta `cell:blocked` (se saca al resolverse).

## Reporte de avance (una línea)
Primero a los 10 min, luego cada 20 min o por hito (comentario del PR):
`[<ticket>#<n>] <ESTADO> · <%> · hecho: ... · ahora: ... · falta: ... · bloqueos: ... · tokens: <usados>/<tope> · contexto: <%>`
ESTADO: EN CURSO | ESPERANDO | VERDE-LOCAL. Aviso explícito al 50 %, 80 % y 100 % del tope. Al 60 % de contexto: handoff en tu rama (`docs/cell/handoffs/<issue>-<n>.md`) y frená; el líder lanza uno fresco. Sin reporte en 20 min el líder lo pide; en 30 frena la sesión.

## TERMINÉ
```
TERMINÉ [<ticket>#<n>] <rol-N>
PR: <url> · commit: <sha> · rama: <rama>
Tests: <comando> => <resultado real>
Desvíos: ninguno | ...
Plata/contrato/seguridad: sí|no
Tokens: <usados>/<tope> · contexto: <%>
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

## Handoff (`docs/cell/handoffs/<ticket>-<n>.md`)
Secciones fijas: Estado (verde / falta), Archivos tocados, Decisiones tomadas, Cómo seguir (comandos), Tokens gastados. Se commitea en la rama del worker (no en un archivo de estado compartido). Lo lee un agente nuevo sin más contexto.

## Reglas
- Datos reales (comando, exit code, conteo de tests), no "debería andar". Lo no verificado se dice.
- Sin prosa de relleno ni resúmenes que dupliquen la salida.
