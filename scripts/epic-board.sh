#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/cell-board.sh"

EPIC_MARKER='<!-- epic-board -->'
MISSING_MARKER='<!-- epic-board-missing -->'
AUTOLABEL_MARKER='<!-- epic-autolabel-missing -->'
EPIC_CONFIG="${EPIC_BOARD_CONFIG:-.github/epic-board.json}"

collect_epics() {
  local dir="$1" since ignored
  mkdir -p "$dir"
  cp "$EPIC_CONFIG" "$dir/config.json"
  since=$(jq -r '.since' "$EPIC_CONFIG")
  ignored=$(jq -c '.ignored_head_prefixes' "$EPIC_CONFIG")
  api_stream "repos/$REPO/pulls?state=closed&base=develop&sort=updated&direction=desc&per_page=100" \
    | jq --arg since "$since" --argjson ignored "$ignored" '
        [ .[]
          | select(.merged_at != null and .merged_at >= $since)
          | . as $p
          | select([$ignored[] | . as $prefix | $p.head.ref | startswith($prefix)] | any | not)
          | {number, title, url: .html_url, merged_at, labels: [.labels[].name]} ]' > "$dir/merged.json"
}

render_epics() {
  local dir="$1" now="${NOW:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
  jq -n -r --arg now "$now" --arg marker "$EPIC_MARKER" \
    --slurpfile config "$dir/config.json" --slurpfile merged "$dir/merged.json" '
    def esc: gsub("\\s+"; " ") | gsub("`"; "'"'"'") | gsub("\\|"; "\\|") | "`" + . + "`";
    def cell: tostring | gsub("\\s+"; " ") | gsub("\\|"; "\\|");
    def pct($done; $total):
      if $total == null or $total == 0 or $done == null then "—"
      else (([$done / $total * 100, 100] | min) * 10 | round / 10 | tostring) + " %" end;
    def table($head; $rows; $empty):
      if ($rows | length) == 0 then $empty
      else ($head | join(" | ")) as $h
        | "| \($h) |\n|" + ([$head[] | " --- |"] | join("")) + "\n" + ($rows | map("| " + join(" | ") + " |") | join("\n")) end;
    $config[0] as $cfg
    | ($cfg.epics | map(.id | ascii_upcase)) as $known
    | ($merged[0] | unique_by(.number)
        | map(. + {epics: ([.labels[] | select(ascii_downcase | startswith("epic:")) | .[5:] | ascii_upcase] | unique)})
        | sort_by(.merged_at) | reverse) as $prs
    | $marker,
    "## Tablero de épicas",
    "",
    "_Actualizado: \($now) UTC · línea base: \($cfg.baseline_date) · paquetes del \($cfg.seed_date) más PRs mergeados a develop desde \($cfg.since)_",
    "",
    "### 1. Avance por épica",
    table(["Épica", "% \($cfg.baseline_date)", "% hoy", "Paquetes", "Últimos PRs", "Próximo paso"];
      [ $cfg.epics[] | . as $e
        | ([ $prs[] | select(.epics | index($e.id | ascii_upcase)) ]) as $mine
        | (($e.done_at_seed // 0) + ($mine | length)) as $done
        | [ ($e.id + (if $e.name != $e.id then " " + $e.name else "" end) | cell),
            pct($e.done_at_baseline; $e.total),
            pct($done; $e.total),
            "\($done)/\($e.total // "?")" + (if $e.total_estimated then " (est.)" else "" end),
            (if ($mine | length) == 0 then "—" else ($mine[:3] | map("[#\(.number)](\(.url))") | join(", ")) end),
            (($e.next_step // "") | if . == "" then "—" else cell end) ] ];
      "_Sin épicas en el catálogo._"),
    "",
    "### 2. Sin épica",
    table(["PR", "Título", "Mergeado (UTC)"];
      [ $prs[] | select(.epics | length == 0) | ["[#\(.number)](\(.url))", (.title | esc), .merged_at] ];
      "_Ninguno._"),
    "",
    "### 3. Épica desconocida",
    table(["PR", "Etiqueta", "Título"];
      [ $prs[] | . as $p | $p.epics[] | select(. as $id | $known | index($id) | not)
        | ["[#\($p.number)](\($p.url))", ("epic:" + . | esc), ($p.title | esc)] ];
      "_Ninguno._")
  '
}

find_epic_board_issue() {
  local label title number
  label=$(jq -r '.board_label' "$EPIC_CONFIG")
  title=$(jq -r '.board_title' "$EPIC_CONFIG")
  number=$(api "repos/$REPO/issues?state=open&labels=$label&per_page=1" --jq '.[0].number // empty')
  if [ -z "$number" ]; then
    ensure_label "$label" "0E8A16"
    number=$(api "repos/$REPO/issues" -f title="$title" -f "labels[]=$label" --jq '.number')
  fi
  echo "$number"
}

warn_missing_epic_label() {
  local dir="$1" number="$2" missing posted body_file
  missing=$(jq -r --argjson n "$number" '
    .[] | select(.number == $n)
    | select([.labels[] | ascii_downcase | startswith("epic:")] | any | not) | .number' "$dir/merged.json")
  [ -n "$missing" ] || return 0
  posted=$(api_stream "repos/$REPO/issues/$number/comments?per_page=100" \
    | jq -r --arg m "$MISSING_MARKER" '[.[] | select(.body | contains($m))] | length')
  [ "$posted" -eq 0 ] || return 0
  body_file="$dir/missing.md"
  printf '%s\nFalta etiqueta `epic:<id>`. El PR ya está mergeado y figura en «Sin épica» del Tablero de épicas; agregá la etiqueta (por ejemplo `epic:E23`) y el tablero se recalcula solo.\n' "$MISSING_MARKER" > "$body_file"
  api "repos/$REPO/issues/$number/comments" -F body=@"$body_file" >/dev/null
}

publish_epics() {
  local dir="$1" board
  board=$(find_epic_board_issue)
  render_epics "$dir" > "$dir/board.md"
  api -X PATCH "repos/$REPO/issues/$board" -F body=@"$dir/board.md" >/dev/null
  if [ "${PR_ACTION:-}" = closed ] && [[ "${PR_NUMBER:-}" =~ ^[0-9]+$ ]]; then
    warn_missing_epic_label "$dir" "$PR_NUMBER"
  fi
}

create_epic_labels() {
  local id
  for id in $(jq -r '.epics[].id' "$EPIC_CONFIG"); do
    ensure_label "epic:$id" "C5DEF5"
  done
}

infer_epic() {
  local dir="$1"
  jq -r --slurpfile cfg "$dir/config.json" '
    def tok($s; $r): ($s | ascii_downcase) | test("(^|[^a-z0-9])" + ($r | ascii_downcase) + "($|[^a-z0-9])");
    def only($c): ($c | map(.id) | unique) as $ids | if ($ids | length) == 1 then $ids[0] else null end;
    $cfg[0] as $c
    | . as $pr
    | if ([$c.ignored_head_prefixes[] | . as $x | $pr.head_ref | startswith($x)] | any) then "ignore"
      elif ([$pr.labels[] | ascii_downcase | startswith("epic:")] | any) then "keep"
      else
        ([ $c.epics[] | . as $e | ($e.refs // [])[] | select(tok($pr.head_ref; .)) | {id: $e.id, len: length} ]) as $b
        | (if ($b | length) == 0 then null else only($b | (map(.len) | max) as $m | map(select(.len == $m))) end) as $byBranch
        | ((($pr.title // "") + "\n" + ($pr.body // ""))) as $text
        | ([ $c.epics[] | . as $e | ($e.issues // [])[] | tostring as $n
            | select($n != ($pr.number | tostring))
            | select($text | test("(\\b(refs?|closes?|closed|fix(es|ed)?|resolves?|resolved)\\b:? *#" + $n + "(?![0-9])|\\(#" + $n + "\\))"; "i"))
            | {id: $e.id} ]) as $i
        | (if ($b | length) > 0 then $byBranch else only($i) end) as $found
        | if $found == null then "none" else "label " + $found end
      end' "$dir/pr.json"
}

autolabel_pr() {
  local dir="$1" number="$2" result body_file posted
  mkdir -p "$dir"
  cp "$EPIC_CONFIG" "$dir/config.json"
  api "repos/$REPO/pulls/$number" \
    | jq '{number, title, body, head_ref: .head.ref, merged, labels: [.labels[].name]}' > "$dir/pr.json"
  [ "$(jq -r '.merged' "$dir/pr.json")" = false ] || return 0
  result=$(infer_epic "$dir")
  case "$result" in
    label\ *)
      ensure_label "epic:${result#label }" "C5DEF5"
      api "repos/$REPO/issues/$number/labels" -f "labels[]=epic:${result#label }" >/dev/null ;;
    none)
      posted=$(api_stream "repos/$REPO/issues/$number/comments?per_page=100" \
        | jq -r --arg m "$AUTOLABEL_MARKER" '[.[] | select(.body | contains($m))] | length')
      [ "$posted" -eq 0 ] || return 0
      body_file="$dir/autolabel.md"
      printf '%s\nNo pude inferir la épica de este PR. Agregá la etiqueta `epic:<id>` (por ejemplo `epic:E23`) o usá una rama con el prefijo de la épica (`e34-c`) o `Refs #<issue>` de una rebanada del catálogo.\n' "$AUTOLABEL_MARKER" > "$body_file"
      api "repos/$REPO/issues/$number/comments" -F body=@"$body_file" >/dev/null ;;
  esac
}

main() {
  local cmd="${1:-publish}" dir="${2:-$(mktemp -d)}"
  case "$cmd" in
    collect) collect_epics "$dir" ;;
    render) render_epics "$dir" ;;
    labels) create_epic_labels ;;
    infer) infer_epic "${2:?dir}" ;;
    autolabel) autolabel_pr "$dir" "${PR_NUMBER:?PR_NUMBER}" ;;
    publish) collect_epics "$dir"; publish_epics "$dir" ;;
    *) echo "uso: epic-board.sh collect|render|labels|infer|autolabel|publish [dir]" >&2; exit 2 ;;
  esac
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
