# Estado y plan del proyecto, 28-nov-2026 (traspaso a otra cuenta)

Documento para que otra sesión de Claude Code (otra cuenta, otra PC) retome el trabajo sin contexto previo. Escrito por la sesión coordinadora de la célula a las ~13:30 de Buenos Aires (UTC-3). El estado vivo está en GitHub (Issues y PRs); este archivo es la foto de hoy. Si algo discrepa, ganan el código y los Issues.

## 0. Actualización de las ~13:45 de Buenos Aires (leer primero)

- **Release 32 MERGEADO a master:** PR #1728, `release/32-rc` cortado en `e32ab218`, master en `b2e2a23a`. 255 PRs, migraciones V466 a V510, cambios rompedores de contrato autorizados por Gabriel (etiqueta `contract-break-approved` puesta). **No se desplegó**: el despliegue es de Gabriel. Falta verificar que el workflow de backport (`backport.yml`) corra en verde sin abrir un PR `backport/*` (si se abre, cerrarlo: la rama empezaba con `release/`). Riesgo nuevo del dossier: la migración V490 aborta si alguna carrera tiene el nombre vacío; en producción debe dar 0 filas `select id from program where name is null;`.
- **F2 del frontend de preceptores COMPLETO:** mergeados #1715 (CursoWizard), #1720 (editor por comisión) y #1726 («Completar» responsable de cohorte). Falta el único `update_baselines` de capturas visuales, que dispara Gabriel (resuelve de paso el nightly de capturas #1707).
- **No queda ninguna sesión paralela viva** (los líderes se archivaron) **ni subagentes corriendo.** Los dos últimos subagentes (egreso con requisitos pendientes, auditoría de reglas de diseño y movimiento) se pararon por orden de Gabriel y quedaron a medias.
- **El barrido horario** (trigger `trig_01T9mgufRg1hVbXw5HLEMd58`) está atado a la sesión coordinadora original. La nueva sesión debe crear el suyo (merge de PRs verdes, avisar rojos, comentar #1592 solo si cambió algo).
- **Acciones que el clasificador le niega a una sesión y que debe hacer Gabriel:** marcar un PR borrador como «Ready for review» (hoy #1723, esqueleto del manual), editar `.claude/settings.json`, y escribir «autorizo» en sesiones secundarias. Desde la sesión principal los subagentes pudieron hacer commit, push y PR sin denegaciones.
- **Vault de Obsidian:** repo privado `Gabriel030/agustina-bermejo-vault` (no se adjuntó a la sesión; contenido sin verificar). No hacerlo público sin una instrucción explícita. En ese repo no se debe poner `Co-Authored-By: Claude` ni el link de sesión en los commits (pedido de Gabriel).
- **Pendientes que solo puede resolver Gabriel (en orden):** «Ready for review» de #1723; disparar `update_baselines`; despliegue del release 32 (backup previo, ventana, `JWT_SECRET` ≥32 caracteres y ≥10 distintos, `JWT_ACCEPT_MISSING_AUDIENCE=true` en este release, chequeo de V490, aviso a Dirección, QR de constancias por el endpoint público retirado); contar qué no le convence del billing para escribir su spec de rediseño; cerrar los Issues terminados (#1696 y #1697, y el #1692 tras #1717) solo con su palabra.
- **Para la sesión que retome (lo primero que haría):** 1) verificar el backport del release; 2) escribir la regla de «requisitos de aprobación» pendiente (sección 4) o pedir a Gabriel que confirme el diseño; 3) cuando Gabriel marque #1723 como listo, mergearlo y lanzar la página `/ayuda` (tarea B de #1721); 4) lanzar la auditoría de reglas de diseño y movimiento (tarea A de #1724) y luego tokens y componentes base; 5) terminar «cohortes sin facturación» (falta una tarea).

## 1. Cómo se trabaja ahora

