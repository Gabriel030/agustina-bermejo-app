# Backups y restauración de Postgres

Runbook de las copias de la base y de los archivos subidos. Todo corre en Docker desde `deploy/backup/`; no depende del hosting.

Los comandos de este documento son los de `deploy/backup/scripts/`. Si cambian los scripts, cambia este documento en el mismo PR.

## Política

| Concepto | Valor |
|---|---|
| Frecuencia | Cada 4 h (`BACKUP_SCHEDULE=0 */4 * * *`) |
| RPO | Hasta 4 h (`BACKUP_TARGET_RPO_HOURS=4`) |
| Retención diaria | Último backup de cada día, últimos 7 días (`BACKUP_KEEP_DAILY=7`) |
| Retención semanal | Último backup de cada semana, últimas 4 semanas (`BACKUP_KEEP_WEEKLY=4`) |
| Retención trimestral | Último backup de cada trimestre, sin vencimiento (`BACKUP_KEEP_QUARTERLY=0`) |
| Pre-release | Copia manual antes de cada release con migraciones (`pre-release.sh`, ver más abajo) |
| Destino | Segundo disco local del servidor (`BACKUP_DIR=/ruta/absoluta`), distinto del disco de Postgres |
| Cifrado | `age`. La clave privada no se guarda en el servidor (ver "Custodia de la clave") |
| Prueba de integridad | Semanal, automática (`BACKUP_RESTORE_TEST_SCHEDULE=0 5 * * 0`) |
| Simulacro de restauración | Manual, al menos una vez por trimestre (ver "Simulacro") |
| Estado | Pantalla solo lectura `GET /backups/status` (roles `DIRECTOR` y `PLATFORM_ADMIN`) |

Un backup que nunca se restauró no es un backup: por eso existen la prueba semanal y el simulacro.

## Qué se guarda

Cada ejecución de `backup.sh` produce, con el mismo sello de tiempo:

- `abdb_<fecha>.dump` (o `.dump.age`): `pg_dump -Fc` de la base. Se escribe primero como `.partial` y se renombra solo si terminó bien.
- `abfiles_<fecha>.tar.zst` (o `.tar.zst.age`): archivos subidos y certificados del volumen `app_data`.
- `.sha256` de cada archivo y `.meta` con las migraciones Flyway y el conteo de filas por tabla (lo usa la prueba de restauración).

Antes de rotar se elige cada tier con `select-expired.awk`. Lo que no entra en ningún tier se borra.

## Custodia de la clave `age`

- **Quién**: Gabriel y una segunda persona de confianza. Las dos tienen una copia.
- **Dónde**: la clave privada (`clave.txt`, generada con `age-keygen -o clave.txt`) vive en un gestor de contraseñas y en una copia en papel guardada en un lugar físico distinto de la oficina. **Nunca** en el servidor, ni en el repositorio, ni en un chat.
- **Qué se guarda en el servidor**: solo la clave pública en `BACKUP_AGE_RECIPIENT`.
- **Cómo se prueba la custodia**: cada trimestre, en el simulacro, cada custodio abre su copia y confirma que puede leerla. Si uno no puede, se genera un par nuevo y se cambia `BACKUP_AGE_RECIPIENT`; los dumps anteriores siguen necesitando la clave vieja, así que esta se conserva mientras existan.
- **Si se pierde la clave**: los dumps `.age` no se pueden restaurar. Hay que usar la copia del custodio. Si se pierden las dos, no hay recuperación de esos backups. Por eso hay dos custodios.

## Backup manual

```bash
docker compose -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml exec backup backup.sh
docker compose -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml exec backup ls -la /backups
```

## Copia previa a cada release con migraciones

Flyway no revierte migraciones. Antes de desplegar un release con migraciones nuevas:

```bash
deploy/backup/scripts/pre-release.sh v1.2.3
```

El script:

1. Para `backend` (el resto sigue andando).
2. Toma un backup con prefijo `abpre-<versión>` y archivos `abprefiles-<versión>`, sin rotación.
3. Verifica que el dump exista y vuelve a arrancar `backend`, aunque el backup haya fallado.
4. Actualiza `status.json`.

La pantalla de estado lista las últimas 5 copias pre-release. Los scripts no tienen una limpieza automática para estas copias: se borran a mano cuando ya no hacen falta. Si el script falla, `backend` queda levantado y el release no sale.

Como alternativa sin el servicio de backup, `docs/deploy/README.md` (sección "Actualizar a una versión nueva") describe el `pg_dump` manual.

Checklist previo a release (verificar antes de `dc up -d --build`):

