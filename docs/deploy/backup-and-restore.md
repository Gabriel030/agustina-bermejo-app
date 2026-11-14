# Backups y restauración de Postgres

Todo vive en `deploy/backup/` y es solo Docker (sin depender del hosting). Se suma al compose de producción con `-f`:

```
docker compose -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml up -d backup
```

El servicio `backup` se conecta al servicio `postgres` del otro compose (variable `BACKUP_PGHOST` si se llama distinto).

## Qué hace

- **Cada 6 horas** (`BACKUP_SCHEDULE`, cron, zona `BACKUP_TZ`) corre `pg_dump -Fc`. Escribe primero un `.partial` y recién al terminar bien lo renombra: nunca queda un dump a medias con nombre válido.
- Por cada dump genera un `.sha256` (checksum) y un `.meta` con la cantidad de migraciones Flyway y el conteo de filas de cada tabla al momento del backup (lo usa la prueba de restauración).
- **Un backup diario no es un proceso aparte**: la rotación se queda con el último backup de cada día.
- **Rotación** (`BACKUP_KEEP_*`): se conservan, sumados sin repetir, los últimos 4 backups (`KEEP_RECENT`), el último de cada uno de los 7 días (`KEEP_DAILY`), de cada una de las 4 semanas (`KEEP_WEEKLY`) y de cada uno de los 6 meses (`KEEP_MONTHLY`). Se probó con 664 backups simulados: quedan 17.
- **Destino**: volumen Docker `backup_data` por defecto, o una carpeta del host con `BACKUP_DIR=/ruta/absoluta`. Opcional: copia a S3 compatible (AWS, Backblaze B2, Cloudflare R2, MinIO) si se define `BACKUP_S3_BUCKET`; la rotación también se aplica allá. Las credenciales entran solo por variables de entorno, nunca al repo.
- **Cifrado opcional** con `age`: definir `BACKUP_AGE_RECIPIENT` con la clave pública (`age1...`). El dump queda como `.dump.age`. Generar el par con `age-keygen -o clave.txt`; **la clave privada se guarda fuera del servidor** (gestor de contraseñas y copia en papel). Sin ella los backups cifrados no se pueden restaurar.
- **Avisos**: si el backup, el cifrado, la subida a S3 o el checksum fallan, avisa por webhook y/o Telegram (`ALERT_WEBHOOK_URL`, `ALERT_TELEGRAM_BOT_TOKEN` + `ALERT_TELEGRAM_CHAT_ID`). Una vez por día (`BACKUP_FRESHNESS_SCHEDULE`) revisa además que el último backup tenga menos de `BACKUP_MAX_AGE_HOURS` (13) horas, para detectar que el cron mismo dejó de correr. Si se define `BACKUP_HEARTBEAT_URL` (por ejemplo un check de healthchecks.io), se le hace un ping después de cada backup exitoso: si el servidor entero muere, el servicio externo avisa por ausencia.
- Escribe métricas en el volumen `backup_metrics` (`backup_last_success_timestamp_seconds`, etc.) que lee el perfil `monitoring` (ver `monitoring.md`).

## Backup manual

```
docker compose -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml exec backup backup.sh
docker compose ... exec backup ls -la /backups
```

## Runbook: restaurar paso a paso

Cuándo: pérdida o corrupción de datos, o migración a otro servidor.

1. **Frenar el backend** para que no escriba durante la restauración: `docker compose -f docker-compose.prod.yml stop backend`.
2. **Elegir el dump**: `docker compose ... exec backup ls -la /backups`. Si es de S3, bajarlo primero con `rclone` o la consola del proveedor y dejarlo en `/backups` junto con su `.sha256`.
3. **(Recomendado) Guardar el estado actual** antes de pisarlo: `exec backup backup.sh`.
4. **Restaurar**:
   ```
   docker compose ... exec backup restore.sh /backups/abdb_YYYYMMDDTHHMMSSZ.dump
   ```
   Pide escribir el nombre de la base para confirmar y **borra y recrea** la base destino (`--db NOMBRE` para otra; `--yes` saltea la confirmación). Verifica el checksum antes de tocar nada: si no coincide, aborta. Para un dump `.age`, montar la clave privada y definir `AGE_IDENTITY_FILE` (por ejemplo `docker compose run --rm -v $PWD/clave.txt:/clave.txt:ro -e AGE_IDENTITY_FILE=/clave.txt backup restore.sh /backups/....dump.age`).
5. **Levantar el backend**: `docker compose ... start backend`. Flyway valida el historial al arrancar; si el dump es de una versión anterior, aplica las migraciones pendientes.
6. **Comprobar**: `curl -f http://localhost:8080/actuator/health`, login con un usuario real y revisar que los datos recientes estén.

El restore funciona desde cualquier máquina con Docker usando la imagen del servicio `backup` (`docker build deploy/backup`), no hace falta el servidor original.

