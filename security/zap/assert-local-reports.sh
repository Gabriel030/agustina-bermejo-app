#!/usr/bin/env bash
set -euo pipefail

output="${1:-}"
port="${ZAP_LOCAL_PORT:-8080}"

[ -d "$output" ] || { echo "ZAP local: no existe el directorio de reportes" >&2; exit 1; }

shopt -s nullglob
reports=("$output"/*.json)
[ "${#reports[@]}" -gt 0 ] || { echo "ZAP local: no hay reportes JSON para verificar" >&2; exit 1; }

foreign="$(jq -r '[.site[]? | ., (.alerts[]?.instances[]?.uri)] | map(if type == "object" then ("http://" + .["@host"] + ":" + .["@port"]) else . end) | .[]' "${reports[@]}" \
  | grep -Ev "^https?://(localhost|127\.0\.0\.1):${port}(/|$)" || true)"

if [ -n "$foreign" ]; then
  echo "ZAP local abortado: hubo requests a hosts distintos de localhost:$port" >&2
  echo "$foreign" | sort -u | head -20 >&2
  exit 1
fi

echo "Reportes verificados: solo localhost:$port"
