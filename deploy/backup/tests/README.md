# Tests de los backups

## Rotación (sin Docker, corre en el CI)

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
