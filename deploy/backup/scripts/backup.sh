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
files_base="${BACKUP_DIR}/${FILES_PREFIX}_${stamp}"
files_archive="${files_base}.tar.zst"
files_tmp="${files_base}.tar.zst.partial"

fail() {
  remove_files "$tmp" "$dump" "${dump}.age" "${dump}.sha256" "${dump}.age.sha256" "${base}.meta" \
    "$files_tmp" "$files_archive" "${files_archive}.age" "${files_archive}.sha256" "${files_archive}.age.sha256"
  local previous
  previous=$(cat "$METRICS_DIR/.last_success" 2> /dev/null || echo 0)
  write_metrics 0 "$previous" 0
  notify "BACKUP FALLIDO: $1"
  exit 1
}

mkdir -p "$BACKUP_DIR" || fail "no se puede escribir en $BACKUP_DIR"
[ -d "$DATA_DIR" ] || fail "no existe $DATA_DIR: el servicio de backup tiene que montar app_data"
log "starting backup of $PGDATABASE@$PGHOST"

pg_dump -Fc --no-owner --no-privileges -f "$tmp" || fail "pg_dump devolvio error"
[ -s "$tmp" ] || fail "el dump quedo vacio"
pg_restore --list "$tmp" > /dev/null || fail "el dump generado no se puede leer con pg_restore --list"

migrations=$(psql -Atc "SELECT count(*) FROM flyway_schema_history WHERE success" 2> /dev/null) || fail "no se pudo leer flyway_schema_history"
max_version=$(psql -Atc "SELECT coalesce(max(version::int), 0) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+\$'" 2> /dev/null || echo 0)
counts=$(psql -At -F '|' -c "SELECT table_name, (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text::bigint FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY 1" 2> /dev/null) || fail "no se pudieron contar las tablas"

tar --zstd -C "$DATA_DIR" -cf "$files_tmp" .
tar_status=$?
if [ "$tar_status" -eq 1 ]; then
  log "WARNING: algun archivo de $DATA_DIR cambio mientras se copiaba (backup best-effort)"
elif [ "$tar_status" -ne 0 ]; then
  fail "tar de $DATA_DIR devolvio error ($tar_status)"
fi
[ -s "$files_tmp" ] || fail "el archivo de $DATA_DIR quedo vacio"
tar --zstd -tf "$files_tmp" > /dev/null || fail "el archivo de $DATA_DIR no se puede leer con tar"
files_count=$(find "$DATA_DIR" -type f | wc -l | tr -d ' ')
files_bytes=$(( $(du -sk "$DATA_DIR" | cut -f1) * 1024 ))
[ "$files_count" -gt 0 ] || warn_once_per_day data-empty "BACKUP: $DATA_DIR esta vacio, verificar que el volumen app_data este montado"

final="$dump"
files_final="$files_archive"
if [ -n "${AGE_RECIPIENT:-}" ]; then
  age -r "$AGE_RECIPIENT" -o "${dump}.age" "$tmp" || fail "fallo el cifrado con age del dump"
  age -r "$AGE_RECIPIENT" -o "${files_archive}.age" "$files_tmp" || fail "fallo el cifrado con age de los archivos"
  remove_files "$tmp" "$files_tmp"
  final="${dump}.age"
  files_final="${files_archive}.age"
else
  mv "$tmp" "$dump"
  mv "$files_tmp" "$files_archive"
  warn_unencrypted_once
fi

for artifact in "$final" "$files_final"; do
  (cd "$BACKUP_DIR" && sha256sum "$(basename "$artifact")" > "$(basename "$artifact").sha256") || fail "no se pudo calcular el checksum de $(basename "$artifact")"
done

{
  echo "created_at=$stamp"
  echo "database=$PGDATABASE"
  echo "encrypted=$([ -n "${AGE_RECIPIENT:-}" ] && echo yes || echo no)"
  echo "files_archive=$(basename "$files_final")"
  echo "files_count=$files_count"
  echo "files_bytes=$files_bytes"
  echo "migrations_count=$migrations"
  echo "migrations_max_version=$max_version"
  echo "[tables]"
  echo "$counts"
} > "${base}.meta"

size=$(wc -c < "$final" | tr -d ' ')
files_size=$(wc -c < "$files_final" | tr -d ' ')
log "dump written: $(basename "$final") ($size bytes); files archive: $(basename "$files_final") ($files_size bytes, $files_count files)"

if s3_enabled; then
  rclone_env
  remote=$(s3_remote)
  for file in "$final" "${final}.sha256" "$files_final" "${files_final}.sha256" "${base}.meta"; do
    rclone copyto "$file" "$remote/$(basename "$file")" || fail "fallo la subida a S3 de $(basename "$file")"
  done
  log "uploaded to $remote"
fi

now=$(date -u +%s)
mkdir -p "$METRICS_DIR" 2> /dev/null && echo "$now" > "$METRICS_DIR/.last_success"
write_metrics 1 "$now" "$size"
append_files_metric "$files_size"

/opt/backup/rotate.sh || notify "la rotacion de backups fallo (el backup en si se hizo bien)"
ping_heartbeat
log "backup finished"
