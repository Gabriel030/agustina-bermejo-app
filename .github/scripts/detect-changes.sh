#!/usr/bin/env bash
# Decide que partes del CI corren y si el arbol de un push a develop (merge commit o squash) ya fue verificado por un PR verde.
# Entradas (env): GITHUB_EVENT_NAME, GITHUB_REF, GITHUB_OUTPUT, GITHUB_REPOSITORY, GH_TOKEN,
#   PR_BASE_SHA (pull_request), PR_BASE_REF (pull_request), PR_NUMBER (pull_request), TESTED_TREE_DIR (salida para PR)
# Salidas: relevant, frontend, lockfile, run_migration, run_demo_seed, run_docs, deploy, deploy_ops, extra_groups,
#   reuse_backend, reuse_frontend, reuse_run, reuse_pr
set -uo pipefail

event="${GITHUB_EVENT_NAME}"
ref="${GITHUB_REF}"
repo="${GITHUB_REPOSITORY}"

relevant=true
frontend=true
lockfile=true
run_migration=true
run_demo_seed=false
run_docs=false
deploy=true
deploy_ops=true
extra_groups='[]'
reuse_backend=false
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

deploy_scope='^(backend/Dockerfile|backend/\.dockerignore|frontend/Dockerfile|frontend/\.dockerignore|frontend/Caddyfile|docker-compose\.prod\.yml|deploy/|\.github/workflows/|\.github/scripts/)'

backend_scope='^(backend/|contracts/|scripts/|\.github/workflows/|\.github/scripts/)'
migration_scope='^(backend/src/main/resources/db/migration/|backend/src/test/.*Migrat[^/]*\.java$|backend/pom\.xml$|scripts/|\.github/workflows/|\.github/scripts/)'

run_everything_scope='(^|/)(pom\.xml|mvnw|mvnw\.cmd|package\.json|package-lock\.json|pnpm-lock\.yaml|yarn\.lock)$|^backend/\.mvn/'
classified_scope='^(backend/|frontend/|perf/|docs/|\.claude/|\.gitignore$|diagrama_entidades\.puml$)|^[^.].*\.md$'
docs_backend_scope='^(docs/|perf/seed-large\.sql$)|^[^.].*\.md$'
frontend_read_scope='^(docs/(api/error-catalog|context/testing)\.md|backend/src/main/java/.*/shared/audit/AuditAction\.java)$'

demo_seed_scope='^(backend/src/main/java/.*/(config|demo)/[^/]*Demo[^/]*\.java$|backend/src/main/java/.*/demo/|backend/src/main/java/.*Seed[^/]*\.java$|backend/src/test/java/.*Demo[^/]*SeedIT\.java$|backend/src/main/resources/application-demo\.properties$|backend/src/main/resources/db/migration/)'

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
  if changed=$(git -c core.quotePath=false diff --no-renames --name-only "$base" HEAD 2>/dev/null); then
    if ! grep -qE '^(backend/|contracts/|scripts/|\.github/workflows/|\.github/scripts/)' <<< "$changed"; then
      relevant=false
    fi
    if ! grep -qE '^(frontend/|perf/|contracts/openapi\.json$|\.github/workflows/)' <<< "$changed"; then
      frontend=false
    fi
    if ! grep -qE "$deploy_scope" <<< "$changed"; then
      deploy=false
    fi
    if ! grep -qE '^frontend/package-lock\.json$' <<< "$changed"; then
      lockfile=false
    fi
    if ! grep -qE '^(deploy/backup/|deploy/monitoring/|deploy/smoke(\.test)?\.sh$|\.github/workflows/ci\.yml$|\.github/scripts/detect-changes\.sh$)' <<< "$changed"; then
      deploy_ops=false
    fi
    if [ "$event" = pull_request ] && grep -qE "$demo_seed_scope" <<< "$changed"; then
      run_demo_seed=true
    fi
    if [ "$full_tier" = false ]; then
      if ! grep -qE "$migration_scope" <<< "$changed"; then
        run_migration=false
      fi
    fi
    if [ "$event" = pull_request ] && [ -n "$changed" ]; then
      if grep -qE "$run_everything_scope" <<< "$changed" || grep -qvE "$classified_scope" <<< "$changed"; then
        relevant=true
        frontend=true
        run_migration=true
        echo "pull request touches shared or unclassified paths: backend and frontend run"
      fi
      if grep -qE "$docs_backend_scope" <<< "$changed"; then
        relevant=true
      fi
      if grep -qE "$frontend_read_scope" <<< "$changed"; then
        frontend=true
      fi
    fi
  fi
