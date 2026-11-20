#!/usr/bin/env bash
set -uo pipefail
umask 077
. /opt/backup/lib.sh

DATA_DIR="${BACKUP_DATA_DIR:-/data}"
scheduled_run=true
[ "${BACKUP_SKIP_ROTATION:-false}" = "true" ] && scheduled_run=false
export PGHOST="${PGHOST:-postgres}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGDATABASE="${PGDATABASE:-agustina_bermejo}"

stamp=$(date -u +%Y%m%dT%H%M%SZ)
base="${BACKUP_DIR}/${BACKUP_PREFIX}_${stamp}"
files_base="${BACKUP_DIR}/${FILES_PREFIX}_${stamp}"
suffix=""
[ -n "${AGE_RECIPIENT:-}" ] && suffix=".age"
dump="${base}.dump${suffix}"
files_archive="${files_base}.tar.zst${suffix}"
dump_tmp="${dump}.partial"
files_tmp="${files_archive}.partial"
tar_count="${base}.tar-count.partial"
verify_rc="${base}.verify-rc.partial"

fail() {
  remove_files "$dump_tmp" "$files_tmp" "$tar_count" "$verify_rc" "$dump" "${dump}.sha256" "$files_archive" "${files_archive}.sha256" "${base}.meta"
  local previous
  if [ "$scheduled_run" = "true" ]; then
    previous=$(cat "$METRICS_DIR/.last_success" 2> /dev/null || echo 0)
    write_metrics 0 "$previous" 0
    bump_consecutive_failures
  fi
  record_alert CRITICAL BACKUP_FAILED
  notify "BACKUP FALLIDO: $1"
  refresh_status
  exit 1
}

encrypt_sink() {
  if [ -n "${AGE_RECIPIENT:-}" ]; then
    age -r "$AGE_RECIPIENT" -o "$1"
  else
    cat > "$1"
  fi
}

mkdir -p "$BACKUP_DIR" || fail "no se puede escribir en $BACKUP_DIR"
chmod 700 "$BACKUP_DIR" || fail "no se pueden fijar los permisos de $BACKUP_DIR"
exec 9> "$BACKUP_DIR/.backup.lock"
lock_wait="${BACKUP_LOCK_WAIT_SECONDS:-0}"
if [ "$lock_wait" -gt 0 ]; then
  flock -w "$lock_wait" 9 || lock_failed=true
else
  flock -n 9 || lock_failed=true
fi
if [ "${lock_failed:-false}" = "true" ]; then
  log "otra corrida de backup sigue en curso: se omite esta"
  exit 0
fi
find "$BACKUP_DIR" -maxdepth 1 -name '*.partial' -mmin +60 -delete 2> /dev/null
[ -d "$DATA_DIR" ] || fail "no existe $DATA_DIR: el servicio de backup tiene que montar app_data"
log "starting backup of $PGDATABASE@$PGHOST"

pg_dump -Fc --no-owner --no-privileges | verify_while_streaming "$verify_rc" pg_restore --list | encrypt_sink "$dump_tmp"
dump_statuses=("${PIPESTATUS[@]}")
[ "${dump_statuses[2]}" -eq 0 ] || fail "no se pudo escribir o cifrar el dump"
[ "${dump_statuses[0]}" -eq 0 ] || fail "pg_dump devolvio error (${dump_statuses[0]})"
wait_for_file "$verify_rc"
[ "$(cat "$verify_rc" 2> /dev/null)" = "0" ] || fail "el dump generado no se puede leer con pg_restore --list"
[ -s "$dump_tmp" ] || fail "el dump quedo vacio"

migrations=$(psql -Atc "SELECT count(*) FROM flyway_schema_history WHERE success" 2> /dev/null) || fail "no se pudo leer flyway_schema_history"
max_version=$(psql -Atc "SELECT coalesce(max(version::int), 0) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+\$'" 2> /dev/null || echo 0)
counts=$(psql -At -F '|' -c "$TABLE_COUNTS_SQL" 2> /dev/null) || fail "no se pudieron contar las tablas"

tar --zstd -C "$DATA_DIR" -cvf - . 2> >(count_files_in_tar_listing "$tar_count") | encrypt_sink "$files_tmp"
tar_statuses=("${PIPESTATUS[@]}")
[ "${tar_statuses[1]}" -eq 0 ] || fail "no se pudo escribir o cifrar el archivo de $DATA_DIR"
wait_for_file "$tar_count" || fail "no se pudo contar los archivos de $DATA_DIR"
files_changed=no
if [ "${tar_statuses[0]}" -eq 1 ]; then
  files_changed=yes
  log "WARNING: algun archivo de $DATA_DIR cambio mientras se copiaba (backup best-effort)"
elif [ "${tar_statuses[0]}" -ne 0 ]; then
  fail "tar de $DATA_DIR devolvio error (${tar_statuses[0]})"
fi
[ -s "$files_tmp" ] || fail "el archivo de $DATA_DIR quedo vacio"
files_count=$(cat "$tar_count")
remove_files "$tar_count"
if [ "$files_count" -eq 0 ]; then
  if [ "${BACKUP_ALLOW_EMPTY_DATA:-false}" = "true" ]; then
    log "WARNING: $DATA_DIR esta vacio (BACKUP_ALLOW_EMPTY_DATA=true)"
  else
    record_alert CRITICAL DATA_DIR_EMPTY
    fail "$DATA_DIR esta vacio: verificar que el volumen app_data este montado (BACKUP_ALLOW_EMPTY_DATA=true en una instalacion nueva)"
  fi
fi
[ -z "$suffix" ] && warn_unencrypted_once

mv "$dump_tmp" "$dump" || fail "no se pudo finalizar el dump"
mv "$files_tmp" "$files_archive" || fail "no se pudo finalizar el archivo de $DATA_DIR"
remove_files "$verify_rc"
for artifact in "$dump" "$files_archive"; do
  (cd "$BACKUP_DIR" && sha256sum "$(basename "$artifact")" > "$(basename "$artifact").sha256") || fail "no se pudo calcular el checksum de $(basename "$artifact")"
done

{
  echo "created_at=$stamp"
  echo "database=$PGDATABASE"
  echo "encrypted=$([ -n "$suffix" ] && echo yes || echo no)"
  echo "consistency=best_effort"
  echo "files_archive=$(basename "$files_archive")"
  echo "files_count=$files_count"
  echo "files_changed_during_copy=$files_changed"
  echo "migrations_count=$migrations"
  echo "migrations_max_version=$max_version"
  echo "[tables]"
  echo "$counts"
} > "${base}.meta" || fail "no se pudo escribir el .meta"

size=$(wc -c < "$dump" | tr -d ' ')
files_size=$(wc -c < "$files_archive" | tr -d ' ')
log "dump written: $(basename "$dump") ($size bytes); files archive: $(basename "$files_archive") ($files_size bytes, $files_count files)"

now=$(date -u +%s)
if [ "$scheduled_run" = "true" ]; then
  mkdir -p "$METRICS_DIR" 2> /dev/null && echo "$now" > "$METRICS_DIR/.last_success"
  reset_consecutive_failures
  write_metrics 1 "$now" "$size"
  append_files_metric "$files_size"
fi

if s3_enabled; then
  upload_pair_to_s3 "$dump" "$files_archive" "${base}.meta" || notify "SUBIDA A S3 FALLIDA: el backup local de $stamp esta bien y se conserva"
fi

if [ "$scheduled_run" = "true" ]; then
  /opt/backup/rotate.sh || notify "la rotacion de backups fallo (el backup en si se hizo bien)"
fi
refresh_status
ping_heartbeat
log "backup finished"
