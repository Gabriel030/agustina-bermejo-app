#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
slot_script="$HOME/.claude/epica18/ci-slot.sh"
owner="system-tests-$$"

free_port() {
  node -e "const s=require('net').createServer();s.listen(0,()=>{console.log(s.address().port);s.close()})"
}

if [[ -f "$slot_script" ]]; then
  zsh "$slot_script" acquire "$owner"
  trap 'zsh "$slot_script" release "$owner"' EXIT
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker no esta corriendo: el backend de sistema usa Testcontainers (ver docs/context/debugging.md)" >&2
  exit 1
fi

export E2E_BACKEND_PORT="${E2E_BACKEND_PORT:-$(free_port)}"
export E2E_FRONTEND_PORT="${E2E_FRONTEND_PORT:-$(free_port)}"

npm --prefix "$repo_root/frontend" ci --no-audit --no-fund
cd "$repo_root/e2e"
npm ci
npx playwright install chromium
npx playwright test "$@"
