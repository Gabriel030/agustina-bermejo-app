# Monitoreo mínimo

Todo en `deploy/monitoring/`, solo Docker. Hay dos niveles:

1. **Por defecto, siempre**: el servicio `uptime-check`. Liviano, un solo contenedor.
2. **Opcional, perfil `monitoring`**: Prometheus + Alertmanager + Grafana + node-exporter, ya provisionados.

```
docker compose -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml -f deploy/monitoring/docker-compose.monitoring.yml up -d uptime-check
docker compose ... --profile monitoring up -d
```

## 1. uptime-check (alerta de caída)

Consulta `UPTIME_URL` (default `http://backend:8080/actuator/health`; si el contexto público es `/api`, `UPTIME_URL` se puede apuntar a la URL pública para probar también el proxy) cada 30 s. Avisa **CAÍDA** cuando falla 3 veces seguidas y **RECUPERADO** cuando vuelve a responder. El health del backend no incluye el mail a propósito (ver `application.properties`), así que un problema de SMTP no genera falsas alarmas.

**Limitación que hay que conocer**: corre en la misma máquina que el sistema. Si cae la máquina entera, nadie avisa. Para eso hay dos mecanismos externos, ambos gratuitos y recomendados:

- `UPTIME_HEARTBEAT_URL`: en cada chequeo exitoso hace un ping a un servicio externo tipo healthchecks.io; si los pings dejan de llegar, el servicio avisa. Cubre "se cayó el servidor".
- Un monitor externo (UptimeRobot, Better Stack) apuntando a la URL pública. Cubre también red, DNS y certificados.

## 2. Perfil `monitoring`

| Servicio | Para qué | Notas |
|---|---|---|
| prometheus | Scrapea `/actuator/prometheus` cada 30 s con las credenciales `ACTUATOR_USER` / `ACTUATOR_PASSWORD` (las mismas del backend) y evalúa las reglas de `prometheus/alerts.yml` | Retención `PROMETHEUS_RETENTION` (30d) |
| alertmanager | Envía las alertas por webhook / Telegram | Se agrupan y repiten cada 4 h |
| node-exporter | Disco del host y lee las métricas del backup | Monta `/` del host solo lectura |
| grafana | Dashboard "Agustina Bermejo - Resumen" ya cargado | Solo en `127.0.0.1:3001` (`GRAFANA_BIND`); exponerlo públicamente requiere ponerlo detrás del proxy con HTTPS |

Reglas de alerta: caída del backend, latencia, errores 5xx (> 5 % durante 5 min), pool de conexiones al 90 %, disco libre < 15 %, backup con más de 13 h, última corrida de backup fallida y ausencia de métricas de backup.

Nota de latencia: en producción el backend no publica histogramas (`percentiles-histogram.all=false`, por cardinalidad), así que no se puede calcular un p95 real. La alerta `HighLatency` usa el promedio (> 1 s durante 10 min); `HighLatencyP95` queda lista y se activa sola si algún día se habilita el histograma de `http.server.requests` (como ya hace el perfil `perf`).

Nota de disco: se mide el sistema de archivos raíz del host, que es donde Docker guarda por defecto los volúmenes de Postgres y de backups. Si se usa un disco aparte, agregar su punto de montaje en `alerts.yml`. En Docker Desktop (Mac/Windows) mide la VM, no el equipo.

## Healthchecks de los servicios

Los de `backup`, `uptime-check`, `prometheus`, `alertmanager`, `node-exporter` y `grafana` ya vienen incluidos. Los de `postgres` y `backend` están en `docker-compose.prod.yml` (otro paquete) y deberían ser:

```yaml
postgres:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
    interval: 10s
    timeout: 5s
    retries: 5
backend:
  healthcheck:
    test: ["CMD-SHELL", "wget -q --spider http://localhost:8080/actuator/health/readiness || exit 1"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 60s
  depends_on:
    postgres:
      condition: service_healthy
```

(`/actuator/health/readiness` existe porque `management.endpoint.health.probes.enabled=true`. Si la imagen del backend no trae `wget`, usar `curl` o instalar uno.)

## Qué hacer ante una caída

1. Mirar el aviso: dice qué URL falló y desde cuándo.
2. `docker compose ps` para ver qué contenedor está `unhealthy` o parado, y `docker compose logs --tail 200 backend postgres`.
3. Causas habituales: disco lleno (`df -h`, ver alerta DiskAlmostFull), Postgres caído o sin conexiones (alerta DbPoolExhausted), memoria (`docker stats`), un release reciente (volver a la imagen anterior).
4. Reiniciar solo el servicio afectado: `docker compose restart backend`.
5. Si la base está corrupta o se perdieron datos: runbook de restauración en `backup-and-restore.md`.
6. Confirmar el aviso **RECUPERADO** y anotar la causa.

## Variables nuevas (para `deploy/.env.example`)

| Variable | Default | Descripción |
|---|---|---|
| `UPTIME_URL` | `http://backend:8080/actuator/health` | URL a chequear |
| `UPTIME_INTERVAL_SECONDS` | `30` | Cada cuánto |
| `UPTIME_FAILURES_BEFORE_ALERT` | `3` | Fallos seguidos antes de avisar |
| `UPTIME_TIMEOUT_SECONDS` | `5` | Timeout de cada chequeo |
| `UPTIME_HEARTBEAT_URL` | vacío | Ping externo en cada chequeo exitoso |
| `ACTUATOR_USER` / `ACTUATOR_PASSWORD` | vacío | Obligatorias con el perfil `monitoring` (ya existen para el backend) |
| `BACKEND_METRICS_TARGET` | `backend:8080` | host:puerto del backend para Prometheus |
| `PROMETHEUS_RETENTION` | `30d` | Retención de métricas |
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | `admin` / `admin` | **Cambiar la contraseña** |
| `GRAFANA_BIND` | `127.0.0.1:3001` | Dirección publicada de Grafana |
| `ALERT_*` | | Compartidas con backup, ver `backup-and-restore.md` |

## Qué se probó y qué no

`docker compose config` (solo y combinado) y la lógica de CAÍDA del uptime-check se ejecutaron. **No se levantó** el perfil `monitoring` completo (Prometheus/Alertmanager/Grafana) pero las 9 reglas sí pasan `promtool check rules`; no se probó la entrega real a Telegram o webhook: probar tras el primer despliegue con `docker compose --profile monitoring up -d` y `curl localhost:9090/api/v1/rules`.
