# Informe de índices (Épica 33, subtarea 33.2)

Solo medición e informe: sin migraciones, sin cambios en `backend/src` y sin borrar índices. La propuesta de migración es de 33.3a/33.3b. Fecha: 2026-11-17.

## Cómo se obtuvo

Base `perf` (Postgres 16, compose de `perf/`) con el perfil `demo` más `perf/seed-large.sql` (33.1). Después del seed: `ANALYZE`, `pg_stat_reset()` y la carga completa de lectura: `measure.py`, `load.py` y `capture-origin.py` con `PERF_TEACHER=perft12`. Con esos contadores:

```
docker exec -i perf-a0-pg psql -U postgres -d perf < perf/index-report.sql
docker exec -i perf-a0-pg psql -U postgres -d perf < perf/index-candidates.sql > perf/index-candidates.txt
```

- `perf/index-report.sql` (repetible, solo lecturas del catálogo y `pg_stat_*`): índices sin uso, duplicados/redundantes, tablas con seq scans altos y FKs sin índice. Solo mira tablas de más de 1.000 filas donde corresponde (en tablas vacías no hay señal).
- `perf/index-candidates.sql` crea cada índice candidato a mano, mide y lo borra (`drop index`): no deja nada en la base. Salida completa en `perf/index-candidates.txt`. Antes y después: una pasada de calentamiento descartada y una medida, después de `vacuum analyze`. Caché caliente, un solo hilo, VM de 4 vCPU compartida con el backend.

Ruido: con el plan idéntico (C1, el índice no se usó) el mismo EXPLAIN dio 28,5 y 18,9 ms. Cualquier mejora menor a ~30 % con una sola corrida no es señal.

## Hallazgos del script

- **Índices duplicados/redundantes:** uno solo, `ix_contact_consent_person (person_id)`, cubierto por `uq_contact_consent` (prefijo de columnas; tabla casi vacía, 8 kB).
- **Sin uso (tablas > 1.000 filas, sin PK ni únicos):** 15 índices, listados abajo los más grandes.
- **Seq scans altos** (`seq_tup_read`): `invoice_line` 5,6 M filas leídas en 188 scans (30.000 por scan: morosos), `student` 5,5 M, `person` 4,6 M, `program_enrollment` 2,2 M (10 % de los accesos), `student_current_account` 1,4 M (32 % de los accesos son seq), `subject_section_enrollment` 1,1 M, `person_role` 0,8 M. `attendance`, `grade_item`, `invoice` y `notification` casi no hacen seq scan (índices usados).
- **FKs sin índice** (sección 4 del script, 221 filas en total, la mayoría en tablas vacías). Sobre tablas de más de 1.000 filas el script marca 25. Hay que leerlas con dos reservas, porque el criterio es estricto (prefijo no parcial exacto):
  - Falso positivo parcial: `attendance` (`course_section_enrollment_id`, `program_section_enrollment_id`), `class_session` (`course_section_id`, `program_section_id`) y `invoice_line` (`course_enrollment_id`, `program_enrollment_id`) tienen un índice **parcial** (`WHERE col IS NOT NULL`) que Postgres sí usa para `col = x` y para el chequeo de la FK. `invoice_line.invoice_id, currency` (FK compuesta) lo sostiene `uk_invoice_line_number (invoice_id, line_number)` por el prefijo `invoice_id`.
  - Candidatas reales, sin ningún índice que empiece por la columna: `attendance.justified_by_id` y `recorded_by_id`; `audit_log.actor_person_id` (100.016 filas); `grade_item.exam_board_id` y `recovery_loaded_by`; `invoice.monthly_invoice_run_id` (solo un parcial `DRAFT`) y `invoice.student_current_account_id, currency`; `invoice_line.exam_board_registration_id`, `price_version_id` y `subject_id`; `class_session.created_by_id`; `student.institution_id` y `registered_by_person_id`; `program_enrollment.program_id`, `program_cohort_id` y `current_billing_template_id`. Costo de no tenerlos: el borrado/actualización de la fila referenciada (persona, programa, institución) escanea la tabla hija. No medí ese costo: la carga no borra ni actualiza padres.

## 1. Faltantes priorizados (con mejora medida)

Consultas de `db-baseline.md` (Q1..Q20). Las numeradas Q2, Q3, Q4 y Q7 son las del baseline; Q8, Q10 y Q11 son las de `pg_stat_statements` con literales a mano.

