# Medicion de rendimiento (25-A P0)

Los presupuestos de `perf/budget.json` son relativos a la medicion base A0 por 1,5 de holgura, no un SLA. Antes de medir correr `uptime` y anotar el load average junto al resultado: con carga ajena los absolutos son pesimistas.

## Reproducir

1. Postgres con `pg_stat_statements` (contenedor `perf-a0-pg`, base `perf`, puerto 5461, sin tocar el 5433 del entorno demo):

```
docker compose -f perf/docker-compose.perf.yml up -d
```

2. Backend con el perfil `perf` (`local,demo` crea el esquema por Flyway y los usuarios `*.demo`). Desde `backend/`:

```
ACTUATOR_USER=perf ACTUATOR_PASSWORD=perf JWT_SECRET="dev-secret-local-solo-para-pruebas" SPRING_PROFILES_ACTIVE=local,demo,perf SERVER_PORT=8461 DB_URL=jdbc:postgresql://localhost:5461/perf ./mvnw -q spring-boot:run
```

3. Con el backend ya arrancado (el seed necesita las tablas y los datos demo), cargar el seed grande (re-ejecutable: limpia y recarga):

```
docker exec -i perf-a0-pg psql -U postgres -d perf < perf/seed-large.sql
```

4. Medir y verificar presupuestos desde la raiz del repo:

```
python3 perf/measure.py && python3 perf/check_budget.py
```

`perf/measure.py` imprime una linea JSON por endpoint (con su presupuesto si existe) y escribe `perf/out/latest.json` con `p50`, `p95`, `p99`, `queries` y `bytes` por endpoint. `check_budget.py` sale con codigo 1 si algun endpoint supera su presupuesto o no se midio. Variables: `PERF_BASE`, `PERF_RUNS`, `PERF_ONLY`, `PERF_PG`, `PERF_BUDGET`, `PERF_LATEST`.

## Pool Hikari y percentiles

El actuator exige credenciales (sin `ACTUATOR_USER` y `ACTUATOR_PASSWORD` al arrancar responde 401), por eso el paso 2 las define.

```
curl -s -u perf:perf localhost:8461/actuator/metrics/hikaricp.connections.pending
curl -s -u perf:perf localhost:8461/actuator/metrics/hikaricp.connections.active
curl -s -u perf:perf localhost:8461/actuator/prometheus | grep http_server_requests_seconds_bucket
```

Con el histograma activo, Prometheus exporta buckets de `http_server_requests_seconds`: p50, p95 y p99 se calculan con `histogram_quantile` sobre esos buckets; no aparecen como series `quantile` ni en `/actuator/metrics`.

## Consultas lentas

```
docker exec perf-a0-pg psql -U postgres -d perf -c "select calls, round(mean_exec_time::numeric,2) as mean_ms, left(query,100) from pg_stat_statements order by total_exec_time desc limit 20"
```

## Carga concurrente

```
k6 run load/read-hotspots.js
LOAD_DIRECTOR_VUS=20 LOAD_DURATION=2m BASE_URL=http://localhost:8461 k6 run load/read-hotspots.js
```

`perf/load.py` mide concurrencia con hilos Python y pool activo; `load/q15a.js` es la prueba de carga de Q15a.

## Seed grande (33.1)

`perf/seed-large.sql` es el unico seed de rendimiento (`seed.sql` se retiro en E29-F5) y exige el backend `local,demo,perf` ya arrancado: depende de person id 1, `director.demo`, roles 1/2, programas 1/2, term 1, subjects 1..16 y evaluation_scheme 1 del perfil demo. Es re-ejecutable. Resultados y método en `perf/db-baseline.md`; con ese seed correr `PERF_TEACHER=perft12` antes de `measure.py`/`load.py`.

Vigilancia: `PerfSeedIT` (`@NightlyTest`, `backend/src/test/java/com/red_escuela/backend/demo/`) siembra el dataset demo `full`, carga `seed-large.sql` dos veces sobre el esquema Flyway actual y verifica los volumenes. Si una migracion cambia una tabla que toca el seed, el nightly falla.

## Presupuestos por pantalla (Epica 31)

Las claves `screen:/<ruta>` de `perf/budget.json` fijan, por pantalla del Director, el maximo de peticiones y de duplicadas. Los valores son provisorios (lo medido mas 3 de holgura, cero duplicadas). `PERF_SCOPE=screen python3 perf/check_budget.py` los compara con `perf/out/screens.json`, que escribe `frontend/src/test/flows/directorScreenRequests.integration.test.jsx` cuando existe `PERF_SCREENS_OUT`. Corre en el job `frontend-screen-budget` de `ci.yml`: en paralelo con los tests del frontend, solo si cambia el frontend, sin sumar tiempo al camino critico y sin backend ni base. Sin `PERF_SCOPE` se verifican solo los presupuestos por endpoint (requieren backend y `perf/measure.py`). Los niveles de cascada dependen de la latencia real: se miden con `perf/e31`.

