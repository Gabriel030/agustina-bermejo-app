#!/bin/sh
set -eu

config=/tmp/alertmanager.yml
receiver_body=""

if [ -n "${ALERT_WEBHOOK_URL:-}" ]; then
  printf '%s' "$ALERT_WEBHOOK_URL" > /tmp/webhook_url
  receiver_body="${receiver_body}
    webhook_configs:
      - url_file: /tmp/webhook_url"
fi

if [ -n "${ALERT_TELEGRAM_BOT_TOKEN:-}" ] && [ -n "${ALERT_TELEGRAM_CHAT_ID:-}" ]; then
  printf '%s' "$ALERT_TELEGRAM_BOT_TOKEN" > /tmp/telegram_token
  receiver_body="${receiver_body}
    telegram_configs:
      - bot_token_file: /tmp/telegram_token
        chat_id: ${ALERT_TELEGRAM_CHAT_ID}"
fi

if [ -z "$receiver_body" ]; then
  echo "WARNING: no hay ALERT_WEBHOOK_URL ni Telegram configurados: las alertas se evaluan pero no se envian a nadie"
fi

cat > "$config" <<CONFIG
route:
  receiver: default
  group_by: [alertname]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
receivers:
  - name: default${receiver_body}
CONFIG

exec /bin/alertmanager --config.file="$config" --storage.path=/alertmanager
