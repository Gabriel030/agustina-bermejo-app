#!/usr/bin/env bash
set -uo pipefail
scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scripts" && pwd)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
schema="$here/../../../docs/deploy/backup-status.schema.json"
. "$here/lib-test.sh"
use_scripts_as_opt_backup

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" BACKUP_TZ_OFFSET_SECONDS=0
export BACKUP_NOW_EPOCH
BACKUP_NOW_EPOCH=$(date -u -d '2041-03-10 15:00:00' +%s)
mkdir -p "$BACKUP_DIR"
status_json="$BACKUP_DIR/status/status.json"

add_pair() {
  local stamp="$1"
  head -c 1000 /dev/urandom > "$BACKUP_DIR/abdb_$stamp.dump"
  head -c 2000 /dev/urandom > "$BACKUP_DIR/abfiles_$stamp.tar.zst"
  printf 'created_at=%s\n[tables]\n' "$stamp" > "$BACKUP_DIR/abdb_$stamp.meta"
  (cd "$BACKUP_DIR" && sha256sum "abdb_$stamp.dump" > "abdb_$stamp.dump.sha256" && sha256sum "abfiles_$stamp.tar.zst" > "abfiles_$stamp.tar.zst.sha256")
}
add_pre_release() {
  local version="$1" stamp="$2"
  head -c 500 /dev/urandom > "$BACKUP_DIR/abpre-${version}_$stamp.dump"
  head -c 700 /dev/urandom > "$BACKUP_DIR/abprefiles-${version}_$stamp.tar.zst"
  (cd "$BACKUP_DIR" && sha256sum "abpre-${version}_$stamp.dump" > "abpre-${version}_$stamp.dump.sha256" && sha256sum "abprefiles-${version}_$stamp.tar.zst" > "abprefiles-${version}_$stamp.tar.zst.sha256")
}
validates() { python3 "$here/validate-status.py" "$schema" "$status_json"; }

add_pair 20410310T040000Z
add_pair 20410310T080000Z
add_pair 20410309T200000Z
add_pair 20410303T060000Z
add_pair 20401231T060000Z
add_pre_release v1.2.3 20410308T100000Z
"$scripts/status.sh" > /dev/null
check "status.json cumple el esquema del contrato" "" "$(validates)"
check "un nivel por cada tipo con backups, mas el pre-release" "INTRADAY DAILY WEEKLY QUARTERLY PRE_RELEASE" "$(jq -r '[.tiers[].tier] | join(" ")' "$status_json")"
check "INTRADAY cuenta las corridas del dia en curso" "2" "$(jq '.tiers[] | select(.tier=="INTRADAY") | .kept' "$status_json")"
check "el nivel INTRADAY apunta al ultimo backup" "2041-03-10T08:00:00Z" "$(jq -r '.tiers[] | select(.tier=="INTRADAY") | .takenAt' "$status_json")"
check "solo PRE_RELEASE lleva label y respeta el formato de version" "v1.2.3 null null null null" "$(jq -r '[.tiers[].label] | reverse | map(. // "null") | join(" ")' "$status_json")"
check "los backups sanos quedan VERIFIED" "VERIFIED" "$(jq -r '[.tiers[].verification] | unique | join(",")' "$status_json")"
check "los nombres de archivo incluyen el par y su meta" "abdb_20410310T080000Z.dump abdb_20410310T080000Z.dump.sha256 abfiles_20410310T080000Z.tar.zst abfiles_20410310T080000Z.tar.zst.sha256 abdb_20410310T080000Z.meta" "$(jq -r '.tiers[] | select(.tier=="INTRADAY") | .fileNames | join(" ")' "$status_json")"
check "lastCompleteBackupAt es el ultimo par completo" "2041-03-10T08:00:00Z" "$(jq -r '.lastCompleteBackupAt' "$status_json")"
check "sin prueba de restauracion el campo es null" "null" "$(jq -c '.lastRestoreTest' "$status_json")"
check "el archivo queda legible (0644) y el directorio con acceso (0755)" "644 755" "$(stat -c %a "$status_json") $(stat -c %a "$BACKUP_DIR/status")"
check "el destino informa disco y ruta" "$BACKUP_DIR true" "$(jq -r '[.destination.path, (.destination.totalBytes > 0)] | join(" ")' "$status_json")"

