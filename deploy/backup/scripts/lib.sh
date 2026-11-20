#!/usr/bin/env bash

BACKUP_DIR="${BACKUP_DIR:-/backups}"
METRICS_DIR="${BACKUP_METRICS_DIR:-/metrics}"
ALERT_LABEL="${ALERT_LABEL:-agustina-bermejo}"
BACKUP_PREFIX="${BACKUP_PREFIX:-abdb}"
FILES_PREFIX="${FILES_PREFIX:-abfiles}"

log() {
  printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

remove_files() {
  local file
  for file in "$@"; do
    if [ -n "$file" ] && [ -f "$file" ]; then
      unlink "$file"
    fi
  done
}

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr '\n' ' '
}

notify() {
  local message="[$ALERT_LABEL] $1"
  log "ALERT: $message"
  if [ -n "${ALERT_WEBHOOK_URL:-}" ]; then
    local payload
    payload=$(printf '{"text":"%s","content":"%s"}' "$(json_escape "$message")" "$(json_escape "$message")")
    curl -fsS -m 15 -H 'Content-Type: application/json' -d "$payload" "$ALERT_WEBHOOK_URL" > /dev/null || log "webhook notification failed"
  fi
  if [ -n "${ALERT_TELEGRAM_BOT_TOKEN:-}" ] && [ -n "${ALERT_TELEGRAM_CHAT_ID:-}" ]; then
    curl -fsS -m 15 "https://api.telegram.org/bot${ALERT_TELEGRAM_BOT_TOKEN}/sendMessage" \
      --data-urlencode "chat_id=${ALERT_TELEGRAM_CHAT_ID}" --data-urlencode "text=${message}" > /dev/null || log "telegram notification failed"
  fi
}

ping_heartbeat() {
  if [ -n "${BACKUP_HEARTBEAT_URL:-}" ]; then
    curl -fsS -m 15 "$BACKUP_HEARTBEAT_URL" > /dev/null || log "heartbeat ping failed"
  fi
}

s3_enabled() {
  [ -n "${BACKUP_S3_BUCKET:-}" ]
}

rclone_env() {
  export RCLONE_CONFIG_BACKUPS3_TYPE=s3
  export RCLONE_CONFIG_BACKUPS3_PROVIDER="${BACKUP_S3_PROVIDER:-Other}"
  export RCLONE_CONFIG_BACKUPS3_ENDPOINT="${BACKUP_S3_ENDPOINT:-}"
  export RCLONE_CONFIG_BACKUPS3_REGION="${BACKUP_S3_REGION:-}"
  export RCLONE_CONFIG_BACKUPS3_ACCESS_KEY_ID="${BACKUP_S3_ACCESS_KEY_ID:-}"
  export RCLONE_CONFIG_BACKUPS3_SECRET_ACCESS_KEY="${BACKUP_S3_SECRET_ACCESS_KEY:-}"
}

s3_remote() {
  printf 'BACKUPS3:%s/%s' "$BACKUP_S3_BUCKET" "${BACKUP_S3_PREFIX:-agustina-bermejo}"
}

stamp_of() {
  basename "$1" | sed -E 's/^[^_]*_([0-9]{8}T[0-9]{6}Z)\..*$/\1/'
}

files_archive_names() {
  printf '%s\n' "${FILES_PREFIX}_$1.tar.zst" "${FILES_PREFIX}_$1.tar.zst.sha256" "${FILES_PREFIX}_$1.tar.zst.age" "${FILES_PREFIX}_$1.tar.zst.age.sha256"
}

latest_local_dump() {
  find "$BACKUP_DIR" -maxdepth 1 -type f \( -name "${BACKUP_PREFIX}_*.dump" -o -name "${BACKUP_PREFIX}_*.dump.age" \) | sort | tail -n 1
}

epoch_of_file() {
  stat -c %Y "$1" 2> /dev/null || stat -f %m "$1"
}

