#!/usr/bin/env bash
set -uo pipefail
scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scripts" && pwd)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
schema="$here/../../../docs/deploy/backup-status.schema.json"
. "$here/lib-test.sh"
use_scripts_as_opt_backup

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export BACKUP_DIR="$work/backups" BACKUP_METRICS_DIR="$work/metrics" PGHOST=127.0.0.1 PGPORT=1 PGUSER=postgres PGPASSWORD=x PGDATABASE=postgres
status_json="$BACKUP_DIR/status/status.json"

fresh_dir() {
  rm -rf "$BACKUP_DIR" "$BACKUP_METRICS_DIR"
  mkdir -p "$BACKUP_DIR"
}
add_pair() {
  local stamp="$1" suffix="${2:-}" source_dir="$work/source"
  mkdir -p "$source_dir"
  echo "contenido" > "$source_dir/a.txt"
  head -c 3000 /dev/urandom > "$BACKUP_DIR/abdb_$stamp.dump$suffix"
  tar --zstd -C "$source_dir" -cf "$BACKUP_DIR/abfiles_$stamp.tar.zst$suffix" .
  (cd "$BACKUP_DIR" && sha256sum "abdb_$stamp.dump$suffix" > "abdb_$stamp.dump$suffix.sha256" && sha256sum "abfiles_$stamp.tar.zst$suffix" > "abfiles_$stamp.tar.zst$suffix.sha256")
  printf 'created_at=%s\nmigrations_count=1\n[tables]\n' "$stamp" > "$BACKUP_DIR/abdb_$stamp.meta"
}
run_restore_test() { "$scripts/restore-test.sh" > "$work/output.log" 2>&1; echo $?; }
last_restore() { jq -r '.lastRestoreTest | if . == null then "null" else "\(.result) \(.failureCode)" end' "$status_json"; }

fresh_dir
add_pair "$(date -u +%Y%m%dT%H%M%SZ)"
echo extra >> "$BACKUP_DIR/abfiles_$(ls "$BACKUP_DIR" | sed -n 's/^abfiles_\(.*\)\.tar\.zst$/\1/p' | head -1).tar.zst"
check "con el archivo de /data alterado la prueba falla" "1" "$(run_restore_test)"
check "la falla por checksum se informa con su codigo cerrado" "FAILED CHECKSUM_MISMATCH" "$(last_restore)"
check "la falla de la prueba genera la alerta RESTORE_TEST_FAILED" "CRITICAL" "$(jq -r '.alerts[] | select(.code=="RESTORE_TEST_FAILED") | .level' "$status_json")"
check "la falla se avisa por los canales de alerta" "1" "$(grep -c 'PRUEBA DE RESTAURACION FALLIDA (CHECKSUM_MISMATCH)' "$work/output.log")"
check "el estado tras la falla cumple el esquema" "" "$(python3 "$here/validate-status.py" "$schema" "$status_json")"

fresh_dir
add_pair "$(date -u +%Y%m%dT%H%M%SZ)"
check "con checksums sanos pero sin base alcanzable la prueba falla" "1" "$(run_restore_test)"
check "la falla de restauracion se informa como RESTORE_FAILED" "FAILED RESTORE_FAILED" "$(last_restore)"
check "no queda ninguna carpeta temporal de la prueba" "0" "$(find "$BACKUP_DIR" -maxdepth 1 -name '.restore-test.*' | wc -l | tr -d ' ')"

fresh_dir
check "sin ningun backup la prueba falla" "1" "$(run_restore_test)"
check "sin backups se informa RESTORE_FAILED" "FAILED RESTORE_FAILED" "$(last_restore)"

