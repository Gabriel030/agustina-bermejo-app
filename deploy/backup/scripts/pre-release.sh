#!/usr/bin/env bash
set -uo pipefail

version="${1:-}"
if [ "${#version}" -gt 32 ] || ! printf '%s' "$version" | grep -qE '^v?[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$'; then
  echo "Uso: pre-release.sh <version> (por ejemplo v1.2.3, hasta 32 caracteres)" >&2
  exit 2
fi

compose=${COMPOSE_CMD:-"docker compose -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml"}
read -r -a compose_cmd <<< "$compose"

result=0
stop_attempted=false

start_backend_once() {
  [ "$stop_attempted" = "true" ] || return 0
  stop_attempted=false
  "${compose_cmd[@]}" start backend || { echo "NO SE PUDO REINICIAR backend: iniciarlo a mano" >&2; return 1; }
}

restart_backend() {
  local status=$?
  trap - EXIT INT TERM HUP
  start_backend_once || status=1
  exit "$status"
}
trap restart_backend EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

stop_attempted=true
"${compose_cmd[@]}" stop backend || { echo "no se pudo parar backend: no se hace el backup" >&2; exit 1; }

"${compose_cmd[@]}" exec -T -e "BACKUP_PREFIX=abpre-$version" -e "FILES_PREFIX=abprefiles-$version" -e BACKUP_SKIP_ROTATION=true \
  -e BACKUP_LOCK_WAIT_SECONDS=600 backup /opt/backup/backup.sh &
wait $! || result=$?
if [ "$result" -eq 0 ]; then
  "${compose_cmd[@]}" exec -T backup sh -c "ls /backups/abpre-${version}_*.dump* > /dev/null 2>&1" || result=1
fi

start_backend_once || result=1

"${compose_cmd[@]}" exec -T backup /opt/backup/status.sh || { echo "no se pudo actualizar status.json" >&2; result=1; }

[ "$result" -eq 0 ] && echo "backup pre-release $version listo"
exit "$result"
