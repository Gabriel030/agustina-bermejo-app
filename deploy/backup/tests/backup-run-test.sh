#!/usr/bin/env bash
set -uo pipefail
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
psql -q -c "CREATE TABLE flyway_schema_history (version text, success boolean); INSERT INTO flyway_schema_history VALUES ('1', true), ('2', true); CREATE TABLE person (id serial, name text); INSERT INTO person (name) SELECT 'p' || g FROM generate_series(1, 500) g;" || exit 2

data="$work/data"
mkdir -p "$data/uploads/docs"
head -c 200000 /dev/urandom > "$data/uploads/docs/comprobante.bin"
echo "hola" > "$data/uploads/a.txt"

run_backup() {
  BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" BACKUP_DATA_DIR="$data" "$scripts/backup.sh" "$@" 2>&1
}
newest() { ls "$work/backups" 2> /dev/null | grep -E "$1" | sort | tail -1; }

started=$(date +%s)
output=$(run_backup)
elapsed=$(( $(date +%s) - started ))
status=$?
dump=$(newest '^abdb_.*\.dump$')
archive=$(newest '^abfiles_.*\.tar\.zst$')
check "corrida sin cifrar: genera dump y archivo de /data con el mismo sello" \
  "$(echo "$dump" | sed -E 's/^abdb_(.*)\.dump$/\1/')" "$(echo "$archive" | sed -E 's/^abfiles_(.*)\.tar\.zst$/\1/')"
check "corrida sin cifrar: los dos checksums verifican" "OK" "$(cd "$work/backups" && sha256sum -c --status "$dump.sha256" "$archive.sha256" && echo OK)"
check "el archivo contiene los archivos de /data" "2" "$(tar --zstd -tf "$work/backups/$archive" | grep -cE 'a\.txt|comprobante\.bin')"
meta="$work/backups/$(echo "$dump" | sed 's/\.dump$/.meta/')"
check "el .meta registra el archivo de /data" "files_archive=$archive" "$(grep '^files_archive=' "$meta")"
check "el .meta cuenta los archivos" "files_count=2" "$(grep '^files_count=' "$meta")"
check "el .meta guarda el resultado de las tablas" "person|500" "$(grep '^person|' "$meta")"
check "sin AGE_RECIPIENT la corrida avisa que no cifra" "1" "$(echo "$output" | grep -c 'BACKUP SIN CIFRAR')"
dump_bytes=$(wc -c < "$work/backups/$dump" | tr -d ' ')
archive_bytes=$(wc -c < "$work/backups/$archive" | tr -d ' ')
echo "medicion: dump ${dump_bytes} bytes, archivo /data ${archive_bytes} bytes, duracion ${elapsed}s"
check "segunda corrida el mismo dia no repite el aviso de cifrado" "0" "$(sleep 1; run_backup | grep -c 'BACKUP SIN CIFRAR')"

identity="$work/key.txt"
age-keygen -o "$identity" 2> /dev/null
recipient=$(age-keygen -y "$identity")
rm -rf "$work/backups"
AGE_RECIPIENT="$recipient" run_backup > /dev/null
dump=$(newest '^abdb_.*\.dump\.age$')
archive=$(newest '^abfiles_.*\.tar\.zst\.age$')
check "con age: dump y archivo salen cifrados con su checksum" "OK" "$(cd "$work/backups" && sha256sum -c --status "$dump.sha256" "$archive.sha256" && echo OK)"
check "con age: no quedan copias sin cifrar" "0" "$(ls "$work/backups" | grep -cE '\.(dump|tar\.zst)$')"
check "con age: el archivo descifrado conserva los archivos" "2" "$(age -d -i "$identity" "$work/backups/$archive" | tar --zstd -tf - | grep -cE 'a\.txt|comprobante\.bin')"

rm -rf "$work/backups" "$work/metrics" "$work/empty"
mkdir -p "$work/empty"
real_data="$data"; data="$work/empty"; output=$(run_backup); data="$real_data"
check "con /data vacio la corrida avisa" "1" "$(echo "$output" | grep -c 'esta vacio')"
rm -rf "$work/backups"
output=$(BACKUP_DATA_DIR="$work/no-existe" BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" "$scripts/backup.sh" 2>&1); status=$?
check "sin /data montado la corrida falla" "1" "$status"
check "sin /data montado alerta BACKUP FALLIDO" "1" "$(echo "$output" | grep -c 'BACKUP FALLIDO')"
check "sin /data montado no queda ningun archivo del intento" "" "$(ls "$work/backups" 2> /dev/null)"

fakebin="$work/fakebin"
mkdir -p "$fakebin"
printf '#!/bin/sh\n/usr/bin/tar "$@"\n[ "$4" = "-cf" ] && exit 2\nexit 0\n' > "$fakebin/tar"
chmod +x "$fakebin/tar"
output=$(PATH="$fakebin:$PATH" run_backup); status=$?
check "si falla el tar la corrida falla" "1" "$status"
check "si falla el tar alerta BACKUP FALLIDO" "1" "$(echo "$output" | grep -c 'BACKUP FALLIDO')"
check "si falla el tar no queda un dump huerfano" "" "$(ls "$work/backups" 2> /dev/null)"
check "si falla el tar la metrica marca la corrida como fallida" "backup_last_run_success 0" "$(grep '^backup_last_run_success' "$work/metrics/backup.prom")"

finish