## Seed de carga (E25 A5-S1)

`perf/seed-load.py` deja listos los datos de los escenarios k6 de carga (S3 inscripcion masiva, S4 notas de fin de cuatrimestre, S5 corrida mensual de facturacion). Necesita el backend `local,demo,perf` ya arrancado y el demo completo sembrado (ver "Reproducir"); no usa los datos de `seed-large.sql` y usa rangos de ids propios, pero `seed-large.sql` borra `subject_section_enrollment` con id >= 100000 e `invoice` con id >= 500000, lo que incluye las inscripciones (desde 2000000) y facturas sembradas aqui. Orden: primero `seed-large.sql`, despues `seed-load.py`; si se recarga `seed-large.sql`, volver a correr `seed-load.py`. Requiere Docker local: el SQL se ejecuta con `docker exec` en el contenedor `PERF_PG` (`perf-a0-pg`). Se niega a correr si `PERF_BASE` no es `localhost`/`127.0.0.1`, salvo `LOAD_CONFIRM=1`. Valida `LOAD_STUDENTS + LOAD_GRADED_STUDENTS <= 9999`, `LOAD_GRADE_SECTIONS >= 3` y `LOAD_TEACHERS <= 999`. Es re-ejecutable: limpia y recarga solo sus rangos (personas 400001..499999 y 410001..410999, comisiones desde 1400000, inscripciones desde 2000000).

```
python3 perf/seed-load.py
LOAD_STUDENTS=5000 LOAD_GRADED_STUDENTS=1000 LOAD_ENROLL_SECTIONS=100 LOAD_GRADE_SECTIONS=40 python3 perf/seed-load.py
```

Lo que crea (por la API, para que pase las validaciones reales, y por SQL para el volumen):

- Ventana de inscripcion abierta: `POST /terms/{LOAD_TERM_ID}/enrollment/open` sobre el ciclo demo (id 1).
- Esquema de evaluacion "Esquema carga perf: 2 parciales y final" (2 parciales, 3 TP, final 50 %), usado por todas las comisiones sembradas.
- Mesas de examen: `LOAD_EXAM_BOARDS` (8) con aula `LOAD-n`, inscripcion abierta, en dias habiles; y las inscripciones (`REGISTERED`) de los alumnos del grupo de notas.
- Plantilla de facturacion del programa (`LOAD_PROGRAM_ID`, 1) con 10 cuotas, cohorte FIRST, plan de pago MENSUAL y todas las inscripciones a carrera apuntando a plantilla, cohorte y plan, con cuenta corriente en cero.
- Grupo de inscripcion (`LOAD_STUDENTS`, 2000): usuarios `loads1..N`, sin comisiones, para S3. Comisiones de inscripcion (`LOAD_ENROLL_SECTIONS`, 40) con cupo `LOAD_CAPACITY` (60).
- Grupo de notas (`LOAD_GRADED_STUDENTS`, 500): usuarios `loads(N+1)..`, cada uno inscripto (ENROLLED) en 3 comisiones de notas (`LOAD_GRADE_SECTIONS`, 20), sin notas, para S4. Docentes `loadt1..loadt{LOAD_TEACHERS}` (10), LEAD de las comisiones en reparto circular. Clave de todos: la del perfil demo (`Test1234`).

La limpieza usa `session_replication_role = replica` y borra explicitamente, por pertenencia a los rangos (alumnos 400001..499999, comisiones 1400000..1499999, inscripciones 2000000..2999999), lo que crean los escenarios k6: inscripciones, asistencias, notas, inscripciones a mesas, facturas, lineas, pagos, asientos de cuenta y notas de credito. Al final avanza las secuencias de las tablas donde inserta ids explicitos.

Para S5 correr la simulacion o la corrida con un periodo posterior al mes de alta de los precios demo (los precios nacen vigentes desde el dia en que se creo la base; el mes en curso falla con `NoEffectivePriceException`). Verificado: `POST /monthly-invoice-runs/simulations {"period":"<mes siguiente>"}` emite las 400 inscripciones con 300+100 alumnos.

El script reintenta ante 429 (el limite de 120 escrituras por minuto es por usuario y el arranque `demo` completo lo consume con administrativo.demo; por eso las mesas se crean con preceptor.demo).

## Perfil perf para escenarios de carga (E25 A5-S2)

`application-perf.properties` (solo se activa con `SPRING_PROFILES_ACTIVE=...,perf`) relaja lo necesario para que los escenarios k6 no choquen con el rate limit y midan billing real:

| Propiedad | Perf | Produccion |
|---|---|---|
| `auth.rate-limit.login.capacity` / `refill-per-minute` | 1000000 | 10 |
| `app.rate-limit.user-write.capacity` / `refill-per-minute` | 1000000 | 120 |
| `app.rate-limit.user-read.capacity` / `refill-per-minute` | 1000000 | 600 |
| `platform.provisioning.initial-billing-mode` | FULL | OFF |

