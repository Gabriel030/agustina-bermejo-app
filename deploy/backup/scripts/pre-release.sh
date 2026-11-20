#!/usr/bin/env bash
set -uo pipefail

version="${1:-}"
if ! printf '%s' "$version" | grep -qE '^v?[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$'; then
  echo "Uso: pre-release.sh <version> (por ejemplo v1.2.3)" >&2
  exit 2
fi

compose=${COMPOSE_CMD:-"docker compose -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml"}
read -r -a compose_cmd <<< "$compose"

"${compose_cmd[@]}" stop backend || { echo "no se pudo parar backend: no se hace el backup" >&2; exit 1; }

result=0
"${compose_cmd[@]}" exec -T -e "BACKUP_PREFIX=abpre-$version" -e "FILES_PREFIX=abprefiles-$version" -e BACKUP_SKIP_ROTATION=true \
  -e BACKUP_LOCK_WAIT_SECONDS=600 backup /opt/backup/backup.sh || result=$?
if [ "$result" -eq 0 ]; then
  "${compose_cmd[@]}" exec -T backup sh -c "ls /backups/abpre-${version}_*.dump* > /dev/null 2>&1" || result=1
fi

"${compose_cmd[@]}" start backend || { echo "NO SE PUDO REINICIAR backend: iniciarlo a mano" >&2; result=1; }
"${compose_cmd[@]}" exec -T backup /opt/backup/status.sh || echo "no se pudo actualizar status.json" >&2

[ "$result" -eq 0 ] && echo "backup pre-release $version listo"
exit "$result"
