#!/usr/bin/env bash
set -uo pipefail
. /opt/backup/lib.sh

max_hours="${BACKUP_MAX_AGE_HOURS:-5}"
latest=$(latest_local_dump)
if [ -z "$latest" ]; then
  notify "NO HAY NINGUN BACKUP en $BACKUP_DIR"
  exit 1
fi
age_seconds=$(( $(date +%s) - $(epoch_of_file "$latest") ))
if [ "$age_seconds" -gt $(( max_hours * 3600 )) ]; then
  notify "el ultimo backup tiene mas de ${max_hours} horas ($(basename "$latest"))"
  exit 1
fi
stamp=$(stamp_of "$latest")
companion_found=false
for companion in $(files_archive_names "$stamp"); do
  [ -f "$BACKUP_DIR/$companion" ] && companion_found=true
done
if [ "$companion_found" != "true" ]; then
  notify "el ultimo backup $(basename "$latest") no tiene archivo de /data asociado"
  exit 1
fi
log "freshness ok: $(basename "$latest")"
