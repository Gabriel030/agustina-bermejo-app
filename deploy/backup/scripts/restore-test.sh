#!/usr/bin/env bash
set -uo pipefail
umask 077
. /opt/backup/lib.sh

export PGHOST="${PGHOST:-postgres}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGDATABASE="${PGDATABASE:-agustina_bermejo}"

scratch=""
work=""

cleanup() {
  [ -n "$scratch" ] && psql -Atc "DROP DATABASE IF EXISTS \"$scratch\"" > /dev/null 2>&1
  [ -n "$work" ] && rm -rf "$work"
}
trap cleanup EXIT

write_state() {
  local result="$1" code="$2" tmp
  ensure_status_dir || return 0
  tmp="$STATUS_DIR/.restore-test.tmp"
  printf 'ran_at=%s\nresult=%s\nfailure_code=%s\n' "$(iso_now)" "$result" "$code" > "$tmp" && mv -f "$tmp" "$STATUS_DIR/restore-test"
}

fail_test() {
  local code="$1"
  write_state FAILED "$code"
  record_alert CRITICAL RESTORE_TEST_FAILED
  notify "PRUEBA DE RESTAURACION FALLIDA ($code)"
  refresh_status
  exit 1
}

if [ "${1:-}" = "--record" ]; then
  result="${2:-}"
  code="${3:-}"
  case "$result:$code" in
    PASSED:) ;;
    FAILED:CHECKSUM_MISMATCH | FAILED:RESTORE_FAILED | FAILED:COUNTS_MISMATCH | FAILED:SCHEMA_VALIDATION_FAILED) ;;
    *) echo "Uso: restore-test.sh --record PASSED | --record FAILED <CHECKSUM_MISMATCH|RESTORE_FAILED|COUNTS_MISMATCH|SCHEMA_VALIDATION_FAILED>" >&2; exit 2 ;;
  esac
  write_state "$result" "$code"
  [ "$result" = "FAILED" ] && record_alert CRITICAL RESTORE_TEST_FAILED
  refresh_status
  exit 0
fi

dump_name=$(latest_complete_dump)
[ -n "$dump_name" ] || fail_test RESTORE_FAILED
stamp=$(stamp_of "$dump_name")
archive_name=$(for candidate in "${FILES_PREFIX}_${stamp}.tar.zst" "${FILES_PREFIX}_${stamp}.tar.zst.age"; do [ -f "$BACKUP_DIR/$candidate" ] && echo "$candidate"; done | head -n 1)
[ -n "$archive_name" ] || fail_test RESTORE_FAILED

integrity_only=false
if [ "${dump_name##*.}" = "age" ] || [ "${archive_name##*.}" = "age" ]; then
  integrity_only=true
fi

for name in "$dump_name" "$archive_name"; do
  if ! (cd "$BACKUP_DIR" && sha256sum -c --status "$name.sha256" 2> /dev/null); then
    if [ "$integrity_only" = "true" ]; then
      notify "CHEQUEO DE INTEGRIDAD FALLIDO: el checksum de $name no coincide"
      refresh_status
      exit 1
    fi
    fail_test CHECKSUM_MISMATCH
  fi
done

if [ "$integrity_only" = "true" ]; then
  log "backup cifrado sin clave privada en el servidor: integridad verificada (checksums); la restauracion completa es el simulacro manual con verify-restore.sh"
  refresh_status
  exit 0
fi

work=$(mktemp -d "$BACKUP_DIR/.restore-test.XXXXXX") || fail_test RESTORE_FAILED
dump_file="$BACKUP_DIR/$dump_name"
archive_file="$BACKUP_DIR/$archive_name"

tar --zstd -tf "$archive_file" > /dev/null 2>&1 || fail_test RESTORE_FAILED

for leftover in $(psql -Atc "SELECT datname FROM pg_database WHERE datname LIKE 'restore\_test\_%'" 2> /dev/null); do
  psql -Atc "DROP DATABASE IF EXISTS \"$leftover\"" > /dev/null 2>&1
done

scratch="restore_test_$(date -u +%s)_$$"
psql -Atc "CREATE DATABASE \"$scratch\"" > /dev/null 2>&1 || fail_test RESTORE_FAILED
pg_restore --no-owner --no-privileges --exit-on-error -d "$scratch" "$dump_file" > /dev/null 2>&1 || fail_test RESTORE_FAILED

meta="$BACKUP_DIR/${dump_name%.age}"
meta="${meta%.dump}.meta"
expected_migrations=$(sed -n 's/^migrations_count=//p' "$meta")
actual_migrations=$(psql -d "$scratch" -Atc "SELECT count(*) FROM flyway_schema_history WHERE success" 2> /dev/null)
[ -n "$expected_migrations" ] && [ "$expected_migrations" = "$actual_migrations" ] || fail_test SCHEMA_VALIDATION_FAILED

expected_counts=$(sed -n '/^\[tables\]/,$p' "$meta" | tail -n +2)
actual_counts=$(psql -d "$scratch" -At -F '|' -c "$TABLE_COUNTS_SQL" 2> /dev/null)
[ "$expected_counts" = "$actual_counts" ] || fail_test COUNTS_MISMATCH

write_state PASSED ""
log "restore-test passed: $dump_name + $archive_name"
refresh_status