printf 'ran_at=2041-03-09T05:00:00Z\nresult=FAILED\nfailure_code=COUNTS_MISMATCH\n' > "$BACKUP_DIR/status/restore-test"
printf '%s\n' '2041-03-10T01:00:00Z CRITICAL BACKUP_FAILED' 'basura que no es una alerta' '2041-03-10T02:00:00Z WARNING TEXTO_LIBRE' '2041-03-10T03:00:00Z WARNING DISK_LOW' > "$BACKUP_DIR/status/alerts.log"
echo 3 > "$BACKUP_DIR/status/consecutive-failures"
"$scripts/status.sh" > /dev/null
check "con estado de restore y alertas sigue cumpliendo el esquema" "" "$(validates)"
check "la prueba de restauracion fallida se informa con su codigo cerrado" "FAILED COUNTS_MISMATCH" "$(jq -r '.lastRestoreTest | "\(.result) \(.failureCode)"' "$status_json")"
check "solo pasan las alertas con codigo cerrado" "BACKUP_FAILED DISK_LOW" "$(jq -r '[.alerts[].code] | join(" ")' "$status_json")"
check "las fallas consecutivas salen del contador" "3" "$(jq '.consecutiveFailures' "$status_json")"
printf 'ran_at=2041-03-09T05:00:00Z\nresult=FAILED\nfailure_code=cualquier texto libre\n' > "$BACKUP_DIR/status/restore-test"
"$scripts/status.sh" > /dev/null
check "un codigo de falla fuera del esquema no se escribe nunca" "null" "$(jq -c '.lastRestoreTest' "$status_json")"
rm -f "$BACKUP_DIR/status/restore-test" "$BACKUP_METRICS_DIR"/.recorded_* "$BACKUP_DIR/status/alerts.log"
"$scripts/status.sh" > /dev/null
check "sin prueba de restauracion y con backups de mas de 14 dias se avisa" "WARNING RESTORE_TEST_FAILED" "$(jq -r '.alerts[] | select(.code=="RESTORE_TEST_FAILED") | "\(.level) \(.code)"' "$status_json")"
printf 'ran_at=2041-03-09T05:00:00Z\nresult=PASSED\nfailure_code=\n' > "$BACKUP_DIR/status/restore-test"
rm -f "$BACKUP_METRICS_DIR"/.recorded_* "$BACKUP_DIR/status/alerts.log"
"$scripts/status.sh" > /dev/null
check "con una prueba de restauracion reciente no se avisa" "0" "$(jq '[.alerts[] | select(.code=="RESTORE_TEST_FAILED")] | length' "$status_json")"
check "la prueba de restauracion que paso se informa sin codigo de falla" "PASSED null" "$(jq -r '.lastRestoreTest | "\(.result) \(.failureCode)"' "$status_json")"
check "con la prueba reciente el estado cumple el esquema" "" "$(validates)"
rm -f "$BACKUP_DIR/status/restore-test" "$BACKUP_METRICS_DIR"/.recorded_* "$BACKUP_DIR/status/alerts.log"

echo corrupto >> "$BACKUP_DIR/abfiles_20410310T080000Z.tar.zst"
"$scripts/status.sh" > /dev/null
check "un checksum que no coincide deja el nivel DAMAGED" "DAMAGED" "$(jq -r '.tiers[] | select(.tier=="INTRADAY") | .verification' "$status_json")"
check "un backup danado genera la alerta BACKUP_DAMAGED" "1" "$(jq '[.alerts[] | select(.code=="BACKUP_DAMAGED")] | length' "$status_json")"
check "con un backup danado el estado sigue cumpliendo el esquema" "" "$(validates)"

before=$(sha256sum "$status_json" | cut -d' ' -f1)
fakebin="$work/fakebin"
mkdir -p "$fakebin"
printf '#!/bin/sh\nexit 1\n' > "$fakebin/mv"
chmod +x "$fakebin/mv"
PATH="$fakebin:$PATH" "$scripts/status.sh" > /dev/null 2>&1
check "si falla el renombrado final el status.json anterior queda intacto" "$before" "$(sha256sum "$status_json" | cut -d' ' -f1)"
check "si falla el renombrado no queda un temporal suelto" "0" "$(find "$BACKUP_DIR/status" -name '.status.json.*' | wc -l | tr -d ' ')"

parse_failures=0
( for _ in $(seq 1 25); do "$scripts/status.sh" > /dev/null; done ) &
writer=$!
while kill -0 "$writer" 2> /dev/null; do
  jq -e . "$status_json" > /dev/null 2>&1 || parse_failures=$((parse_failures + 1))
done
check "un lector concurrente nunca ve un status.json a medio escribir" "0" "$parse_failures"

finish
