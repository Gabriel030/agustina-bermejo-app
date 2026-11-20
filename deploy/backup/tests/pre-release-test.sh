#!/usr/bin/env bash
set -uo pipefail
scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scripts" && pwd)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
schema="$here/../../../docs/deploy/backup-status.schema.json"
. "$here/lib-test.sh"
use_scripts_as_opt_backup

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" COMPOSE_LOG="$work/compose.log"
mkdir -p "$BACKUP_DIR"
status_json="$BACKUP_DIR/status/status.json"

cat > "$work/fake-compose" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >> "$COMPOSE_LOG"
case "$*" in
  "stop backend") [ "${FAKE_STOP_FAIL:-0}" = "1" ] && exit 1; exit 0 ;;
  "start backend") exit 0 ;;
  *"/opt/backup/backup.sh"*)
    sleep "${FAKE_BACKUP_SLEEP:-0}"
    [ "${FAKE_BACKUP_FAIL:-0}" = "1" ] && exit 1
    prefix=$(echo "$*" | sed -n 's/.*BACKUP_PREFIX=\([^ ]*\).*/\1/p')
    files_prefix=$(echo "$*" | sed -n 's/.*FILES_PREFIX=\([^ ]*\).*/\1/p')
    stamp=$(date -u +%Y%m%dT%H%M%SZ)
    echo dump > "$BACKUP_DIR/${prefix}_$stamp.dump"
    echo files > "$BACKUP_DIR/${files_prefix}_$stamp.tar.zst"
    (cd "$BACKUP_DIR" && sha256sum "${prefix}_$stamp.dump" > "${prefix}_$stamp.dump.sha256" && sha256sum "${files_prefix}_$stamp.tar.zst" > "${files_prefix}_$stamp.tar.zst.sha256")
    exit 0 ;;
  *"sh -c"*) all="$*"; command=${all#*sh -c }; eval "${command//\/backups/$BACKUP_DIR}" ;;
  *"/opt/backup/status.sh"*) [ "${FAKE_STATUS_FAIL:-0}" = "1" ] && exit 1; "$SCRIPTS/status.sh" ;;
esac
FAKE
chmod +x "$work/fake-compose"
export COMPOSE_CMD="$work/fake-compose" SCRIPTS="$scripts"
run_pre_release() { "$scripts/pre-release.sh" "$@" > "$work/output.log" 2>&1; echo $?; }

: > "$COMPOSE_LOG"
check "una version invalida se rechaza antes de tocar nada" "2" "$(run_pre_release 'v1.2.3; rm -rf /')"
check "una version invalida no llama a compose" "0" "$(wc -l < "$COMPOSE_LOG" | tr -d ' ')"
check "una version sin formato de version se rechaza" "2" "$(run_pre_release latest)"

: > "$COMPOSE_LOG"
check "un pre-release correcto termina bien" "0" "$(run_pre_release v1.2.3)"
check "el orden es parar backend, backup, comprobar, arrancar backend y escribir estado" "stop backend|exec -T -e BACKUP_PREFIX=abpre-v1.2.3 -e FILES_PREFIX=abprefiles-v1.2.3 -e BACKUP_SKIP_ROTATION=true -e BACKUP_LOCK_WAIT_SECONDS=600 backup /opt/backup/backup.sh|exec -T backup sh -c ls /backups/abpre-v1.2.3_*.dump* > /dev/null 2>&1|start backend|exec -T backup /opt/backup/status.sh" "$(paste -sd'|' "$COMPOSE_LOG")"
check "el backup del pre-release no usa el prefijo de la rotacion" "1" "$(ls "$BACKUP_DIR"/abpre-v1.2.3_*.dump | wc -l | tr -d ' ')"
check "el estado muestra el PRE_RELEASE con su version" "v1.2.3" "$(jq -r '.tiers[] | select(.tier=="PRE_RELEASE") | .label' "$status_json")"
check "el estado del pre-release cumple el esquema" "" "$(python3 "$here/validate-status.py" "$schema" "$status_json")"

