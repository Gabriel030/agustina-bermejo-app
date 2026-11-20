#!/usr/bin/env bash
set -uo pipefail
umask 022
. /opt/backup/lib.sh

AWK_FILE="${SELECT_EXPIRED_AWK:-/opt/backup/select-expired.awk}"
TARGET_RPO_HOURS="${BACKUP_TARGET_RPO_HOURS:-4}"
KEEP_DAILY="${BACKUP_KEEP_DAILY:-7}"
KEEP_WEEKLY="${BACKUP_KEEP_WEEKLY:-4}"
KEEP_QUARTERLY="${BACKUP_KEEP_QUARTERLY:-0}"
PRE_RELEASE_LIMIT=5
RESTORE_TEST_MAX_DAYS="${BACKUP_RESTORE_TEST_MAX_DAYS:-14}"
VERSION_PATTERN='v?[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?'

tz_offset_seconds() {
  local zone sign
  zone=$(date +%z)
  sign=1
  [ "${zone:0:1}" = "-" ] && sign=-1
  echo $(( sign * (10#${zone:1:2} * 3600 + 10#${zone:3:2} * 60) ))
}

file_size() {
  stat -c %s "$1" 2> /dev/null || stat -f %z "$1" 2> /dev/null || echo 0
}

checksum_state() {
  local name
  for name in "$@"; do
    [ -f "$BACKUP_DIR/$name" ] && [ -f "$BACKUP_DIR/$name.sha256" ] || { echo DAMAGED; return; }
    (cd "$BACKUP_DIR" && sha256sum -c --status "$name.sha256" 2> /dev/null) || { echo DAMAGED; return; }
  done
  echo VERIFIED
}

pair_archive_of() {
  local files_prefix="$1" stamp="$2" archive
  for archive in "${files_prefix}_${stamp}.tar.zst" "${files_prefix}_${stamp}.tar.zst.age"; do
    [ -f "$BACKUP_DIR/$archive" ] && { echo "$archive"; return; }
  done
}

json_file_names() {
  local first=true name
  printf '['
  for name in "$@"; do
    [ -f "$BACKUP_DIR/$name" ] || continue
    $first || printf ','
    first=false
    printf '"%s"' "$name"
  done
  printf ']'
}

tier_json() {
  local tier="$1" dump_name="$2" kept="$3" label="$4" files_prefix="$5" stamp archive meta state label_json
  stamp=$(stamp_of "$dump_name")
  archive=$(pair_archive_of "$files_prefix" "$stamp")
  meta="${dump_name%.age}"
  meta="${meta%.dump}.meta"
  state=$(checksum_state "$dump_name" "$archive")
  label_json=null
  [ -n "$label" ] && label_json="\"$label\""
  printf '{"tier":"%s","takenAt":"%s","label":%s,"dumpSizeBytes":%s,"filesSizeBytes":%s,"verification":"%s","kept":%s,"fileNames":%s}' \
    "$tier" "$(iso_of_stamp "$stamp")" "$label_json" "$(file_size "$BACKUP_DIR/$dump_name")" "$(file_size "$BACKUP_DIR/${archive:-missing}")" \
    "$state" "$kept" "$(json_file_names "$dump_name" "$dump_name.sha256" "$archive" "$archive.sha256" "$meta")"
}

check_disk_space() {
  local total_kb free_kb
  read -r total_kb free_kb < <(df -Pk "$BACKUP_DIR" 2> /dev/null | awk 'NR == 2 { print $2, $4 }')
  total_bytes=$(( ${total_kb:-0} * 1024 ))
  free_bytes=$(( ${free_kb:-0} * 1024 ))
  [ "$total_bytes" -gt 0 ] || return 0
  if [ $(( free_bytes * 100 / total_bytes )) -lt 5 ]; then
    warn_once_per_day disk-critical "ESPACIO DE BACKUPS CRITICO: queda menos del 5 % libre en $BACKUP_DIR"
    alert_once_per_day disk-critical CRITICAL DISK_CRITICAL
  elif [ $(( free_bytes * 100 / total_bytes )) -lt 15 ]; then
    warn_once_per_day disk-low "espacio de backups bajo: queda menos del 15 % libre en $BACKUP_DIR"
    alert_once_per_day disk-low WARNING DISK_LOW
  fi
}

check_restore_test_age() {
  local state="$STATUS_DIR/restore-test" oldest_stamp oldest_epoch last_epoch ran_at
  [ -n "$complete_list" ] || return 0
  oldest_stamp=$(stamp_of "$(printf '%s\n' "$complete_list" | head -n 1)")
  oldest_epoch=$(date -u -d "$(iso_of_stamp "$oldest_stamp")" +%s 2> /dev/null) || return 0
  [ $(( now_epoch - oldest_epoch )) -gt $(( RESTORE_TEST_MAX_DAYS * 86400 )) ] || return 0
  ran_at=$(sed -n 's/^ran_at=//p' "$state" 2> /dev/null)
  last_epoch=$(date -u -d "$ran_at" +%s 2> /dev/null) || last_epoch=0
  if [ $(( now_epoch - last_epoch )) -gt $(( RESTORE_TEST_MAX_DAYS * 86400 )) ]; then
    warn_once_per_day restore-stale "no hay una prueba de restauracion de los ultimos ${RESTORE_TEST_MAX_DAYS} dias"
    alert_once_per_day restore-stale WARNING RESTORE_TEST_FAILED
  fi
}

restore_test_json() {
  local state="$STATUS_DIR/restore-test" ran_at result code
  [ -f "$state" ] || { printf 'null'; return; }
  ran_at=$(sed -n 's/^ran_at=//p' "$state")
  result=$(sed -n 's/^result=//p' "$state")
  code=$(sed -n 's/^failure_code=//p' "$state")
  if ! printf '%s' "$ran_at" | grep -qE '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$'; then printf 'null'; return; fi
  case "$result" in
    PASSED) printf '{"ranAt":"%s","result":"PASSED","failureCode":null}' "$ran_at" ;;
    FAILED)
      case "$code" in
        CHECKSUM_MISMATCH | RESTORE_FAILED | COUNTS_MISMATCH | SCHEMA_VALIDATION_FAILED) printf '{"ranAt":"%s","result":"FAILED","failureCode":"%s"}' "$ran_at" "$code" ;;
        *) printf 'null' ;;
      esac
      ;;
    *) printf 'null' ;;
  esac
}