## Prueba automática de restauración

```
deploy/backup/verify-restore.sh --volume <volumen_backup_data>      # o --dir /ruta/a/los/dumps
```

Hace, con un Postgres 16 descartable que se destruye al terminar: construye la imagen de herramientas, toma el **último** dump, lo restaura con el mismo `restore.sh` del runbook (incluye checksum y descifrado), compara la cantidad de migraciones restauradas contra el `.meta` y contra los `V*.sql` del repositorio, compara el conteo de filas de **todas** las tablas contra el `.meta` (tolerancia `VERIFY_TOLERANCE_PCT`, 5 %), y corre `flyway validate`. Devuelve código distinto de cero si algo falla. `--skip-flyway` omite el último paso (evita bajar la imagen de Flyway), `--migrations` cambia la carpeta de migraciones. Si los dumps están cifrados, exportar `AGE_IDENTITY_FILE`.

**Cada cuánto probarlo**: una vez por semana (cron del host o tarea programada) y siempre después de (a) cambiar versión de Postgres, (b) tocar los scripts de backup, (c) un release con migraciones. Un backup que nunca se restauró no es un backup.

## RPO / RTO esperados

| | Valor | Por qué |
|---|---|---|
| RPO (datos que se pueden perder) | hasta 6 h | Frecuencia del backup. Bajarlo es cambiar `BACKUP_SCHEDULE`; por debajo de 1 h conviene archivado WAL (no incluido). |
| RTO (tiempo hasta volver) | 30 a 60 min | Restaurar un dump de ~700 KB tomó 10 s; con datos reales escala con el tamaño. El resto es detectar la falla, bajar el dump y levantar los servicios. No incluye reprovisionar un servidor nuevo. |
| Pérdida del servidor entero | RPO 6 h solo si hay copia en S3 | Sin `BACKUP_S3_BUCKET` los backups viven en el mismo disco que la base: **configurar S3 en producción**. |

## Variables nuevas (para `deploy/.env.example`)

| Variable | Default | Descripción |
|---|---|---|
| `BACKUP_SCHEDULE` | `0 */6 * * *` | Cron del backup |
| `BACKUP_TZ` | `America/Argentina/Buenos_Aires` | Zona horaria del cron |
| `BACKUP_DIR` | `backup_data` (volumen) | Ruta absoluta del host para usar una carpeta en vez de volumen |
| `BACKUP_PGHOST` / `BACKUP_PGPORT` | `postgres` / `5432` | Dónde está Postgres |
| `BACKUP_RUN_ON_START` | `false` | Hacer un backup al arrancar el contenedor |
| `BACKUP_KEEP_RECENT` / `_DAILY` / `_WEEKLY` / `_MONTHLY` | `4` / `7` / `4` / `6` | Retención |
| `BACKUP_FRESHNESS_SCHEDULE` | `30 9 * * *` | Cuándo revisar que haya un backup reciente |
| `BACKUP_MAX_AGE_HOURS` | `13` | Antigüedad máxima aceptable |
| `BACKUP_HEARTBEAT_URL` | vacío | URL a la que se hace ping tras cada backup OK (dead man's switch externo) |
| `BACKUP_AGE_RECIPIENT` | vacío | Clave pública `age` para cifrar |
| `BACKUP_S3_BUCKET` | vacío | Activa la copia a S3 |
| `BACKUP_S3_PREFIX` | `agustina-bermejo` | Carpeta dentro del bucket |
| `BACKUP_S3_ENDPOINT` | vacío | Endpoint del proveedor (vacío en AWS) |
| `BACKUP_S3_REGION` | vacío | Región |
| `BACKUP_S3_PROVIDER` | `Other` | `AWS`, `Backblaze`, `Cloudflare`, `Minio`, `Other` |
| `BACKUP_S3_ACCESS_KEY_ID` / `BACKUP_S3_SECRET_ACCESS_KEY` | vacío | Credenciales (secretas) |
| `ALERT_LABEL` | `agustina-bermejo` | Prefijo de los avisos |
| `ALERT_WEBHOOK_URL` | vacío | Webhook que recibe `{"text":..., "content":...}` (Slack, Discord, ntfy, etc.) |
| `ALERT_TELEGRAM_BOT_TOKEN` / `ALERT_TELEGRAM_CHAT_ID` | vacío | Aviso por Telegram |

`POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_DB` se reutilizan de las variables que ya existen. Aviso por email: no incluido; se resuelve con un webhook (por ejemplo ntfy o un puente a email).

## Qué se probó y qué no

Ver el cuerpo del PR: restauración real contra un Postgres 16 con las 207 migraciones del repo, caso de dump corrupto, rotación y alerta de caída. No probados contra servicios reales: subida a S3, cifrado `age` de punta a punta, entrega a Telegram/webhook.
