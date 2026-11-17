# Línea base de consultas (Épica 33, subtarea 33.1)

Medición sin cambios de código de producción ni migraciones. Fecha: 2026-11-17.

## Seed y cómo recrearlo

`perf/seed-large.sql` crea un instituto grande, determinista (sin `random()`; todo sale de `generate_series` y aritmética sobre el índice, con distribución sesgada: las comisiones bajas concentran inscripciones y los alumnos/notificaciones/auditorías bajos concentran filas). Es re-ejecutable: empieza borrando sus propias filas (ids `20001-25000`, `30001-30050`, `14001-14300`, etc.) con `session_replication_role = replica` porque las líneas de factura emitidas son inmutables por trigger.

| Tabla | Filas |
|---|---|
| alumnos (`student`/`person`) | 5.000 (+ 50 docentes `perft1..perft50`) |
| comisiones (`subject_section`) | 300 (14001-14300), con 7.200 `class_session` |
| inscripciones (`subject_section_enrollment`) | 40.000 (5.000 en `WAITLISTED`) |
| asistencia (`attendance`) | 205.500 |
| notas (`grade_item`) | 68.500 |
| notificaciones | 50.000 |
| `audit_log` | 100.000 |
| facturas / líneas (cuotas) | 30.000 / 30.000 |
| alumnos con deuda (facturas `OVERDUE`, cuenta en `OWES`) | 3.000 |

Recrear (desde la raíz, con backend `local,demo,perf` ya arrancado, ver `perf/README.md`):

```
docker exec -i perf-a0-pg psql -U postgres -d perf < perf/seed-large.sql
```

Reproducibilidad: `perf/seed-large-counts.sql` imprime conteos y un checksum de inscripciones; se corrió, se volvió a correr el seed (que limpia y recarga) y la salida fue idéntica (`attendance|205500`, `audit_log|100000`, `checksum|-96902656633487369`, `debtors|3000`, `enrollments|40000`, `invoices|30000`, `notifications|50000`, `sections|300`, `students|5000`).

Medir y capturar (el seed grande reemplaza a `seed.sql`; no cargar los dos):

```
export PERF_TEACHER=perft12
python3 perf/measure.py
python3 perf/load.py
python3 perf/capture-origin.py
./perf/top-queries.sh
```

`PERF_TEACHER` (docente de la comisión 14011) y `PERF_SECTION` son variables nuevas de `measure.py`; `load.py` ahora escribe en `perf/out/load.json`. `capture-origin.py` resetea `pg_stat_statements`, llama cada endpoint de `measure.py` 20 veces y atribuye cada consulta al endpoint que más tiempo le sumó. `perf/top-queries.sh` lista las 20 consultas de mayor `total_exec_time` (calls, mean, rows).

## Entorno

- Nube, 4 vCPU (`nproc` = 4), 15 GB RAM, backend y Postgres 16 (compose perf) en la misma VM, sin otra carga.
- `uptime` al medir: load average 0,81 / 0,60 / 0,28 (antes de la carga), 3,07 al final de `load.py`.
- Cliente de carga en la misma máquina (compite por CPU con backend y Postgres): los absolutos son pesimistas.

## Latencia por endpoint (`measure.py`, 20 corridas, un hilo, ms)

| Rol | Endpoint | p50 | p95 | Consultas | Bytes |
|---|---|---|---|---|---|
| director | `/financial-dashboard` | 19 | 27 | 71 | 282 |
| director | `/financial-dashboard/debtors` | 435 | 495 | 6 | 612175 |
| director | `/financial-dashboard/debtors/page?page=0&size=25` | 28 | 42 | 2 | 5120 |
| director | `/staff` | 9 | 10 | 6 | 10655 |
| director | `/students` | 28 | 31 | 3 | 7496 |
| director | `/students?page=0&size=25` | 21 | 27 | 3 | 7496 |
| director | `/students?page=0&size=25&search=perf` | 19 | 26 | 2 | 49 |
| director | `/me/notifications` | 4 | 5 | 3 | 3850 |
| director | `/me/notifications/unread-count` | 3 | 4 | 2 | 14 |
| director | `/waitlists` | 37 | 47 | 4 | 4096 |
| director | `/terms` | 4 | 4 | 2 | 576 |
| docente | `/me/teaching-sections` | 6 | 7 | 6 | 1101 |
| director | `/program-sections/14011` | 4 | 5 | 3 | 233 |
| director | `/programs` | 9 | 11 | 6 | 3277 |
| director | `/students/20001/billing-overview` | 13 | 16 | 12 | 6899 |
| preceptor | `/program-sections/14011/attendance/absence-report` | 15 | 19 | 6 | 25263 |
| preceptor | `/academic-offering-cycles` | 3 | 4 | 2 | 2 |
| preceptor | `/terms/1/classrooms/availability?dayOfWeek=MONDAY&startTime=18:00&endTime=20:00` | 8 | 10 | 5 | 2 |
| preceptor | `/students/20001/program-enrollments` | 3 | 4 | 2 | 187 |
| preceptor | `/program-enrollments/20001/progress` | 10 | 12 | 10 | 253 |
| preceptor | `/program-enrollments/20001/attendance-summary` | 9 | 9 | 4 | 571 |
| preceptor | `/program-enrollments/20001/section-enrollments` | 9 | 13 | 1 | 3801 |
| administrativo | `/program-enrollments/20001/invoices` | 10 | 12 | 10 | 6219 |
| docente | `/program-sections/14011/roster` | 14 | 19 | 9 | 25263 |
| docente | `/program-sections/14011/grade-grid` | 21 | 31 | 10 | 51873 |
| docente | `/program-sections/14011/attendance/absence-report` | 14 | 21 | 9 | 25263 |
| alumno | `/me/program-enrollments` | 4 | 5 | 3 | 187 |
| alumno | `/me/program-enrollments/20001/progress` | 8 | 9 | 12 | 253 |
| alumno | `/me/section-enrollments` | 6 | 9 | 5 | 4185 |
| alumno | `/me/program-enrollments/20001/invoices` | 11 | 13 | 11 | 6387 |
| alumno | `/me/section-enrollments/100011/grade-items` | 4 | 4 | 5 | 2 |
| alumno | `/me/section-enrollments/100011/attendance` | 3 | 4 | 5 | 2 |

