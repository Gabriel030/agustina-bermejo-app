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

3. Con el backend ya arrancado (el seed necesita las tablas y los datos demo), cargar el seed ampliado una sola vez:

```
docker exec -i perf-a0-pg psql -U postgres -d perf < perf/seed.sql
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
