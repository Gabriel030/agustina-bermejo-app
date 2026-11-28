# Release 32 (develop → master): datos del dossier recalculados

Calculado el 28-nov-2026, solo lectura. Punta de `origin/develop` = `7009d673`; `origin/master` = `720961f1` (merge del release 31, cuyo contenido es `develop@11b597c0`, 24-nov). Base de comparación real: **11b597c0** (`git diff origin/master origin/develop` da lo mismo porque el merge a master fue "ours").

## 0. Estado de CI y SHA a cortar (provisorio, no se corta hasta la puerta del punto 6)

| SHA | Run de push | Nota |
|---|---|---|
| `7009d673` (punta) | pendiente al momento de la consulta | no elegible todavía |
| `1da28f8d` (#1714, E34-C C7) | **failure**: jobs `backend-tests (3)` y `backend` | no elegible |
| `2bed08ac` (#1705, E18-S6) | success | último verde visto; no incluye #1714 ni #1713/#1715 |

Habrá que re-elegir SHA al cortar (hoy el verde más reciente es `2bed08ac`, 3 PRs menos que la punta: #1714, #1715 y los que sigan). No hay release abierto contra master que yo pueda confirmar (`gh pr list` por GraphQL está bloqueado en la sesión: verificar a mano).

## 1. PRs mergeados desde el último release

**247 PRs** (`git log --first-parent 11b597c0..origin/develop`; 17 son merges de ramas y 230 squash). Todos cumplen la cadencia (15 PRs). Por tipo: 97 feat, 40 fix, 32 refactor, 23 test, 17 docs, 6 perf, 3 ci, 29 sin prefijo (merges de rama o títulos libres).
Frontend (47 con prefijo `front`) + backend por módulo. Agrupado en lenguaje claro (número = PR):

### 1.1 Plan de estudios, cohortes y estructura académica (E34-A / E34-B / E34-C)
| PRs | Qué hace |
|---|---|
| #1648, #1665, #1693, #1681, #1704 | El plan de estudios pasa a ser **versionado** (borrador / activo / vencido, con vigencia y plazo de egreso); cohorte y matrícula apuntan a un plan; un solo motor de progreso por plan (nota más reciente, egreso por equivalencias); baja de carrera, constancias y listados corregidos |
| #1660, #1642, #1677, #1682, #1689, #1709, #1713, #1715 | Cohortes: plantilla de facturación **opcional**, la cohorte se mueve a academicstructure, checklist de apertura y acción "Abrir", **responsable (preceptor) obligatorio** al crear curso/cohorte y al aprobar el ciclo, contrato de cohortes y pantallas |
| #1649, #1657, #1683, #1674, #1646 | Oferta por ciclo: la comisión nace con configuración de evaluación, guardas de oferta/ciclo/inscripción, validación de plan/estado/recurrencia, correlatividades al editar/desactivar materias, **feriados con alcance** (clases, facturación o ambos) y plazo de configuración |
| #1654, #1690, #1650/#1714 | Preceptores: alcance por comisión y cohorte, administración de asignaciones, preceptor y **reemplazo atómico por comisión** |
| #1578, #1595, #1611, #1583, #1593 | Catálogo: área y fecha de publicación, página "Oferta académica", panel en la barra del alumno, **charlas informativas** con registro y permiso nuevo |
| #1623, #1619, #1637 | Carga horaria de cursos, feriados nacionales 2027 (seed) |
| #1406, #1539, #1671 | Choques de horario: aulas y docentes con restricción EXCLUDE en la base y traducción de errores (409) |

### 1.2 Evaluación, cierres y egreso
| PRs | Qué hace |
|---|---|
| #1662, #1655, #1652, #1658, #1666, #1685 | Configuración de evaluación para cursos, cantidad de TPs por comisión, cierre de alumno libre, bloqueo de escrituras con comisión cerrada, ventanas de mesa/reapertura de acta, rendir respeta matrícula y fechas |
| #1684, #1691 | Fin de término con **gracia de 14 días**, escalada a Dirección y **cierre automático** de matrículas por notas (E34-B P3) |
| #1432, #1438, #1448 | Nota mínima de correlativas en la policy, en la mesa de examen y en el progreso curricular |
| #1618, #1626 | Acta imprimible de la mesa, publicar nota final al guardar |
| #1668, #1673, #1675, #1680, #1694 | Bajas de comisión/mesa, inscripción a curso con cierre cerrado, motor de simulación de uno y cinco años (tests) |
| #1647 | Clases canceladas no cuentan en asistencia + controles de integridad |

### 1.3 Documentos verificables y trámites (#1103, #1104, #1523)
| PRs | Qué hace |
|---|---|
| #1378, #1493, #1507, #1513, #1514, #1517, #1518, #1520 | **Constancia de materias aprobadas (M1)** automática: emisión, descarga, revocación, vencimiento y verificación pública |
| #1526, #1527, #1534 | Constancia de examen X1 verificable y generada por el alumno; fecha de vigencia impresa |
| #1458, #1509, #1512, #1515, #1519, #1516, #1521, #1522 | Plantillas de documento publicadas/versionadas, emisión R1/N1/X1/M1 con plantilla publicada, adaptadores de excepción por deuda |
| #1567, #1616 | **Se retiran** el endpoint legacy de constancia de materias aprobadas y `GET /public/certificates/{code}` |
| #1710, #1427, #1508 | Aviso al alumno si se invalida una constancia, nombres en excepciones, `studentName` nullable en el lote |

### 1.4 Inscripción, lista de espera y alumnos
| PRs | Qué hace |
|---|---|
| #1428, #1435, #1440, #1442, #1475 | Lista de espera: historial con auditoría, tipos de aviso propios, posición del alumno, alta por personal con solicitud y factura |
| #1639, #1659, #1688 | Inscripción a cursos y aprobación de carrera con **billing OFF** inscribe directo; seed de demo |
| #1529, #1530, #1531, #1641/#1629 | Editar datos del alumno, cambio de email con re-verificación, señales por fila del padrón |
| #1453, #1456, #1452, #1457, #1461 | Equivalencias: notificaciones, sección y mapa curricular en la ficha, cargar/anular |
| #1436, #1444, #1447, #1450, #1451 | Mapa curricular del alumno, proyección de egreso con confianza monótona |
| #1498 | `GET /prospects` pasa a **paginado con búsqueda en servidor** (cambio de forma de respuesta) |

### 1.5 Asistencia
| PRs | Qué hace |
|---|---|
| #1613, #1634, #1653, #1687, #1711 | Revertir decisión de solicitud de asistencia, historial de resueltas, campos de reversión y píldora "Revertida" |
| #1651, #1443 | Validar fecha/día de clase al cargar asistencia; tomar asistencia con un nivel de pestañas |

### 1.6 Seguridad y autenticación
| PRs | Qué hace |
|---|---|
| #1573 | **Audiencia (`aud`) en el JWT** y clave mínima con 10 caracteres distintos (el backend NO arranca con un `JWT_SECRET` flojo) |
| #1571, #1577, #1585, #1587, #1700, #1698, #1445, #1446, #1420 | Auditoría 24.4: ownership en matriz de seguridad, aislamiento de `/me/**`, escape de comodines LIKE, pentest interno (casi todo tests/docs) |

### 1.7 Facturación, comunicaciones y monitoreo
| PRs | Qué hace |
|---|---|
| #1533, #1486, #1484, #1488, #1489, #1464, #1596, #1574, #1572, #1582 | `currency` en métricas mensuales; compositores transaccionales de facturación (refactor, sin cambio funcional previsto) |
| #1467, #1483, #1501, #1505, #1478, #1565 | Masivos: sin anonimización redundante, cupo diario compartido con invitaciones, destino para menores, vista previa de audiencia, runbook Brevo, **se elimina la columna `anonymized_at`** |
| #1628 | Botón "Reintentar fallidos" en oleadas de invitaciones |
| #1495, #1499, #1504, #1506 | Monitoreo: overlay de **GlitchTip** autoalojado, topes de eventos de error (límites nuevos) |
| #1679 | Bloqueo de cambio de billing OFF→MIRROR/FULL con cohortes sin plantilla |

### 1.8 Arquitectura, rendimiento y desempeño
| PRs | Qué hace |
|---|---|
| #1539..#1605 etc. (≈32 refactor) | Cortes de módulos (Modulith) y puertos api: enrollment, billing, evaluation, graduation, identity; baja la línea base de violaciones |
| #1454, #1455, #1548, #1500, #1502, #1546, #1620, #1633, #1624 | Rendimiento: secuencias y batching en importación, índice de cierre de comisión, front sin re-renders, Lighthouse móvil/PWA, decisión sobre vistas materializadas |

### 1.9 Frontend, navegación y diseño
| PRs | Qué hace |
|---|---|
| #1584, #1601, #1602, #1612, #1605, #1656, #1621, #1712, #1664 | **Nueva barra superior** con menús desplegables por rol (Alumno, Director, Docente, Preceptor, Platform admin, Subdirector), cajón móvil por niveles, menú de usuario único |
| #1317 (#1429, #1433, #1471), #1434, #1541, #1543, #1564 | Usabilidad móvil: migas colapsadas, objetivos táctiles 44 px, fade en tablas con scroll |
| #1457..#1477, #1463, #1460, #1466, #1459, #1469, #1472, #1485 | Sistema de diseño: StatusPill con ícono, estados deshabilitados por token, Button en lugar de `button` crudo, baselines visuales |
| #1630, #1576 | Modo MSW con roles de preceptor/subdirector, inicio de Gestión académica |

### 1.10 Pruebas, CI y docs de la célula
Unas 23 de test (nightly: #1701, #1708, #1699, #1676, #1672, #1544...), 17 docs (reglas 20-22 de la célula, runbooks de deploy local, GlitchTip, skills), 3 de CI (Lighthouse, nightly, tablero de épicas #1449/#1393). Sin efecto en producción.

> La lista cruda de los 247 está en `/tmp/claude-0/-home-user/c8b871c9-38ed-58ba-9f2c-89ca3b7fe7b5/scratchpad/prs.txt`.

## 2. Migraciones: V466 a V510 (45 archivos, todos nuevos)

Verificaciones: sin números duplicados; contiguas con master (último en master = V465); ninguna migración de master modificada (`M`) ni borrada (`D`); hubo dos renumeraciones ya resueltas (#1625 V482→V484, #1686 V499→V500). El dossier anterior cubría V466–V484 sobre `f168159a9`: ahora **se suman V485–V510 (26 migraciones)**.

### 2.1 Tabla de migraciones

| N° | Qué hace | Riesgo |
|---|---|---|
| V466 | Tabla `approved_subjects_certificate` (constancia M1) | bajo |
| V467, V468 | Amplía CHECK `notification_type_enum` (lista de espera, equivalencias): DROP + ADD NOT VALID + **VALIDATE en la misma transacción** | medio: ACCESS EXCLUSIVE sobre `notification` durante el escaneo completo |
| V469 | `import_row`/`import_created_entity`: de identity a secuencia con pool 50 | medio: cambia mecanismo de ids; irreversible sin backup |
| V470 | EXCLUDE (aula y docente) sobre franjas horarias, `active`, triggers, tabla `section_schedule_slot_instructor`, UPDATE de todas las franjas | **alto**: ABORTA si hay solapes activos de aula o docente; tiene bloque `[V470]` en pre-checks |
| V471 | `template_version_id` en 4 tablas de constancias + índices parciales | bajo |
| V472 | Inserta una versión PUBLISHED nueva de plantillas R1 y X1 con texto de "válida hasta {validUntil}" | medio: cambia el texto emitido (backfill de datos; revisar con Dirección) |
| V473 | Índice sobre `commission_closure_student(enrollment_id)` (sin CONCURRENTLY) | bajo/medio según tamaño |
| V474 | Único parcial de matrícula activa por (alumno, carrera) | **alto**: ABORTA con duplicados ENROLLED/PAUSED; bloque en pre-checks |
| V475 | Único parcial de solicitud de constancia PENDING_STAFF por matrícula | **alto**: ABORTA con duplicados; bloque en pre-checks |
| V476 | 2 CHECK en `program_enrollment` (WITHDRAWN con fecha efectiva; COMPLETED con fecha de finalización) **NOT VALID, sin VALIDATE posterior en ninguna migración** | medio: **VALIDATE manual pendiente**; bloque en pre-checks |
| V477 | `drop column broadcast_recipient.anonymized_at` | medio: destructiva, irreversible (columna sin uso según #1565); **sin bloque** en pre-checks |
| V478 | `unique(id, student_id)` en matrículas + FK compuestas NOT VALID en comisión | medio: se valida condicionalmente en V494; bloque en pre-checks |
| V479 | CHECK `course_enrollment` baja registrada con fecha efectiva, NOT VALID, **sin VALIDATE posterior** | medio: **VALIDATE manual**; bloque en pre-checks |
| V480 | Área de catálogo y fecha de publicación en `program`/`course`; CHECK de área; vuelve a crear `program_level_enum` (agrega BACHELOR) con VALIDATE en la misma transacción | bajo/medio (tablas chicas; el nivel ya está cubierto por el bloque de enums V209/V210) |
| V481 | Tablas `informative_session*` y permiso `informative_session.manage` al rol PRECEPTOR | bajo |
| V482, V483 | `course.duration_hours` con CHECK NOT VALID y VALIDATE aparte | bajo |
| V484 | Amplía `ck_audit_log_action` y `ck_audit_log_entity_type` con VALIDATE en la misma transacción | medio: ACCESS EXCLUSIVE sobre `audit_log` (tabla grande) |
| V485 | Seed de feriados nacionales 2027 (`on conflict do nothing`) | bajo |
| V486 | Tabla `preceptor_assignment` + permiso `attendance.read_all_change_requests` a ADMINISTRATIVO | bajo; cambia visibilidad de preceptores |
| V487 | Recrea únicos parciales de ciclo de oferta (uno vivo por programa/curso y término) | **alto**: ABORTA si hay ciclos no rechazados repetidos; **es el único bloque que figuraba antes** |
| V488 | `program_cohort`: `program_id`, `term_id` (backfill desde plantilla, NOT NULL), `template_id` pasa a nullable, nuevo único (program, term, intake), FK NOT VALID, permiso `manage_program_cohorts` | **alto**: backfill + DROP de constraint; **sin bloque**; las FK quedan NOT VALID y nunca se validan |
| V489 | `section_evaluation_config` acepta comisión de curso (columna nueva, subject nullable, CHECK de un solo dueño) | bajo: CHECK validado sobre tabla existente (todas las filas tienen subject) |
| V490 | Tabla `curriculum_plan`; crea un plan v1 ACTIVE por carrera con fecha de hoy; `program_subject.plan_id` NOT NULL; cambia único de materia por carrera a por plan; CHECKs NOT VALID | **alto**: reestructura grande, backfill, irreversible; **sin bloque** |
| V491 | VALIDATE de los 5 CHECK de `curriculum_plan` | bajo |
| V492 | Tabla `exam_board_audit` + permiso sensible `evaluation.reopen_exam_board` | bajo |
| V493 | CHECK de capacidad > 0 y `attempt_number >= 1` (NOT VALID); columna `version` en term/program/evaluation_scheme; índice en `processed_effect.processed_at` (sin CONCURRENTLY) | medio |
| V494 | VALIDATE de los CHECK de V493 y, **solo si no hay huérfanos**, de las FK compuestas de V478 (si hay, deja NOT VALID con warning y NO falla) | **alto**: aborta con capacidad ≤ 0 o attempt_number < 1; bloque `[V494]` en pre-checks |
| V495 | `plan_id` en cohorte y matrícula (backfill), FK, CHECK `plan_required` NOT VALID | **alto**: copia plan de la cohorte ajena; bloque `[V495]` en pre-checks |
| V496 | VALIDATE de `plan_required` | **alto**: ABORTA si alguna carrera no tiene plan no-DRAFT |
| V497, V498 | `holiday.scope` default BOTH, CHECK NOT VALID y VALIDATE aparte | bajo |
| V499 | `graduation_request.outdated_at` | bajo |
| V500 | `program_cohort.opened_at` y backfill `= created_at` (UPDATE de toda la tabla) | bajo |
| V501 | `preceptor_assignment.course_id`, reemplaza CHECK y agrega único | bajo |
| V502 | Campos de reversión en `attendance_change_request` + FK + índice | bajo |
| V503 | `director_approval_request.consumed_at` | bajo |
| V504, V505 | Amplía `notification_type_enum` (cierre de término / mesa) NOT VALID y VALIDATE aparte | bajo (patrón correcto) |
| V506 | Permiso `manage_preceptor_assignments` + amplía CHECK de `audit_log` con VALIDATE en la misma transacción | medio: ACCESS EXCLUSIVE sobre `audit_log` |
| V507, V508 | `commission_closure`: `closed_automatically`, rehace CHECKs y `actor_person_id` nullable; VALIDATE aparte en V508 | **alto**: V508 ABORTA con cierres CLOSED incompletos; bloque `[V507/V508]` |
| V509, V510 | Tipo de notificación `CERTIFICATE_INVALIDATED`, NOT VALID + VALIDATE aparte | bajo |

### 2.2 Riesgos agrupados
- **CHECK/VALIDATE en la misma transacción** (bloquean la tabla mientras escanean): V467, V468, V484, V506 (tablas `notification` y `audit_log`, potencialmente grandes) y V480 (tablas chicas). El resto (V482/83, V490/91, V495/96, V497/98, V504/05, V507/08, V509/10) sí separa NOT VALID de VALIDATE, aunque Flyway corre cada migración en su propia transacción.
- **Pueden ABORTAR por datos**: V470 (solapes), V474, V475 (duplicados), V487 (ciclos), V494 (capacidades/attempt_number), V496 (carreras sin plan no-DRAFT), V508 (cierres incompletos). V488 podría abortar en el único (program, term, intake) si dos cohortes colisionan.
- **Quedan NOT VALID para siempre si nadie corre VALIDATE**: V476 (2 CHECK), V479 (1 CHECK), V478 (2 FK, solo si V494 encuentra huérfanos), V488 (FK `fk_program_cohort_program` y `fk_program_cohort_term`).
- **Índices sin CONCURRENTLY**: V473, V474, V475, V487, V493 (`processed_effect`), V495 y los de V486/V501. No hay `CONCURRENTLY` en ninguna.
- **Destructivas / irreversibles sin backup**: V477 (DROP COLUMN), V469 (cambio de identity), V488 y V490 (DROP de constraints y reestructura), V472 (inserta versiones de plantilla), V470 (triggers y UPDATE de todas las franjas).
- `btree_gist` ya existe desde V110: V470 no suma riesgo de privilegios.

### 2.3 `pre-migration-checks.sql`: corrección del dato
El enunciado dice "hoy solo está [V487]"; en `origin/develop` el archivo ya trae **bloques para V474, V475, V476, V478, V479, V487, V494, V495, V507/V508 y V470** (dos bloques [V470] al final, los lee `SectionScheduleSlotExcludeMigrationTest`), además de los de master (V111 ... V465). Cambió +304 líneas en 28 PRs. Por lo tanto **ya no se debe agregar casi nada**, salvo:

| Migración | ¿Falta bloque? | Qué convendría |
|---|---|---|
| V477 | Sí (informativo) | Contar filas con `anonymized_at` no nulo antes de borrar la columna |
| V488 | Sí | Detectar cohortes con mismo (program, term, intake) vía plantilla y cohortes sin plantilla |
| V490 | Sí (informativo) | Programas con `program_subject` duplicado por materia (el nuevo único (plan, materia) lo cubre) y reporte de qué plan v1 se crea |
| V467/V468/V484/V506 | No aplica | Solo conviene verificar tamaño de `notification`/`audit_log` y correr en ventana |
| V472 | No aplica | Revisar que las plantillas R1/X1 tengan el texto "30 días" que se reemplaza |

Los bloques ya existentes cubren todas las migraciones con aborto de datos de V466–V510 salvo V488 y V496 (cubierto por el bloque V495). Si no se agregan, aclararlo en el PR.

## 3. Contrato (`bash scripts/contract-diff.sh origin/master origin/develop`)

Resultado: **30 hallazgos "potencialmente rompedores"** (1 REQUEST, 24 RESPONSE, 5 GENERAL). Clasificación:

| Hallazgo | ¿Breaking real? | Origen / comentario |
|---|---|---|
| `POST /subjects/{id}/sections`, `POST /course-subjects/{id}/sections`, esquema `CreateSectionRequest` removidos | **Sí** (clientes que creen comisiones por esa vía reciben 404/405) | #1657 (E34-A S5). El front del release ya no los usa; afecta a pestañas/PWA cacheadas |
| `GET /public/certificates/{code}` y esquema `PublicCertificateView` removidos | **Sí** (enlaces/QR viejos de constancias) | #1616 (E21-A S2). Verificar que la verificación pública nueva cubra los QR ya emitidos |
| `/me/program-enrollments/{id}/certificates/approved-subjects` removido | **Sí** | #1567 (endpoint legacy) |
| `GET /prospects` 200: de respuesta inline a `PageResponseProspectListItemResponse` | **Sí** (forma de la respuesta cambia: lista → página) | #1498 |
| `ExamCertificateBatchItemResponse.studentName` y `SectionEvaluationConfigResponse.subjectSectionId` pasan a nullable | Sí para clientes tipados estrictos | #1508, #1662 |
| 21 `nuevo required` en respuestas (`CertificateExceptionResponse`, `CurriculumBlockingPrerequisiteResponse`, `HolidayResponse`, `MonthlyMetricResponse.currency`, `ProgramCohortCatalogResponse`, `ProgramCohortResponse.planId`, `SectionEvaluationConfigResponse`, `StudentResponse`) | No: son aditivos para quien lee | oasdiff los marca por política; un cliente viejo los ignora |

Notas:
- El PR de release necesita la etiqueta `contract-break-approved`, la pone el coordinador con autorización expresa de Gabriel; después hay que cerrar y reabrir el PR (la etiqueta solo se lee en `opened/synchronize/reopened/ready_for_review`).
- El diff de oasdiff completo (`scripts/oasdiff-breaking.sh`) no se corrió acá (descarga binarios); el job `backend-contract` del CI lo hará.
- Los 4 breaking reales son de endpoints retirados o cambios de forma; el frontend del mismo release ya está alineado, por lo que el riesgo es solo de clientes cacheados y enlaces externos (QR).

## 4. Variables de entorno y configuración nuevas

| Variable | Default | Valor esperado en producción | Origen |
|---|---|---|---|
| `JWT_AUDIENCE` (`jwt.audience`) | `agustina-bermejo-app-api` | dejar el default | #1573 |
| `JWT_ACCEPT_MISSING_AUDIENCE` | `true` | ver punto 6: `true` en el release que introduce `aud`, `false` en el siguiente. Hoy master NO tiene `aud`, así que este es el primer release que lo emite | #1573 |
| `JWT_SECRET` | sin default (ya existía) | **nuevo requisito**: ≥ 32 caracteres y ≥ 10 distintos, si no el backend **no arranca** (`jwt.secret debe tener al menos 10 caracteres distintos`) | #1573 (`JwtSecretPolicy`) |
| `SENTRY_TUNNEL_GLOBAL_PER_MINUTE` | 120 | default | #1504 |
| `SENTRY_MAX_EVENTS_PER_MINUTE` / `SENTRY_EVENT_DEDUPE_SECONDS` | 30 / 60 | default | #1506 |
| `RETENTION_PROCESSED_EFFECT_MONTHS` | 6 | default (nuevo job de retención sobre `processed_effect`) | E33 (no verificado el PR) |
| `GLITCHTIP_IMAGE`, `GLITCHTIP_SECRET_KEY`, `GLITCHTIP_POSTGRES_PASSWORD` (+ opcionales `GLITCHTIP_DOMAIN`, `_BIND`, `_RETENTION_DAYS`, límites de CPU/memoria) | obligatorias solo si se levanta el overlay `deploy/glitchtip/docker-compose.glitchtip.yml` | **opcional**: no hace falta para este release; ver `docs/deploy/glitchtip.md` | #1495 |
| `VITE_MOCK_ROLE` (frontend) | vacío | no se usa en producción | #1630 |
| `BROADCAST_UNSUBSCRIBE_SECRET` | ya existía | confirmar valor propio distinto de `JWT_SECRET` (staging con perfil prod no arranca sin ella) | docs/deploy/README.md |

`app.scheduling.enabled` (interruptor de jobs, #1663) viene con `matchIfMissing=true`: sin variable, los jobs siguen activos. Postgres 18 con `max_locks_per_transaction=512` se recomienda para el overlay de GlitchTip (no afecta a la app).

## 5. Riesgos y rollback

Riesgos principales (ordenados):
1. **Datos reales que hagan abortar una migración** (V470, V474, V475, V487, V494, V496, V508): Flyway deja la base a mitad; se corrige corriendo `pre-migration-checks.sql` en producción antes.
2. **Validaciones pendientes** (V476, V479, V478, FK de V488): quedan NOT VALID si no se corre `VALIDATE CONSTRAINT`; el backend asume el invariante.
3. **Bloqueos de tablas grandes** (V467, V468, V484, V506 en `notification`/`audit_log`; índices sin CONCURRENTLY): ejecutar con la app parada.
4. **Seguridad**: `JWT_SECRET` débil impide arrancar; `aud` invalida los access tokens sin audiencia si se pone `JWT_ACCEPT_MISSING_AUDIENCE=false` el mismo día (duran 15 min; los refresh siguen sirviendo).
5. **Contrato**: endpoints retirados y `GET /prospects` paginado (punto 3).
6. **Develop no está verde en su punta**: CI de `1da28f8d` en rojo (`backend-tests (3)`), `7009d673` pendiente. Cortar en un SHA verde.
7. **Cambio de comportamiento sin migración**: cierre automático por gracia de 14 días (#1684, #1691), preceptor obligatorio al crear curso/cohorte (#1682, #1713, #1715), y bloqueo OFF→MIRROR/FULL con cohortes sin plantilla (#1679): requieren avisar a Dirección.
8. **Volumen**: 2.151 archivos y +87.852/-13.716 líneas; revisar `git diff origin/master origin/develop` por features perdidas en refactors (regla de `workflow.md` §6).

Rollback: Flyway no hace down. Procedimiento: restaurar el **backup previo a V466** y reponer la **imagen anterior del backend** (release 31). Irreversibles sin backup: V477, V469, V488, V490 (y sus backfills V495, V500), V472. Un rollback parcial (solo imagen) **no sirve** una vez migrado: las columnas `NOT NULL` y el cambio de únicos rompen la imagen vieja.

## 6. Condición del deploy y pre-condiciones de Gabriel

**Puerta (regla 17, `cell.md`):** el release 32 **NO se corta** hasta que el release 31 esté desplegado (**15-dic**). Hoy 28-nov: faltan 17 días. Mientras tanto no abrir `release/32-rc` ni ramas; si hace falta cortar antes, es decisión expresa de Gabriel, escrita en el PR. El dossier se vuelve a recalcular el día del corte (SHA verde nuevo; la lista y las migraciones seguirán creciendo en `develop`; si entra una V511+ o un bloque nuevo, actualizar este documento).

Pre-condiciones que debe cumplir Gabriel **antes del deploy del 32** (y no antes de que el 31 esté en producción):

| # | Pre-condición | Detalle |
|---|---|---|
| 1 | `jwt.accept-missing-audience=false` | Poner `JWT_ACCEPT_MISSING_AUDIENCE=false` solo cuando todos los access tokens vigentes ya traigan `aud`. Ojo: master (release 31) **todavía no emite `aud`**, así que `false` en el mismo deploy del 32 desloguea a todos por 15 min; la doc del README dice `true` en el release que lo introduce y `false` en el siguiente. **Decidir con Gabriel.** |
| 2 | `JWT_SECRET` de producción válido | ≥ 32 caracteres y ≥ 10 distintos: `printf %s "$JWT_SECRET" \| fold -w1 \| sort -u \| wc -l`. Si no, rotar con `docs/security/secrets-rotation.md` antes del deploy |
| 3 | `pre-migration-checks.sql` corrido en producción | `psql -f docs/release/pre-migration-checks.sql`; todas las consultas de los bloques V470, V474, V475, V476, V478, V479, V487, V494, V495, V507/V508 deben dar 0 filas. Corregir datos antes (es decisión de producto en V476/V479) |
| 4 | VALIDATE manual de V476, V478 y V479 | `ALTER TABLE program_enrollment VALIDATE CONSTRAINT ck_program_enrollment_withdrawn_has_effective_date` y `..._completed_has_completion_date`; `ALTER TABLE course_enrollment VALIDATE CONSTRAINT ck_course_enrollment_registered_withdrawal_has_effective_date`; las FK `fk_subject_section_enrollment_parent_student` y `fk_course_section_enrollment_parent_student` si V494 las dejó NOT VALID (warning en el log) |
| 5 | V494 | Confirmar 0 filas del bloque `[V494]`: si falla, la migración aborta |
| 6 | Backup previo a V466 | Es el rollback |
| 7 | Ventana con la app parada | Por los bloqueos de V467/V468/V484/V506 e índices sin CONCURRENTLY |
| 8 | Avisar a Dirección | Cierre automático por gracia de 14 días, preceptor obligatorio, charlas informativas, barra de navegación nueva |
| 9 | Etiqueta `contract-break-approved` | Autorización expresa; la pone el coordinador |
