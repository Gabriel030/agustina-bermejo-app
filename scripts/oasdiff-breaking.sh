#!/usr/bin/env bash
set -euo pipefail

OASDIFF_VERSION="1.33.0"
BASE_URL="https://github.com/oasdiff/oasdiff/releases/download/v${OASDIFF_VERSION}"
CACHE_DIR="${OASDIFF_HOME:-${TMPDIR:-/tmp}/oasdiff-${OASDIFF_VERSION}}"

if [ "$#" -ne 2 ]; then
  echo "uso: $0 <spec-base.json> <spec-nuevo.json>" >&2
  exit 2
fi

case "$(uname -s)-$(uname -m)" in
  Darwin-*) ARCHIVE="oasdiff_${OASDIFF_VERSION}_darwin_all.tar.gz" ;;
  Linux-x86_64) ARCHIVE="oasdiff_${OASDIFF_VERSION}_linux_amd64.tar.gz" ;;
  Linux-aarch64 | Linux-arm64) ARCHIVE="oasdiff_${OASDIFF_VERSION}_linux_arm64.tar.gz" ;;
  *) echo "plataforma no soportada: $(uname -s)-$(uname -m)" >&2; exit 2 ;;
esac

if [ ! -x "${CACHE_DIR}/oasdiff" ]; then
  mkdir -p "${CACHE_DIR}"
  curl -fsSL -o "${CACHE_DIR}/${ARCHIVE}" "${BASE_URL}/${ARCHIVE}"
  curl -fsSL -o "${CACHE_DIR}/checksums.txt" "${BASE_URL}/checksums.txt"
  expected=$(grep " ${ARCHIVE}\$" "${CACHE_DIR}/checksums.txt" | awk '{print $1}')
  actual=$(shasum -a 256 "${CACHE_DIR}/${ARCHIVE}" | awk '{print $1}')
  if [ -z "${expected}" ] || [ "${expected}" != "${actual}" ]; then
    echo "checksum de ${ARCHIVE} no coincide: esperado '${expected}', obtenido '${actual}'" >&2
    rm -f "${CACHE_DIR}/${ARCHIVE}"
    exit 3
  fi
  tar -xzf "${CACHE_DIR}/${ARCHIVE}" -C "${CACHE_DIR}" oasdiff
  echo "oasdiff ${OASDIFF_VERSION} verificado (sha256 ${actual})" >&2
fi

"${CACHE_DIR}/oasdiff" breaking "$1" "$2" --fail-on ERR
