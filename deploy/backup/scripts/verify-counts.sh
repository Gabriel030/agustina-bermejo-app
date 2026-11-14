#!/usr/bin/env bash
set -uo pipefail

meta="${1:-}"
migrations_dir="${2:-}"
tolerance="${VERIFY_TOLERANCE_PCT:-5}"
export PGDATABASE="${PGDATABASE:-agustina_bermejo}"

status=0
fail() { echo "FALLO: $1" >&2; status=1; }

restored=$(psql -Atc "SELECT count(*) FROM flyway_schema_history WHERE success") || { echo "FALLO: no existe flyway_schema_history en la base restaurada" >&2; exit 1; }
[ "$restored" -gt 0 ] || fail "flyway_schema_history vacia"
echo "migraciones aplicadas en la base restaurada: $restored"

if [ -n "$migrations_dir" ] && [ -d "$migrations_dir" ]; then
  files=$(find "$migrations_dir" -maxdepth 1 -name 'V*.sql' | wc -l | tr -d ' ')
  echo "migraciones en el repositorio: $files"
  [ "$restored" -le "$files" ] || fail "la base tiene mas migraciones ($restored) que las del repositorio ($files)"
fi

if [ -n "$meta" ] && [ -f "$meta" ]; then
  expected=$(sed -n 's/^migrations_count=//p' "$meta")
  [ "$restored" = "$expected" ] || fail "migraciones restauradas $restored distinto de las registradas al hacer el backup $expected"
  checked=0
  in_tables=false
  while IFS= read -r line; do
    if [ "$line" = "[tables]" ]; then in_tables=true; continue; fi
    $in_tables || continue
    [ -n "$line" ] || continue
    table="${line%%|*}"
    want="${line##*|}"
    got=$(psql -Atc "SELECT count(*) FROM \"$table\"") || { fail "no se pudo contar $table"; continue; }
    checked=$((checked + 1))
    margin=$(( want * tolerance / 100 ))
    if [ "$want" -gt 0 ] && [ "$got" -eq 0 ]; then
      fail "la tabla $table tenia $want filas y quedo vacia"
    elif [ "$got" -lt $(( want - margin - 1 )) ] || [ "$got" -gt $(( want + margin + 1 )) ]; then
      fail "la tabla $table: restauradas $got filas, esperadas ~$want (tolerancia ${tolerance}%)"
    fi
  done < "$meta"
  echo "tablas comparadas contra el .meta: $checked"
  [ "$checked" -gt 0 ] || fail "el .meta no tiene tablas"
else
  echo "sin .meta: se omite la comparacion de conteos por tabla"
fi

[ $status -eq 0 ] && echo "verificacion de conteos OK"
exit $status
