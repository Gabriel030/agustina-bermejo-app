#!/usr/bin/env bash
set -uo pipefail
. /opt/backup/lib.sh

usage() {
  cat <<USAGE
Uso: restore.sh [--yes] [--db NOMBRE] [--no-checksum] ARCHIVO_DUMP

Restaura un dump (.dump o .dump.age) en una base limpia: BORRA y vuelve a crear la base destino.
Conexion por variables PGHOST, PGPORT, PGUSER, PGPASSWORD. Base destino: --db o PGDATABASE.
Si el archivo es .age hace falta AGE_IDENTITY_FILE (clave privada).
USAGE
  exit 2
}

assume_yes=false
verify_checksum=true
target="${PGDATABASE:-agustina_bermejo}"
dump=""
while [ $# -gt 0 ]; do
  case "$1" in
    --yes) assume_yes=true ;;
    --no-checksum) verify_checksum=false ;;
    --db) shift; target="${1:-}" ;;
    -h | --help) usage ;;
    *) dump="$1" ;;
  esac
  shift
done
[ -n "$dump" ] && [ -f "$dump" ] || { echo "restore: archivo de dump inexistente: '$dump'" >&2; usage; }
export PGHOST="${PGHOST:-postgres}" PGPORT="${PGPORT:-5432}" PGUSER="${PGUSER:-postgres}"
export PGDATABASE=postgres

if [ "$verify_checksum" = true ]; then
  if [ -f "$dump.sha256" ]; then
    (cd "$(dirname "$dump")" && sha256sum -c "$(basename "$dump").sha256") || { echo "restore: el checksum NO coincide, abortando" >&2; exit 1; }
  else
    echo "restore: no hay $dump.sha256; use --no-checksum si es intencional" >&2
    exit 1
  fi
fi

if [ "$assume_yes" != true ]; then
  printf 'Esto BORRA la base "%s" en %s y la reemplaza por el dump. Escriba el nombre de la base para confirmar: ' "$target" "$PGHOST"
  read -r answer
  [ "$answer" = "$target" ] || { echo "restore: cancelado"; exit 1; }
fi

work="$dump"
cleanup=""
case "$dump" in
  *.age)
    [ -n "${AGE_IDENTITY_FILE:-}" ] && [ -f "$AGE_IDENTITY_FILE" ] || { echo "restore: falta AGE_IDENTITY_FILE para descifrar" >&2; exit 1; }
    work=$(mktemp /tmp/restore.XXXXXX)
    cleanup="$work"
    age -d -i "$AGE_IDENTITY_FILE" -o "$work" "$dump" || { remove_files "$work"; echo "restore: fallo el descifrado" >&2; exit 1; }
    ;;
esac
trap 'remove_files "$cleanup"' EXIT

pg_restore --list "$work" > /dev/null || { echo "restore: el dump esta corrupto" >&2; exit 1; }

log "recreating database $target"
psql -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$target' AND pid <> pg_backend_pid()" > /dev/null || exit 1
psql -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$target\"" || exit 1
psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$target\"" || exit 1

log "restoring"
pg_restore --no-owner --no-privileges --exit-on-error -d "$target" "$work" || { echo "restore: pg_restore fallo" >&2; exit 1; }
log "restore finished into $target"