fi

if [ "$event" = pull_request ] && [ -n "${TESTED_TREE_DIR:-}" ]; then
  mkdir -p "$TESTED_TREE_DIR"
  printf '{"tree":"%s","pr":"%s","backend":"%s","frontend":"%s","migration":"%s"}\n' \
    "$(git rev-parse 'HEAD^{tree}')" "${PR_NUMBER:-}" "$relevant" "$frontend" "$run_migration" > "$TESTED_TREE_DIR/tested-tree.json"
fi

pr_head=""
if [ "$develop_push" = true ]; then
  if git rev-parse -q --verify 'HEAD^2' > /dev/null 2>&1; then
    pr_head=$(git rev-parse 'HEAD^2')
  else
    head_sha=$(git rev-parse HEAD)
    pr_head=$(gh api "repos/$repo/commits/$head_sha/pulls" --jq "[.[] | select(.base.ref == \"develop\" and .merged_at != null and .merge_commit_sha == \"$head_sha\")] | if length == 1 then .[0].head.sha else empty end" 2>/dev/null || true)
    if [[ ! "$pr_head" =~ ^[0-9a-f]{40}$ ]]; then
      pr_head=""
      echo "no single merged pull request maps to $head_sha: everything runs"
    fi
  fi
fi

if [ -n "$pr_head" ]; then
  tree=$(git rev-parse 'HEAD^{tree}')
  run=$(gh run list --repo "$repo" --workflow ci.yml --event pull_request --commit "$pr_head" --status success --limit 1 --json databaseId --jq '.[0].databaseId // empty' 2>/dev/null || true)
  if [ -n "$run" ]; then
    download="${RUNNER_TEMP:-/tmp}/tested-tree"
    rm -rf "$download"
    if gh run download "$run" --repo "$repo" -n tested-tree -D "$download" > /dev/null 2>&1 && [ -f "$download/tested-tree.json" ]; then
      recorded=$(jq -r '.tree // empty' "$download/tested-tree.json")
      pr_number=$(jq -r '.pr // empty' "$download/tested-tree.json")
      checks_green=false
      if bad=$(gh api --paginate "repos/$repo/commits/$pr_head/check-runs?filter=latest&per_page=100" --jq '.check_runs[] | select(.conclusion != "success" and .conclusion != "skipped" and .conclusion != "neutral") | .name' 2>/dev/null); then
        if [ -z "$bad" ]; then
          checks_green=true
        else
          echo "checks of $pr_head are not all green ($(tr '\n' ',' <<< "$bad")): everything runs"
        fi
      else
        echo "could not read the check runs of $pr_head: everything runs"
      fi
      if [ "$checks_green" = true ] && [ -n "$recorded" ] && [ "$recorded" = "$tree" ] && [[ "$run" =~ ^[0-9]+$ ]] && [[ "$pr_number" =~ ^[0-9]+$ ]]; then
        reuse_run="$run"
        reuse_pr="$pr_number"
        [ "$(jq -r '.backend' "$download/tested-tree.json")" = true ] && reuse_backend=true
        [ "$(jq -r '.frontend' "$download/tested-tree.json")" = true ] && reuse_frontend=true
        extra=()
        if [ "$reuse_backend" = true ]; then
          [ "$run_migration" = true ] && [ "$(jq -r '.migration // false' "$download/tested-tree.json")" != true ] && extra+=('"migration"')
        fi
        if [ "${#extra[@]}" -gt 0 ]; then
          extra_groups="[$(IFS=,; echo "${extra[*]}")]"
        fi
        echo "tree $tree already verified by PR #$reuse_pr (run $run): backend=$reuse_backend frontend=$reuse_frontend"
        if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
          {
            echo "### Reused from PR #$reuse_pr"
            echo "- Tree: \`$tree\` (identical to the tree verified by run $run)"
            echo "- Skipped here: backend=$reuse_backend, frontend=$reuse_frontend"
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
  echo "frontend=$frontend"
  echo "lockfile=$lockfile"
  echo "run_migration=$run_migration"
  echo "run_demo_seed=$run_demo_seed"
  echo "run_docs=$run_docs"
  echo "deploy=$deploy"
  echo "deploy_ops=$deploy_ops"
  echo "extra_groups=$extra_groups"
  echo "reuse_backend=$reuse_backend"
  echo "reuse_frontend=$reuse_frontend"
  echo "reuse_run=$reuse_run"
  echo "reuse_pr=$reuse_pr"
} >> "$GITHUB_OUTPUT"
