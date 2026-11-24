---
name: cell-relevo
description: Relevo de sesiones de la célula (regla 15 de docs/context/cell.md). Cuándo relevar, plantilla del handoff de una página con tabla de sesiones vivas, procedimiento del relevo y prompt de apertura de un líder por área. Usar en "relevo", "handoff", "pasá la posta", "armá el prompt del nuevo líder", o al llegar a ~250k de contexto o 4 horas.
---

# cell-relevo

Reglas de fondo en `docs/context/cell.md`: 8 (relevo de líderes), 12 (estado en GitHub), 15 (sesiones compartidas), 16 (líderes por área) y 19 (revisión y merge por el worker). Esta skill es el procedimiento; no repite las reglas.

## 1. Cuándo relevar
| Sesión | Se releva |
|---|---|
| Líder técnico | A ~250k de contexto o 4 horas (lo que llegue primero) |
| Coordinador | A ~250k de contexto o 4 horas; sesión silenciosa |
| Worker | Al tope de 300k de contexto, o tras 2 intentos fallidos de corregir lo mismo (handoff con diagnóstico) |

Un líder o coordinador que llega al umbral pide el relevo en ese turno, no al siguiente. El worker no pide relevo: escribe `BLOQUEADO` y el líder lanza uno fresco.

## 2. Handoff de UNA página
Sin prosa de relleno. Datos reales: lo no verificado se dice.

```
## HANDOFF <nombre de la sesión> · <fecha y hora Buenos Aires>

### Sesiones vivas
| Título | ID | Rol | Rama o PR | Estado |
|---|---|---|---|---|
| <título que ve Gabriel en claude.ai/code> | session_… | líder / backend-N / frontend-1 / coordinador | <rama> o #<PR> | en vuelo / esperando CI / bloqueada / terminó |

### PRs en vuelo
| PR | Qué | CI sobre el head real | Falta |
|---|---|---|---|

### Pendientes (en orden)
1. <acción concreta> — <Issue/PR>
2. ...

### Decisiones abiertas de Gabriel
- <pregunta> · A) ... B) ... · recomiendo <x> · bloquea: <qué>
(o «ninguna»)

### Migraciones
- Máximo de develop: V<n> (verificado en `origin/develop` el <hora>)
- Cola #1393: <quién mergea ahora / en espera / vacía>
```

Los títulos son los que Gabriel ve en claude.ai/code, no alias (regla 1). Solo IDs `session_…` reales, sin inventar ni completar de memoria.

## 3. Dónde se publica
| Sesión | Destino |
|---|---|
| Líder | Comentario `ESTADO VIVO` del Issue del área (se edita, no se agregan comentarios nuevos; regla 12) |
| Coordinador | `coordinador/` del vault de Obsidian (vía `obsidian-dev`); push del vault solo ante «sí, pusheá el vault» |
| Worker | `docs/cell/handoffs/<issue>-<n>.md` en su rama (ver `cell-report`) |

En la nube, comentarios por REST: `gh api repos/Gabriel030/agustina-bermejo-app/issues/comments/<id> -X PATCH -F body=@handoff.md` (ver docs/context/cell.md#gh-en-sesiones-de-la-nube-sin-graphql).

## 4. Procedimiento del relevo
1. **Entregar workers en vuelo** (regla 8): los workers solo reportan a su sesión padre, así que el relevo debe quedar como destinatario. Esperar que cada worker deje su estado en el PR/Issue.
2. **Escribir y publicar el handoff** (secciones 2 y 3).
3. **Avisar** `WORKERS ENTREGADOS` (líder) en el Issue del área y al coordinador; el coordinador le pega a Gabriel el prompt de apertura (sección 5) en un bloque de código.
4. **El relevo verifica** cada fila de la tabla con `get_session` por ID. No lista historial de sesiones ni lanza sesiones nuevas para reemplazar las vivas.
5. **El relevo confirma** que recibió la tabla (comentario `RELEVO RECIBIDO` en el Issue del área, una línea).
6. **Recién ahí** se archiva la sesión relevada con `archive_session`. Solo esa: los workers en vuelo no se archivan (regla 15).

## 5. Prompt de apertura de un líder por área
Va completo y autocontenido en un bloque de código; primera línea `Nombre de la sesión: <nombre>` (regla 1). Ejemplo real (líder-egresos); adaptar nombre, área, Issues y cola:

```
Nombre de la sesión: líder-egresos (célula, relevo de líder-1b y líder-5)

Sos el líder técnico del área Egresos y académico de agustina-bermejo-app. SOLO Sonnet o Haiku, prohibido Opus.
Cargá .claude/agents/tech-lead.md y la skill cell-plan. Leé docs/context/cell.md completo (reglas 1 a 19, flujo magro, cola de migraciones #1393).

Handoffs: Issue #1083 y Issue #1103 (comentario ESTADO VIVO de cada uno). Leelos antes de actuar. Sesiones vivas: las de la tabla del handoff; consultalas con get_session por ID, sin listar historial ni lanzar sesiones nuevas.

Reglas con Gabriel:
- Castellano, con tablas, horario de Buenos Aires (UTC-3).
- NECESITO DECISIÓN solo al coordinador, con opciones y recomendación.
- Sin acuses (RECIBIDO, LISTO): solo TERMINÉ, BLOQUEADO y NECESITO DECISIÓN.
- El estado vive en GitHub (Issues, PRs, comentario ESTADO VIVO), no en tu memoria.
- Nunca rodees una denegación del clasificador de permisos: avisá y Gabriel la contesta en la sesión.

Ritmo reducido: máximo 4 workers y 2 con Maven a la vez. Pedí relevo al llegar a ~250k de contexto o 4 horas.

Regla 19: los workers revisan su propio diff y mergean su propia rama con la CI verde sobre el head real; vos no comentás APROBADO #N, pero verificás el diff cuando toca plata, contrato o seguridad.

Migraciones: cola #1393. Número = máximo de develop + 1, revalidado justo antes de mergear; anunciá MIGRACIÓN V<n> EN MERGE y MERGEADO V<n> en el Issue de relevo.

Cola de tareas, en este orden:
1. <Issue/PR> — <qué>
2. <Issue/PR> — <qué>
3. ...
```

## Reglas
- Un relevo sin tabla de sesiones vivas no es un relevo.
- No archivar antes de la confirmación del relevo (paso 5).
- Los avisos de proceso van como comentario en el Issue, no por mensaje entre sesiones (regla 18).

Para medir la ventana de trabajo previa al relevo (PRs mergeados por tipo y con migración): `scripts/cell-window-report.sh <desde-ISO-UTC>`.
