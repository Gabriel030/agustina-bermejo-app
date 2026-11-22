#!/usr/bin/env bash
set -euo pipefail

profile="${1:-}"
target="${2:-}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
output="${ZAP_OUTPUT_DIR:-$root/zap-reports}"
image="ghcr.io/zaproxy/zaproxy:stable"

"$here/assert-staging-target.sh" "$target"

mkdir -p "$output"
chmod 777 "$output"

case "$profile" in
  baseline)
    docker run --rm -v "$here:/zap/config:ro" -v "$output:/zap/wrk:rw" "$image" \
      zap-baseline.py -t "$target" -c /zap/config/baseline.conf \
      -r baseline-report.html -J baseline-report.json -I
    ;;
  api)
    docker run --rm -v "$here:/zap/config:ro" -v "$root/contracts:/zap/contracts:ro" -v "$output:/zap/wrk:rw" "$image" \
      zap-api-scan.py -t /zap/contracts/openapi.json -f openapi -O "$target/api" \
      -c /zap/config/api-scan.conf -r api-scan-report.html -J api-scan-report.json -I -S
    ;;
  *)
    echo "Uso: run-zap.sh <baseline|api> <https://host-de-staging>" >&2
    exit 2
    ;;
esac
