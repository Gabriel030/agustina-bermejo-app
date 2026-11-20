#!/usr/bin/env bash
set -uo pipefail
scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scripts" && pwd)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
schema="$here/../../../docs/deploy/backup-status.schema.json"
. "$(dirname "${BASH_SOURCE[0]}")/lib-test.sh"
use_scripts_as_opt_backup

PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"
work=$(mktemp -d)
container="backup-test-$$"
cleanup() { docker rm -f "$container" > /dev/null 2>&1; rm -rf "$work"; }
trap cleanup EXIT

port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1])')
docker run -d --name "$container" -e POSTGRES_PASSWORD=test -p "127.0.0.1:$port:5432" "$PG_IMAGE" > /dev/null || exit 2
export PGHOST=127.0.0.1 PGPORT="$port" PGUSER=postgres PGPASSWORD=test PGDATABASE=postgres
for _ in $(seq 1 60); do pg_isready -q && break; sleep 1; done
psql -q -c "CREATE TABLE flyway_schema_history (version text, success boolean); INSERT INTO flyway_schema_history VALUES ('1', true), ('2', true); CREATE TABLE person (id serial, name text); INSERT INTO person (name) SELECT md5(random()::text) || md5(random()::text) || md5(random()::text) || md5(random()::text) FROM generate_series(1, 150000) g;" || exit 2

data="$work/data"
mkdir -p "$data/uploads/docs"
head -c 200000 /dev/urandom > "$data/uploads/docs/comprobante.bin"
echo "hola" > "$data/uploads/a.txt"

run_backup() {
  BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" BACKUP_DATA_DIR="$data" "$scripts/backup.sh" 2>&1
}
newest() { find "$work/backups" -maxdepth 1 -name "$1" -printf '%f\n' 2> /dev/null | sort | tail -1; }

started=$(date +%s)
output=$(run_backup)
status=$?
elapsed=$(( $(date +%s) - started ))
check "corrida sin cifrar: termina bien" "0" "$status"
dump=$(newest 'abdb_*.dump')
archive=$(newest 'abfiles_*.tar.zst')
check "corrida sin cifrar: genera dump y archivo de /data con el mismo sello" \
  "$(echo "$dump" | sed -E 's/^abdb_(.*)\.dump$/\1/')" "$(echo "$archive" | sed -E 's/^abfiles_(.*)\.tar\.zst$/\1/')"
check "corrida sin cifrar: los dos checksums verifican" "OK" "$(cd "$work/backups" && sha256sum -c --status "$dump.sha256" "$archive.sha256" && echo OK)"
check "el archivo contiene los archivos de /data" "2" "$(tar --zstd -tf "$work/backups/$archive" | grep -cE 'a\.txt|comprobante\.bin')"
meta="$work/backups/$(echo "$dump" | sed 's/\.dump$/.meta/')"
check "el .meta registra el archivo de /data" "files_archive=$archive" "$(grep '^files_archive=' "$meta")"
check "el .meta cuenta los archivos" "files_count=2" "$(grep '^files_count=' "$meta")"
check "el .meta guarda el resultado de las tablas" "person|150000" "$(grep '^person|' "$meta")"
check "sin AGE_RECIPIENT la corrida avisa que no cifra" "1" "$(echo "$output" | grep -c 'BACKUP SIN CIFRAR')"
dump_bytes=$(wc -c < "$work/backups/$dump" | tr -d ' ')
archive_bytes=$(wc -c < "$work/backups/$archive" | tr -d ' ')
check "el dump real pesa mas de 5 MB (no cabe en un buffer de pipe)" "1" "$([ "$dump_bytes" -gt 5000000 ] && echo 1 || echo 0)"
check "no queda ningun listado ni conteo del tar en el disco" "0" "$(find "$work/backups" -name '*tar-count*' -o -name '*listing*' | wc -l | tr -d ' ')"
echo "medicion: dump ${dump_bytes} bytes, archivo /data ${archive_bytes} bytes, duracion ${elapsed}s"
check "segunda corrida el mismo dia no repite el aviso de cifrado" "0" "$(sleep 1; run_backup | grep -c 'BACKUP SIN CIFRAR')"

