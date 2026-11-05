#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
slot_script="$HOME/.claude/epica18/ci-slot.sh"
owner="load-tests-$$"

free_port() {
  node -e "const s=require('net').createServer();s.listen(0,()=>{console.log(s.address().port);s.close()})"
}

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 no esta instalado: brew install k6" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker no esta corriendo: el backend de carga usa Testcontainers (ver docs/context/debugging.md)" >&2
  exit 1
fi

if [[ -f "$slot_script" ]]; then
  zsh "$slot_script" acquire "$owner"
fi

backend_port="${LOAD_BACKEND_PORT:-$(free_port)}"
sessions_file="${LOAD_TEST_SESSIONS_FILE:-$repo_root/backend/target/load-test-sessions.json}"
backend_log="$(mktemp -t load-backend.XXXXXX)"
backend_pid=""

cleanup() {
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
    backend_pid=""
  fi
  if [[ -f "$slot_script" ]]; then
    zsh "$slot_script" release "$owner" || true
  fi
}
trap cleanup EXIT

rm -f "$sessions_file"
export LOAD_TEST_SESSIONS_FILE="$sessions_file"
export SYSTEM_TEST_BACKEND_PORT="$backend_port"

cd "$repo_root/backend"
./mvnw -o -q spring-boot:test-run \
  -Dspring-boot.run.main-class=com.red_escuela.backend.testsupport.system.LoadTestApplication >"$backend_log" 2>&1 &
backend_pid=$!

startup_timeout="${LOAD_STARTUP_TIMEOUT_SECONDS:-600}"
waited=0
until [[ -f "$sessions_file" ]]; do
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "El backend de carga termino antes de sembrar. Log: $backend_log" >&2
    tail -n 40 "$backend_log" >&2
    exit 1
  fi
  if (( waited >= startup_timeout )); then
    echo "El backend de carga no termino de sembrar en ${startup_timeout}s. Log: $backend_log" >&2
    exit 1
  fi
  sleep 2
  waited=$((waited + 2))
done

cd "$repo_root/load"
k6_status=0
k6 run -e BASE_URL="http://localhost:$backend_port" -e LOAD_TEST_SESSIONS_FILE="$sessions_file" "$@" q15a.js || k6_status=$?
cleanup
trap - EXIT
exit "$k6_status"
