---
name: cell-backlog
description: Backlog en Obsidian y su puente con la célula (solo coordinador). Convertir un ítem de Obsidian en GitHub Issue para el líder, procesar los avisos del líder y actualizar el Tablero de control, y armar el reporte de épicas y tareas para Gabriel. Usar en "delegá esto a la célula", "pasale X al líder", "cómo vienen las épicas", "actualizá Obsidian", o al recibir un mensaje del líder.
---

# cell-backlog (solo coordinador)

Vault: `docs/context/specs.md`. Tablero: `Roadmap frontend/Tablero de control.md` (lo sincroniza `python3 ~/.claude/epica18/on-merge.py`; datos en `~/.claude/epica18/tablero.json`).

## 1. Ítem de Obsidian => Issue
Definition of Ready antes de crear el Issue: hay spec o nota con criterios de aceptación, mockup aprobado si toca pantalla (o se pide diseño al líder, tipo B/C), y ninguna pregunta de producto abierta. Si falta algo, preguntá a Gabriel primero.

```
gh issue create --label cell --title "<Épica N> · <qué se puede hacer después, en llano>" --body-file <archivo>
```
Cuerpo:
```
## Qué
<1-3 líneas: valor observable, quién lo usa>
## Fuente
Obsidian: <ruta de la nota/spec, sección exacta>
## Criterios de aceptación
- [ ] ...
## Diseño
Sin pantalla | Mockup aprobado: <link Artifact> | Necesita mockup
## Prioridad
<alta/media/baja> · Épica <N> · bloquea: <...>
## Fuera de alcance
...
```
Después: anotar `Issue #N` en la nota del ítem y en "Próximo paso" de la épica en el Tablero; SendMessage al líder: `Issue #N listo · prioridad <x> · épica <N>`.

## 2. Mensajes del líder => Obsidian
El líder manda una línea por hito (formato en `cell-report`, sección "Al coordinador"):

| Mensaje | Qué actualizás |
|---|---|
| `MERGEADO #PR (Issue #N)` | `on-merge.py`; índice de la épica: paquete hecho, % |
| `CERRADO Issue #N` | nota del ítem: hecho con fecha y PRs; Tablero: próximo paso |
| `BLOQUEADO Issue #N · motivo` | Tablero: columna Bloqueos; si es de Gabriel, a "Necesito de vos" |
| `DECISIÓN Issue #N · pregunta · opciones · recomienda` | AskUserQuestion a Gabriel, respuesta al líder, registrar en Obsidian |
| `RELEASE · N PRs desde el último` | Tablero sección Release; avisar a Gabriel |

Si un mensaje llegó como comentario en un Issue con etiqueta `cell:coordinator` (no había sesión coordinadora), procesarlo igual y sacar la etiqueta: `gh issue edit N --remove-label cell:coordinator`.

## 3. Reporte a Gabriel
Al pedido, corto, con este formato fijo:

**Épicas**

| Épica | % | Avance | Próximo paso | Bloqueo |
|---|:---:|:---:|---|---|

Avance con `▓░` (10 casillas). % = paquetes hechos / totales, solo con PR mergeado o evidencia en `origin/develop`.

**Delegado a la célula**

| Issue | Qué | Estado | PRs | Bloqueo |
|---|---|---|---|---|

Estados: ✅ hecho · 🔄 en curso · 👀 en revisión · ⏳ en cola · 🛑 bloqueado. Fuente: `gh issue list --label cell --state all` y `gh pr list --label cell:<issue>`. Para el detalle por puesto pedile al líder su tabla.

**Necesito de vos**: preguntas agrupadas, con opciones y recomendación (después, AskUserQuestion).

## Reglas
- Solo el coordinador escribe en Obsidian.
- No copiar specs enteras al Issue: ruta y sección.
- No crear Issues de cosas que Gabriel no priorizó.