El resto de los endpoints de `measure.py` (dashboard financiero, morosos, listados, notificaciones, `/me/*`) están en `perf/out/latest.json` al correrlo. Resumen de la corrida base: `/financial-dashboard/debtors` p50 424 ms con 612 KB de respuesta (el peor); `/financial-dashboard` 19 ms; `/students` 22 ms; `/waitlists` 37 ms; `attendance-summary` 508 consultas por request (N+1 de aplicación); el resto por debajo de 25 ms.

## Carga concurrente (`load.py`, ms)

| Path | Hilos | Requests | rps | p50 | p95 | p99 | Errores | Conexiones DB pico |
|---|---|---|---|---|---|---|---|---|
| `/financial-dashboard` | 20 | 200 | 216.8 | 82 | 143 | 165 | 0 | 20 |
| `/financial-dashboard/debtors` | 4 | 20 | 5.7 | 668 | 872 | 886 | 0 | 22 |
| `/students` | 4 | 20 | 131.0 | 27 | 38 | 43 | 0 | 20 |
| `/me/section-enrollments` | 20 | 200 | 594.5 | 27 | 74 | 80 | 0 | 20 |
| `/program-sections/14011/grade-grid` | 20 | 200 | 171.1 | 105 | 182 | 205 | 0 | 20 |
| `/program-sections/14011/attendance/absence-report` | 20 | 200 | 230.2 | 78 | 126 | 156 | 0 | 20 |
| `/me/program-enrollments/20001/invoices` | 20 | 200 | 281.8 | 64 | 122 | 139 | 0 | 20 |

## Top 20 consultas por tiempo total

Sobre `capture-origin.py` (todas las pantallas, 20 llamadas cada una, `pg_stat_statements` reseteado al inicio). Orden por `total_exec_time` (frecuencia × duración).