write_metrics() {
  local status="$1" timestamp="$2" size="$3"
  mkdir -p "$METRICS_DIR" 2> /dev/null || return 0
  local tmp="$METRICS_DIR/.backup.prom.tmp"
  {
    echo "# TYPE backup_last_run_success gauge"
    echo "backup_last_run_success $status"
    echo "# TYPE backup_last_success_timestamp_seconds gauge"
    echo "backup_last_success_timestamp_seconds $timestamp"
    echo "# TYPE backup_last_size_bytes gauge"
    echo "backup_last_size_bytes $size"
  } > "$tmp" && mv "$tmp" "$METRICS_DIR/backup.prom"
}

append_files_metric() {
  [ -f "$METRICS_DIR/backup.prom" ] || return 0
  {
    echo "# TYPE backup_last_files_size_bytes gauge"
    echo "backup_last_files_size_bytes $1"
  } >> "$METRICS_DIR/backup.prom"
}

warn_once_per_day() {
  local name="$1" message="$2" marker today
  marker="$METRICS_DIR/.alerted_$name"
  today=$(date -u +%Y%m%d)
  [ "$(cat "$marker" 2> /dev/null)" = "$today" ] && return 0
  notify "$message"
  mkdir -p "$METRICS_DIR" 2> /dev/null && echo "$today" > "$marker"
}

warn_unencrypted_once() {
  warn_once_per_day unencrypted "BACKUP SIN CIFRAR: falta AGE_RECIPIENT, los backups quedan legibles en el disco"
}

verify_while_streaming() {
  local rc_file="$1"
  shift
  tee >( { "$@" > /dev/null 2>&1; echo $? > "$rc_file"; cat > /dev/null; } )
}

count_files_in_tar_listing() {
  local count_file="$1"
  awk -v count_file="$count_file" '
    /^\.\// { if ($0 !~ /\/$/) files++; next }
    { print > "/dev/stderr" }
    END { print files + 0 > count_file }
  '
}

wait_for_file() {
  local file="$1" _
  for _ in $(seq 1 60); do
    [ -s "$file" ] && return 0
    sleep 1
  done
  return 1
}

complete_dumps_from_listing() {
  local listing dump_name stamp
  listing=$(cat)
  for dump_name in $(echo "$listing" | grep -E "^${BACKUP_PREFIX}_[0-9]{8}T[0-9]{6}Z\.dump(\.age)?$"); do
    stamp=$(stamp_of "$dump_name")
    echo "$listing" | grep -qxF "$dump_name.sha256" || continue
    for archive in "${FILES_PREFIX}_${stamp}.tar.zst" "${FILES_PREFIX}_${stamp}.tar.zst.age"; do
      if echo "$listing" | grep -qxF "$archive" && echo "$listing" | grep -qxF "$archive.sha256"; then
        echo "$dump_name"
        break
      fi
    done
  done
}

latest_complete_dump() {
  find "$BACKUP_DIR" -maxdepth 1 -type f -printf '%f\n' | complete_dumps_from_listing | sort | tail -n 1
}

write_cron_env() {
  local target="$1" name
  : > "$target"
  chmod 600 "$target"
  for name in $(compgen -e | grep -E '^(PG|BACKUP_|AGE_|ALERT_)|^TZ$'); do
    printf 'export %s=%q\n' "$name" "${!name}" >> "$target"
  done
}

upload_pair_to_s3() {
  local dump="$1" archive="$2" meta="$3" remote name done_name uploaded=()
  rclone_env
  remote=$(s3_remote)
  for name in "$dump" "$dump.sha256" "$archive" "$archive.sha256" "$meta"; do
    if rclone copyto "$name" "$remote/$(basename "$name")"; then
      uploaded+=("$(basename "$name")")
    else
      log "fallo la subida a S3 de $(basename "$name"): se retira lo ya subido de este par"
      for done_name in "${uploaded[@]}"; do
        rclone deletefile "$remote/$done_name" || log "no se pudo retirar $done_name de S3"
      done
      return 1
    fi
  done
  log "uploaded to $remote"
}