: > "$COMPOSE_LOG"
check "si el backup falla el pre-release falla" "1" "$(FAKE_BACKUP_FAIL=1 run_pre_release v1.2.4)"
check "si el backup falla igual se vuelve a arrancar backend" "1" "$(grep -c '^start backend$' "$COMPOSE_LOG")"

: > "$COMPOSE_LOG"
check "si no se puede parar backend no se hace el backup" "1" "$(FAKE_STOP_FAIL=1 run_pre_release v1.2.5)"
check "si no se puede parar backend no se llama al backup" "0" "$(grep -c 'backup.sh' "$COMPOSE_LOG")"
check "si no se puede parar backend igual se intenta arrancarlo" "1" "$(grep -c '^start backend$' "$COMPOSE_LOG")"

: > "$COMPOSE_LOG"
check "si no se puede actualizar el estado final el pre-release falla" "1" "$(FAKE_STATUS_FAIL=1 run_pre_release v1.2.6)"
check "aunque falle el estado final el backend ya se arranco" "1" "$(grep -c '^start backend$' "$COMPOSE_LOG")"
check "una version de mas de 32 caracteres se rechaza" "2" "$(run_pre_release v1.2.3-aaaaaaaaaaaaaaaaaaaaaaaaaaaa)"

: > "$COMPOSE_LOG"
FAKE_BACKUP_SLEEP=30 "$scripts/pre-release.sh" v1.2.7 > "$work/output.log" 2>&1 &
pre_release_pid=$!
sleep 2
kill -TERM "$pre_release_pid"
wait "$pre_release_pid" 2> /dev/null
interrupted_status=$?
pkill -f "$work/fake-compose" 2> /dev/null; pkill -f "sleep 30" 2> /dev/null; sleep 1
check "si llega SIGTERM durante el backup el backend se vuelve a arrancar" "1" "$(grep -c '^start backend$' "$COMPOSE_LOG")"
check "tras SIGTERM el pre-release termina con error" "143" "$interrupted_status"

rm -rf "$BACKUP_DIR"
mkdir -p "$BACKUP_DIR"
for stamp in 20410101T060000Z 20410102T060000Z 20410103T060000Z 20410309T060000Z; do
  echo d > "$BACKUP_DIR/abdb_$stamp.dump"; echo f > "$BACKUP_DIR/abfiles_$stamp.tar.zst"
  echo x > "$BACKUP_DIR/abdb_$stamp.dump.sha256"; echo x > "$BACKUP_DIR/abfiles_$stamp.tar.zst.sha256"
done
echo d > "$BACKUP_DIR/abpre-v0.9.0_20400101T060000Z.dump"; echo f > "$BACKUP_DIR/abprefiles-v0.9.0_20400101T060000Z.tar.zst"
echo x > "$BACKUP_DIR/abpre-v0.9.0_20400101T060000Z.dump.sha256"; echo x > "$BACKUP_DIR/abprefiles-v0.9.0_20400101T060000Z.tar.zst.sha256"
BACKUP_NOW_EPOCH=$(date -u -d '2041-03-10 15:00:00' +%s) BACKUP_TZ_OFFSET_SECONDS=0 BACKUP_KEEP_DAILY=1 BACKUP_KEEP_WEEKLY=0 BACKUP_KEEP_QUARTERLY=1 "$scripts/rotate.sh" > /dev/null
check "la rotacion nunca borra un pre-release aunque sea muy viejo" "4" "$(find "$BACKUP_DIR" -maxdepth 1 -name 'abpre*' | wc -l | tr -d ' ')"
check "la rotacion sigue borrando los backups normales vencidos" "0" "$(find "$BACKUP_DIR" -maxdepth 1 -name 'abdb_20410101*' | wc -l | tr -d ' ')"

finish