fresh_dir
add_pair "$(date -u +%Y%m%dT%H%M%SZ)" .age
check "un backup cifrado sin clave privada verifica la integridad y termina bien" "0" "$(run_restore_test)"
check "sin clave privada se avisa que solo se verifico la integridad" "1" "$(grep -c 'integridad verificada' "$work/output.log")"
check "sin clave privada no se inventa un resultado de restauracion" "null" "$(last_restore)"
check "sin clave privada el estado se actualiza igual y cumple el esquema" "" "$(python3 "$here/validate-status.py" "$schema" "$status_json")"
check "sin clave privada el backup sano queda VERIFIED" "VERIFIED" "$(jq -r '.tiers[0].verification' "$status_json")"

mkdir -p "$work/agebin"
printf '#!/bin/sh\necho invocado >> "%s/age-invoked"\nexit 1\n' "$work" > "$work/agebin/age"
chmod +x "$work/agebin/age"
echo "AGE-SECRET-KEY-falsa" > "$work/identity.txt"
check "aunque haya una clave privada disponible un backup cifrado no se descifra" "0" "$(PATH="$work/agebin:$PATH" AGE_IDENTITY_FILE="$work/identity.txt" run_restore_test)"
check "con clave privada disponible no se invoca age" "0" "$(ls "$work/age-invoked" 2> /dev/null | wc -l | tr -d ' ')"
check "con clave privada disponible tampoco se toca lastRestoreTest" "null" "$(last_restore)"

stamp=$(ls "$BACKUP_DIR" | sed -n 's/^abdb_\(.*\)\.dump\.age$/\1/p' | head -1)
echo extra >> "$BACKUP_DIR/abfiles_$stamp.tar.zst.age"
check "un checksum roto en un backup cifrado sin clave falla" "1" "$(run_restore_test)"
check "un checksum roto se informa como BACKUP_DAMAGED y no como falla de restauracion" "CRITICAL BACKUP_DAMAGED null" "$(jq -r '[(.alerts[] | select(.code=="BACKUP_DAMAGED") | "\(.level) \(.code)"), (.lastRestoreTest | tostring)] | join(" ")' "$status_json")"
check "un checksum roto deja el nivel DAMAGED" "DAMAGED" "$(jq -r '.tiers[0].verification' "$status_json")"

fresh_dir
add_pair "$(date -u +%Y%m%dT%H%M%SZ)"
check "el simulacro manual puede registrar un resultado PASSED" "0" "$("$scripts/restore-test.sh" --record PASSED > /dev/null 2>&1; echo $?)"
check "el resultado registrado aparece en el estado" "PASSED null" "$(last_restore)"
check "el simulacro manual puede registrar una falla con codigo cerrado" "0" "$("$scripts/restore-test.sh" --record FAILED COUNTS_MISMATCH > /dev/null 2>&1; echo $?)"
check "la falla registrada aparece en el estado y genera la alerta" "FAILED COUNTS_MISMATCH 1" "$(echo "$(last_restore) $(jq '[.alerts[] | select(.code=="RESTORE_TEST_FAILED")] | length' "$status_json")")"
check "un codigo fuera de la lista se rechaza" "2" "$("$scripts/restore-test.sh" --record FAILED texto-libre > /dev/null 2>&1; echo $?)"
check "un PASSED con codigo se rechaza" "2" "$("$scripts/restore-test.sh" --record PASSED COUNTS_MISMATCH > /dev/null 2>&1; echo $?)"
check "una fecha pasada como argumento se rechaza" "2" "$("$scripts/restore-test.sh" --record PASSED 2020-01-01T00:00:00Z > /dev/null 2>&1; echo $?)"
"$scripts/restore-test.sh" --record PASSED > /dev/null 2>&1
check "la fecha del resultado registrado es la hora del servidor" "1" "$(jq -r --argjson now "$(date -u +%s)" '(.lastRestoreTest.ranAt | fromdateiso8601) as $ran | if ($now - $ran) < 60 and ($now - $ran) >= 0 then 1 else 0 end' "$status_json")"
check "el estado tras registrar cumple el esquema" "" "$(python3 "$here/validate-status.py" "$schema" "$status_json")"

finish
