#!/usr/bin/env bash
set -uo pipefail

usage() {
  cat <<USAGE
Uso: verify-restore.sh [--dir CARPETA | --volume VOLUMEN_DOCKER] [--migrations CARPETA] [--skip-flyway]

Prueba automatica de restauracion: levanta un Postgres 16 descartable, restaura el ultimo dump,
compara migraciones y conteos de tablas contra el .meta y corre flyway validate.
Sale con codigo distinto de cero si algo no coincide. Requiere Docker.
  --dir         carpeta con los dumps (por defecto ./backups)
  --volume      volumen Docker con los dumps (por ejemplo el volumen backup_data del compose)
  --migrations  carpeta con los V*.sql (por defecto backend/src/main/resources/db/migration)
  Variables: AGE_IDENTITY_FILE (clave privada si los dumps estan cifrados), FLYWAY_IMAGE, PG_IMAGE
USAGE
  exit 2
}

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source_mount=""
migrations="$here/../../backend/src/main/resources/db/migration"
skip_flyway=false
dir="$PWD/backups"
while [ $# -gt 0 ]; do
  case "$1" in
    --dir) shift; dir="$(cd "${1:-}" && pwd)" || exit 2; source_mount="$dir" ;;
    --volume) shift; source_mount="${1:-}" ;;
    --migrations) shift; migrations="${1:-}" ;;
    --skip-flyway) skip_flyway=true ;;
    *) usage ;;
  esac
  shift
done
[ -n "$source_mount" ] || source_mount="$dir"

PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:11-alpine}"
TOOLS_IMAGE="agustina-bermejo/backup:verify"
suffix="$$"
network="verify-restore-$suffix"
pg="verify-restore-pg-$suffix"
password="verify-$suffix"
database="verify_restore"

cleanup() {
  docker rm -f "$pg" > /dev/null 2>&1
  docker network rm "$network" > /dev/null 2>&1
}
trap cleanup EXIT

step() { printf '\n== %s\n' "$1"; }
die() { printf 'VERIFICACION FALLIDA: %s\n' "$1" >&2; exit 1; }

step "Construyendo la imagen de herramientas"
docker build -q -t "$TOOLS_IMAGE" "$here" > /dev/null || die "no se pudo construir la imagen de backup"

step "Buscando el ultimo dump"
latest=$(docker run --rm -v "$source_mount:/in:ro" --entrypoint sh "$TOOLS_IMAGE" -c 'ls /in | grep -E "^abdb_[0-9]{8}T[0-9]{6}Z\.dump(\.age)?$" | sort | tail -n 1')
[ -n "$latest" ] || die "no hay ningun dump en $source_mount"
echo "ultimo dump: $latest"
stem="${latest%.age}"
stem="${stem%.dump}"

step "Levantando Postgres descartable"
docker network create "$network" > /dev/null || die "no se pudo crear la red"
docker run -d --name "$pg" --network "$network" -e POSTGRES_PASSWORD="$password" "$PG_IMAGE" > /dev/null || die "no arranco Postgres"
ready=false
for _ in $(seq 1 60); do
  if docker exec "$pg" pg_isready -q -U postgres 2> /dev/null; then ready=true; break; fi
  sleep 1
done
$ready || die "Postgres descartable no quedo listo"

tools_env=(-e PGHOST="$pg" -e PGUSER=postgres -e PGPASSWORD="$password" -e PGDATABASE="$database")
tools_mounts=(-v "$source_mount:/in:ro")
age_args=()
if [ -n "${AGE_IDENTITY_FILE:-}" ]; then
  tools_mounts+=(-v "$AGE_IDENTITY_FILE:/identity.txt:ro")
  age_args=(-e AGE_IDENTITY_FILE=/identity.txt)
fi
if [ -d "$migrations" ]; then
  migrations="$(cd "$migrations" && pwd)"
  tools_mounts+=(-v "$migrations:/migrations:ro")
fi

step "Restaurando con restore.sh"
docker run --rm --network "$network" "${tools_env[@]}" ${age_args[@]+"${age_args[@]}"} "${tools_mounts[@]}" --entrypoint restore.sh "$TOOLS_IMAGE" --yes --db "$database" "/in/$latest" || die "restore.sh fallo"

step "Comparando migraciones y conteos"
meta="/in/$stem.meta"
docker run --rm --network "$network" "${tools_env[@]}" "${tools_mounts[@]}" --entrypoint sh "$TOOLS_IMAGE" -c "m=''; [ -f '$meta' ] && m='$meta'; g=''; [ -d /migrations ] && g=/migrations; verify-counts.sh \"\$m\" \"\$g\"" || die "los conteos no coinciden"

if [ "$skip_flyway" = false ] && [ -d "$migrations" ]; then
  step "flyway validate"
  docker run --rm --network "$network" -v "$migrations:/flyway/sql:ro" "$FLYWAY_IMAGE" \
    -url="jdbc:postgresql://$pg:5432/$database" -user=postgres -password="$password" \
    -locations=filesystem:/flyway/sql -validateMigrationNaming=true validate || die "flyway validate fallo"
else
  echo "flyway validate omitido"
fi

step "RESTAURACION VERIFICADA: $latest"
