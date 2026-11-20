#!/usr/bin/env bash
set -uo pipefail
scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scripts" && pwd)"
. "$(dirname "${BASH_SOURCE[0]}")/lib-test.sh"
. "$scripts/lib.sh"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

head -c 12000000 /dev/zero | verify_while_streaming "$work/rc" head -c 100 | cat > "$work/sink"
statuses=("${PIPESTATUS[@]}")
wait_for_file "$work/rc"
check "un verificador que sale temprano no mata al productor" "0 0 0" "${statuses[*]}"
check "el sumidero recibe el stream completo aunque el verificador salga temprano" "12000000" "$(wc -c < "$work/sink" | tr -d ' ')"
check "el codigo de salida del verificador queda registrado" "0" "$(cat "$work/rc")"

head -c 12000000 /dev/zero | verify_while_streaming "$work/rc-fail" false | cat > "$work/sink-fail"
statuses=("${PIPESTATUS[@]}")
wait_for_file "$work/rc-fail"
check "un verificador que falla deja su codigo y no corta el stream" "0 0 0 1" "${statuses[*]} $(cat "$work/rc-fail")"

printf './a.txt\n./dir/\n./dir/b.txt\ntar: ./c.txt: file changed as we read it\n' | count_files_in_tar_listing "$work/count" 2> "$work/warnings"
check "el conteo de archivos ignora los directorios" "2" "$(cat "$work/count")"
check "las advertencias del tar siguen llegando a stderr" "tar: ./c.txt: file changed as we read it" "$(cat "$work/warnings")"
printf '' | count_files_in_tar_listing "$work/count-empty"
check "un listado vacio cuenta cero archivos" "0" "$(cat "$work/count-empty")"

finish