- [ ] Copia previa hecha con `pre-release.sh <versión>` o con el `pg_dump` manual, y guardada fuera del servidor.
- [ ] `BROADCAST_UNSUBSCRIBE_SECRET` definido en `deploy/.env` y distinto de `JWT_SECRET`. Sin él el backend no arranca en prod ni en staging.
- [ ] `POSTGRES_PASSWORD`, `JWT_SECRET` y `BACKUP_S3_*` (si aplica) no cambiaron sin querer.
- [ ] El PR del release dice qué migraciones trae y si alguna es destructiva.
- [ ] `GET /backups/status` muestra el último backup de menos de 4 h y `consecutiveFailures` en 0.
- [ ] Último resultado de la prueba semanal `PASSED`.

## Restaurar paso a paso

Cuándo: pérdida o corrupción de datos, o migración a otro servidor.

Hay dos caminos. Elegir según el caso.

### A. Recuperar datos puntuales (la app sigue andando)

1. Elegir el backup anterior a la pérdida: `docker compose ... exec backup ls -la /backups`.
2. Restaurar en una base aparte, sin tocar la base en uso:
   ```bash
   docker compose ... exec backup restore.sh /backups/abdb_AAAAMMDDTHHMMSSZ.dump --db agustina_bermejo_recuperada
   # cifrado:
   docker compose run --rm -v $PWD/clave.txt:/clave.txt:ro -e AGE_IDENTITY_FILE=/clave.txt backup restore.sh /backups/abdb_AAAAMMDDTHHMMSSZ.dump.age --db agustina_bermejo_recuperada
   ```
3. Consultar la base aparte y pasar a la base en uso solo lo que falta:
   ```bash
   docker compose exec postgres psql -U "$POSTGRES_USER" -d agustina_bermejo_recuperada
   ```
4. Borrar la base aparte:
   ```bash
   docker compose exec -T postgres dropdb -U "$POSTGRES_USER" agustina_bermejo_recuperada
   ```

### B. Reemplazar la base completa (la app se para)

1. Avisar a los usuarios: la app va a estar parada unos minutos.
2. Parar el backend: `docker compose -f docker-compose.prod.yml stop backend`.
3. Guardar el estado actual: `docker compose ... exec backup backup.sh`.
4. Restaurar en una base nueva:
   ```bash
   docker compose ... exec backup restore.sh /backups/abdb_AAAAMMDDTHHMMSSZ.dump --db agustina_bermejo_nueva
   ```
   Pide escribir el nombre de la base para confirmar. Verifica el checksum antes de tocar nada: si no coincide, aborta. Para un `.age`, usar el comando con `AGE_IDENTITY_FILE` del paso A.
5. Cambiar de base: renombrar la dañada y poner la restaurada con el nombre original.
   ```bash
   docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres \
     -c "ALTER DATABASE agustina_bermejo RENAME TO agustina_bermejo_danada_<fecha>;" \
     -c "ALTER DATABASE agustina_bermejo_nueva RENAME TO agustina_bermejo;"
   ```
   Para volver atrás, renombrar al revés. No hace falta cambiar `DB_URL` ni `POSTGRES_DB`.
6. Restaurar los archivos subidos y los certificados del mismo momento:
   ```bash
   age -d -i <clave privada> /backups/abfiles_AAAAMMDDTHHMMSSZ.tar.zst.age \
     | zstd -d | docker run --rm -i -v app_data:/data alpine tar -x -C /data
   ```
   Si el archivo no está cifrado, omitir `age -d -i ...` y pasar el `.tar.zst` directo a `zstd -d`.
7. Levantar el backend: `docker compose -f docker-compose.prod.yml start backend`. Flyway valida el historial al arrancar y aplica las migraciones pendientes si el dump es de una versión anterior.
8. Verificar antes de abrir a los usuarios. Todo tiene que pasar:
   - La app arranca: `curl -fsS https://<dominio>/api/actuator/health`.
   - La versión de la base coincide con la del código:
     ```bash
     docker compose exec -T postgres psql -U "$POSTGRES_USER" -d agustina_bermejo \
       -tc "select max(version) from flyway_schema_history where success"
     ```
   - Las cantidades coinciden con las del `.meta` del backup.
   - Se entra con un usuario de prueba.
   - Se abre un certificado y un archivo subido.
9. Reabrir y avisar a los usuarios hasta qué fecha y hora quedaron los datos.

El restore funciona desde cualquier máquina con Docker usando la imagen del servicio `backup` (`docker build deploy/backup`). No hace falta el servidor original.

