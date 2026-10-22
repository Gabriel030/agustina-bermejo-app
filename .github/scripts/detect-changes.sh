#!/usr/bin/env bash
# Decide que partes del CI corren y si el arbol de un push a develop ya fue verificado por un PR verde.
# Entradas (env): GITHUB_EVENT_NAME, GITHUB_REF, GITHUB_OUTPUT, GITHUB_REPOSITORY, GH_TOKEN,
#   PR_BASE_SHA (pull_request), PR_BASE_REF (pull_request), PR_NUMBER (pull_request), TESTED_TREE_DIR (salida para PR)
# Salidas: relevant, mutation, frontend, lockfile, run_migration, run_karate, run_docs, extra_groups,
#   reuse_backend, reuse_mutation, reuse_frontend, reuse_run, reuse_pr
set -uo pipefail

event="${GITHUB_EVENT_NAME}"
ref="${GITHUB_REF}"
repo="${GITHUB_REPOSITORY}"

relevant=true
mutation=true
frontend=true
lockfile=true
run_migration=true
run_karate=true
run_docs=false
extra_groups='[]'
reuse_backend=false
reuse_mutation=false
reuse_frontend=false
reuse_run=""
reuse_pr=""

develop_push=false
if [ "$event" = push ] && [ "$ref" = refs/heads/develop ]; then
  develop_push=true
fi

full_tier=false
if [ "$event" = push ] || { [ "$event" = pull_request ] && [ "${PR_BASE_REF:-}" = master ]; }; then
  full_tier=true
fi
if [ "$ref" = refs/heads/master ] || { [ "$event" = pull_request ] && [ "${PR_BASE_REF:-}" = master ]; }; then
  run_docs=true
fi

backend_scope='^(backend/|contracts/|scripts/|\.github/workflows/|\.github/scripts/)'
migration_scope='^(backend/src/main/resources/db/migration/|backend/src/test/.*Migrat[^/]*\.java$|backend/pom\.xml$|scripts/|\.github/workflows/|\.github/scripts/)'
karate_billing_layers='/(controller|dto|api|usecase|service|events|gateway|infrastructure|entity|repository)/'

