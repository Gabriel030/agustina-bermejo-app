#!/usr/bin/env bash
set -uo pipefail
. /opt/backup/lib.sh

export PGHOST="${PGHOST:-postgres}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGDATABASE="${PGDATABASE:-agustina_bermejo}"

stamp=$(date -u +%Y%m%dT%H%M%SZ)
base="${BACKUP_DIR}/${BACKUP_PREFIX}_${stamp}"
dump="${base}.dump"
tmp="${base}.dump.partial"

fail() {
  remove_files "$tmp" "$dump" "${dump}.age" "${dump}.sha256" "${dump}.age.sha256" "${base}.meta"
  local previous
  previous=$(cat "$METRICS_DIR/.last_success" 2> /dev/null || echo 0)
  write_metrics 0 "$previous" 0
  notify "BACKUP FALLIDO: $1"
  exit 1
}

mkdir -p "$BACKUP_DIR" || fail "no se puede escribir en $BACKUP_DIR"
log "starting backup of $PGDATABASE@$PGHOST"

pg_dump -Fc --no-owner --no-privileges -f "$tmp" || fail "pg_dump devolvio error"
[ -s "$tmp" ] || fail "el dump quedo vacio"
pg_restore --list "$tmp" > /dev/null || fail "el dump generado no se puede leer con pg_restore --list"

migrations=$(psql -Atc "SELECT count(*) FROM flyway_schema_history WHERE success" 2> /dev/null) || fail "no se pudo leer flyway_schema_history"
max_version=$(psql -Atc "SELECT coalesce(max(version::int), 0) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+\$'" 2> /dev/null || echo 0)
counts=$(psql -At -F '|' -c "SELECT table_name, (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text::bigint FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY 1" 2> /dev/null) || fail "no se pudieron contar las tablas"

final="$dump"
if [ -n "${AGE_RECIPIENT:-}" ]; then
  age -r "$AGE_RECIPIENT" -o "${dump}.age" "$tmp" || fail "fallo el cifrado con age"
  remove_files "$tmp"
  final="${dump}.age"
else
  mv "$tmp" "$dump"
fi

(cd "$BACKUP_DIR" && sha256sum "$(basename "$final")" > "$(basename "$final").sha256") || fail "no se pudo calcular el checksum"

{
  echo "created_at=$stamp"
  echo "database=$PGDATABASE"
  echo "encrypted=$([ -n "${AGE_RECIPIENT:-}" ] && echo yes || echo no)"
  echo "migrations_count=$migrations"
  echo "migrations_max_version=$max_version"
  echo "[tables]"
  echo "$counts"
} > "${base}.meta"

size=$(wc -c < "$final" | tr -d ' ')
log "dump written: $(basename "$final") ($size bytes)"

if s3_enabled; then
  rclone_env
  remote=$(s3_remote)
  for file in "$final" "${final}.sha256" "${base}.meta"; do
    rclone copyto "$file" "$remote/$(basename "$file")" || fail "fallo la subida a S3 de $(basename "$file")"
  done
  log "uploaded to $remote"
fi

now=$(date -u +%s)
mkdir -p "$METRICS_DIR" 2> /dev/null && echo "$now" > "$METRICS_DIR/.last_success"
write_metrics 1 "$now" "$size"

/opt/backup/rotate.sh || notify "la rotacion de backups fallo (el backup en si se hizo bien)"
ping_heartbeat
log "backup finished"