| Prioridad | Consulta / pantalla | Índice probado | Antes (ms) | Después (ms) | ¿Lo usó el plan? | Veredicto |
|---|---|---|---|---|---|---|
| 1 | Q4 `/waitlists`: seq scan de 40.000 inscripciones + agregado por comisión | `subject_section_enrollment (program_section_id, status)` | 41,7 | 23,1 (−45 %) | sí, Index Only Scan | Mejora real; reemplazaría a `ix_subject_section_enrollment_section` (prefijo idéntico) y al waitlist parcial sin uso |
| 2 | Q3 `/financial-dashboard/debtors/page`: subconsultas correlacionadas por factura | `invoice_line (invoice_id, line_number)` parcial `course_enrollment_id` o `program_enrollment_id` no nulos | 54,0 | 39,5 (−27 %) | sí, Index Scan en la subconsulta | Mejora moderada, en el límite del ruido; la solución de fondo es reescribir la consulta (N+1 dentro del SQL), no el índice |
| 3 | Q8 `attendance/absence-report` | `attendance (program_section_enrollment_id, status)` | 1,47 | 1,42 | sí, Index Scan | Sin mejora: ya es 1,5 ms con `uk_attendance_program_date` |
| 4 | Q10 `/financial-dashboard` (join factura-cuenta) | `invoice (student_current_account_id)` parcial `OVERDUE` | 4,5 | 6,0 | sí, Index Only Scan | Sin mejora: ya resolvía con Bitmap Scan sobre `ix_invoice_overdue` (existente) y hash join; el Index Only Scan nuevo no gana; no recomendado |
| 5 | Q2/Q7 listado y conteo de alumnos (`/students`) | `person (last_name, name, id)` | 28,5 | 18,9 | **no** (mismo plan) | La diferencia es ruido; no hay índice faltante acá. El costo es el join con `person` x2, no el orden |
| 6 | Q7 conteo de alumnos | `student (onboarding_status, person_id)` | 5,7 | 5,4 | no | Sin mejora: 5.044 de 5.044 alumnos están `CONFIRMADO`, el filtro no discrimina |
| 7 | Q11 `distinct student_id` por estado | `program_enrollment (status, student_id)` | 2,5 | 2,7 | no | Sin mejora: 5.040 de 5.040 inscripciones en el mismo estado |

Conclusión honesta: en este dataset, con las consultas actuales, solo 1 faltante (lista de espera) tiene una mejora clara y 1 (morosos paginado) moderada. El resto de la lentitud medida en 33.1 (`debtors` sin paginar a 435 ms, `students`, `waitlists`) viene de la forma de la consulta (N+1 en SQL, join doble a `person`, 3.000 parámetros), no de índices faltantes.

Lo que no pude medir:
- Búsqueda `search=perf` de alumnos (Q6, 13,7 ms): usa `unaccent(lower(col)) like ...` con `OR` sobre 4 columnas; un GIN `pg_trgm` solo sirve si la consulta usa la misma expresión sobre una función inmutable (`unaccent` no lo es); necesita cambio de código o migración con wrapper. Sin medir.
- Q1 (`debtors` sin paginar): devuelve 90.000 filas por diseño; un índice no cambia el volumen. Sin medir.
- Costo de escritura de los candidatos (inscribir, tomar asistencia, pagos): la carga es de solo lectura.
- Concurrencia: todos los EXPLAIN son de un hilo y caché caliente.

## 2. Sin uso o duplicados, candidatos a eliminar (no se eliminó ninguno)

`idx_scan = 0` sobre una carga que es solo lectura de ~35 endpoints: que no se haya usado acá no prueba que sobre, hay que mirar también la carga de escritura y los jobs. Costo de escritura estimado: una entrada de índice por cada INSERT (y por cada UPDATE que cambie una columna indexada); tamaño medido, bytes por fila = tamaño / filas de la tabla. El contador de escrituras de la carga es 0 (el seed se cargó antes del reset de estadísticas), así que el costo por escritura es estimación, no medición.

