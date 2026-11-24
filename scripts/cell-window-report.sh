#!/usr/bin/env bash
set -euo pipefail

REPO="${CELL_REPO:-Gabriel030/agustina-bermejo-app}"
TYPES=(feat fix test ci refactor perf docs otros)

usage() {
  echo "uso: cell-window-report.sh <desde-ISO-UTC>  (ej: 2026-11-23T16:30:00Z)" >&2
  exit 2
}

classify() {
  local prefix
  prefix=$(sed -E 's/^([A-Za-z]+).*/\1/' <<<"$1" | tr '[:upper:]' '[:lower:]')
  case "$prefix" in
    feat|fix|test|ci|refactor|perf|docs) echo "$prefix" ;;
    *) echo "otros" ;;
  esac
}

has_migration() {
  if gh api "repos/$REPO/pulls/$1/files?per_page=100" --jq '.[].filename' | grep -q 'db/migration'; then
    echo "si"
  else
    echo "no"
  fi
}

merged_since() {
  gh api "repos/$REPO/pulls?state=closed&base=$1&sort=updated&direction=desc&per_page=100" \
    --jq ".[] | select(.merged_at != null and .merged_at >= \"$2\") | [.number, .merged_at, .title] | @tsv"
}

main() {
  [ "$#" -eq 1 ] || usage
  local since="$1" base number merged_at title type migration
  local -A totals migrations
  local rows=() total=0 total_migrations=0

  [[ "$since" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || usage

  for type in "${TYPES[@]}"; do
    totals[$type]=0
    migrations[$type]=0
  done

  for base in develop master; do
    while IFS=$'\t' read -r number merged_at title; do
      [ -n "$number" ] || continue
      type=$(classify "$title")
      migration=$(has_migration "$number")
      rows+=("$(printf '#%s\t%s\t%s\t%s\t%s\t%s' "$number" "$base" "$merged_at" "$type" "$migration" "$title")")
      totals[$type]=$((totals[$type] + 1))
      total=$((total + 1))
      if [ "$migration" = "si" ]; then
        migrations[$type]=$((migrations[$type] + 1))
        total_migrations=$((total_migrations + 1))
      fi
    done < <(merged_since "$base" "$since")
  done

  echo "PRs mergeados a develop y master desde $since"
  echo
  if [ "$total" -eq 0 ]; then
    echo "Ningun PR mergeado en la ventana."
  else
    echo "| PR | Base | Mergeado (UTC) | Tipo | Migración | Título |"
    echo "|---|---|---|---|---|---|"
    printf '%s\n' "${rows[@]}" | sort -t$'\t' -k3 | awk -F'\t' '{ printf "| %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6 }'
  fi
  echo
  echo "Totales por tipo"
  echo "| Tipo | PRs | Con migración |"
  echo "|---|---:|---:|"
  for type in "${TYPES[@]}"; do
    echo "| $type | ${totals[$type]} | ${migrations[$type]} |"
  done
  echo "| **total** | **$total** | **$total_migrations** |"
}

main "$@"
