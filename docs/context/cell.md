# Célula de agentes

Equipo fijo de agentes que ejecuta tickets de punta a punta. El rol vive en archivos del repo (`.claude/agents/`, `.claude/skills/`); la sesión que lo toma es descartable.

## Roles

| Rol | Ficha | Modelo | Qué hace |
|---|---|---|---|
| Coordinador | `.claude/agents/coordinator.md` | Sonnet, local | Backlog en Obsidian, prioridades, Issues `cell` para el líder, reporte de épicas a Gabriel; único que escribe en Obsidian |
| Líder técnico | `.claude/agents/tech-lead.md` | Sonnet | Issue => plan, contratos, asignación, orden de merge, preguntas al usuario |
| Backend (backend-1 a backend-5) | `.claude/agents/backend.md` | Haiku (1 a 3), Sonnet (4 y 5) | Una subtarea de backend en el módulo que indique el ticket |
| Frontend (frontend-1) | `.claude/agents/frontend.md` | Haiku | Una subtarea de front, con diseño aprobado |
| Revisor (subagente) | `.claude/agents/reviewer.md` | Sonnet, `maxTurns` 30 | Revisión independiente del diff; lo lanza el líder por PR con contexto limpio |
| Mecánico (subagente) | `.claude/agents/mechanic.md` | Haiku, `maxTurns` 25 | Traer develop, conflictos de docs/logs; lo lanza el líder por tarea |

Skills: `cell-backlog` (backlog en Obsidian ↔ Issues, solo coordinador), `cell-plan` (ticket => plan), `cell-design` (ticket => mockup aprobado), `cell-contract` (mockup => PR de contrato), `cell-worker` (tomar subtarea), `cell-review` (revisar PR), `cell-report` (formatos de reporte y mensajes), `code-index` (índice de código del MCP `codebase-memory`, proyecto `agustina-develop`, solo en la Mac; `scripts/update-code-index.sh` lo deja en `origin/develop` al abrir sesión y tras cada merge del líder).

## Cómo arranca una sesión

Una línea por sesión (workers y líder). Revisores y mecánico no son sesiones: el líder los lanza como subagentes con la herramienta Agent (`reviewer` / `mechanic`), con contexto limpio y solo lo necesario (revisor: diff + checklist + D-xxx).
- "Sos backend-1: cargá `.claude/agents/backend.md` y la skill `cell-worker`; tu subtarea es `<issue>#<n>`."
- "Sos frontend-1: cargá `.claude/agents/frontend.md` y la skill `cell-worker`; tu subtarea es `<issue>#<n>`."
- "Sos el líder: cargá `.claude/agents/tech-lead.md` y la skill `cell-plan`; ticket: ..."
- "Sos el coordinador: cargá `.claude/agents/coordinator.md` y la skill `cell-backlog`." (solo en la Mac)

Cada sesión trabaja en su propio worktree/checkout, con rama propia desde `origin/develop`.

## Flujo de un ticket

Tipo de ticket (lo fija el líder en el plan): A solo backend (contrato => backend); B cambia una pantalla existente (mockup => OK de Gabriel => contrato => backend y frontend en paralelo); C pantalla nueva (mockup completo => `ux-advisor` => OK de Gabriel => contrato => backend y frontend en paralelo). Gate: con pantalla, sin OK de Gabriel no arrancan contrato ni frontend; el backend que no depende de la pantalla (dominio, migraciones, reglas, tests) puede arrancar antes. El contrato es del líder (`cell-contract`) y se mergea primero; los cambios a mitad de camino solo entran vía él.