| Tabla | Índice | Tamaño | Filas de la tabla | Costo de escritura estimado | Nota |
|---|---|---|---|---|---|
| `audit_log` | `ix_audit_log_occurred_at (occurred_at DESC, id DESC)` | 5,5 MB | 100.016 | ~56 B/INSERT, 1 entrada extra en una tabla de solo inserciones | La lectura por fecha de auditoría no está en las pantallas medidas; confirmar con el código antes |
| `audit_log` | `ix_audit_log_entity (entity_type, entity_id, occurred_at DESC)` | 4,7 MB | 100.016 | ~48 B/INSERT | Es el índice natural para el historial por entidad; probable que se use en la pantalla de auditoría que no se midió |
| `grade_item` | `ix_grade_item_course_section_enrollment` | 456 kB | 68.710 | ~7 B/INSERT | FK del legado de cursos; no hay lecturas por esa columna en la carga |
| `subject_section_enrollment` | `ix_subject_section_enrollment_waitlist` (parcial `WAITLISTED`) | 312 kB | 40.117 | ~8 B/INSERT de lista de espera | Lo reemplazaría el candidato 1 de arriba |
| `subject_section_enrollment` | `idx_subject_section_enrollment_withdrawn_by_person` | 272 kB | 40.117 | ~7 B/INSERT | Índice de FK; sirve para borrar personas, no para lecturas |
| `invoice` | `ix_invoice_account_open_by_due_date` (parcial abiertas) | 248 kB | 30.000 | ~8 B/INSERT de factura abierta | Posible uso en la cuenta corriente por alumno, que la carga mide solo con el alumno 20001 |
| `invoice` | `ix_invoice_issuing_reason_issue_date` | 208 kB | 30.000 | ~7 B/INSERT | Sin lecturas medidas |
| `program_enrollment` | `ix_program_enrollment_status` | 48 kB | 5.040 | ~10 B/INSERT | Baja cardinalidad (todo `ENROLLED`) |
| `program_enrollment` | `idx_program_enrollment_withdrawn_by_person` | 48 kB | 5.040 | ~10 B/INSERT | Índice de FK |
| `contact_consent` | `ix_contact_consent_person` | 8 kB | pocas | despreciable | Duplicado de prefijo de `uq_contact_consent (person_id, …)` |

Además hay 4 índices parciales de 8 kB sin uso (`ix_invoice_run_draft`, `ix_invoice_origin`, `ix_attendance_justification_*`, `ix_invoice_line_template_slot`) que sostienen flujos que esta carga no ejecuta (emisión de facturas, justificaciones): no son candidatos.

## 3. Compuestos con orden de columnas dudoso

No medí cambiar el orden en ninguno; son observaciones del catálogo y del uso. Fuente: sección 5 del script.

| Índice | Observación | Qué habría que revisar |
|---|---|---|
| `ix_invoice_payment_status_due_date (payment_status, due_date)` parcial `payment_status IN ('PENDING','PAYMENT_UNDER_REVIEW')` | El predicado ya restringe `payment_status` a 2 valores; como primera columna casi no aporta. 0 usos. | Si hay consulta por esos dos estados ordenada por vencimiento, `(due_date)` sola con el mismo predicado sirve igual y es más chica |
| `ix_subject_section_enrollment_waitlist (program_section_id, waitlist_prioritized_at, enrolled_at, id)` | 4 columnas; el orden es el de la cola de espera, pero la consulta real (Q4) cuenta por comisión sin ese orden y no lo usó. 0 usos. | Confirmar la consulta de promoción de la cola (`waitlist`) antes de decidir; si no se usa, 33.3 lo baja |
| `ix_invoice_account_open_by_due_date (student_current_account_id, due_date)` parcial | Orden razonable para la cuenta por alumno; 0 usos porque la carga no recorre esa pantalla | Re-medir con la pantalla de cuenta corriente real |
| `ix_notification_recipient_created_at (recipient_id, created_at DESC)` y `ix_notification_recipient_unread (recipient_id) WHERE read = false` | Orden correcto; se usaron (222 scans, `unread-count` a 0,7 ms). Se solapan solo en `recipient_id` | Sin acción |
| `uk_attendance_program_date (program_section_enrollment_id, class_date)` parcial | Es el único índice usable de `attendance` por inscripción (88.386 scans); el orden es correcto para el informe de ausencias | Sin acción |

## Qué queda para 33.3a/33.3b

- Candidato claro: `subject_section_enrollment (program_section_id, status)` (−45 % en lista de espera).
- Candidato moderado: `invoice_line (invoice_id, line_number)` parcial; mejor evaluar junto con la reescritura de morosos.
- No hacer: los otros 5 probados no mejoran.
- Decidir con los dueños del código qué FKs del listado necesitan índice por integridad de borrado (`audit_log.actor_person_id` es la de mayor volumen).
- Sacar los sin uso solo tras medir una carga de escritura y revisar jobs y pantallas no cubiertas.
