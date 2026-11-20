#!/usr/bin/env bash
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$here/lib-test.sh"
scripts="$(cd "$here/../scripts" && pwd)"
schema="$here/../../../docs/deploy/backup-status.schema.json"
use_scripts_as_opt_backup

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin" "$work/backups"
cat > "$work/bin/docker" <<'FAKE'
#!/usr/bin/env bash
case "$*" in
  *"ls /in"*) echo "abdb_20410310T060000Z.dump" ;;
  *"restore.sh"*) exit "${FAKE_RESTORE_RC:-0}" ;;
  *"verify-counts.sh"*) exit "${FAKE_COUNTS_RC:-0}" ;;
  *"flyway"*) exit "${FAKE_FLYWAY_RC:-0}" ;;
  *"pg_isready"*) exit 0 ;;
esac
exit 0
FAKE
chmod +x "$work/bin/docker"
export PATH="$work/bin:$PATH"
status_dir="$work/backups/status"
verify() { "$here/../verify-restore.sh" --dir "$work/backups" --migrations "$work/migrations" "$@" > "$work/output.log" 2>&1; echo $?; }
state() { grep -E '^(result|failure_code)=' "$status_dir/restore-test" 2> /dev/null | tr '\n' ' '; }
mkdir -p "$work/migrations"
status_validates() {
  BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" "$scripts/status.sh" > /dev/null 2>&1
  python3 "$here/validate-status.py" "$schema" "$status_dir/status.json"
}
last_restore() { jq -r '.lastRestoreTest | if . == null then "null" else "\(.result) \(.failureCode)" end' "$status_dir/status.json"; }

check "sin --status-dir el simulacro no escribe estado" "0 0" "$(rc=$(FAKE_RESTORE_RC=0 verify --skip-flyway); echo "$rc $(ls "$status_dir" 2> /dev/null | wc -l | tr -d ' ')")"
check "un simulacro exitoso con --status-dir registra PASSED" "0 result=PASSED failure_code= " "$(rc=$(verify --skip-flyway --status-dir "$status_dir"); echo "$rc $(state)")"
check "si falla restore.sh se registra FAILED RESTORE_FAILED" "1 result=FAILED failure_code=RESTORE_FAILED " "$(rc=$(FAKE_RESTORE_RC=1 verify --skip-flyway --status-dir "$status_dir"); echo "$rc $(state)")"
check "si los conteos no coinciden se registra FAILED COUNTS_MISMATCH" "1 result=FAILED failure_code=COUNTS_MISMATCH " "$(rc=$(FAKE_COUNTS_RC=1 verify --skip-flyway --status-dir "$status_dir"); echo "$rc $(state)")"
check "si falla flyway validate se registra FAILED SCHEMA_VALIDATION_FAILED" "1 result=FAILED failure_code=SCHEMA_VALIDATION_FAILED " "$(rc=$(FAKE_FLYWAY_RC=1 verify --status-dir "$status_dir"); echo "$rc $(state)")"
check "el archivo de estado queda legible por el estado de los backups" "ran_at=" "$(grep -o '^ran_at=' "$status_dir/restore-test")"

verify --skip-flyway --status-dir "$status_dir" > /dev/null
check "el estado que deja un simulacro exitoso cumple el esquema" " PASSED null" "$(status_validates) $(last_restore)"
FAKE_RESTORE_RC=1 verify --skip-flyway --status-dir "$status_dir" > /dev/null
check "el estado que deja RESTORE_FAILED cumple el esquema" " FAILED RESTORE_FAILED" "$(status_validates) $(last_restore)"
FAKE_COUNTS_RC=1 verify --skip-flyway --status-dir "$status_dir" > /dev/null
check "el estado que deja COUNTS_MISMATCH cumple el esquema" " FAILED COUNTS_MISMATCH" "$(status_validates) $(last_restore)"
FAKE_FLYWAY_RC=1 verify --status-dir "$status_dir" > /dev/null
check "el estado que deja SCHEMA_VALIDATION_FAILED cumple el esquema" " FAILED SCHEMA_VALIDATION_FAILED" "$(status_validates) $(last_restore)"

finish
