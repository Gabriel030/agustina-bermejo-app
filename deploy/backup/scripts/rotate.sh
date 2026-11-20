#!/usr/bin/env bash
set -uo pipefail
. /opt/backup/lib.sh

KEEP_DAILY="${BACKUP_KEEP_DAILY:-7}"
KEEP_WEEKLY="${BACKUP_KEEP_WEEKLY:-4}"
KEEP_QUARTERLY="${BACKUP_KEEP_QUARTERLY:-0}"
AWK_FILE="${SELECT_EXPIRED_AWK:-/opt/backup/select-expired.awk}"

tz_offset_seconds() {
  local zone sign hours minutes
  zone=$(date +%z)
  sign=1
  [ "${zone:0:1}" = "-" ] && sign=-1
  hours=$((10#${zone:1:2}))
  minutes=$((10#${zone:3:2}))
  echo $(( sign * (hours * 3600 + minutes * 60) ))
}

NOW_EPOCH="${BACKUP_NOW_EPOCH:-$(date -u +%s)}"
TZ_OFFSET="${BACKUP_TZ_OFFSET_SECONDS:-$(tz_offset_seconds)}"

ORPHAN_MINUTES=$(( ${BACKUP_ORPHAN_HOURS:-6} * 60 ))

expired_from() {
  complete_dumps_from_listing | sort |
    awk -v keep_daily="$KEEP_DAILY" -v keep_weekly="$KEEP_WEEKLY" -v keep_quarterly="$KEEP_QUARTERLY" \
      -v now_epoch="$NOW_EPOCH" -v tz_offset="$TZ_OFFSET" -f "$AWK_FILE"
}

status=0

for expired in $(find "$BACKUP_DIR" -maxdepth 1 -type f -printf '%f\n' | expired_from); do
  log "rotation: removing local pair of ${expired}"
  stamp=$(stamp_of "$expired")
  stem="${expired%.age}"
  stem="${stem%.dump}"
  remove_files "$BACKUP_DIR/$expired" "$BACKUP_DIR/$expired.sha256" "$BACKUP_DIR/$stem.meta"
  for companion in $(files_archive_names "$stamp"); do
    remove_files "$BACKUP_DIR/$companion"
  done
done
find "$BACKUP_DIR" -maxdepth 1 -name '*.partial' -mmin +120 -delete 2> /dev/null

while IFS= read -r archive; do
  stamp=$(stamp_of "$archive")
  if [ ! -e "$BACKUP_DIR/${BACKUP_PREFIX}_${stamp}.dump" ] && [ ! -e "$BACKUP_DIR/${BACKUP_PREFIX}_${stamp}.dump.age" ]; then
    log "rotation: removing orphan files archive ${archive}"
    remove_files "$BACKUP_DIR/$archive"
  fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name "${FILES_PREFIX}_*" -mmin "+$ORPHAN_MINUTES" -printf '%f\n')

if s3_enabled; then
  rclone_env
  remote=$(s3_remote)
  listing=$(rclone lsf "$remote" --files-only) || exit 1
  for expired in $(echo "$listing" | expired_from); do
    log "rotation: removing remote pair of ${expired}"
    stamp=$(stamp_of "$expired")
    stem="${expired%.age}"
    stem="${stem%.dump}"
    rclone deletefile "$remote/$expired" || status=1
    rclone deletefile "$remote/$expired.sha256" || status=1
    rclone deletefile "$remote/$stem.meta" || status=1
    for companion in $(files_archive_names "$stamp"); do
      echo "$listing" | grep -qx "$companion" && { rclone deletefile "$remote/$companion" || status=1; }
    done
  done
fi

exit $status