alerts_json() {
  local first=true line
  printf '['
  [ -f "$STATUS_DIR/alerts.log" ] || { printf ']'; return; }
  while IFS= read -r line; do
    if printf '%s' "$line" | grep -qE '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z (WARNING|CRITICAL) (BACKUP_FAILED|BACKUP_STALE|BACKUP_DAMAGED|RESTORE_TEST_FAILED|DISK_LOW|DISK_CRITICAL|ENCRYPTION_DISABLED|DATA_DIR_EMPTY)$'; then
      $first || printf ','
      first=false
      # shellcheck disable=SC2086
      set -- $line
      printf '{"at":"%s","level":"%s","code":"%s"}' "$1" "$2" "$3"
    fi
  done < <(tail -n 200 "$STATUS_DIR/alerts.log")
  printf ']'
}

ensure_status_dir || { log "no se puede crear $STATUS_DIR"; exit 1; }
total_bytes=0
free_bytes=0
now_epoch=${BACKUP_NOW_EPOCH:-$(date -u +%s)}
tz_offset=${BACKUP_TZ_OFFSET_SECONDS:-$(tz_offset_seconds)}

complete_list=$(find "$BACKUP_DIR" -maxdepth 1 -type f -printf '%f\n' | complete_dumps_from_listing | sort)
tier_lines=""
[ -n "$complete_list" ] && tier_lines=$(printf '%s\n' "$complete_list" | awk -v keep_daily="$KEEP_DAILY" -v keep_weekly="$KEEP_WEEKLY" -v keep_quarterly="$KEEP_QUARTERLY" \
  -v now_epoch="$now_epoch" -v tz_offset="$tz_offset" -v print_tiers=1 -f "$AWK_FILE")

tiers=()
while IFS=$'\t' read -r tier dump_name kept; do
  [ -n "$tier" ] && tiers+=("$(tier_json "$tier" "$dump_name" "$kept" "" "$FILES_PREFIX")")
done <<< "$tier_lines"

while IFS= read -r pre_name; do
  [ -n "$pre_name" ] || continue
  version=$(printf '%s' "$pre_name" | sed -E "s/^abpre-(${VERSION_PATTERN})_[0-9]{8}T[0-9]{6}Z\.dump(\.age)?\$/\1/")
  tiers+=("$(tier_json PRE_RELEASE "$pre_name" 1 "$version" "abprefiles-$version")")
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -printf '%f\n' | grep -E "^abpre-${VERSION_PATTERN}_[0-9]{8}T[0-9]{6}Z\.dump(\.age)?\$" | sort -t_ -k2 -r | head -n "$PRE_RELEASE_LIMIT")

tiers_joined=$(IFS=,; printf '%s' "${tiers[*]:-}")
if printf '%s' "$tiers_joined" | grep -q '"verification":"DAMAGED"'; then
  warn_once_per_day damaged "BACKUP DANADO: un checksum no coincide en $BACKUP_DIR"
  alert_once_per_day damaged CRITICAL BACKUP_DAMAGED
fi
check_disk_space
check_restore_test_age

last_complete=null
[ -n "$complete_list" ] && last_complete="\"$(iso_of_stamp "$(stamp_of "$(printf '%s\n' "$complete_list" | tail -n 1)")")\""
failures=$(cat "$STATUS_DIR/consecutive-failures" 2> /dev/null || echo 0)
case "$failures" in '' | *[!0-9]*) failures=0 ;; esac

tmp="$STATUS_DIR/.status.json.$$"
trap 'rm -f "$tmp"' EXIT
printf '{"schemaVersion":1,"generatedAt":"%s","targetRpoHours":%s,"destination":{"path":"%s","freeBytes":%s,"totalBytes":%s},"tiers":[%s],"lastRestoreTest":%s,"alerts":%s,"consecutiveFailures":%s,"lastCompleteBackupAt":%s}\n' \
  "$(iso_now)" "$TARGET_RPO_HOURS" "$(json_escape "$BACKUP_DIR")" "$free_bytes" "$total_bytes" "$tiers_joined" "$(restore_test_json)" "$(alerts_json)" "$failures" "$last_complete" > "$tmp" || exit 1
chmod 644 "$tmp"
mv -f "$tmp" "$STATUS_DIR/status.json"
log "status.json updated"