## Prueba semanal de integridad

Corre sola cada domingo a las 05:00 (`restore-test.sh`). Toma el último dump, valida checksum, lo restaura en una base descartable y compara conteos y migraciones. El resultado queda en `status.json` y en `GET /backups/status`. Si falla, se emite alerta `RESTORE_TEST_FAILED`.

Limitación conocida: la prueba automática no descifra con `AGE_IDENTITY_FILE` (la clave no está en el servidor). Con dumps `.age` no puede restaurar; por eso el simulacro trimestral es manual y es la prueba real del cifrado.

Para correr la verificación completa a mano, con un Postgres 16 descartable:

```bash
deploy/backup/verify-restore.sh --volume <volumen_backup_data>      # o --dir /ruta/a/los/dumps
```

Cuando cambia la versión de Postgres, cuando se tocan los scripts de backup, o antes de un release con migraciones, correrla también a mano.

## Simulacro trimestral

Hacerlo en una fecha fija de cada trimestre, en un servidor de prueba o en staging, nunca sobre producción.

1. Elegir el último backup `.dump.age` y verificar su `.sha256`.
2. Descifrar con la clave privada que trae el custodio (no la del servidor). Confirmar que la clave abre el archivo.
3. Restaurar con el paso A.2 del runbook, a una base de prueba.
4. Comparar conteos contra el `.meta`.
5. Restaurar el tar de archivos y comparar un certificado y un archivo subido.
6. Confirmar con el segundo custodio que puede leer su copia de la clave.
7. Registrar fecha, backup usado, resultado y custodios presentes en el PR o issue de operaciones.

## Dónde se ve el estado

- `GET /backups/status`: pantalla de solo lectura para `DIRECTOR` y `PLATFORM_ADMIN`. Lee `status.json`.
- `deploy/backup/scripts/status.sh` regenera `status.json` (lo corre `pre-release.sh`).
- Alertas por webhook y/o Telegram (`ALERT_WEBHOOK_URL`, `ALERT_TELEGRAM_BOT_TOKEN` + `ALERT_TELEGRAM_CHAT_ID`) para fallas de backup, dump dañado, prueba de restauración fallida y disco bajo.
- Revisión diaria de antigüedad (`BACKUP_FRESHNESS_SCHEDULE`, `30 9 * * *`): alerta si el último backup supera `BACKUP_MAX_AGE_HOURS`.
- `BACKUP_HEARTBEAT_URL` (opcional, por ejemplo healthchecks.io): ping tras cada backup exitoso. Avisa si el servidor entero deja de responder.

## Variables

Las que usan los scripts. Están en `deploy/.env.example`.

| Variable | Default | Descripción |
|---|---|---|
| `BACKUP_SCHEDULE` | `0 */4 * * *` | Cron del backup |
| `BACKUP_RESTORE_TEST_SCHEDULE` | `0 5 * * 0` | Cron de la prueba semanal |
| `BACKUP_FRESHNESS_SCHEDULE` | `30 9 * * *` | Cron de la revisión de antigüedad |
| `BACKUP_TZ` | `America/Argentina/Buenos_Aires` | Zona horaria de los crons |
| `BACKUP_DIR` | `backup_data` (volumen) | Ruta absoluta del segundo disco del host |
| `BACKUP_TARGET_RPO_HOURS` | `4` | RPO objetivo que muestra el estado |
| `BACKUP_MAX_AGE_HOURS` | `13` | Antigüedad máxima aceptable |
| `BACKUP_KEEP_DAILY` / `_WEEKLY` / `_QUARTERLY` | `7` / `4` / `0` | Retención (`0` en trimestral = sin vencimiento) |
| `BACKUP_AGE_RECIPIENT` | vacío | Clave pública `age1...` para cifrar |
| `BACKUP_HEARTBEAT_URL` | vacío | Ping tras cada backup exitoso |
| `BACKUP_S3_BUCKET` y `BACKUP_S3_*` | vacío | Copia a S3 compatible. Credenciales solo por variables de entorno |
| `ALERT_WEBHOOK_URL` | vacío | Webhook de avisos |
| `ALERT_TELEGRAM_BOT_TOKEN` / `ALERT_TELEGRAM_CHAT_ID` | vacío | Aviso por Telegram |

Los avisos por email no están incluidos: se resuelven con un webhook.

## Pendientes conocidos

- La prueba automática no descifra dumps `.age` (ver "Prueba semanal de integridad").
- La copia en S3 no se probó contra un proveedor real. Sin S3, el backup vive en el mismo servidor: configurar copia externa en producción.
