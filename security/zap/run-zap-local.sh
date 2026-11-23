#!/usr/bin/env bash
set -euo pipefail

profile="${1:-}"
target="${2:-}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
output="${ZAP_OUTPUT_DIR:-$root/zap-reports}"
port="${ZAP_LOCAL_PORT:-8080}"
digest="${ZAP_IMAGE_DIGEST:-}"
zap_uid="${ZAP_RUN_UID:-20001}"

[[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || { echo "ZAP local rechazado: ZAP_IMAGE_DIGEST obligatorio con formato sha256:<64 hex>" >&2; exit 1; }
[ "${ZAP_BLOCK_EXTERNAL_EGRESS:-true}" = "true" ] || { echo "ZAP local rechazado: ZAP_BLOCK_EXTERNAL_EGRESS debe ser true" >&2; exit 1; }

"$here/assert-local-target.sh" "$target"

image="ghcr.io/zaproxy/zaproxy@$digest"
export ZAP_LOCAL_PORT="$port"

mkdir -p "$output"
chmod 0755 "$output"
rm -f "$output"/*.json "$output"/*.html
sudo chown "$zap_uid:$zap_uid" "$output"

scope_regex="^(?!http://(localhost|127\\.0\\.0\\.1):${port}(/|\$)).*\$"
zap_options="-silent -config globalexcludeurl.url_list.url(0).regex=true -config globalexcludeurl.url_list.url(0).pattern=${scope_regex} -config globalexcludeurl.url_list.url(0).enabled=true"

block_rule=(OUTPUT -m owner --uid-owner "$zap_uid" ! -d 127.0.0.0/8 -j REJECT)
sudo iptables -I "${block_rule[@]}"
trap 'sudo iptables -D "${block_rule[@]}" || true' EXIT

status=0
case "$profile" in
  baseline)
    docker run --rm --network host --user "$zap_uid:$zap_uid" -v "$here:/zap/config:ro" -v "$output:/zap/wrk:rw" "$image" \
      zap-baseline.py -t "$target" -c /zap/config/baseline.conf \
      -r baseline-report.html -J baseline-report.json -I -z "$zap_options" || status=$?
    ;;
  api)
    docker run --rm --network host --user "$zap_uid:$zap_uid" -v "$here:/zap/config:ro" -v "$root/contracts:/zap/contracts:ro" -v "$output:/zap/wrk:rw" "$image" \
      zap-api-scan.py -t /zap/contracts/openapi.json -f openapi -O "$target" \
      -c /zap/config/api-scan.conf -r api-scan-report.html -J api-scan-report.json -I -S -z "$zap_options" || status=$?
    ;;
  *)
    echo "Uso: run-zap-local.sh <baseline|api> http://localhost:<puerto>" >&2
    exit 2
    ;;
esac

"$here/assert-local-reports.sh" "$output"
exit "$status"
