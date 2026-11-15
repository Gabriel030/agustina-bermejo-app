#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
smoke="$script_dir/smoke.sh"
work_dir="$(mktemp -d)"
server_pid=""
stop_server() {
  if [ -n "$server_pid" ]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
    server_pid=""
  fi
}
cleanup() {
  stop_server
  rm -rf "$work_dir"
}
trap cleanup EXIT

cat > "$work_dir/server.py" <<'PY'
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

broken = set(sys.argv[2].split(",")) if len(sys.argv) > 2 and sys.argv[2] else set()

SECURITY = {
    "Strict-Transport-Security": "max-age=31536000",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), microphone=()",
    "Cross-Origin-Opener-Policy": "same-origin-allow-popups",
    "Content-Security-Policy": "default-src 'self'; frame-ancestors 'none'",
}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body, content_type="application/json", headers=None):
        payload = body.encode()
        self.send_response_only(status)
        if "server" in broken:
            self.send_header("Server", "fake")
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        if self.path == "/":
            headers = dict(SECURITY)
            if "headers" in broken:
                del headers["Content-Security-Policy"]
            self.reply(200, '<html><body><div id="root"></div></body></html>', "text/html", headers)
        elif self.path == "/api/actuator/health":
            status = "DOWN" if "health" in broken else "UP"
            self.reply(200, json.dumps({"status": status}))
        elif self.path == "/api/me":
            if "api" in broken:
                self.reply(500, json.dumps({"code": "INTERNAL_ERROR"}))
            else:
                self.reply(401, json.dumps({"code": "UNAUTHENTICATED"}))
        else:
            self.reply(404, "{}")


server = HTTPServer(("127.0.0.1", 0), Handler)
with open(sys.argv[1], "w") as port_file:
    port_file.write(str(server.server_port))
server.serve_forever()
PY

migrations="$work_dir/migrations"
mkdir -p "$migrations"
touch "$migrations/V9__a.sql" "$migrations/V10__b.sql" "$migrations/V247__c.sql"

start_server() {
  stop_server
  rm -f "$work_dir/port"
  python3 "$work_dir/server.py" "$work_dir/port" "$1" &
  server_pid=$!
  for _ in $(seq 1 50); do
    [ -s "$work_dir/port" ] && break
    sleep 0.1
  done
  [ -s "$work_dir/port" ] || { echo "El servidor falso no arrancó" >&2; exit 1; }
  base_url="http://127.0.0.1:$(cat "$work_dir/port")"
}

failed_cases=0

expect_exit() {
  local label="$1" expected="$2" actual
  shift 2
  set +e
  "$@" > "$work_dir/out.txt" 2>&1
  actual=$?
  set -e
  if [ "$actual" -eq "$expected" ]; then
    echo "PASA  $label (salida $actual)"
  else
    echo "ROJO  $label: esperado $expected, salida $actual" >&2
    cat "$work_dir/out.txt" >&2
    failed_cases=$((failed_cases + 1))
  fi
}

expect_output() {
  local label="$1" needle="$2"
  if grep -q "$needle" "$work_dir/out.txt"; then
    echo "PASA  $label"
  else
    echo "ROJO  $label: no aparece '$needle'" >&2
    cat "$work_dir/out.txt" >&2
    failed_cases=$((failed_cases + 1))
  fi
}

export SMOKE_MIGRATIONS_DIR="$migrations"
export SMOKE_TIMEOUT=5

start_server ""
SMOKE_DB_VERSION_CMD="echo 247" expect_exit "healthyStackWithMatchingMigrationsPasses" 0 "$smoke" "$base_url"
expect_output "matchingMigrationsAreReportedAsVerified" "la base está en V247"
expect_exit "missingDatabaseAccessIsUnverifiedNotSuccessWhenRequired" 1 "$smoke" --require-migrations "$base_url"
expect_exit "missingDatabaseAccessIsReportedAsUnverifiedByDefault" 0 "$smoke" "$base_url"
expect_output "unverifiedMigrationsAreNotReportedAsOk" "NO VERIFICADO"
SMOKE_DB_VERSION_CMD="echo 246" expect_exit "staleDatabaseVersionFails" 1 "$smoke" "$base_url"
expect_output "staleDatabaseVersionIsNamed" "V246 pero el código trae V247"
SMOKE_DB_VERSION_CMD="false" expect_exit "unreadableDatabaseVersionFails" 1 "$smoke" "$base_url"
expect_exit "missingBaseUrlIsAUsageError" 2 "$smoke"

start_server "headers"
SMOKE_DB_VERSION_CMD="echo 247" expect_exit "missingSecurityHeaderFails" 1 "$smoke" "$base_url"
expect_output "missingSecurityHeaderIsNamed" "Content-Security-Policy"

start_server "health"
SMOKE_DB_VERSION_CMD="echo 247" expect_exit "healthDownFails" 1 "$smoke" "$base_url"

start_server "api"
SMOKE_DB_VERSION_CMD="echo 247" expect_exit "serverErrorInsteadOfUnauthorizedFails" 1 "$smoke" "$base_url"

start_server "server"
SMOKE_DB_VERSION_CMD="echo 247" expect_exit "exposedServerHeaderFails" 1 "$smoke" "$base_url"
expect_output "exposedServerHeaderIsNamed" "cabecera Server"

stop_server
expect_exit "unreachableServerFails" 1 "$smoke" "$base_url"

if [ "$failed_cases" -gt 0 ]; then
  echo "$failed_cases casos fallaron" >&2
  exit 1
fi
echo "Todos los casos pasaron"
