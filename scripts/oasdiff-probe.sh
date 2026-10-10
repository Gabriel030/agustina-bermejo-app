#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

SPEC="${ROOT}/contracts/openapi.json"
cp "${SPEC}" "${WORK}/base.json"

python3 - "${SPEC}" "${WORK}/field-removed.json" <<'PY'
import json, sys
spec = json.load(open(sys.argv[1]))
schema = spec["components"]["schemas"]["AcademicOfferingCycleResponse"]
del schema["properties"]["rejectionReason"]
schema["required"].remove("rejectionReason")
json.dump(spec, open(sys.argv[2], "w"))
PY

echo "== caso 1: spec idéntico, debe pasar (exit 0)"
"${ROOT}/scripts/oasdiff-breaking.sh" "${WORK}/base.json" "${SPEC}"
echo "exit 0 como se esperaba"

echo "== caso 2: se borra AcademicOfferingCycleResponse.rejectionReason, debe fallar (exit != 0)"
if "${ROOT}/scripts/oasdiff-breaking.sh" "${WORK}/base.json" "${WORK}/field-removed.json"; then
  echo "ERROR: oasdiff no detectó el campo borrado" >&2
  exit 1
fi
echo "falló como se esperaba"
