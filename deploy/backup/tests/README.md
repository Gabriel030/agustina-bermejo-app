# Tests de los backups

## Sin Docker (corren en el CI)

```
deploy/backup/tests/rotation-test.sh
deploy/backup/tests/dump-stream-test.sh
deploy/backup/tests/status-test.sh
deploy/backup/tests/restore-test-test.sh
deploy/backup/tests/pre-release-test.sh
```

Los tres últimos necesitan `jq` y `python3` con `jsonschema` (`python3 -m pip install jsonschema`).

- `status-test.sh`: `status.json` contra `docs/deploy/backup-status.schema.json`, niveles, etiquetas, alertas con códigos cerrados, escritura atómica y lector concurrente.
- `restore-test-test.sh`: la prueba de restauración falla con código cerrado (`CHECKSUM_MISMATCH`, `RESTORE_FAILED`) y deja el estado y la alerta; un backup cifrado sin clave privada no inventa un resultado.
- `pre-release-test.sh`: `pre-release.sh` con un `docker compose` falso: orden (parar backend, backup, comprobar, arrancar backend, estado), backend siempre reiniciado y un pre-release que la rotación nunca borra.

`dump-stream-test.sh` fija que el verificador de `pg_restore --list` drena todo el stream (un verificador que sale
temprano no puede matar a `pg_dump` con SIGPIPE) y cubre el conteo de archivos del tar.

## Rotación (detalle)

```
deploy/backup/tests/rotation-test.sh
```

Cubre `select-expired.awk` y `rotate.sh`: día en curso, borde de día con zona horaria, semanas lunes-domingo, trimestres
(incluido el fin de año), dumps cifrados y que el par dump + archivo de `/data` se quita o se conserva siempre junto.

## Corrida real contra Postgres (local, no corre en el CI)

```
deploy/backup/tests/backup-run-test.sh
```

Levanta un `postgres:16-alpine` descartable (variable `PG_IMAGE` para cambiarlo), corre `backup.sh` con un `/data` de prueba y
verifica sello compartido, checksums, `.meta`, cifrado con `age`, `/data` ausente o vacío y un `tar` que falla.
Necesita `tar`, `zstd`, `age`, `pg_dump`, `psql`, `python3` y Docker. Crea un symlink `/opt/backup` hacia los scripts (con `sudo` si
hace falta) y no pisa un `/opt/backup` que no sea un symlink.

## Cómo se levanta el servicio

El servicio de backup se combina siempre con el compose de producción, con `docker-compose.prod.yml` primero (las rutas relativas
se resuelven contra el primer archivo):

```
docker compose -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml up -d --build backup
```

El archivo `docker-compose.backup.yml` solo, sin el de producción, no es un uso válido.

## Limpieza manual de dumps anteriores a BK#1a (sin archivo de `/data`)

La rotación no cuenta ni borra los dumps que no tienen su archivo de `/data` (podrían ser los únicos backups viejos). Para listarlos,
dentro de la carpeta de backups (`BACKUP_DIR`):

```
for dump in abdb_*.dump abdb_*.dump.age; do
  [ -e "$dump" ] || continue
  stamp=${dump#abdb_}; stamp=${stamp%%.*}
  ls "abfiles_$stamp.tar.zst" "abfiles_$stamp.tar.zst.age" > /dev/null 2>&1 || echo "$dump"
done
```

Después de revisar la lista, para borrarlos (el dump, su checksum y su `.meta`):

```
for dump in abdb_*.dump abdb_*.dump.age; do
  [ -e "$dump" ] || continue
  stamp=${dump#abdb_}; stamp=${stamp%%.*}
  ls "abfiles_$stamp.tar.zst" "abfiles_$stamp.tar.zst.age" > /dev/null 2>&1 && continue
  rm -v -- "$dump" "$dump.sha256" "abdb_$stamp.meta"
done
```

## Backup pre-release y prueba de restauración

```
deploy/backup/scripts/pre-release.sh v1.2.3
```

Desde la raíz del repo: para el backend, hace un backup etiquetado (`abpre-v1.2.3_<TS>.dump` y `abprefiles-v1.2.3_<TS>.tar.zst`, fuera de la rotación; la versión solo puede ser
`vX.Y.Z` o `X.Y.Z[-sufijo]`, nunca texto libre), vuelve a arrancar el backend (aunque el backup falle) y actualiza `status.json`.
`COMPOSE_CMD` cambia el comando de compose.

La prueba de restauración semanal (`restore-test.sh`, domingo 05:00, `BACKUP_RESTORE_TEST_SCHEDULE`) restaura el último par completo en una base
descartable del mismo servidor y compara migraciones y conteos con el `.meta`. Con backups cifrados necesita `AGE_IDENTITY_FILE`; sin él solo verifica los
checksums y no actualiza el resultado (el simulacro completo con la clave privada se hace con `deploy/backup/verify-restore.sh` en otra máquina).
