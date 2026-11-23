#!/usr/bin/env bash
set -euo pipefail

target="${1:-}"
port="${ZAP_LOCAL_PORT:-8080}"

reject() {
  echo "ZAP local rechazado: $1" >&2
  exit 1
}

[[ "$port" =~ ^[1-9][0-9]{0,4}$ ]] || reject "ZAP_LOCAL_PORT debe ser un puerto numérico"
[ "$port" -le 65535 ] || reject "ZAP_LOCAL_PORT fuera de rango"
[ -n "$target" ] || reject "falta la URL de destino"

[[ "$target" =~ ^http://(localhost|127\.0\.0\.1):([0-9]+)(/[a-z0-9._~/-]*)?$ ]] || reject "la URL debe ser http://localhost:PUERTO[/ruta] o http://127.0.0.1:PUERTO[/ruta], sin credenciales, parámetros ni fragmentos"
[ "${BASH_REMATCH[2]}" = "$port" ] || reject "el puerto no es el esperado ($port)"
case "${BASH_REMATCH[3]}" in
  *..*) reject "la ruta no puede contener .." ;;
esac

echo "Destino local aceptado: ${BASH_REMATCH[1]}:$port"
