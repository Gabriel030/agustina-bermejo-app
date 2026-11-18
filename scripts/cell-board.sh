#!/usr/bin/env bash
set -euo pipefail

MARKER='<!-- cell-board -->'
CONFIG="${CELL_BOARD_CONFIG:-.github/cell-board.json}"

api() { gh api "$@"; }

api_stream() { gh api --paginate "$@" --jq '.[]' | jq -s '.'; }

iso_ago() { date -u -d "$1 ago" +%Y-%m-%dT%H:%M:%SZ; }

ci_state() {
  api "repos/$REPO/commits/$1/check-runs?per_page=100" --jq '
    .check_runs as $r
    | if ($r | length) == 0 then "pendiente"
      elif ([$r[] | select(.status == "completed" and (.conclusion | IN("failure","timed_out","cancelled","action_required")))] | length) > 0 then "rojo"
      elif ([$r[] | select(.status != "completed")] | length) > 0 then "pendiente"
      else "verde" end'
}

ensure_label() {
  api "repos/$REPO/labels" -f name="$1" -f color="$2" >/dev/null 2>&1 || true
}

collect() {
  local dir="$1" board_label title window follow
  mkdir -p "$dir"
  cp "$CONFIG" "$dir/config.json"
  board_label=$(jq -r '.board_label' "$CONFIG")
  window=$(jq -r '.merged_window_hours' "$CONFIG")
  follow=$(jq -c '.follow_up_issues' "$CONFIG")

  : > "$dir/pulls.ndjson"
  for n in $(api_stream "repos/$REPO/pulls?state=open&per_page=100" | jq -r '.[].number'); do
    api "repos/$REPO/pulls/$n" > "$dir/pull.json"
    sha=$(jq -r '.head.sha' "$dir/pull.json")
    ci=$(ci_state "$sha")
    jq -c --arg ci "$ci" '{number, title, url: .html_url, draft, mergeable_state: (.mergeable_state // "unknown"), ci: $ci}' "$dir/pull.json" >> "$dir/pulls.ndjson"
  done
  jq -s '.' "$dir/pulls.ndjson" > "$dir/pulls.json"

  api "repos/$REPO/pulls?state=closed&sort=updated&direction=desc&per_page=100" \
    | jq --arg since "$(iso_ago "$window hours")" '[.[] | select(.merged_at != null and .merged_at >= $since) | {number, title, url: .html_url, merged_at}]' > "$dir/merged.json"

  api_stream "repos/$REPO/issues?state=open&per_page=100" \
    | jq --arg bl "$board_label" --argjson follow "$follow" '[.[] | select(.pull_request == null) | select(([.labels[].name | select(startswith("cell:") and . != $bl)] | length) > 0 or (.number | IN($follow[])))] | map({number, title, created_at})' > "$dir/issues.json"

  for n in $(jq -r '.[].number' "$dir/issues.json") $(jq -r '.[]' <<<"$follow"); do
    api_stream "repos/$REPO/issues/$n/comments?per_page=100" \
      | jq --argjson n "$n" '[.[] | {issue: $n, created_at, body, url: .html_url}]'
  done | jq -s 'add // []' > "$dir/comments.json"

  for n in $(jq -r '.[]' <<<"$follow"); do
    api "repos/$REPO/issues/$n" --jq '{number, title, created_at}'
  done | jq -s '.' > "$dir/follow_issues.json"
}

