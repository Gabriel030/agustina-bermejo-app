# ADR 0008 — Vistas materializadas: no por ahora

> Verificado: 2026-11-27, commit `9def3b2e0`.

- **Estado:** aceptado (decisión de Gabriel, registrada en el Issue #1608, E25-B S3). La decisión es no crear vistas materializadas y documentar qué medición reabriría el tema.
- **Fuentes:** punto A3 de la Épica 25 (vive en el vault, no en el repo; acá se referencia solo lo verificable en el repo).

## Contexto

La Épica 25 (punto A3) planteó si las lecturas agregadas (dashboard financiero, morosos, resúmenes de asistencia, listados por sección) necesitan una estructura precalculada. El repo ya tiene, verificable:

- Índices de lectura: `V216__retention_indexes.sql`, `V240__notification_unread_index.sql`, `V360__subject_section_enrollment_section_status_index.sql` (#1171), `V390__foreign_key_read_indexes.sql` y `V473__commission_closure_student_enrollment_index.sql`.
- N+1 resueltos y fijados con tests de conteo de consultas en la Épica 33 (#954; ver `docs/cell/plans/954.md`) y en E29-F (#1197: el grade-grid hace 7 consultas el de docente y 6 el de staff, constantes con 3 o 25 alumnos, `GradeGridQueryCountTest`; ver la nota en `perf/db-baseline.md`).
- Presupuestos por endpoint (`p95` y cantidad de consultas) y por pantalla (requests y duplicados) en [`perf/budget.json`](../../perf/budget.json), verificados por `perf/check_budget.py` en el CI (`.github/workflows/ci.yml`). Cómo se mide: `perf/README.md`; baseline: `perf/db-baseline.md`.

No hay en el repo una medición que muestre un endpoint de lectura fuera de presupuesto por falta de una estructura precalculada.

## Alternativas

1. **Vista materializada** (por ejemplo, saldos por alumno o resumen del dashboard financiero). Lectura barata, pero exige `REFRESH` (programado o disparado), define una ventana de datos viejos que el producto tendría que aceptar, agrega migraciones y un job, y duplica la lógica de cálculo que hoy vive en el dominio (`Money`, políticas de billing). Las decisiones de plata (`docs/billing/decision-registry.md`) se calculan en línea; un valor precalculado puede contradecirlas hasta el próximo refresco.
2. **Tabla de resumen por evento.** Se actualiza con los eventos del outbox ([ADR 0003](0003-outbox-de-eventos-sin-broker.md)). Sin datos viejos prolongados, pero cada evento nuevo que afecte el agregado debe mantener la tabla, hay que poder reconstruirla y los errores de consistencia son silenciosos. Es la opción más costosa de mantener.
3. **Nada por ahora.** Se mantienen índices, fetch plan y consultas por lote, y el CI vigila los presupuestos.

## Decisión

Se elige la alternativa 3: **no se crea ninguna vista materializada ni tabla de resumen**. No hay medición que la justifique tras los índices, el N+1 resuelto y los presupuestos vigentes. Agregarla hoy sería una abstracción especulativa con costo de consistencia y mantenimiento.

## Qué medición reabre el tema

Se reabre este ADR (con un ADR nuevo que lo reemplace) solo si se cumple todo:

1. Un endpoint de lectura supera su presupuesto de `perf/budget.json` (`p95` o consultas) medido con `perf/measure.py`, con `uptime` anotado.
2. Ya se descartaron los índices (`perf/index-report.md`, `perf/explain-top5.txt`) y el fetch plan (consultas por lote, sin N+1).
3. La medición se hizo con volumen real o equivalente (`perf/seed-large.sql`), no con el seed de desarrollo.
4. El producto acepta explícitamente la ventana de datos viejos si se elige la alternativa 1, o el costo de mantenimiento por evento si se elige la 2.

## Consecuencias

- Las lecturas siguen calculándose en línea; no hay job de refresco ni tablas derivadas que mantener.
- El control de regresión es `perf/check_budget.py` en el CI: si un presupuesto se rompe, la respuesta es primero índice y fetch plan.
- Subir un presupuesto de `perf/budget.json` para esquivar la medición no reabre el tema; hay que justificarlo en el PR.
