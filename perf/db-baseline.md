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

Prerrequisitos: el esquema Flyway y los datos del perfil `demo` ya cargados (person id 1 con password, usuario `director.demo`, roles 1 y 2, programas 1 y 2, term 1, subjects 1..16, evaluation_scheme 1, institution 1, role ALUMNO). Sin eso el seed falla.

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

> Nota (33.3b, #1042): `GET /financial-dashboard/debtors` se retiró; las filas que lo mencionan son la medición histórica del baseline. El listado vigente es `/financial-dashboard/debtors/page`.

El resto de los endpoints de `measure.py` (dashboard financiero, morosos, listados, notificaciones, `/me/*`) están en `perf/out/latest.json` al correrlo. Resumen de la corrida base: `/financial-dashboard/debtors` p50 424 ms con 612 KB de respuesta (el peor); `/financial-dashboard` 19 ms; `/students` 22 ms; `/waitlists` 37 ms; `attendance-summary` 4 consultas por request (una corrida previa llegó a contar 508 por ruido de jobs de fondo durante la ventana de conteo); el resto por debajo de 25 ms.

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
| 1 | `/financial-dashboard/debtors` | 20 | 1084.8 | 54.24 | 90000 | `select distinct i1_0.id,i1_0.lines_total,i1_0.lines_total_currency,i1_` |
| 2 | `/financial-dashboard/debtors/page?page=0&size=25` | 20 | 441.1 | 22.06 | 500 | `with overdue as ( select i.id as invoice_id, i.currency, i.due_date, g` |
| 3 | `/students` | 40 | 421.2 | 10.53 | 1000 | `select s1_0.id,s1_0.emergency_contact_name,s1_0.emergency_contact_phon` |
| 4 | `/waitlists` | 20 | 340.8 | 17.04 | 6000 | `select ss1_0.id,(s1_0.name//', '//ss1_0.name),t1_0.description,t1_0.ye` |
| 5 | `/financial-dashboard/debtors` | 20 | 290.6 | 14.53 | 60000 | `select pe1_0.id,pe1_0.admission_application_id,pe1_0.completion_date,p` |
| 6 | `/students?page=0&size=25&search=perf` | 20 | 274.5 | 13.72 | 0 | `select s1_0.id,s1_0.emergency_contact_name,s1_0.emergency_contact_phon` |
| 7 | `/students` | 40 | 89.8 | 2.24 | 40 | `select count(s1_0.id) from student s1_0 where 1=1 and s1_0.onboarding_` |
| 8 | `/program-sections/14011/attendance/absence-report` | 80 | 76.9 | 0.96 | 8960 | `select a1_0.program_section_enrollment_id,count(a1_0.id),sum(case when` |
| 9 | `/program-sections/14011/attendance/absence-report` | 60 | 72.2 | 1.20 | 3180 | `select sse1_0.id,sse1_0.attempt_number,sse1_0.dropped_at,sse1_0.enroll` |
| 10 | `/financial-dashboard` | 20 | 65.1 | 3.26 | 60000 | `select distinct sca1_0.student_id from invoice i1_0,student_current_ac` |
| 11 | `/financial-dashboard` | 20 | 57.7 | 2.88 | 100800 | `select distinct pe1_0.student_id from program_enrollment pe1_0 where p` |
| 12 | `/financial-dashboard` | 20 | 40.8 | 2.04 | 20 | `select i.currency as currency, coalesce(sum(i.outstanding_amount), 0) ` |
| 13 | `/program-sections/14011/grade-grid` | 20 | 32.0 | 1.60 | 2240 | `select sse1_0.id,sse1_0.attempt_number,sse1_0.dropped_at,sse1_0.enroll` |
| 14 | `/staff` | 20 | 27.3 | 1.36 | 1220 | `select p1_0.id,p1_0.address_id,p1_0.auth_provider,p1_0.avatar_path,p1_` |
| 15 | `/me/notifications/unread-count` | 20 | 13.8 | 0.69 | 20 | `select count(n1_0.id) from notification n1_0 where n1_0.recipient_id=$` |
| 16 | `/program-sections/14011/grade-grid` | 20 | 11.5 | 0.58 | 4480 | `select gi1_0.id,gi1_0.comments,gi1_0.course_section_enrollment_id,gi1_` |
| 17 | `/students/20001/billing-overview` | 60 | 11.3 | 0.19 | 360 | `select distinct i1_0.id,i1_0.lines_total,i1_0.lines_total_currency,i1_` |
| 18 | `/me/section-enrollments` | 20 | 9.4 | 0.47 | 160 | `select distinct sse1_0.id,sse1_0.attempt_number,sse1_0.dropped_at,sse1` |
| 19 | `/program-enrollments/20001/section-enrollments` | 20 | 9.0 | 0.45 | 160 | `select distinct sse1_0.id,sse1_0.attempt_number,sse1_0.dropped_at,sse1` |
| 20 | `/program-sections/14011/attendance/absence-report` | 881 | 7.0 | 0.01 | 0 | `BEGIN READ ONLY` |

Atribución: es por delta de `pg_stat_statements` mientras corre cada endpoint. `top-queries.sh` excluye los `EXPLAIN` propios. Las consultas de inscripciones con 0 filas e `insert into subject_section_enrollment` (si aparecen) no son confiables: un GET de lectura no inserta inscripciones, así que casi seguro son jobs programados del backend corriendo durante la medición. No lo investigué.

## EXPLAIN (ANALYZE, BUFFERS) de las 5 peores

Salida completa en [`perf/explain-top5.txt`](explain-top5.txt) (se regenera con `docker exec -i perf-a0-pg psql -U postgres -d perf < perf/explain-top5.sql > perf/explain-top5.txt`). Los `$n` se reemplazaron por literales escritos a mano: es una aproximación, no el plan exacto que ve Hibernate. Abajo, el extracto de cada plan (nodos, sorts, buffers y tiempos) y su diagnóstico.

| # | Consulta | Tiempo | Diagnóstico (una línea) |
|---|---|---|---|
| 1 | facturas `OVERDUE` con líneas y descuentos (`/financial-dashboard/debtors`) | 31,2 ms (54,2 ms mean en la app) | seq scan de `invoice` e `invoice_line` completos y `distinct` + sort sobre 4.500 facturas / 90.000 filas por request: sin paginar |
| 2 | morosos paginado (`/financial-dashboard/debtors/page`) | 27,6 ms | subplans correlacionados por factura (9.000 loops sobre `invoice_line`, ~32.000 buffers): N+1 dentro del SQL |
| 3 | listado de alumnos página 0 (`/students`) | 14,4 ms (10,5 ms mean) | seq scan de `person` (x2) y `student` con sort top-N sobre 5.000 filas para devolver 25 |
| 4 | lista de espera (`/waitlists`) | 22,8 ms | seq scan de las 40.000 inscripciones + HashAggregate por comisión; el `having` filtra después de agregar |
| 5 | `program_enrollment` por `student_id IN (3.000 ids)` (`/financial-dashboard/debtors`) | 2,4 ms en DB vs 14,5 ms mean en la app | el costo no está en el plan (hash join sobre seq scan de 5.040 filas) sino en parsear/planificar una sentencia con 3.000 parámetros por llamada |

```
=== Q1 invoices OVERDUE con lineas (debtors)
   Buffers: shared hit=2483
   ->  Incremental Sort  (cost=3813.61..4585.10 rows=4500 width=1593) (actual time=16.190..26.168 rows=4500 loops=1)
         Full-sort Groups: 141  Sort Method: quicksort  Average Memory: 38kB  Peak Memory: 38kB
         Buffers: shared hit=2483
         ->  Nested Loop Left Join  (cost=3813.48..4382.60 rows=4500 width=1593) (actual time=15.975..22.023 rows=4500 loops=1)
               Buffers: shared hit=2483
               ->  Gather Merge  (cost=3813.48..4326.35 rows=4500 width=965) (actual time=15.965..19.804 rows=4500 loops=1)
                     Buffers: shared hit=2483
                     ->  Sort  (cost=2813.47..2820.09 rows=2647 width=965) (actual time=13.737..13.842 rows=2250 loops=2)
                           Sort Method: quicksort  Memory: 1178kB
                           Buffers: shared hit=2483
                           ->  Hash Join  (cost=1287.00..2662.98 rows=2647 width=965) (actual time=9.101..11.232 rows=2250 loops=2)
                                 Hash Cond: (i.id = l.invoice_id)
                                 Buffers: shared hit=2394
                                 ->  Parallel Seq Scan on invoice i  (cost=0.00..1339.59 rows=2647 width=800) (actual time=1.518..2.474 rows=2250 loop
                                       Buffers: shared hit=1119
                                 ->  Hash  (cost=912.00..912.00 rows=30000 width=165) (actual time=7.384..7.384 rows=30000 loops=2)
                                       Buffers: shared hit=1224
                                       ->  Seq Scan on invoice_line l  (cost=0.00..912.00 rows=30000 width=165) (actual time=0.007..2.169 rows=30000 l
                                             Buffers: shared hit=1224
               ->  Seq Scan on invoice_discount d  (cost=0.00..0.00 rows=1 width=628) (actual time=0.000..0.000 rows=0 loops=4500)
   Buffers: shared hit=871

=== Q2 students page 0 size 25
   Buffers: shared hit=994
   ->  Sort  (cost=1774.58..1778.02 rows=1376 width=4869) (actual time=14.050..14.056 rows=25 loops=1)
         Sort Method: top-N heapsort  Memory: 45kB
         Buffers: shared hit=994
         ->  Hash Join  (cost=1205.87..1735.75 rows=1376 width=4869) (actual time=8.372..10.772 rows=5044 loops=1)
               Hash Cond: (p2.id = p3.id)
               Buffers: shared hit=994
               ->  Seq Scan on person p2  (cost=0.00..484.45 rows=8445 width=2331) (actual time=0.005..1.081 rows=5106 loops=1)
                     Buffers: shared hit=400
               ->  Hash  (cost=1188.67..1188.67 rows=1376 width=2554) (actual time=8.351..8.355 rows=5044 loops=1)
                     Buffers: shared hit=594
                     ->  Hash Right Join  (cost=658.79..1188.67 rows=1376 width=2554) (actual time=5.234..6.843 rows=5044 loops=1)
                           Hash Cond: (p1.id = s.person_id)
                           Buffers: shared hit=594
                           ->  Seq Scan on person p1  (cost=0.00..484.45 rows=8445 width=30) (actual time=0.004..0.694 rows=5106 loops=1)
                                 Buffers: shared hit=400
                           ->  Hash  (cost=641.59..641.59 rows=1376 width=2532) (actual time=5.217..5.220 rows=5044 loops=1)
                                 Buffers: shared hit=194
                                 ->  Hash Semi Join  (cost=359.34..641.59 rows=1376 width=2532) (actual time=2.727..4.129 rows=5044 loops=1)
                                       Hash Cond: (s.person_id = p3.id)
                                       Buffers: shared hit=194
                                       ->  Seq Scan on student s  (cost=0.00..245.09 rows=8327 width=2516) (actual time=0.005..0.731 rows=5044 loops=1

=== Q3 debtors page
   Buffers: shared hit=32626
   ->  Sort  (cost=7157.59..7168.84 rows=4500 width=119) (actual time=27.241..27.250 rows=25 loops=1)
         Sort Method: top-N heapsort  Memory: 29kB
         Buffers: shared hit=32626
         ->  WindowAgg  (cost=6401.99..7030.61 rows=4500 width=119) (actual time=26.276..26.655 rows=3000 loops=1)
               Buffers: shared hit=32626
               ->  Hash Join  (cost=6401.99..6963.11 rows=4500 width=111) (actual time=24.337..25.393 rows=3000 loops=1)
                     Hash Cond: (per.id = s.person_id)
                     Buffers: shared hit=32626
                     ->  Seq Scan on person per  (cost=0.00..484.45 rows=8445 width=39) (actual time=0.004..0.569 rows=5106 loops=1)
                           Buffers: shared hit=400
                     ->  Hash  (cost=6345.74..6345.74 rows=4500 width=88) (actual time=24.204..24.212 rows=3000 loops=1)
                           Buffers: shared hit=32226
                           ->  Hash Join  (cost=6142.66..6345.74 rows=4500 width=88) (actual time=20.650..23.634 rows=3000 loops=1)
                                 Hash Cond: ((COALESCE(ce.student_id, pe.student_id)) = s.id)
                                 Buffers: shared hit=32226
                                 ->  GroupAggregate  (cost=5814.31..5960.56 rows=4500 width=80) (actual time=19.447..21.910 rows=3000 loops=1)
                                       Buffers: shared hit=32085
                                       ->  Sort  (cost=5814.31..5825.56 rows=4500 width=113) (actual time=19.430..19.615 rows=4500 loops=1)
                                             Sort Method: quicksort  Memory: 638kB
                                             Buffers: shared hit=32085
                                             ->  Hash Left Join  (cost=1802.40..5541.25 rows=4500 width=113) (actual time=7.392..18.210 rows=4500 loop

=== Q4 waitlists
 HashAggregate  (cost=4704.62..5240.12 rows=12600 width=131) (actual time=22.301..22.446 rows=300 loops=1)
   Buffers: shared hit=1709
   ->  Hash Right Join  (cost=38.95..3288.58 rows=66637 width=92) (actual time=0.370..11.134 rows=40212 loops=1)
         Hash Cond: (sse.program_section_id = ss.id)
         Buffers: shared hit=1709
         ->  Seq Scan on subject_section_enrollment sse  (cost=0.00..2333.37 rows=66637 width=17) (actual time=0.001..3.169 rows=40117 loops=1)
               Buffers: shared hit=1667
         ->  Hash  (cost=34.45..34.45 rows=360 width=83) (actual time=0.360..0.363 rows=416 loops=1)
               Buffers: shared hit=42
               ->  Nested Loop  (cost=5.36..34.45 rows=360 width=83) (actual time=0.048..0.283 rows=416 loops=1)
                     Buffers: shared hit=42
                     ->  Seq Scan on term t  (cost=0.00..1.01 rows=1 width=38) (actual time=0.005..0.005 rows=2 loops=1)
                           Buffers: shared hit=1
                     ->  Hash Join  (cost=5.36..28.94 rows=360 width=61) (actual time=0.021..0.113 rows=416 loops=2)
                           Hash Cond: (ss.subject_id = s.id)
                           Buffers: shared hit=41
                           ->  Seq Scan on subject_section ss  (cost=0.00..22.60 rows=360 width=45) (actual time=0.002..0.051 rows=416 loops=2)
                                 Buffers: shared hit=38
                           ->  Hash  (cost=4.05..4.05 rows=105 width=32) (actual time=0.031..0.033 rows=116 loops=1)
                                 Buffers: shared hit=3
                                 ->  Seq Scan on subject s  (cost=0.00..4.05 rows=105 width=32) (actual time=0.003..0.013 rows=116 loops=1)
                                       Buffers: shared hit=3

=== Q5 program_enrollment por student_id IN (3000 ids) (debtors)
 Hash Join  (cost=42.00..351.23 rows=3000 width=617) (actual time=1.222..2.175 rows=3000 loops=1)
   Hash Cond: (pe.student_id = g.g)
   Buffers: shared hit=171
   ->  Seq Scan on program_enrollment pe  (cost=0.00..254.05 rows=8305 width=617) (actual time=0.041..0.529 rows=5040 loops=1)
         Buffers: shared hit=171
   ->  Hash  (cost=39.50..39.50 rows=200 width=4) (actual time=1.123..1.123 rows=3000 loops=1)
         ->  HashAggregate  (cost=37.50..39.50 rows=200 width=4) (actual time=0.691..0.861 rows=3000 loops=1)
               ->  Function Scan on generate_series g  (cost=0.00..30.00 rows=3000 width=4) (actual time=0.125..0.236 rows=3000 loops=1)
   Buffers: shared hit=71
 Planning Time: 0.279 ms
 Execution Time: 2.367 ms
```

Todos los planes: caché caliente (solo `shared hit`), sin sorts en disco. En frío o con más concurrencia los costos de lectura serían mayores. La consulta de búsqueda de alumnos (`search=perf`) quedó fuera del top 5 al excluir los EXPLAIN de la medición: tiene el mismo seq scan que la 3.

## Lo que no pude medir

- `login` bajo carga: el endpoint tiene rate limit y `load.py` espera 8 s entre logins; solo medí el login de `measure.py` (una pasada).
- Valores exactos de los parámetros de Hibernate: EXPLAIN con literales a mano, no con los reales (`auto_explain` no está habilitado en el compose).
- Atribución de consultas de inscripciones atribuidas a jobs de fondo, y no medí el costo de escritura (inscribir, tomar asistencia, pagos); `perf/measure.py` solo hace lecturas.
- Dashboard financiero con mes en curso `PENDING`: el seed solo tiene facturas `PAID` y `OVERDUE`.
- Concurrencia mayor a 20 hilos y duración mayor a ~1 min (k6 `load/read-hotspots.js` no se corrió; no hay k6 en la VM).
