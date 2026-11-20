#!/usr/bin/env bash
set -uo pipefail

url="${UPTIME_URL:-http://backend:8080/actuator/health}"
interval="${UPTIME_INTERVAL_SECONDS:-30}"
threshold="${UPTIME_FAILURES_BEFORE_ALERT:-3}"
timeout="${UPTIME_TIMEOUT_SECONDS:-5}"
label="${ALERT_LABEL:-agustina-bermejo}"
once="${UPTIME_RUN_ONCE:-false}"

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr '\n' ' '
}

notify() {
  local message="[$label] $1"
  echo "$(date -u +%FT%TZ) ALERT: $message"
  if [ -n "${ALERT_WEBHOOK_URL:-}" ]; then
    curl -fsS -m 15 -H 'Content-Type: application/json' \
      -d "$(printf '{"text":"%s","content":"%s"}' "$(json_escape "$message")" "$(json_escape "$message")")" \
      "$ALERT_WEBHOOK_URL" > /dev/null || echo "webhook notification failed"
  fi
  if [ -n "${ALERT_TELEGRAM_BOT_TOKEN:-}" ] && [ -n "${ALERT_TELEGRAM_CHAT_ID:-}" ]; then
    curl -fsS -m 15 "https://api.telegram.org/bot${ALERT_TELEGRAM_BOT_TOKEN}/sendMessage" \
      --data-urlencode "chat_id=${ALERT_TELEGRAM_CHAT_ID}" --data-urlencode "text=${message}" > /dev/null || echo "telegram notification failed"
  fi
}

failures=0
down_since=""
echo "uptime-check: $url every ${interval}s, alert after $threshold consecutive failures"

while true; do
  if curl -fsS -m "$timeout" -o /dev/null "$url" 2> /dev/null; then
    if [ -n "$down_since" ]; then
      notify "RECUPERADO: $url responde de nuevo (caido desde $down_since)"
      down_since=""
    fi
    failures=0
    if [ -n "${UPTIME_HEARTBEAT_URL:-}" ]; then
      curl -fsS -m 10 "$UPTIME_HEARTBEAT_URL" > /dev/null || echo "heartbeat ping failed"
    fi
  else
    failures=$((failures + 1))
    echo "$(date -u +%FT%TZ) check failed ($failures/$threshold)"
    if [ "$failures" -eq "$threshold" ]; then
      down_since="$(date -u +%FT%TZ)"
      notify "CAIDA: $url no responde ($threshold chequeos seguidos fallaron)"
    fi
  fi
  [ "$once" = "true" ] && exit 0
  sleep "$interval"
done