render() {
  local dir="$1" now="${NOW:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
  jq -n -r --arg now "$now" --arg marker "$MARKER" --slurpfile config "$dir/config.json" \
    --slurpfile pulls "$dir/pulls.json" \
    --slurpfile merged "$dir/merged.json" \
    --slurpfile comments "$dir/comments.json" \
    --slurpfile follow "$dir/follow_issues.json" '
    def esc: gsub("\\|"; "\\|");
    def ago($t): (($now | fromdateiso8601) - ($t | fromdateiso8601)) as $s
      | if $s < 3600 then "\($s / 60 | floor) min" else "\($s / 3600 | floor) h \(($s % 3600) / 60 | floor) min" end;
    def events:
      [ $comments[0][]
        | select((.body // "") | contains($marker) | not)
        | . as $c
        | (.body // "" | split("\n"))[]
        | sub("^\\s+"; "")
        | . as $l
        | (if test("^NECESITO WORKER") then "worker"
           elif test("^CORREGIR #[0-9]+") then "fix"
           elif test("^CORREGIDO #[0-9]+") then "done"
           else null end) as $k
        | select($k != null)
        | { kind: $k, at: $c.created_at, url: $c.url,
            n: (([$l | scan("#([0-9]+)") | .[0] | tonumber] | first) // $c.issue) } ];
    def pending($k):
      events as $e
      | [ $e[] | select(.kind == $k) | . as $r
          | select(([$e[] | select(.kind == "done" and .n == $r.n and .at >= $r.at)] | length) == 0) ];
    def table($head; $rows; $empty):
      if ($rows | length) == 0 then $empty
      else ($head | join(" | ")) as $h
        | "| \($h) |\n|" + ([$head[] | " --- |"] | join("")) + "\n" + ($rows | map("| " + join(" | ") + " |") | join("\n")) end;
    $config[0] as $cfg
    | $marker,
    "## Tablero de la célula",
    "",
    "_Actualizado: \($now) UTC_",
    "",
    "### 1. PRs abiertos",
    table(["PR", "Título", "Merge", "CI"];
      [ $pulls[0][] | ["[#\(.number)](\(.url))", (.title | esc) + (if .draft then " (borrador)" else "" end), .mergeable_state, .ci] ];
      "_Sin PRs abiertos._"),
    "",
    "### 2. Pedidos de worker sin atender",
    table(["Para", "Desde", "Comentario"];
      [ pending("worker")[] | ["#\(.n)", .at, "[ver](\(.url))"] ];
      "_Ninguno._"),
    "",
    "### 3. CORREGIR sin CORREGIDO",
    table(["Para", "Desde", "Comentario"];
      [ pending("fix")[] | ["#\(.n)", .at, "[ver](\(.url))"] ];
      "_Ninguno._"),
    "",
    "### 4. Merges de las últimas \($cfg.merged_window_hours) horas",
    table(["PR", "Título", "Mergeado (UTC)"];
      [ $merged[0][] | ["[#\(.number)](\(.url))", (.title | esc), .merged_at] ];
      "_Ninguno._"),
    "",
    "### 5. Seguimiento sin actividad hace más de \($cfg.stale_minutes) minutos",
    table(["Issue", "Título", "Último comentario", "Sin actividad"];
      [ $follow[0][] | . as $i
        | ([ $comments[0][] | select(.issue == $i.number and ((.body // "") | contains($marker) | not)) | .created_at ] | max // $i.created_at) as $last
        | select((($now | fromdateiso8601) - ($last | fromdateiso8601)) > ($cfg.stale_minutes * 60))
        | ["#\($i.number)", ($i.title | esc), $last, ago($last)] ];
      "_Todos con actividad reciente._")
  '
}

count_pending_workers() {
  jq -n --slurpfile comments "$1/comments.json" --arg marker "$MARKER" '
    [ $comments[0][]
      | select((.body // "") | contains($marker) | not)
      | . as $c
      | (.body // "" | split("\n"))[]
      | sub("^\\s+"; "")
      | . as $l
      | (if test("^NECESITO WORKER") then "worker" elif test("^CORREGIDO #[0-9]+") then "done" else null end) as $k
      | select($k != null)
      | { kind: $k, at: $c.created_at, n: (([$l | scan("#([0-9]+)") | .[0] | tonumber] | first) // $c.issue) } ] as $e
    | [ $e[] | select(.kind == "worker") | . as $r
        | select(([$e[] | select(.kind == "done" and .n == $r.n and .at >= $r.at)] | length) == 0) ] | length'
}

find_board_issue() {
  local board_label title number
  board_label=$(jq -r '.board_label' "$CONFIG")
  title=$(jq -r '.board_title' "$CONFIG")
  number=$(api "repos/$REPO/issues?state=open&labels=$board_label&per_page=1" --jq '.[0].number // empty')
  if [ -z "$number" ]; then
    ensure_label "$board_label" "0E8A16"
    number=$(api "repos/$REPO/issues" -f title="$title" -f "labels[]=$board_label" --jq '.number')
  fi
  echo "$number"
}

publish() {
  local dir="$1" board body_file comment_id pending
  board=$(find_board_issue)
  body_file="$dir/board.md"
  render "$dir" > "$body_file"

  comment_id=$(api_stream "repos/$REPO/issues/$board/comments?per_page=100" \
    | jq -r --arg m "$MARKER" '[.[] | select(.body | contains($m))] | first | .id // empty')
  if [ -n "$comment_id" ]; then
    api -X PATCH "repos/$REPO/issues/comments/$comment_id" -F body=@"$body_file" >/dev/null
  else
    api "repos/$REPO/issues/$board/comments" -F body=@"$body_file" >/dev/null
  fi

  pending=$(count_pending_workers "$dir")
  ensure_label needs-worker "D93F0B"
  if [ "$pending" -gt 0 ]; then
    api "repos/$REPO/issues/$board/labels" -f 'labels[]=needs-worker' >/dev/null
  else
    api -X DELETE "repos/$REPO/issues/$board/labels/needs-worker" >/dev/null 2>&1 || true
  fi
}

main() {
  local cmd="${1:-publish}" dir="${2:-$(mktemp -d)}"
  case "$cmd" in
    collect) collect "$dir" ;;
    render) render "$dir" ;;
    count-pending-workers) count_pending_workers "$dir" ;;
    publish) collect "$dir"; publish "$dir" ;;
    *) echo "uso: cell-board.sh collect|render|count-pending-workers|publish [dir]" >&2; exit 2 ;;
  esac
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