1. Los tickets son GitHub Issues. Líder: lee el issue, `cell-plan` => `docs/cell/plans/<issue>.md` (plantilla `docs/cell/plans/TEMPLATE.md`, escrito UNA vez), lo linkea en el issue y crea las sub-tareas (checklist o sub-issues). Contratos primero, subtareas ≤100k con valor observable, un dueño por archivo, dependencias explícitas, backend y frontend en paralelo con mocks del contrato, una subtarea por puesto.
2. Líder pregunta al usuario, en un solo mensaje, lo que bloquea (negocio, diseño). Front: mockup aprobado antes de codificar.
3. Líder lanza workers con una línea de brief; abren PR en BORRADOR con un primer commit chico y trabajan con etiquetas `cell:<issue>` y `role:<puesto>`. Los PRs referencian el issue (`Refs #N`; `Closes #N` solo el último).
4. Cada PR pasa por el subagente revisor (`cell-review`), una invocación; el informe es un comentario del PR. Plata/contrato/seguridad: dos invocaciones independientes (reviewer-1 y reviewer-2), MERGEABLE de ambas. Una ronda de revisión y una de correcciones.
5. El PR lo mergea el worker dueño, una vez que el líder comenta `APROBADO #N` en el PR (el revisor asignado dio MERGEABLE, o MERGEABLE CON MENORES ya corregidos; plata/contrato/seguridad requieren MERGEABLE de las dos invocaciones) y la CI está verde sobre el head real. Los `release/*` y hotfix a `master` los mergea el líder. PRs apilados en orden estricto; el mecánico trae develop. Los PRs sin migración se mergean con auto-merge (regla 2 de "Reglas de comportamiento"). Releases a `master`: ver "Protocolo de respuesta y flujo continuo".

## Coordinador y líder

| | Coordinador | Líder técnico |
|---|---|---|
| Dueño de | Backlog en Obsidian, épicas, prioridades | Plan, contratos, workers, revisión y merge |
| Entrada | Gabriel | Issues `cell` del coordinador |
| Salida | Issues `cell`, Obsidian actualizado | PRs mergeados, avisos al coordinador |
| Reporta a Gabriel | Avance de épicas y tareas | Estado de la célula (puestos, PRs, tokens) |
| Escribe en Obsidian | Sí, el único | Nunca |

Circuito: Gabriel prioriza => el coordinador convierte el ítem de Obsidian en un Issue `cell` y avisa al líder => la célula ejecuta => el líder avisa al coordinador por SendMessage en cada hito (formatos en `cell-report`) => el coordinador actualiza Obsidian (`~/.claude/epica18/on-merge.py` + índice de la épica). Sin sesión coordinadora abierta, los avisos quedan como comentario en el Issue con etiqueta `cell:coordinator`.

## Protocolo de respuesta y flujo continuo

- Todo pedido entre coordinador, líder y workers se cierra con una respuesta explícita con la señal acordada (`PLANIFICADO #N`, `LISTO … PARA NUBE`, `TERMINÉ`, `BLOQUEADO`). El silencio no es respuesta: quien pidió reclama.
- Un plan o refinamiento no se ejecuta solo: el agente avisa al terminar y espera el OK de quien lo pidió. Producto, plata y prioridades entre épicas las decide Gabriel (AskUserQuestion con recomendación); lo técnico chico lo decide el coordinador y lo informa.
- El coordinador es el pivot: mantiene carga paralela alta según la capacidad de la máquina, asigna cada ítem al puesto que corresponde, pide refinamiento y specs con anticipación y divide en rebanadas lanzables para no depender de Gabriel.
- El líder tiene el dominio de los merges (a `develop` y `release/*` a `master`) desde el 17-nov, con los controles de siempre: CI verde, revisión de plata/contrato/seguridad, smoke de migraciones y etiqueta `contract-break-approved` solo con diff seguro.

## Reglas de comportamiento (valen para todas las sesiones)

Viven acá y en las fichas de `.claude/agents/`, no en la memoria local de ninguna sesión: lo que deba valer para otras sesiones se escribe en estos archivos.

