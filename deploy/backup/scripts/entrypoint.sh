#!/usr/bin/env bash
set -euo pipefail
. /opt/backup/lib.sh

if [ "${1:-}" != "cron" ]; then
  exec "$@"
fi

: "${PGPASSWORD:?PGPASSWORD es obligatorio}"
schedule="${BACKUP_SCHEDULE:-0 */6 * * *}"
freshness="${BACKUP_FRESHNESS_SCHEDULE:-30 9 * * *}"

env | grep -E '^(PG|BACKUP_|AGE_|ALERT_|TZ=)' | sed -e 's/^/export /' -e "s/=/='/" -e "s/\$/'/" > /opt/backup/cron.env
chmod 600 /opt/backup/cron.env

cat > /etc/crontabs/root <<CRON
$schedule . /opt/backup/cron.env; /opt/backup/backup.sh > /proc/1/fd/1 2>&1
$freshness . /opt/backup/cron.env; /opt/backup/check-freshness.sh > /proc/1/fd/1 2>&1
CRON

log "backup scheduler ready: '$schedule' (TZ=${TZ:-UTC})"
if [ "${BACKUP_RUN_ON_START:-false}" = "true" ]; then
  /opt/backup/backup.sh || true
fi
exec crond -f -l 8
