#!/usr/bin/env bash
set -uo pipefail
. /opt/backup/lib.sh

max_hours="${BACKUP_MAX_AGE_HOURS:-5}"
latest_name=$(latest_complete_dump)
if [ -z "$latest_name" ]; then
  notify "NO HAY NINGUN BACKUP COMPLETO (dump + archivo de /data) en $BACKUP_DIR"
  record_alert CRITICAL BACKUP_STALE
  refresh_status
  exit 1
fi
latest="$BACKUP_DIR/$latest_name"
age_seconds=$(( $(date +%s) - $(epoch_of_file "$latest") ))
if [ "$age_seconds" -gt $(( max_hours * 3600 )) ]; then
  notify "el ultimo backup completo tiene mas de ${max_hours} horas ($latest_name)"
  record_alert WARNING BACKUP_STALE
  refresh_status
  exit 1
fi
log "freshness ok: $latest_name"