- **Una sola sesión principal con subagentes efímeros.** Se terminaron los líderes persistentes y los relevos (decisión de Gabriel, 28-nov). La sesión principal planifica, lanza subagentes (`backend`, `frontend`, `reviewer`, `ux-advisor`, `mechanic`, o `general-purpose` para lectura y specs), mergea con CI verde y mantiene el estado en GitHub. Cada subagente trabaja en su propio `git worktree`, abre su PR por REST (`gh pr create` falla por GraphQL 403: usar `gh api repos/Gabriel030/agustina-bermejo-app/pulls -f ...`) y no mergea.
- **Reglas del proyecto:** leer `CLAUDE.md` y `docs/context/*.md` antes de tocar código. Resumen: identificadores y nombres de tests en inglés, cero comentarios y javadoc en código nuevo, textos de usuario en castellano; PRs siempre contra `develop`; CI local antes de abrir el PR; migraciones con `bash scripts/next-migration.sh` (una a la vez, `--check <n>` justo antes de mergear); todo mockup pasa por `ux-advisor` y por el OK de Gabriel antes de codear; mockups siempre como Artifact.
- **Merge:** squash con `gh api -X PUT repos/Gabriel030/agustina-bermejo-app/pulls/<n>/merge -f merge_method=squash -f sha=<head completo>`, solo con CI verde sobre el head real (mirar `check-runs` del head; un draft no corre CI). Antes de mergear un PR de backend, comprobar que su base esté al día con `develop` (hubo un fallo intermitente el 28-nov; ver §7).
- **Plata y estados académicos:** dos revisiones independientes antes de mergear y una tabla antes/después en el PR.
- **Contrato:** `contracts/openapi.json`, `scripts/contract-diff.sh`, etiqueta `contract-break-approved` solo con autorización expresa de Gabriel; el workflow la lee en `opened/synchronize/reopened`, así que hay que cerrar y reabrir el PR después de ponerla.
- **Permisos:** el clasificador de auto mode deniega acciones sensibles (commit/push en ramas nuevas desde sesiones distintas de la principal, `gh pr ready`, editar `.claude/settings.json`, editar tests existentes en algunos casos). Nunca se rodea una denegación: se le explica a Gabriel qué hace falta. Las reglas de allow viven en `.claude/settings.json` (37 reglas allow, 10 deny).
- **Tablero de épicas:** `.github/epic-board.json` + workflow `epic-board.yml` (corre desde master: no existe en la rama por defecto hasta el próximo release) + `epic-autolabel.yml` (etiqueta `epic:<id>` por rama o por `Refs #N`). Issue de estado general: #1592. Cola de migraciones: #1393.
- **Cómo hablarle a Gabriel:** en castellano, tablas, sin códigos sueltos (decir «épica preceptores, 8 de 9 tareas», no «E34-C»), hora de Buenos Aires, preguntas agrupadas con la recomendación primero. Él valida textos y mockups. Cerrar Issues solo con su palabra.

## 2. Estado por épica

| Épica (nombre claro) | Issue | Avance | Qué falta |
|---|---|---|---|
| Integridad académica | #1638 | 12 de 12 tareas mergeadas | Hallazgo abierto: solicitudes de egreso con materias pendientes (ver §4). Cobertura débil: no hay camino a «libre» por API. |
| Planes de estudio versionados | #1643 | 10 de 10 mergeadas | Deuda: validar cohorte-carrera al registrar historial; avisos. |
| Preceptores (cohortes, cursos, comisiones) | #1650 | Backend completo. Frontend: alta de cohorte, alta de curso (#1715) y edición de preceptores por comisión (#1720) mergeados | PR #1726 «Completar» responsable de cohorte (CI en curso). Después: `update_baselines` (lo dispara Gabriel una sola vez). |
| Cohortes sin facturación | #1606 | 4 de 5 | Falta la última tarea (leer qué incluye). |
| Solicitudes de asistencia «Revertida» | #1607 | 7 de 8 (estimado) | Mergeado el front. |
| Modularización (Spring Modulith) | #1696 | `academicstructure` desacoplado de `evaluation` | Quedan 6 pares reales en un solo ciclo (matrícula, asistencia, evaluación, facturación). Plan en `modularizacion-plan.md`. **Congelado**: depende del rediseño de billing. |
| Seguridad de «mis datos» (`/me/**`) | #1697 | 2 de 2 | Cerrable con la palabra de Gabriel. |
| Manual de usuario dentro de la app | #1721 | 11 tareas, 0 mergeadas | Mockup `/ayuda` v2 **aprobado**; esqueleto en PR #1723 (borrador, falta «Ready for review» de Gabriel); luego página `/ayuda`. |
| Re-styling de toda la app | #1724 | 10 tareas, 0 mergeadas | Decisiones aprobadas (§3). Auditoría de etiquetas terminada; auditoría de reglas y movimiento sin terminar. |
| Rediseño de billing | sin Issue | — | Gabriel lo pidió; falta spec. La facturación NO se enciende por ahora. |