karate_needed() {
  local file
  while IFS= read -r file; do
    case "$file" in
      "") continue;;
      *.md) continue;;
      backend/src/test/java/com/red_escuela/backend/billing/support/*) return 0;;
      backend/src/test/java/com/red_escuela/backend/billing/*) continue;;
      backend/src/main/java/com/red_escuela/backend/billing/*)
        if grep -qE "$karate_billing_layers" <<< "$file"; then
          return 0
        fi
        continue;;
    esac
    return 0
  done <<< "$(grep -E "$backend_scope" <<< "$1")"
  return 1
}

base=""
if [ "$event" = pull_request ]; then
  base="${PR_BASE_SHA:-}"
elif [ "$develop_push" = true ]; then
  if last=$(gh run list --repo "$repo" --workflow ci.yml --branch develop --event push --status success --limit 1 --json headSha --jq '.[0].headSha // empty'); then
    if [ -n "$last" ] && git cat-file -e "${last}^{commit}" 2>/dev/null; then
      base="$last"
    fi
  else
    echo "could not list the last green push: everything runs"
  fi
  echo "push diff base: ${base:-none (everything runs)}"
fi

if [ -n "$base" ]; then
  if changed=$(git -c core.quotePath=false diff --name-only "$base" HEAD 2>/dev/null); then
    if ! grep -qE '^(backend/|contracts/|scripts/|\.github/workflows/|\.github/scripts/)' <<< "$changed"; then
      relevant=false
    fi
    if [ "$relevant" = true ] && ! grep -qE '^backend/(pom\.xml|src/(main|test)/java/com/red_escuela/backend/(shared/domain|billing)/)' <<< "$changed"; then
      mutation=false
    fi
    if [ "$relevant" = false ]; then
      mutation=false
    fi
    if ! grep -qE '^(frontend/|\.github/workflows/)' <<< "$changed"; then
      frontend=false
    fi
    if ! grep -qE '^frontend/package-lock\.json$' <<< "$changed"; then
      lockfile=false
    fi
    if [ "$full_tier" = false ]; then
      if ! grep -qE "$migration_scope" <<< "$changed"; then
        run_migration=false
      fi
      if ! karate_needed "$changed"; then
        run_karate=false
      fi
    fi
  fi
fi

if [ "$event" = pull_request ] && [ -n "${TESTED_TREE_DIR:-}" ]; then
  mkdir -p "$TESTED_TREE_DIR"
  printf '{"tree":"%s","pr":"%s","backend":"%s","mutation":"%s","frontend":"%s","migration":"%s","karate":"%s"}\n' \
    "$(git rev-parse 'HEAD^{tree}')" "${PR_NUMBER:-}" "$relevant" "$mutation" "$frontend" "$run_migration" "$run_karate" > "$TESTED_TREE_DIR/tested-tree.json"
fi

if [ "$develop_push" = true ] && git rev-parse -q --verify 'HEAD^2' > /dev/null 2>&1; then
  pr_head=$(git rev-parse 'HEAD^2')
  tree=$(git rev-parse 'HEAD^{tree}')
  run=$(gh run list --repo "$repo" --workflow ci.yml --event pull_request --commit "$pr_head" --status success --limit 1 --json databaseId --jq '.[0].databaseId // empty' 2>/dev/null || true)
  if [ -n "$run" ]; then
    download="${RUNNER_TEMP:-/tmp}/tested-tree"
    rm -rf "$download"
    if gh run download "$run" --repo "$repo" -n tested-tree -D "$download" > /dev/null 2>&1 && [ -f "$download/tested-tree.json" ]; then
      recorded=$(jq -r '.tree // empty' "$download/tested-tree.json")
      pr_number=$(jq -r '.pr // empty' "$download/tested-tree.json")
      if [ -n "$recorded" ] && [ "$recorded" = "$tree" ] && [[ "$run" =~ ^[0-9]+$ ]] && [[ "$pr_number" =~ ^[0-9]+$ ]]; then
        reuse_run="$run"
        reuse_pr="$pr_number"
        [ "$(jq -r '.backend' "$download/tested-tree.json")" = true ] && reuse_backend=true
        [ "$(jq -r '.mutation' "$download/tested-tree.json")" = true ] && reuse_mutation=true
        [ "$(jq -r '.frontend' "$download/tested-tree.json")" = true ] && reuse_frontend=true
        extra=()
        if [ "$reuse_backend" = true ]; then
          [ "$run_migration" = true ] && [ "$(jq -r '.migration // false' "$download/tested-tree.json")" != true ] && extra+=('"migration"')
          [ "$run_karate" = true ] && [ "$(jq -r '.karate // false' "$download/tested-tree.json")" != true ] && extra+=('"karate"')
        fi
        if [ "${#extra[@]}" -gt 0 ]; then
          extra_groups="[$(IFS=,; echo "${extra[*]}")]"
        fi
        echo "tree $tree already verified by PR #$reuse_pr (run $run): backend=$reuse_backend mutation=$reuse_mutation frontend=$reuse_frontend"
        if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
          {
            echo "### Reused from PR #$reuse_pr"
            echo "- Tree: \`$tree\` (identical to the tree verified by run $run)"
            echo "- Skipped here: backend=$reuse_backend, PIT=$reuse_mutation, frontend=$reuse_frontend"
          } >> "$GITHUB_STEP_SUMMARY"
        fi
      else
        echo "tree $tree is not the tree recorded by run $run (${recorded:-none}) or the record is malformed: everything runs"
      fi
    else
      echo "run $run has no readable tested-tree artifact (expired or missing): everything runs"
    fi
  else
    echo "no green pull request run for $pr_head: everything runs"
  fi
fi

{
  echo "relevant=$relevant"
  echo "mutation=$mutation"
  echo "frontend=$frontend"
  echo "lockfile=$lockfile"
  echo "run_migration=$run_migration"
  echo "run_karate=$run_karate"
  echo "run_docs=$run_docs"
  echo "extra_groups=$extra_groups"
  echo "reuse_backend=$reuse_backend"
  echo "reuse_mutation=$reuse_mutation"
  echo "reuse_frontend=$reuse_frontend"
  echo "reuse_run=$reuse_run"
  echo "reuse_pr=$reuse_pr"
} >> "$GITHUB_OUTPUT"
echo "backend=$relevant mutation=$mutation frontend=$frontend lockfile=$lockfile migration=$run_migration karate=$run_karate docs=$run_docs extra=$extra_groups reuse(backend=$reuse_backend mutation=$reuse_mutation frontend=$reuse_frontend run=${reuse_run:-none})"
