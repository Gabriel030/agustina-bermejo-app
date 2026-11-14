#!/usr/bin/env bash
set -uo pipefail
. /opt/backup/lib.sh

KEEP_RECENT="${BACKUP_KEEP_RECENT:-4}"
KEEP_DAILY="${BACKUP_KEEP_DAILY:-7}"
KEEP_WEEKLY="${BACKUP_KEEP_WEEKLY:-4}"
KEEP_MONTHLY="${BACKUP_KEEP_MONTHLY:-6}"
AWK_FILE="${SELECT_EXPIRED_AWK:-/opt/backup/select-expired.awk}"

expired_from() {
  grep -E "^${BACKUP_PREFIX}_[0-9]{8}T[0-9]{6}Z\.dump(\.age)?$" | sort |
    awk -v keep_recent="$KEEP_RECENT" -v keep_daily="$KEEP_DAILY" -v keep_weekly="$KEEP_WEEKLY" -v keep_monthly="$KEEP_MONTHLY" -f "$AWK_FILE"
}

status=0

for expired in $(ls "$BACKUP_DIR" | expired_from); do
  log "rotation: removing local ${expired}"
  stem="${expired%.age}"
  stem="${stem%.dump}"
  remove_files "$BACKUP_DIR/$expired" "$BACKUP_DIR/$expired.sha256" "$BACKUP_DIR/$stem.meta"
done
find "$BACKUP_DIR" -maxdepth 1 -name '*.partial' -mmin +120 -delete 2> /dev/null

if s3_enabled; then
  rclone_env
  remote=$(s3_remote)
  listing=$(rclone lsf "$remote" --files-only) || exit 1
  for expired in $(echo "$listing" | expired_from); do
    log "rotation: removing remote ${expired}"
    stem="${expired%.age}"
    stem="${stem%.dump}"
    rclone deletefile "$remote/$expired" || status=1
    rclone deletefile "$remote/$expired.sha256" || status=1
    rclone deletefile "$remote/$stem.meta" || status=1
  done
fi

exit $status