No hay propiedades nuevas ni cambios de codigo: son las que ya lee el filtro. `PerfProfileGuardTest` falla si `application.properties` cambia estos valores de produccion, si `application-prod`/`application-staging` definen alguna de estas claves o si cualquier `spring.profiles.*` de esos archivos incluye `perf`. Nunca activar `perf` en staging/prod (`STAGING_SPRING_PROFILES` debe quedar en `prod`).

Arranque (misma base que "Reproducir"; correr `seed-large.sql` y luego `seed-load.py`):

```
cd backend && ACTUATOR_USER=perf ACTUATOR_PASSWORD=perf JWT_SECRET="dev-secret-local-solo-para-pruebas" SPRING_PROFILES_ACTIVE=local,demo,perf SERVER_PORT=8461 DB_URL=jdbc:postgresql://localhost:5461/perf ./mvnw -q spring-boot:run
python3 perf/seed-load.py
```

Medir un escenario (los scripts k6 llegan en S3-S5; el contrato es `BASE_URL` apuntando al backend perf):

```
k6 run -e BASE_URL=http://localhost:8461 perf/k6/<escenario>.js
```

En S5 usar un periodo posterior al mes en curso (ver arriba, `NoEffectivePriceException`).

### Alcance y limites del perfil perf

- Con 1000000 el perfil perf mide throughput y latencia del servidor, no el limite por usuario: no puede detectar que un docente choque con las 120 escrituras/min. Para medir ese flujo con los limites de produccion arrancar sin `perf` (por ejemplo `local,demo`: login 200, escritura 120, lectura 600) o pisar las claves por entorno (`RATE_LIMIT_USER_WRITE_PER_MINUTE`, `RATE_LIMIT_USER_READ_PER_MINUTE`). Si un flujo real choca con el limite es un bug de producto, no algo para esconder con el perfil.
- Los scripts S3-S5 deben contar y reportar los 429, no reintentar a ciegas. `seed-load.py` si reintenta ante 429 (devuelve `RATE_LIMITED` y reintenta), porque es preparacion y no medicion.
- El perfil perf no relaja y pueden dar 429 en un escenario: refresh (30/min), register-user (5), forgot-password (5), register (20), reset-password (10), prospects (10), staff-prospects (30).
- `initial-billing-mode=FULL` solo se aplica en una base nueva. `InitialBillingModeProvisioner` cambia el modo solo si la feature BILLING nunca cambio (`version = 0` y sin registro en el log de cambios) y el modo difiere; es idempotente (si ya cambio o ya esta en ese modo no hace nada). En una base que ya arranco con OFF se ignora en silencio y S5 mediria con billing apagado. Verificar antes de medir:

```
docker exec perf-a0-pg psql -U postgres -d perf -tc "select mode from platform_feature where code = 'BILLING'"
```

Debe devolver `FULL`; si devuelve `OFF`, recrear la base o cambiar el modo por la API de plataforma.

## Escenario k6: inscripcion masiva (E25 A5-S3)

`perf/scenarios/mass-enrollment.js` mide el throughput de `POST /me/section-enrollments` con el seed de A5-S1: cada alumno `loads1..N` (id de inscripcion a carrera `400000+N`) inicia sesion y se inscribe en una comision de inscripcion, repartidas en ronda sobre las `LOAD_ENROLL_SECTIONS` (cupo 60, por eso `LOAD_STUDENTS <= LOAD_ENROLL_SECTIONS * LOAD_CAPACITY`). Es solo de throughput: corre con el perfil `perf`, que sube los limites de escritura por usuario, y NO mide el limite de 120 escrituras por minuto (eso se mide sin `perf`, contando 429).

```
python3 perf/seed-load.py
k6 run perf/scenarios/mass-enrollment.js
LOAD_STUDENTS=2000 LOAD_VUS=100 BASE_URL=http://localhost:8461 k6 run perf/scenarios/mass-enrollment.js
```

Variables: `BASE_URL`, `LOAD_STUDENTS`, `LOAD_ENROLL_SECTIONS` (iguales a las del seed), `LOAD_VUS` (50), `LOAD_MAX_DURATION` (10m), `LOAD_PASSWORD`. Umbrales: `THRESHOLD_ENROLL_P95_MS` (800), `THRESHOLD_ENROLL_P99_MS` (2000), `THRESHOLD_LOGIN_P95_MS` (1000), `THRESHOLD_ERROR_RATE` (0,01) y `THRESHOLD_MIN_ENROLLMENTS_PER_SECOND` (20). Los umbrales son provisorios: no hay medicion base todavia. Para repetir la corrida hay que volver a correr `seed-load.py` (limpia las inscripciones creadas).
