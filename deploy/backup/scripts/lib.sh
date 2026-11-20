#!/usr/bin/env bash

BACKUP_DIR="${BACKUP_DIR:-/backups}"
METRICS_DIR="${BACKUP_METRICS_DIR:-/metrics}"
ALERT_LABEL="${ALERT_LABEL:-agustina-bermejo}"
BACKUP_PREFIX="${BACKUP_PREFIX:-abdb}"
FILES_PREFIX="${FILES_PREFIX:-abfiles}"
DATA_DIR="${BACKUP_DATA_DIR:-/data}"

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