| # | Endpoint de origen | calls | total ms | mean ms | rows | Consulta |
|---|---|---|---|---|---|---|
| 1 | `/financial-dashboard/debtors` | 20 | 1094.6 | 54.73 | 90000 | `select distinct i1_0.id,i1_0.lines_total,i1_0.lines_total_currency,i1_` |
| 2 | `/students?page=0&size=25` | 40 | 588.8 | 14.72 | 1000 | `select s1_0.id,s1_0.emergency_contact_name,s1_0.emergency_contact_phon` |
| 3 | `/financial-dashboard/debtors/page?page=0&size=25` | 20 | 475.5 | 23.77 | 500 | `with overdue as ( select i.id as invoice_id, i.currency, i.due_date, g` |
| 4 | `/waitlists` | 20 | 418.2 | 20.91 | 6000 | `select ss1_0.id,(s1_0.name//$11//ss1_0.name),t1_0.description,t1_0.yea` |
| 5 | `/students?page=0&size=25&search=perf` | 20 | 284.0 | 14.20 | 0 | `select s1_0.id,s1_0.emergency_contact_name,s1_0.emergency_contact_phon` |
| 6 | `/financial-dashboard/debtors` | 20 | 266.7 | 13.34 | 60000 | `select pe1_0.id,pe1_0.admission_application_id,pe1_0.completion_date,p` |
| 7 | `/students` | 40 | 123.9 | 3.10 | 40 | `select count(s1_0.id) from student s1_0 where 1=1 and s1_0.onboarding_` |
| 8 | `/program-sections/14011/attendance/absence-report` | 60 | 71.1 | 1.19 | 3180 | `select sse1_0.id,sse1_0.attempt_number,sse1_0.dropped_at,sse1_0.enroll` |
| 9 | `/program-sections/14011/attendance/absence-report` | 80 | 68.3 | 0.85 | 8960 | `select a1_0.program_section_enrollment_id,count(a1_0.id),sum(case when` |
| 10 | `/financial-dashboard` | 20 | 47.7 | 2.38 | 60000 | `select distinct sca1_0.student_id from invoice i1_0,student_current_ac` |
| 11 | `/financial-dashboard` | 20 | 42.2 | 2.11 | 100377 | `select distinct pe1_0.student_id from program_enrollment pe1_0 where p` |
| 12 | `(sin atribución)` | 1 | 40.7 | 40.72 | 0 | `explain (analyze, buffers) select distinct i.*, l.*, d.* from invoice ` |
| 13 | `(sin atribución)` | 1 | 38.9 | 38.91 | 0 | `explain (analyze, buffers) select ss.id,(s.name//$1//ss.name),t.descri` |
| 14 | `(sin atribución)` | 1 | 36.0 | 36.00 | 0 | `explain (analyze, buffers) with overdue as (select i.id as invoice_id,` |
| 15 | `/staff` | 20 | 33.6 | 1.68 | 1220 | `select p1_0.id,p1_0.address_id,p1_0.auth_provider,p1_0.avatar_path,p1_` |
| 16 | `/financial-dashboard` | 20 | 30.8 | 1.54 | 20 | `select i.currency as currency, coalesce(sum(i.outstanding_amount), 0) ` |
| 17 | `/program-sections/14011/grade-grid` | 20 | 28.3 | 1.42 | 2240 | `select sse1_0.id,sse1_0.attempt_number,sse1_0.dropped_at,sse1_0.enroll` |
| 18 | `(sin atribución)` | 1 | 23.4 | 23.36 | 0 | `explain (analyze, buffers) select s.*, p2.* from student s join person` |
| 19 | `/financial-dashboard/debtors` | 474 | 19.8 | 0.04 | 0 | `select sse1_0.id,sse1_0.attempt_number,sse1_0.dropped_at,sse1_0.enroll` |
| 20 | `/me/notifications/unread-count` | 20 | 19.0 | 0.95 | 20 | `select count(n1_0.id) from notification n1_0 where n1_0.recipient_id=$` |

Atribución: es por delta de `pg_stat_statements` mientras corre cada endpoint. Las filas 15, 17 y 18 (`select ... subject_section_enrollment` con 0 filas e `insert into subject_section_enrollment`, 57 calls) no son confiables: un GET de lectura no inserta inscripciones, así que casi seguro son jobs programados del backend corriendo durante la medición y quedaron atribuidos a `/financial-dashboard/debtors`. No lo investigué.

## EXPLAIN (ANALYZE, BUFFERS) de las 5 peores

Planes completos en `perf/explain-top5.sql` (se corre con `docker exec -i perf-a0-pg psql -U postgres -d perf < perf/explain-top5.sql`); los parámetros `$n` se reemplazaron por literales equivalentes escritos a mano, así que son una aproximación, no el plan exacto que ve Hibernate.

| # | Consulta | Tiempo | Diagnóstico |
|---|---|---|---|
| 1 | facturas `OVERDUE` con líneas y descuentos (`/financial-dashboard/debtors`) | 34,0 ms (54,7 ms mean en la app) | seq scan de `invoice` y de `invoice_line` completo, `distinct` + sort sobre 4.500 facturas y 90.000 filas devueltas por request: carga todo sin paginar |
| 2 | listado de alumnos página 0 (`/students`) | 13,9 ms | seq scan de `person` (dos veces) y `student` y sort top-N sobre 5.000 filas para devolver 25 |
| 3 | morosos paginado (`/financial-dashboard/debtors/page`) | 32,3 ms | subplans correlacionados por factura (9.000 loops sobre `invoice_line`, 32.000 buffers) más agrupación en memoria; escala con la cantidad de facturas vencidas |
| 4 | lista de espera (`/waitlists`) | 36,7 ms | seq scan de las 40.000 inscripciones + HashAggregate por comisión; el `having` filtra después de agregar todo |
| 5 | alumnos con búsqueda (`search=perf`) | 20,2 ms | mismo seq scan de `student`/`person`; `unaccent(lower())` y `LIKE '%x%'` no pueden usar índice |

Todos los planes: caché caliente (solo `shared hit`), sin sorts en disco. En frío o con más concurrencia los costos de lectura serían mayores.

## Lo que no pude medir

- `login` bajo carga: el endpoint tiene rate limit y `load.py` espera 8 s entre logins; solo medí el login de `measure.py` (una pasada).
- Valores exactos de los parámetros de Hibernate: EXPLAIN con literales a mano, no con los reales (`auto_explain` no está habilitado en el compose).
- Atribución de las filas 15, 17 y 18 (jobs de fondo), y no medí el costo de escritura (inscribir, tomar asistencia, pagos); `perf/measure.py` solo hace lecturas.
- Dashboard financiero con mes en curso `PENDING`: el seed solo tiene facturas `PAID` y `OVERDUE`.
- Concurrencia mayor a 20 hilos y duración mayor a ~1 min (k6 `load/read-hotspots.js` no se corrió; no hay k6 en la VM).
