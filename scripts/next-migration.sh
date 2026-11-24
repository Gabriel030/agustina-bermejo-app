#!/usr/bin/env bash
set -euo pipefail

ROOT="${NEXT_MIGRATION_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
MIGRATION_DIR="backend/src/main/resources/db/migration/"
REFS=(origin/develop origin/master)

usage() {
  echo "Uso: scripts/next-migration.sh [--check <n>]" >&2
  exit 2
}

check_number=""
case "${1:-}" in
  "") ;;
  --check)
    [[ $# -eq 2 && "$2" =~ ^[0-9]+$ ]] || usage
    check_number=$((10#$2))
    ;;
  *) usage ;;
esac

list_numbers() {
  git -C "$ROOT" ls-tree -r --name-only "$1" "$MIGRATION_DIR" \
    | sed -nE 's|.*/V([0-9]+)__.*\.sql$|\1|p' \
    | sed -E 's/^0+([0-9])/\1/'
}

max=0
available_refs=()
all_numbers=""

for ref in "${REFS[@]}"; do
  if ! git -C "$ROOT" rev-parse --verify -q "${ref}^{commit}" >/dev/null; then
    echo "Aviso: la referencia $ref no existe, se calcula solo con la otra." >&2
    continue
  fi
  available_refs+=("$ref")
  numbers="$(list_numbers "$ref")"
  duplicates="$(printf '%s\n' "$numbers" | grep -v '^$' | sort -n | uniq -d || true)"
  if [[ -n "$duplicates" ]]; then
    for n in $duplicates; do
      echo "Aviso: número de migración V$n duplicado dentro de $ref." >&2
    done
  fi
  all_numbers+="$numbers"$'\n'
  ref_max="$(printf '%s\n' "$numbers" | grep -v '^$' | sort -n | tail -1 || true)"
  if [[ -n "$ref_max" && "$ref_max" -gt "$max" ]]; then
    max="$ref_max"
  fi
done

if [[ ${#available_refs[@]} -eq 0 ]]; then
  echo "Error: no existe ninguna de las referencias ${REFS[*]}." >&2
  exit 3
fi

next=$((max + 1))

tracked_paths=""
for ref in "${available_refs[@]}"; do
  tracked_paths+="$(git -C "$ROOT" ls-tree -r --name-only "$ref" "$MIGRATION_DIR")"$'\n'
done

if [[ -d "$ROOT/$MIGRATION_DIR" ]]; then
  for file in "$ROOT/$MIGRATION_DIR"V[0-9]*__*.sql; do
    [[ -e "$file" ]] || continue
    relative="$MIGRATION_DIR$(basename "$file")"
    number="$(basename "$file" | sed -E 's/^V0*([0-9]+)__.*/\1/')"
    if grep -qxF "$relative" <<<"$tracked_paths"; then
      continue
    fi
    if grep -qx "$number" <<<"$all_numbers"; then
      echo "Aviso: migración duplicada en el árbol de trabajo: $(basename "$file") usa V$number, que ya existe en ${available_refs[*]}." >&2
    fi
  done
fi

if [[ -z "$check_number" ]]; then
  echo "$next"
  exit 0
fi

if grep -qx "$check_number" <<<"$all_numbers"; then
  echo "V$check_number ya existe en ${available_refs[*]}. El próximo libre es V$next." >&2
  exit 1
fi

if [[ "$check_number" -ne "$next" ]]; then
  echo "V$check_number no es el próximo número: se esperaba V$next (máximo actual V$max + 1)." >&2
  exit 1
fi

echo "V$check_number es el próximo número libre."
exit 0