Otros: nightly (#1695 parcial): `FirstYearSimulationIT` arreglado (#1717, cerrable el #1692), tests de conteo de asistencia estabilizados (#1722), pendientes #1706 (mora, plata) y #1707 (capturas visuales: se resuelve con `update_baselines`).

## 3. Decisiones de Gabriel ya tomadas (no volver a preguntar)

- Facturación apagada en producción y rediseño de billing aparte; congelar todo trabajo de billing y las rebanadas de modularización que lo tocan.
- Una sola sesión principal con subagentes.
- Manual de usuario: lector = personal del instituto (no alumnos); formato = página `/ayuda` dentro de la app con buscador y filtro por rol; documenta los 7 roles (incluye Subdirector y Admin de plataforma); Gabriel valida el texto; entrada «Ayuda» en el menú de usuario; por defecto solo el rol propio con interruptor «Ver todos los roles»; el test de cobertura bloquea el CI con excepciones explícitas; se publica por bloques; capturas al final, después del re-styling; Cobranza como sección «pendiente de rediseño».
- Re-styling: paleta A «Petróleo y coral», movimiento sobrio con `prefers-reduced-motion`, inicio «Hola, X + lo más urgente» para roles con pendientes (Director primero), orden por familia y por impacto de rol. Objetivo central: menos colores de etiqueta, un conjunto cerrado de tonos (neutro, acento, éxito, aviso, peligro), animaciones coherentes, aspecto más profesional. El mockup «Rediseño · Agustina Bermejo» (https://claude.ai/artifact/UfUkKkUR5awithE8A4iebf) es solo referencia de estilo.
- Mockups aprobados: preceptores F1 v7 (https://claude.ai/artifact/N6kviUmdCot8VFZn1BKFRb), `/ayuda` v2 (https://claude.ai/artifact/Hn6nH9H2Pfv8JpMfvSi18M).
- Regla de promoción (aclarada por Gabriel): promocionar (nota de cursada) y aprobar (requisitos: notas + correlativas + pagos cuando exista billing + asistencia + trabajos prácticos + otros) son cosas distintas. Si promocionó pero falta un requisito (por ejemplo la correlativa), la materia **no va a final**: queda «promocionada, falta: [requisitos]» y se aprueba sola cuando se cumplen todos. El egreso nace solo cuando todas las materias obligatorias están realmente aprobadas, confirmado por el cierre.
- Cohortes y cursos: el responsable general de la cohorte y los preceptores de comisión son obligatorios (los de comisión, al «Abrir»).

## 4. Trabajo en vuelo o parado (nada de esto está mergeado)

- **Egreso / promoción con requisitos pendientes:** un subagente quedó a medias y se lo paró por orden de Gabriel. Hay una rama con un cambio parcial: `origin/fix/graduation-request-on-closure` (la solicitud de egreso ya no nace en la promoción provisional; rompe `MultiYearSimulationIT`, no se debe mergear sola). La segunda rama prevista, `fix/promotion-pending-requirements`, puede existir solo en un worktree local sin pushear. Diseño pedido: abstracción chica y extensible de «requisitos de aprobación» (correlativas ahora; pagos, asistencia, TP, documentos después), reutilizando `PROMOTION_CONDITIONAL` si encaja (hoy se usa para mora y documentos: no romperlo), reevaluación automática cuando se cumple un requisito, simulación quinquenal ajustada sin subir techos. Código relevante: `ClosureRequirementsCalculator.assessRow`, `PromotionPolicy.restrictApprovalWhenPrerequisitesUnmet`, `ClosureConditionPolicy`, `ClosureOutcomeApplier`, `GradeItemRecorder.applyProgramEnrollmentCondition`. Hallazgos H10 y H15 del Issue #1638. Ver el diagnóstico en la conversación original: hoy el cierre manda a final a quien promocionó pero tiene una correlativa sin aprobar, y eso explica 334 degradaciones y 48 solicitudes de egreso inconsistentes.
- **Re-styling:** falta la auditoría de reglas de diseño y movimiento por pantalla (se la paró). La auditoría de etiquetas está en `restyling-auditoria-etiquetas.md`.
- **Release 32 a master:** Gabriel ordenó meter todo en master «ya mismo» (ver §5). Estado: pendiente de que entre #1726, elegir SHA verde de develop, cortar `release/32-rc` y abrir el PR con dossier.

## 5. Release 32 (develop a master): MERGEADO el 28-nov, falta el despliegue de Gabriel

- Gabriel ordenó adelantar el release antes del deploy del 15-dic (decisión expresa, 28-nov). Mergear a master **no despliega**: no hay workflow de deploy automático; el despliegue es manual según `docs/deploy/README.md`.
- Procedimiento: skill `.claude/skills/cell-release/SKILL.md`. Cortar `release/32-rc` con `git push origin <sha>:refs/heads/release/32-rc` desde un SHA de develop con el run de `push` verde (nunca con la herramienta de crear rama). El nombre debe empezar con `release/` para que no se dispare un backport. Dossier completo en `release-32-dossier.md`.
- Alcance al 28-nov: unos 284 commits first-parent de develop sobre master, migraciones V466 a V510, contrato con cambios rompedores (necesita `contract-break-approved` con autorización de Gabriel): se retiran dos POST de creación de comisión, `GET /public/certificates/{code}` (cuidado con los QR ya emitidos), el endpoint legacy de materias aprobadas, y el listado de prospectos pasa de lista a página.
- Riesgos para el despliegue (de Gabriel, no del merge): backup previo a V466; varias migraciones validan restricciones sobre tablas grandes en la misma transacción; migraciones irreversibles (V477, V469, V488, V490); `pre-migration-checks.sql` sin bloques informativos para V477, V488 y V490; `JWT_SECRET` de al menos 32 caracteres y 10 distintos o el backend no arranca; el release introduce `aud` en el token: con `JWT_ACCEPT_MISSING_AUDIENCE=false` el mismo día se desloguea a todos unos 15 minutos (el README recomienda `true` en este release); avisar a Dirección por el cierre automático con 14 días de gracia, el preceptor obligatorio y el bloqueo de OFF a MIRROR o FULL con cohortes sin plantilla.
- Después del merge: verificar que el backport no se dispare (`gh run list --workflow backport.yml`).
- No desplegar develop antes de que F2 del frontend de preceptores esté completo (si no, crear cohorte o curso desde la pantalla da 400).

## 6. Pendiente por prioridad

1. Mergear #1726 («Completar» responsable) con CI verde; luego pedir a Gabriel el único `update_baselines`.
2. Cortar el release 32 (§5) con la autorización de contrato.
3. Cohortes sin facturación: la última tarea.
4. Egreso y requisitos de aprobación (§4), con doble revisión.
5. Manual: «Ready for review» de #1723, luego la página `/ayuda` (tarea B de #1721) con los componentes reales del catálogo y cuatro nuevos (`HelpSectionCard`, `HelpResultItem`, `HelpTaskArticle`, `HelpToc`), la entrada «Ayuda» en `navModel.js` y verificar que el alumno no la vea; después el contenido por bloques (tareas E a J).
6. Re-styling: terminar la auditoría de reglas y movimiento (tarea A), paleta y escala tipográfica en tokens (B), componentes base: un solo `StatusPill`, un solo `Notice`, un solo sistema de movimiento (C), guardas automáticas (D), y luego pantallas por familia (E a I).
7. Deploy de producción y entorno local con dominio y datos de prueba (`docs/deploy/local-con-dominio-y-seed.md`): Gabriel aporta dominio, DNS, `JWT_SECRET`, `pre-migration-checks.sql` en producción, GlitchTip y Brevo.
8. Rediseño de billing (spec nueva, falta que Gabriel cuente qué no le convence).
9. Modularización restante (después del rediseño de billing).

## 7. Cosas aprendidas hoy (evitan errores repetidos)

- Un PR de backend verde sobre su head puede romper `develop` al combinarse con otros: el 28-nov `AttendanceSectionQueryCountTest` falló dos veces con +3 sentencias. Resultó intermitente (probable causa: tareas programadas en el mismo contexto) y se apagó el scheduling en el soporte común (#1722). Si vuelve a fallar con eso aplicado, la causa es otra.
- Los PRs en borrador no corren CI.
- En PRs solo de frontend los jobs de backend salen `skipped`: es normal.
- `git merge origin/develop`, nunca rebase de ramas ya empujadas.
- Un squash de un PR que traía otro PR dentro deja conflicto en JSON del catálogo: traer develop con merge y conservar ambos lados.
- Los subagentes `frontend` no tienen Artifact: el mockup lo publica la sesión principal después de pasar por `ux-advisor`.
- Maven: un fork (`-Dtest.forkCount=1`), una corrida a la vez; los ITs de simulación tardan ~4 minutos; `dockerd` hay que levantarlo a mano en la VM.

## 8. Informes de apoyo (esta carpeta)

- `release-32-dossier.md`: dossier recalculado del release 32.
- `modularizacion-plan.md`: plan por rebanadas de la modularización restante.
- `restyling-auditoria-etiquetas.md`: inventario de colores y etiquetas de la app.
- `manual-auditoria.md`: qué quedó vigente y obsoleto del manual viejo (Doc «Agustina Bermejo — Documentación funcional», https://claude.ai/artifact/XqmE2kmZmsb4CQjZBJt1dK).

Issues clave: #1592 (estado general), #1393 (migraciones), #1108 (handoffs), #1721 (manual), #1724 (re-styling), #1638, #1643, #1650, #1606, #1607, #1696, #1697.