status_json="$work/backups/status/status.json"
check "la corrida real deja un status.json que cumple el esquema" "" "$(python3 "$here/validate-status.py" "$schema" "$status_json")"
check "la corrida real informa un backup completo y sin fallas" "true 0" "$(jq -r '[(.lastCompleteBackupAt != null), .consecutiveFailures] | join(" ")' "$status_json")"
restore_env() { BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" "$scripts/restore-test.sh" > "$work/restore.log" 2>&1; echo $?; }
check "la prueba de restauracion contra Postgres pasa con un backup sano" "0" "$(restore_env)"
check "la prueba de restauracion que pasa queda en el estado" "PASSED null" "$(jq -r '.lastRestoreTest | "\(.result) \(.failureCode)"' "$status_json")"
check "la prueba de restauracion no deja bases descartables" "0" "$(psql -Atc "SELECT count(*) FROM pg_database WHERE datname LIKE 'restore_test_%'")"
sed -i 's/^person|150000$/person|1/' "$work"/backups/*.meta
check "si los conteos del backup no coinciden la prueba falla" "1" "$(restore_env)"
check "la falla de conteos se informa como COUNTS_MISMATCH" "FAILED COUNTS_MISMATCH" "$(jq -r '.lastRestoreTest | "\(.result) \(.failureCode)"' "$status_json")"
sed -i 's/^person|1$/person|150000/' "$work"/backups/*.meta
psql -Atc "CREATE DATABASE restore_test_orfana" > /dev/null
restore_env > /dev/null
check "la prueba de restauracion barre las bases descartables huerfanas" "0" "$(psql -Atc "SELECT count(*) FROM pg_database WHERE datname LIKE 'restore_test_%'")"

tiers_before=$(jq -c '[.tiers[] | {tier, takenAt}]' "$status_json")
last_success_before=$(cat "$work/metrics/.last_success")
failures_before=$(jq '.consecutiveFailures' "$status_json")
sleep 1
BACKUP_PREFIX=abpre-v1.0.0 FILES_PREFIX=abprefiles-v1.0.0 BACKUP_SKIP_ROTATION=true run_backup > /dev/null
check "un backup pre-release no cambia los niveles reales del estado" "$tiers_before" "$(jq -c '[.tiers[] | select(.tier != "PRE_RELEASE") | {tier, takenAt}]' "$status_json")"
check "un backup pre-release aparece como PRE_RELEASE con su version" "v1.0.0" "$(jq -r '.tiers[] | select(.tier=="PRE_RELEASE") | .label' "$status_json")"
check "un backup pre-release no toca .last_success ni las fallas consecutivas" "$last_success_before $failures_before" "$(cat "$work/metrics/.last_success") $(jq '.consecutiveFailures' "$status_json")"
check "el estado con un pre-release cumple el esquema" "" "$(python3 "$here/validate-status.py" "$schema" "$status_json")"
rm -f "$work"/backups/abpre-* "$work"/backups/abprefiles-*
check "el directorio de backups queda en 0700" "700" "$(stat -c %a "$work/backups")"
check "todos los archivos quedan en 0600" "" "$(find "$work/backups" -maxdepth 1 -type f ! -perm 600)"
check "el .meta marca el backup como best_effort" "consistency=best_effort" "$(grep '^consistency=' "$meta")"
check "el .meta marca que nada cambio durante la copia" "files_changed_during_copy=no" "$(grep '^files_changed_during_copy=' "$meta")"

touch -d '3 hours ago' "$work/backups/abdb_20000101T000000Z.dump.partial"
run_backup > /dev/null
check "las corridas limpian los .partial viejos" "0" "$(find "$work/backups" -name 'abdb_20000101T000000Z*' | wc -l | tr -d ' ')"

identity="$work/key.txt"
age-keygen -o "$identity" 2> /dev/null
recipient=$(age-keygen -y "$identity")
rm -rf "$work/backups"
mkdir -p "$work/agebin"
printf '#!/bin/sh\nls -A "%s" >> "%s"\nexec %s "$@"\n' "$work/backups" "$work/age-sightings.log" "$(command -v age)" > "$work/agebin/age"
chmod +x "$work/agebin/age"
PATH="$work/agebin:$PATH" AGE_RECIPIENT="$recipient" run_backup > /dev/null
check "con age: nunca hubo un dump o tar en claro en el disco" "0" "$(grep -cE '\.(dump|tar\.zst)(\.partial)?$' "$work/age-sightings.log")"
check "con age: los archivos tambien quedan en 0600" "" "$(find "$work/backups" -maxdepth 1 -type f ! -perm 600)"
dump=$(newest 'abdb_*.dump.age')
archive=$(newest 'abfiles_*.tar.zst.age')
check "con age: dump y archivo salen cifrados con su checksum" "OK" "$(cd "$work/backups" && sha256sum -c --status "$dump.sha256" "$archive.sha256" && echo OK)"
check "con age: no quedan copias sin cifrar" "0" "$(find "$work/backups" -maxdepth 1 \( -name '*.dump' -o -name '*.tar.zst' \) | wc -l | tr -d ' ')"
check "con age: el archivo descifrado conserva los archivos" "2" "$(age -d -i "$identity" "$work/backups/$archive" | tar --zstd -tf - | grep -cE 'a\.txt|comprobante\.bin')"

rm -rf "$work/backups" "$work/metrics" "$work/empty"
mkdir -p "$work/empty"
real_data="$data"; data="$work/empty"; output=$(run_backup); status=$?
check "con /data vacio la corrida falla" "1" "$status"
check "con /data vacio alerta BACKUP FALLIDO" "1" "$(echo "$output" | grep -c 'BACKUP FALLIDO')"
check "con /data vacio no queda ningun backup" "" "$(find "$work/backups" -maxdepth 1 -type f ! -name .backup.lock)"
output=$(BACKUP_ALLOW_EMPTY_DATA=true run_backup); status=$?
check "con BACKUP_ALLOW_EMPTY_DATA=true una instalacion nueva puede hacer su primer backup" "0" "$status"
data="$real_data"
rm -rf "$work/backups"
output=$(BACKUP_DATA_DIR="$work/no-existe" BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" "$scripts/backup.sh" 2>&1); status=$?
check "sin /data montado la corrida falla" "1" "$status"
check "sin /data montado alerta BACKUP FALLIDO" "1" "$(echo "$output" | grep -c 'BACKUP FALLIDO')"
check "sin /data montado no queda ningun archivo del intento" "" "$(find "$work/backups" -maxdepth 1 -type f ! -name .backup.lock 2> /dev/null)"

fakebin="$work/fakebin"
mkdir -p "$fakebin"
printf '#!/bin/sh\n/usr/bin/tar "$@"\n[ "$4" = "-cvf" ] && exit 2\nexit 0\n' > "$fakebin/tar"
chmod +x "$fakebin/tar"
output=$(PATH="$fakebin:$PATH" run_backup); status=$?
check "si falla el tar la corrida falla" "1" "$status"
check "si falla el tar alerta BACKUP FALLIDO" "1" "$(echo "$output" | grep -c 'BACKUP FALLIDO')"
check "si falla el tar no queda un dump huerfano" "" "$(find "$work/backups" -maxdepth 1 -type f ! -name .backup.lock 2> /dev/null)"
check "una corrida fallida deja la alerta BACKUP_FAILED y cuenta la falla" "1 1" "$(jq -r '[(.consecutiveFailures >= 1), ([.alerts[] | select(.code=="BACKUP_FAILED")] | length >= 1)] | map(if . then 1 else 0 end) | join(" ")' "$work/backups/status/status.json")"
check "si falla el tar la metrica marca la corrida como fallida" "backup_last_run_success 0" "$(grep '^backup_last_run_success' "$work/metrics/backup.prom")"

rm -rf "$work/backups" "$work/metrics"
printf '#!/bin/sh\n/usr/bin/tar "$@"\n[ "$4" = "-cvf" ] && exit 1\nexit 0\n' > "$fakebin/tar"
output=$(PATH="$fakebin:$PATH" run_backup); status=$?
check "si un archivo cambia mientras se copia la corrida sigue" "0" "$status"
check "si un archivo cambia el .meta lo registra" "files_changed_during_copy=yes" "$(grep -h '^files_changed_during_copy=' "$work"/backups/*.meta)"
rm -f "$fakebin/tar"

rm -rf "$work/backups" "$work/metrics"
rclone_log="$work/rclone.log"
printf '#!/bin/sh\necho "$@" >> "%s"\ncase "$1 $2" in "copyto "*abfiles*) exit 1 ;; esac\nexit 0\n' "$rclone_log" > "$fakebin/rclone"
chmod +x "$fakebin/rclone"
output=$(PATH="$fakebin:$PATH" BACKUP_S3_BUCKET=bucket run_backup); status=$?
check "si falla la subida a S3 la corrida termina bien y alerta" "0 1" "$status $(echo "$output" | grep -c 'SUBIDA A S3 FALLIDA')"
check "si falla la subida a S3 el backup local bueno se conserva" "2" "$(find "$work/backups" -maxdepth 1 \( -name 'abdb_*.dump' -o -name 'abfiles_*.tar.zst' \) | wc -l | tr -d ' ')"
check "si sube el dump y falla abfiles se retira el dump remoto" "1" "$(grep -c '^deletefile .*abdb_.*\.dump$' "$rclone_log")"
rm -f "$fakebin/rclone"

rm -rf "$work/backups" "$work/metrics"
mkdir -p "$work/backups"
(
  exec 8> "$work/backups/.backup.lock"
  flock -n 8
  sleep 20
) &
holder=$!
sleep 1
output=$(run_backup); status=$?
kill "$holder" 2> /dev/null
check "con otra corrida en curso la segunda sale sin fallar y avisa" "0 1" "$status $(echo "$output" | grep -c 'sigue en curso')"
check "la segunda corrida no genera backups" "0" "$(find "$work/backups" -name 'abdb_*' | wc -l | tr -d ' ')"

finish
