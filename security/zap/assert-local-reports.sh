#!/usr/bin/env bash
set -euo pipefail

output="${1:-}"
port="${ZAP_LOCAL_PORT:-8080}"

command -v jq > /dev/null || { echo "ZAP local: falta jq" >&2; exit 1; }
[ -d "$output" ] || { echo "ZAP local: no existe el directorio de reportes" >&2; exit 1; }

shopt -s nullglob
reports=("$output"/*.json)
[ "${#reports[@]}" -gt 0 ] || { echo "ZAP local: no hay reportes JSON para verificar" >&2; exit 1; }

hosts="$(jq -r '
  (.site // error("el reporte no tiene site"))
  | if type != "array" then error("site no es una lista") else . end
  | .[]
  | ("http://" + ((.["@host"] // "") | tostring) + ":" + ((.["@port"] // "") | tostring) + "/"),
    ((.alerts // [])[] | (.instances // [])[] | (.uri // ""))
' "${reports[@]}")"

[ -n "$hosts" ] || { echo "ZAP local: los reportes no listan ningún sitio" >&2; exit 1; }

allowed="^https?://(localhost|127\.0\.0\.1):${port}(/|$)"
foreign=""
while IFS= read -r line; do
  [[ "$line" =~ $allowed ]] || foreign+="$line"$'\n'
done <<< "$hosts"

if [ -n "$foreign" ]; then
  echo "ZAP local abortado: hubo requests a hosts distintos de localhost:$port" >&2
  printf '%s' "$foreign" | sort -u | head -20 >&2
  exit 1
fi

echo "Reportes verificados: solo localhost:$port"
