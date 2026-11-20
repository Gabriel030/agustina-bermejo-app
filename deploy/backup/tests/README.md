# Tests de los backups

## Sin Docker (corren en el CI)

```
deploy/backup/tests/rotation-test.sh
deploy/backup/tests/dump-stream-test.sh
```

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
