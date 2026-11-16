---
name: cell-report
description: Formatos fijos de reporte de progreso, informe final y mensajes entre sesiones de la célula (TERMINÉ, BLOQUEADO, NECESITO DECISIÓN). Usar al reportar avance, cerrar una subtarea, pedir una decisión o escribir un handoff.
---

# cell-report

Mensajes entre sesiones: SOLO tres tipos. Todo lo demás vive en archivos (plan, contrato, tablero, handoffs). Una sesión no manda detalle por mensaje: lo escribe en el plan o en un handoff y manda la ruta.

## Reporte de avance (una línea)
Primero a los 10 min, luego cada 20 min o por hito:
`[<ticket>#<n>] <ESTADO> · <%> · hecho: ... · ahora: ... · falta: ... · bloqueos: ... · tokens: <usados>/<tope>`
ESTADO: EN CURSO | ESPERANDO | VERDE-LOCAL. Aviso explícito al 50 %, 80 % y 100 % del tope. Sin reporte en 20 min el líder lo pide; en 30 frena la sesión.

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

## Handoff (`docs/cell/handoffs/<ticket>-<n>.md`)
Secciones fijas: Estado (verde / falta), Archivos tocados, Decisiones tomadas, Cómo seguir (comandos), Tokens gastados. Lo lee un agente nuevo sin más contexto.

## Reglas
- Datos reales (comando, exit code, conteo de tests), no "debería andar". Lo no verificado se dice.
- Sin prosa de relleno ni resúmenes que dupliquen la salida.
