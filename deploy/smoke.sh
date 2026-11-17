#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Uso: deploy/smoke.sh [--require-migrations] <url-base>

Prueba posterior al despliegue. Variables opcionales:
  SMOKE_BASE_URL            URL base si no se pasa como argumento (ej. https://escuela.example.com)
  SMOKE_DB_VERSION_CMD      comando que imprime la última versión de flyway_schema_history
  SMOKE_MIGRATIONS_DIR      carpeta de migraciones del código desplegado
  SMOKE_API_PROTECTED_PATH  ruta que exige sesión (por defecto /api/me)
  SMOKE_TIMEOUT             segundos por request (por defecto 10)

Sin SMOKE_DB_VERSION_CMD las migraciones quedan "no verificadas"; con --require-migrations eso falla.
Código de salida: 0 = sin fallos, 1 = al menos un chequeo falló, 2 = uso incorrecto.
USAGE
}

require_migrations=false
base_url="${SMOKE_BASE_URL:-}"
while [ $# -gt 0 ]; do
  case "$1" in
    --require-migrations) require_migrations=true ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "Opción desconocida: $1" >&2; usage >&2; exit 2 ;;
    *) base_url="$1" ;;
  esac
  shift
done
if [ -z "$base_url" ]; then
  usage >&2
  exit 2
fi
base_url="${base_url%/}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
migrations_dir="${SMOKE_MIGRATIONS_DIR:-$script_dir/../backend/src/main/resources/db/migration}"
protected_path="${SMOKE_API_PROTECTED_PATH:-/api/me}"
timeout="${SMOKE_TIMEOUT:-10}"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

failures=0
unverified=0

pass() { echo "OK              $1"; }
fail() { echo "FALLO           $1" >&2; failures=$((failures + 1)); }
skip() { echo "NO VERIFICADO   $1"; unverified=$((unverified + 1)); }

fetch() {
  local name="$1" path="$2" status
  status="$(curl -sS -o "$work_dir/$name.body" -D "$work_dir/$name.headers" -w '%{http_code}' \
    --max-time "$timeout" "$base_url$path" 2>"$work_dir/$name.err")" || status="000"
  echo "$status" > "$work_dir/$name.status"
  [ -e "$work_dir/$name.headers" ] || : > "$work_dir/$name.headers"
  [ -e "$work_dir/$name.body" ] || : > "$work_dir/$name.body"
}

status_of() { cat "$work_dir/$1.status"; }

header_value() {
  tr -d '\r' < "$work_dir/$1.headers" | awk -v h="$(echo "$2" | tr '[:upper:]' '[:lower:]')" '
    { i = index($0, ":"); if (i > 0 && tolower(substr($0, 1, i - 1)) == h) { v = substr($0, i + 1); sub(/^[ \t]+/, "", v); print v } }' | tail -n 1
}

check_header_equals() {
  local name="$1" header="$2" expected="$3" actual
  actual="$(header_value "$name" "$header")"
  if [ "$actual" = "$expected" ]; then
    pass "front: $header = $expected"
  else
    fail "front: $header esperado '$expected', recibido '${actual:-<ausente>}'"
  fi
}

check_header_contains() {
  local name="$1" header="$2" needle="$3" actual
  actual="$(header_value "$name" "$header")"
  if [[ "$actual" == *"$needle"* ]]; then
    pass "front: $header contiene $needle"
  else
    fail "front: $header no contiene '$needle' (recibido '${actual:-<ausente>}')"
  fi
}

echo "Prueba posterior al despliegue contra $base_url"

fetch front /
if [ "$(status_of front)" != "200" ]; then
  fail "front: GET / respondió $(status_of front) (esperado 200)"
else
  pass "front: GET / respondió 200"
  if grep -qi '<html' "$work_dir/front.body" && grep -q 'id="root"' "$work_dir/front.body"; then
    pass "front: sirve index.html"
  else
    fail "front: la respuesta de / no parece el index.html de la app"
  fi
fi
check_header_contains front Strict-Transport-Security "max-age="
check_header_equals front X-Content-Type-Options nosniff
check_header_equals front X-Frame-Options DENY
check_header_equals front Referrer-Policy no-referrer
check_header_contains front Permissions-Policy "camera=()"
check_header_contains front Cross-Origin-Opener-Policy "same-origin"
check_header_contains front Content-Security-Policy "default-src 'self'"
check_header_contains front Content-Security-Policy "frame-ancestors 'none'"
if [ -n "$(header_value front Server)" ]; then
  fail "front: la cabecera Server no debería exponerse"
else
  pass "front: sin cabecera Server"
fi

fetch health /api/actuator/health
if [ "$(status_of health)" = "200" ] && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$work_dir/health.body"; then
  pass "api: /api/actuator/health responde UP"
else
  fail "api: /api/actuator/health respondió $(status_of health) sin status UP"
fi

fetch internal /api/actuator/env
if [ "$(status_of internal)" = "404" ]; then
  pass "api: actuator interno no expuesto (404)"
else
  fail "api: /api/actuator/env respondió $(status_of internal) (esperado 404)"
fi

fetch protected "$protected_path"
if [ "$(status_of protected)" = "401" ] && grep -Eq '"code"[[:space:]]*:[[:space:]]*"UNAUTHENTICATED"' "$work_dir/protected.body"; then
  pass "api: $protected_path sin sesión responde 401 UNAUTHENTICATED"
else
  fail "api: $protected_path sin sesión respondió $(status_of protected) (esperado 401 con code UNAUTHENTICATED)"
fi

expected_version=""
if [ -d "$migrations_dir" ]; then
  expected_version="$(find "$migrations_dir" -maxdepth 1 -name 'V*__*.sql' -exec basename {} \; | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -n 1)"
fi
if [ -z "$expected_version" ]; then
  fail "migraciones: no se encontró ningún V###__*.sql en $migrations_dir"
elif [ -z "${SMOKE_DB_VERSION_CMD:-}" ]; then
  if [ "$require_migrations" = true ]; then
    fail "migraciones: sin acceso a la base (SMOKE_DB_VERSION_CMD) y se pidió --require-migrations"
  else
    skip "migraciones: sin SMOKE_DB_VERSION_CMD no se compara la base (esperada V$expected_version)"
  fi
else
  applied_version="$(bash -c "$SMOKE_DB_VERSION_CMD" 2>"$work_dir/db.err" | tr -d '[:space:]')" || applied_version=""
  if ! [[ "$applied_version" =~ ^[0-9]+$ ]]; then
    fail "migraciones: no se pudo leer la versión aplicada (salida '${applied_version:-<vacía>}')"
  elif [ "$applied_version" = "$expected_version" ]; then
    pass "migraciones: la base está en V$applied_version, igual al código desplegado"
  else
    fail "migraciones: la base está en V$applied_version pero el código trae V$expected_version"
  fi
fi

echo
if [ "$failures" -gt 0 ]; then
  echo "RESULTADO: FALLÓ ($failures chequeos fallidos, $unverified no verificados)" >&2
  exit 1
fi
echo "RESULTADO: OK ($unverified no verificados)"