1. **Nombres.** Al hablar con Gabriel se nombra a las sesiones por el título que él ve en claude.ai/code, no por alias (Líder-N, worker-N). Los prompts de apertura de sesión empiezan con la línea `Nombre de la sesión: <nombre>`. Todo prompt que Gabriel deba pegar va completo y autocontenido en un bloque de código en el chat.
2. **Líderes.** (El `APROBADO #N` del líder fue reemplazado por la regla 19.) Un líder nunca deja un PR listo sin acción. Cuando los revisores dan conforme, comenta `APROBADO #N` en el PR y avisa al worker dueño, que mergea. Si el worker no mergeó a los 6 minutos del `APROBADO` (o ya no existe), mergea el líder: sin migración activa el auto-merge (`develop` exige los checks `backend` y `frontend`) y anuncia `AUTO-MERGE ACTIVADO #N`. Si empujan un commit después de su aprobación, re-revisa el delta. Los PRs con migración se mergean a mano, anunciando `MIGRACIÓN V<n> EN MERGE` y `MERGEADO V<n>` en el Issue de relevo; el número se renumera al siguiente libre sobre el máximo de `develop` al mergear (Flyway sin `outOfOrder`).
3. **Comunicación.** Los mensajes entre sesiones en la nube son de una sola vía y los workers y líderes no vigilan el CI por su cuenta. Toda corrección se cierra con un comentario explícito en el Issue: `ARRANQUE`, `TERMINÉ`, `CORREGIDO #N`, `BLOQUEADO` + motivo, `NECESITO WORKER #N`. El coordinador avisa al líder en el momento en que un PR queda `clean`. Un push que no dispara CI se corrige con un push normal con cambio real, nunca con commit vacío. Un rerun no re-evalúa la etiqueta `contract-break-approved`: el workflow solo la lee en `opened`, `synchronize`, `reopened` y `ready_for_review`; para re-evaluarla se cierra y reabre el PR (único caso en que se hace).
4. **Workers.** Tope de 300k de contexto por worker (regla 7; antes 90k tokens y 30 minutos). Un worker que agotó el presupuesto avisa `BLOQUEADO` y pide uno fresco. El worker mergea solo su propio PR y solo después del `APROBADO #N` del líder en el PR y con CI verde sobre el head real: sin migración, `gh pr merge N --squash --auto`; con migración, a mano anunciando `MIGRACIÓN V<n> EN MERGE` y `MERGEADO V<n>` en el Issue de relevo, tras traer `origin/develop` y renumerar al siguiente libre. Nunca mergea PRs ajenos, ni `release/*`, ni con `--admin`, y si empuja un commit después del `APROBADO`, espera uno nuevo.
5. **Contrato.** `contract-break-approved` solo con diff aditivo listado en el cuerpo del PR; un filtro que pasa de valor único a lista se considera aditivo.
6. **Commit, push y ponerse al día con develop.** Autorizado por Gabriel el 23-nov para toda la célula: cada worker puede commitear y pushear su propia rama `feature/*`, `fix/*` o `refactor/*` y abrir su PR en borrador contra `develop` sin pedir un OK por cada commit. Nunca a `develop` ni `master`, y nunca `--force` ni `--force-with-lease`: para ponerse al día con develop se usa `git merge origin/develop` y push normal (nada de rebase sobre ramas ya pusheadas). Si un commit o push es denegado, no se rodea: se avisa. Las migraciones se renumeran a máximo de develop + 1 justo antes de mergear.
7. **Tope de workers y Maven (23-nov).** Hasta 4 workers por líder y como máximo 2 con Maven a la vez (ritmo reducido por cuota, 23-nov): las sesiones comparten 16 GB y con más hubo OOM. Un líder sin texto nuevo en 30 minutos corta sus agentes en vuelo y deja solo los que cierran PRs abiertos. Cada worker trabaja en su propio worktree. Todo worker se relanza nuevo por tarea antes de pasar los 300k de contexto (reemplaza al tope de 90k).
8. **Relevo de líderes (23-nov).** El líder pide relevo al coordinador a los ~250k de contexto o a las 4 horas (decisión de Gabriel, 23-nov: cada turno relee todo el contexto y a 750k era el mayor gasto de cuota). El líder viejo entrega primero sus workers en vuelo (los workers solo reportan a su sesión padre), deja el handoff y avisa «WORKERS ENTREGADOS»; recién ahí se archiva su sesión y arranca el relevo con la cola completa.
9. **Cola acotada (23-nov).** Cada líder mantiene en vuelo solo lo que cabe en los topes de la regla 7, con spec o contrato listo y sin abrir tareas nuevas del fondo de cola con la cuota por encima del 70%. Si se queda sin trabajo con spec lista, avisa al coordinador «SIN COLA: <qué falta>» y el coordinador planifica o reasigna.
10. **Migraciones.** El número de cada migración Flyway es el máximo de develop + 1, revalidado justo antes de mergear; sin `outOfOrder` y sin rangos fijos por líder. Si choca, renumera el que mergea después. Los enums de auditoría y de notificación llevan su CHECK en la misma migración.
11. **Permisos del modelo #1108.** Los 11 permisos solo-Director son `fixed_to_director` y devuelven 422 `FIXED_TO_DIRECTOR_PERMISSION_NOT_EDITABLE` si se intenta editarlos. Los permisos de dinero que ya se aplicaban directo para ADMINISTRATIVO siguen delegables, sin aprobación del Director.
12. **Estado en GitHub.** El estado de cada épica vive en los Issues, los PRs y el comentario ESTADO VIVO del Issue, no en la memoria de ninguna sesión. Al relevar, el handoff apunta a ese comentario.
13. **Coordinador con Gabriel (23-nov).** Habla en castellano, con tablas y en horario de Buenos Aires (UTC-3). Solo le lleva lo que únicamente él decide, con `AskUserQuestion` (chips con opciones y recomendación), agrupado. Sesión silenciosa: sin acuses ni reportes que no cambian nada. Nunca rodea una denegación del clasificador de permisos: Gabriel la contesta en la sesión correspondiente. Commit y push propio solo con su aprobación; el vault solo con «sí, pusheá el vault».
14. **Rol del coordinador.** Es el único que escribe en el vault de Obsidian privado (repo `Gabriel030/agustina-bermejo-vault`, vía el agente `obsidian-dev`). No guarda en memoria local reglas que deban valer para otras sesiones: van a estos archivos.
15. **Relevo con sesiones compartidas (23-nov).** Todo relevo (coordinador, líder, worker) comparte las sesiones vivas con quien lo recibe: el handoff lleva la tabla de sesiones (título, ID `session_…`, rol, rama o PR y estado) y el relevo sigue con esas mismas sesiones, consultándolas con `get_session` por ID, en lugar de listar todo el historial o lanzar sesiones nuevas. Los workers en vuelo se entregan al relevo y no se archivan; solo se archiva la sesión que se releva, y solo cuando el relevo confirma que recibió la tabla.
16. **Líderes por área (23-nov).** Un líder activo por área, sin compartir módulo ni migraciones entre áreas: Egresos y académico (certificados, constancias, importados, equivalencias, 22.10), Seguridad y permisos (permisos, E18, E25, CI, G7), Invitaciones y monitoreo (invitaciones por oleadas, notificaciones, Sentry) y Frontend y diseño. Un líder fuera de su área pide el trabajo al líder dueño.
17. **Puerta de despliegue (23-nov).** No se abre un `release/*` nuevo mientras el anterior no esté desplegado o Gabriel no haya decidido expresamente seguir. Un feature de backend sin pantalla ni uso real no cuenta como entregado.
18. **Mensajería y proceso (23-nov).** Los avisos de proceso (numeración de migraciones, etiquetas, ramas, permisos) van como comentario en el Issue de relevo o del ticket, no por mensaje entre sesiones. El `REPORTE` es un comentario por hito, no por turno.
19. **Revisión y merge por el mismo agente (decisión de Gabriel, 23-nov).** Los líderes ya no aprueban PRs ni comentan `APROBADO #N`: el worker dueño revisa su propio diff contra el checklist y el registro de decisiones, deja el resultado en un comentario del PR y mergea su propia rama cuando la CI está verde sobre el head real (auto-merge sin migración; con migración, por la cola #1393). Reemplaza al `APROBADO #N` del líder y a la revisión independiente de las reglas 2 y 4, del flujo del ticket (pasos 4 y 5) y de la regla 6 del flujo magro. No cambia: `contract-break-approved` solo con diff aditivo y por el coordinador, nada de `--admin` ni merges a `master` o `release/*` por workers, y el líder sigue verificando el diff cuando el PR toca plata, contrato o seguridad.

## Matriz de avisos y pedido de worker

Canal único y confiable: comentario en el Issue de la tarea. El mensaje directo entre sesiones es de una vía y puede no leerse: acelera, nunca reemplaza al comentario.

**Cómo pedir un worker** (lo pide el líder; solo Gabriel puede abrir sesiones nuevas en la nube):
1. Comentar en el Issue `NECESITO WORKER #<PR o Issue>` con cinco datos: rama, qué hacer (una frase), áreas o archivos, criterio de listo (checks que deben quedar verdes) y tope (300k de contexto).
2. Avisar al coordinador por mensaje. El coordinador responde en el mismo turno pegándole a Gabriel el prompt completo, con `Nombre de la sesión: <nombre>` en la primera línea, en un bloque de código.
3. Si el worker existente tiene menos del 50 % de contexto y presupuesto, se lo reutiliza con un mensaje y no se abre otro.
4. Si pasan 6 minutos sin `ARRANQUE` del worker, el líder escribe `PEDIDO DE REPORTE`.

**Quién avisa a quién y cuándo:**

| Quién | A quién | Cuándo | Cómo |
|---|---|---|---|
| Worker | Líder | Al empezar (10 min), en cada hito, al terminar, al trabarse | Issue: `ARRANQUE`, `TERMINÉ`, `BLOQUEADO` + motivo |
| Worker | Líder | Antes de cada push | Local: chequeo de contrato, tests afectados y `ModularityTest`; no se pushea con algo conocido en rojo |
| Líder | Worker | PR rojo o `DIRTY` | Mensaje con la lista completa de lo que falla (no de a uno) y comentario en el Issue |
| Líder | Coordinador | Necesita worker o decisión de Gabriel | `NECESITO WORKER #N` / `NECESITO DECISIÓN` |
| Líder | Todos | Merge con migración | `MIGRACIÓN V<n> EN MERGE`, después `MERGEADO V<n>` en el Issue de relevo |
| Líder | Worker | Revisores conformes | Comentario `APROBADO #N` en el PR; el worker mergea |
| Líder | Todos | PR `clean` con `APROBADO` y sin merge del worker en 6 minutos | Mergea o activa auto-merge él mismo |
| Coordinador | Líder | PR `clean` o `CORREGIDO` | Mensaje y comentario en el mismo turno |
| Coordinador | Gabriel | Algo que solo él puede hacer | Prompt completo en bloque de código |

**Sin avance, escalar (regla de Gabriel, 18-nov):** ninguna tarea abierta puede pasar 30 minutos sin un avance observable (push, merge o comentario con novedad). A los 6 minutos sin respuesta el dueño reclama con `PEDIDO DE REPORTE`; a los 15 el coordinador le pega a Gabriel el prompt para despertar a la sesión; a los 30 se reasigna a un worker fresco (`NECESITO WORKER #N`). Cada escalón queda comentado en el Issue.

## Protocolo de reporte

1. **Reportar.** Toda sesión (líder o worker) cierra cada turno con un comentario `REPORTE` en el Issue de su tarea, formato fijo de 4 líneas: `HECHO` (con PR/commit/hora UTC), `ESPERA` (qué y de quién: CI, worker, decisión, merge), `SIGUIENTE` (acción concreta) y `BLOQUEOS`. Un líder no cierra turno si queda un PR `clean` sin acción (mergea, activa auto-merge o pide worker); un PR `clean` sin acción no puede quedar más de 6 minutos.
2. **Pedir reporte.** Si una sesión lleva más de 6 minutos sin reporte teniendo algo pendiente, el coordinador le escribe `PEDIDO DE REPORTE` y la sesión responde en el mismo turno con el formato anterior. El coordinador avisa al líder dueño en el mismo turno en que un PR queda `clean` o llega un `CORREGIDO`, sin depender de que Gabriel intermedie.

## Lecciones del 18-nov (error → regla)

- Se dijo "CI verde" con el verde del head viejo (#999) → decirlo solo sobre el head real y con la base actual.
- Se dieron cifras sin medir (CI estimado en 25 min, tarda ~6-10) → no dar cifras sin medirlas.
- Sesiones nuevas de más → abrir una solo si el worker agotó presupuesto o llegó al 50 % de contexto; si no, reanudar la existente.
- Rebases repetidos de PRs apilados → un PR apilado queda `dirty` cuando entra su base: rebasear recién cuando le toca el merge.
- Choque de números de migración al rebasear → usar el siguiente libre sobre `develop` al empujar; el líder renumera al mergear.
- `contract-break-approved` no se re-evaluó con un rerun → se evalúa solo en `opened`/`synchronize`/`reopened`/`ready_for_review`: un push real, no rerun ni commit vacío.
- Un PR borró un componente compartido (p. ej. `MeshBackground`) y rompió a otros → los PRs abiertos que lo importan quitan el import antes de mergear.
- Rebase + renumeración + mayores abiertos no entra en el tope normal → esa tarea justifica 120k tokens y 40 min; el resto 90k y 30.

## Coordinación

- Hub-and-spoke: todo pasa por el líder.
- Fuente de verdad = GitHub, no archivos del repo (evita conflictos): issue del ticket, PRs con etiquetas `cell:<issue>`, `role:<puesto>` y `cell:blocked`, y el Project "Célula" (kanban por estado) armado con esos issues/PRs. En el repo solo quedan el plan (una vez), el contrato (`contracts/openapi.json` + sección Contratos) y `docs/cell/handoffs/` (en la rama del worker).
- Estado en la rama del worker: `docs/cell/progress/<issue>-<n>.md` con (a) checklist de criterios de aceptación con estado pasa/no pasa (los tests no se editan para hacerlos pasar) y (b) notas de progreso (hecho / sigue / decisiones / bloqueos); commit y push en cada hito.
- Comentarios del PR SOLO en: arranque (PR borrador), hitos (compila, tests de dominio verdes, ITs verdes, listo), `BLOQUEADO` / `NECESITO DECISIÓN` (más etiqueta `cell:blocked`) y `TERMINÉ` (más `gh pr ready`). No hay reportes periódicos: el líder mira commits y comentarios en los hitos (`gh pr view N --comments`, `gh pr list --label cell:blocked`).
- El líder envía instrucciones a una sesión de la nube con `claude -p "<mensaje>" --cloud <session-id>`; los resultados vuelven por el PR.
- Paralelismo: máximo 3 workers (2 backends + 1 frontend) + los subagentes revisores necesarios.
- Reporte del líder al usuario: al pedido y en cada hito (merge, bloqueo, decisión), con la tabla fija y debajo "Necesito de vos" (preguntas agrupadas, opciones y recomendación):

`| Puesto | Subtarea | PR | Estado | Avance | Tokens | Bloqueo |`

## Presupuesto de cuota y modelos (decisión de Gabriel, 20-nov)

- Prohibido Opus o superior en cualquier sesión o subagente de la célula. Modelos: Coordinador y líderes en Sonnet; backend-1 a backend-3 y frontend-1 en Haiku (tareas mecánicas: tests, docs, fixtures, ajustes de pantalla); backend-4 y backend-5 en Sonnet (migraciones, plata, seguridad y contrato); revisor en Sonnet; mecánico en Haiku. Esfuerzo medio en todos.
- Tareas chicas: cada subtarea para un worker en Haiku es de un solo objetivo, pocos archivos y ≤60k tokens, con brief autocontenido (rama, archivos permitidos, fuera de alcance, comando de verificación). Lo que exige criterio (migraciones, dinero, seguridad, contratos) va a Sonnet.
- Ahorro de relecturas: cada mensaje entre sesiones despierta a la receptora, que relee todo su contexto. Sin mensajes de confirmación (solo `TERMINÉ`, `BLOQUEADO`, `NECESITO DECISIÓN`); un mensaje agrupado por hito; sin sondeos ni turnos si no hay nada accionable; auto-merge en lugar de esperar la CI con turnos; una invocación de revisor por PR (dos en plata, contrato y seguridad); sin re-revisión de deltas triviales; sin mockups ni capturas repetidos.
- Compactación: se pide al 20% con la variable de entorno `CLAUDE_AUTOCOMPACT_PCT_OVERRIDE`, que solo se cambia desde los ajustes del entorno. `/compact` solo lo escribe un usuario dentro de la sesión.

## Flujo magro y consumo de cuota (decisión de Gabriel, 20-nov)

Motivo: cada mensaje entre sesiones despierta a la receptora y esta relee todo su contexto; las sesiones largas y la mensajería redundante agotaban la cuota de 5 horas. Reglas obligatorias para todos los roles:

1. **Un líder activo por área** (regla 16 de «Reglas de comportamiento»). Otro líder en la misma área solo se enciende si Gabriel lo pide; mientras tanto queda archivado.
2. **Workers descartables.** Una sesión por subtarea: al `TERMINÉ` se archiva y la siguiente tarea va a una sesión nueva con brief autocontenido (rama, archivos permitidos, fuera de alcance, comando de verificación). El estado vive en GitHub (Issues, PRs, comentarios) y en las ramas, no en la memoria de la sesión.
3. **Coordinador relevable.** Sesión silenciosa; al pasar de ~250k tokens de contexto o de ~4 horas escribe un handoff en `coordinador/` del vault y se reemplaza por una sesión nueva (la que usa Gabriel).
4. **Mensajes mínimos.** Solo `TERMINÉ`, `BLOQUEADO` y `NECESITO DECISIÓN` (este último solo al Coordinador, con opciones y recomendación). Sin acuses (`RECIBIDO`, `LISTO`), un mensaje agrupado por hito, nada de reportes por cada PR; el líder reporta al cerrar la tanda o ante una decisión o bloqueo.
5. **Sin sondeos ni esperas activas.** Auto-merge en lugar de turnos esperando la CI; si no hay nada accionable no se ejecutan turnos; no se usa Monitor.
6. **Revisión barata.** (Reemplazada por la regla 19 de «Reglas de comportamiento»: el worker revisa y mergea su propia rama.) Una invocación del revisor por PR; dos solo en plata, contrato y seguridad; sin re-revisión de deltas triviales (el líder verifica el diff).
7. **Menos PRs, un poco más grandes** (rebanadas de hasta 100k tokens con valor observable), un solo push por corrección. No se abren PRs solo de tests, CI o refactor salvo que sean la rebanada de la tarea.
8. **Tandas.** El trabajo se organiza en tandas de una ventana de 5 horas con lista cerrada acordada con Gabriel; al avisar Gabriel que la cuota llega al 70% se cierra la tanda.
9. **Contexto.** Compactación automática al 20% (variable `CLAUDE_AUTOCOMPACT_PCT_OVERRIDE`, solo se cambia en los ajustes del entorno); `/compact` solo lo escribe un usuario.
10. **Mockups y capturas.** Se generan una vez; no se regeneran sin pedido.

## Presupuesto y contexto

- Planificación del líder ~120k, corte 150k con handoff. Topes fijos SIN extensiones: worker 300k de contexto (regla 7), revisor 100k, mecánico 80k (tokens de plataforma; la propia cuenta subestima ~2,2x).
- Con ventanas de contexto de 1M el límite real es el tope de tokens, no un % de contexto: el relevo es al tope (worker 300k, sin extensiones; líder ~250k, regla 8) o al cambiar de tarea. El worker escribe el handoff (`docs/cell/handoffs/<issue>-<n>.md`, en su rama) y el líder lanza uno fresco que lo lee. Opcional: `/compact <foco>` antes de un hito largo.
- Criterio de parada: tras 2 intentos fallidos de corregir lo mismo (test que no pasa, CI roja por la misma causa) no hay tercer intento: handoff con diagnóstico y el líder relanza con un brief mejor.
- Brief de cada subtarea (lo escribe el líder en el plan): objetivo, formato de salida esperado, archivos permitidos, FUERA DE ALCANCE explícito, criterios de aceptación verificables y comando de verificación (frontend: además navegador/preview).

## Dónde corren

| | Nube (claude.ai/code), por defecto | Local (Mac de Gabriel) |
|---|---|---|
| Quién | Workers (backends y frontend) | Líder y lo que necesite algo local: base demo 5433, preview en navegador, mockups/Obsidian |
| Entorno | Cada sesión con su propia VM: 4 vCPU, 16 GB, Docker/Postgres y Vitest propios | Cargada: mirar `uptime` |
| Concurrencia | Máx. 2 Maven simultáneos por líder (16 GB compartidos, regla 7) | Semáforo de Maven máx. 2 (`~/.claude/epica18/ci-slot.sh`, `backend/dev-test` lo toma solo); Vitest `--maxWorkers=2` |
| Arranque | Hook `SessionStart` ejecuta `scripts/cloud-session-bootstrap.sh`: toolchains JDK 21 y `dockerd` | Normal |
| Archivos de usuario | `~/.claude` no visible: todo está en el repo | `~/.claude` disponible |

En ambos casos: tests focalizados con `backend/dev-test`; suite completa y PIT en GitHub Actions.

## Setup inicial de GitHub (lo corre el líder la primera vez)

```
gh label create "cell" --color 0E8A16 --description "Ticket delegado a la célula por el coordinador"
gh label create "cell:coordinator" --color FBCA04 --description "Aviso del líder pendiente para el coordinador"
gh label create "cell:blocked" --color D93F0B --description "Bloqueado o con decisión pendiente"
gh label create "role:backend-1" --color 1D76DB
gh label create "role:backend-2" --color 1D76DB
gh label create "role:frontend-1" --color 5319E7
gh auth refresh -s project
gh project create --owner Gabriel030 --title "Célula"
```

- Por ticket: `gh label create "cell:<issue>" --color BFD4F2` antes de lanzar workers.
- En el Project "Célula" crear el campo Status (Por hacer, En curso, En revisión, Bloqueado, Hecho) y agregar issues y PRs (`gh project item-add <n> --owner Gabriel030 --url <url>`); el líder los mueve por estado al leer los comentarios.

## `gh` en sesiones de la nube (sin GraphQL)

Las sesiones cloud de Claude Code bloquean GraphQL (HTTP 403 "GitHub GraphQL is not available from Claude Code sessions"). Ahí no andan `gh issue view|list|comment|create`, `gh pr view|list|checks|create|comment|edit|merge` ni `gh label list`; sí andan `gh pr diff` y `gh api` (REST). En la Mac los comandos normales siguen valiendo. En la tabla, `R=repos/Gabriel030/agustina-bermejo-app`.

| Para qué | Equivalente REST | Estado |
|---|---|---|
| Ver issue | `gh api $R/issues/N` | validado en la nube |
| Listar issues por etiqueta | `gh api "$R/issues?labels=cell&state=all&per_page=50"` (filtrar `.pull_request == null`) | validado en la nube |
| Listar PRs | `gh api "$R/pulls?state=open&per_page=50"` | validado en la nube |
| Ver PR y sus archivos | `gh api $R/pulls/N`, `gh api $R/pulls/N/files`, `gh pr diff N` | validado en la nube |
| Comentar PR o issue | `gh api $R/issues/N/comments -F body=@archivo.md` | validado en la nube |
| Checks de un commit | `gh api $R/commits/<sha>/check-runs` | validado en la nube |
| Crear PR | `gh api $R/pulls -f title=... -f head=<rama> -f base=develop -F draft=true -F body=@cuerpo.md` (o `mcp__github__create_pull_request`) | SIN VALIDAR |
| Etiquetar | `gh api $R/issues/N/labels -f 'labels[]=cell:915'` | SIN VALIDAR |
| Sacar etiqueta | `gh api -X DELETE $R/issues/N/labels/<etiqueta>` | SIN VALIDAR |
| Crear etiqueta | `gh api $R/labels -f name=... -f color=...` | SIN VALIDAR |
| Mergear | `gh api -X PUT $R/pulls/N/merge -f merge_method=squash` | SIN VALIDAR |
| Pasar a ready / volver a borrador | `POST $R/pulls/N/ccr/ready_for_review` y `POST $R/pulls/N/ccr/convert_to_draft` | SIN VALIDAR |
| Crear issue | `gh api $R/issues -f title=... -F body=@archivo.md -f 'labels[]=cell'` | SIN VALIDAR |

## Qué leer

- Reglas de código y proceso: `CLAUDE.md`, `docs/context/workflow.md`, `docs/context/testing.md`.
- Backend: `docs/context/backend.md`, `docs/context/modules.md`. Frontend: `docs/context/frontend.md`, `docs/context/components.md`, `docs/context/design-system.md`.
- Billing: `docs/billing/decision-registry.md`.
