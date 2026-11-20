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

dump_name=$(latest_complete_dump)
[ -n "$dump_name" ] || fail_test RESTORE_FAILED
stamp=$(stamp_of "$dump_name")
archive_name=$(for candidate in "${FILES_PREFIX}_${stamp}.tar.zst" "${FILES_PREFIX}_${stamp}.tar.zst.age"; do [ -f "$BACKUP_DIR/$candidate" ] && echo "$candidate"; done | head -n 1)
[ -n "$archive_name" ] || fail_test RESTORE_FAILED

for name in "$dump_name" "$archive_name"; do
  (cd "$BACKUP_DIR" && sha256sum -c --status "$name.sha256" 2> /dev/null) || fail_test CHECKSUM_MISMATCH
done

work=$(mktemp -d "$BACKUP_DIR/.restore-test.XXXXXX") || fail_test RESTORE_FAILED
dump_file="$BACKUP_DIR/$dump_name"
archive_file="$BACKUP_DIR/$archive_name"
decrypt() {
  if [ "${archive_name##*.}" = "age" ]; then
    age -d -i "$AGE_IDENTITY_FILE" "$1"
  else
    cat "$1"
  fi
}
if [ "${dump_name##*.}" = "age" ]; then
  if [ -z "${AGE_IDENTITY_FILE:-}" ] || [ ! -r "${AGE_IDENTITY_FILE:-}" ]; then
    log "backup cifrado y sin AGE_IDENTITY_FILE: solo se verificaron los checksums, no se restauro"
    exit 0
  fi
  age -d -i "$AGE_IDENTITY_FILE" -o "$work/dump" "$dump_file" || fail_test RESTORE_FAILED
  dump_file="$work/dump"
fi

decrypt "$archive_file" | tar --zstd -tf - > /dev/null 2>&1 || fail_test RESTORE_FAILED

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
