#!/usr/bin/env bash
set -euo pipefail

target="${1:-}"
allowed_hosts="${ZAP_ALLOWED_HOSTS:-}"
forbidden_hosts="${ZAP_FORBIDDEN_HOSTS:-}"
confirmation="${ZAP_CONFIRMATION:-}"

reject() {
  echo "ZAP rechazado: $1" >&2
  exit 1
}

host_in_list() {
  local host="$1"
  local list="${2// /}"
  local item
  local items
  IFS=',' read -ra items <<<"$list"
  for item in "${items[@]}"; do
    if [ -n "$item" ] && [ "$item" = "$host" ]; then
      return 0
    fi
  done
  return 1
}

[ "$confirmation" = "ESTO ES STAGING" ] || reject "falta la confirmación exacta del entorno"
[ -n "$allowed_hosts" ] || reject "la variable ZAP_ALLOWED_HOSTS está vacía"
[ -n "$forbidden_hosts" ] || reject "la variable ZAP_FORBIDDEN_HOSTS está vacía"

lowered="${target,,}"
[[ "$lowered" =~ ^https://([a-z0-9]([a-z0-9.-]*[a-z0-9])?)(:[0-9]+)?(/[A-Za-z0-9._~/-]*)?$ ]] || reject "la URL debe ser https://host[:puerto][/ruta] sin credenciales ni parámetros"

host="${BASH_REMATCH[1]}"

if host_in_list "$host" "${forbidden_hosts,,}"; then
  reject "el host figura como producción"
fi

host_in_list "$host" "${allowed_hosts,,}" || reject "el host no figura en ZAP_ALLOWED_HOSTS"

echo "Destino aceptado: $host"
