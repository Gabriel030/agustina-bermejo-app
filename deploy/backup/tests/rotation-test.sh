#!/usr/bin/env bash
set -uo pipefail
scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")/../scripts" && pwd)"
. "$(dirname "${BASH_SOURCE[0]}")/lib-test.sh"
use_scripts_as_opt_backup

epoch_of() { date -u -d "$1" +%s; }

expired() {
  local now="$1" offset="$2" daily="$3" weekly="$4" quarterly="$5"
  shift 5
  printf '%s\n' "$@" | sort | awk -v keep_daily="$daily" -v keep_weekly="$weekly" -v keep_quarterly="$quarterly" \
    -v now_epoch="$(epoch_of "$now")" -v tz_offset="$offset" -f "$scripts/select-expired.awk" | tr '\n' ' '
}

dump() { echo "abdb_$1.dump"; }

today_files=(
  "$(dump 20410310T000000Z)" "$(dump 20410310T040000Z)" "$(dump 20410310T080000Z)" "$(dump 20410310T120000Z)"
)
yesterday_files=("$(dump 20410309T000000Z)" "$(dump 20410309T200000Z)")

check "intraday: los de hoy se conservan y de ayer queda solo el ultimo" \
  "abdb_20410309T000000Z.dump " \
  "$(expired '2041-03-10 15:00:00' 0 7 4 0 "${today_files[@]}" "${yesterday_files[@]}")"

check "intraday: con daily=1 ayer vence entero pero hoy sigue completo" \
  "abdb_20410309T000000Z.dump abdb_20410309T200000Z.dump " \
  "$(expired '2041-03-10 15:00:00' 0 1 0 0 "${today_files[@]}" "${yesterday_files[@]}")"

check "borde de dia con zona -03:00: 02:00Z es todavia el dia anterior" \
  "abdb_20410309T200000Z.dump " \
  "$(expired '2041-03-10 15:00:00' -10800 7 0 0 "$(dump 20410309T200000Z)" "$(dump 20410310T020000Z)" "$(dump 20410310T130000Z)")"

check "borde de dia con zona -03:00: 03:00Z ya es el dia de hoy y se conserva" \
  "" \
  "$(expired '2041-03-10 15:00:00' -10800 7 0 0 "$(dump 20410310T030000Z)" "$(dump 20410310T130000Z)")"

check "semanal: domingo 10 y lunes 11 son semanas distintas; con 2 semanas queda el ultimo de las dos mas recientes" \
  "abdb_20410310T060000Z.dump abdb_20410311T060000Z.dump abdb_20410316T060000Z.dump " \
  "$(expired '2041-03-24 12:00:00' 0 0 2 0 "$(dump 20410310T060000Z)" "$(dump 20410316T060000Z)" "$(dump 20410311T060000Z)" "$(dump 20410317T060000Z)" "$(dump 20410322T060000Z)")"

check "trimestral con 0 conserva el ultimo de cada trimestre sin vencimiento (borde de fin de ano)" \
  "abdb_20400115T060000Z.dump abdb_20401002T060000Z.dump abdb_20410102T060000Z.dump " \
  "$(expired '2041-12-31 12:00:00' 0 0 0 0 "$(dump 20400115T060000Z)" "$(dump 20400320T060000Z)" "$(dump 20401002T060000Z)" "$(dump 20401231T060000Z)" "$(dump 20410102T060000Z)" "$(dump 20410320T060000Z)")"

check "trimestral con 2 conserva el ultimo de los dos trimestres mas recientes (31-dic y 20-mar)" \
  "abdb_20400320T060000Z.dump abdb_20410110T060000Z.dump " \
  "$(expired '2041-04-20 12:00:00' 0 0 0 2 "$(dump 20400320T060000Z)" "$(dump 20401231T060000Z)" "$(dump 20410110T060000Z)" "$(dump 20410320T060000Z)")"

check "un dump cifrado (.age) se trata igual que uno sin cifrar" \
  "abdb_20410309T000000Z.dump.age " \
  "$(expired '2041-03-10 15:00:00' 0 7 0 0 abdb_20410309T000000Z.dump.age abdb_20410309T200000Z.dump.age abdb_20410310T000000Z.dump.age)"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
make_pair() {
  local stamp="$1" suffix="$2"
  : > "$work/abdb_$stamp.dump$suffix"; : > "$work/abdb_$stamp.dump$suffix.sha256"; : > "$work/abdb_$stamp.meta"
  : > "$work/abfiles_$stamp.tar.zst$suffix"; : > "$work/abfiles_$stamp.tar.zst$suffix.sha256"
}
make_pair 20410309T000000Z ""
make_pair 20410309T200000Z ""
make_pair 20410310T040000Z ".age"
make_pair 20410310T080000Z ".age"
BACKUP_DIR="$work" BACKUP_METRICS_DIR="$work/m" BACKUP_NOW_EPOCH="$(epoch_of '2041-03-10 15:00:00')" BACKUP_TZ_OFFSET_SECONDS=0 \
  BACKUP_KEEP_DAILY=7 BACKUP_KEEP_WEEKLY=0 BACKUP_KEEP_QUARTERLY=0 "$scripts/rotate.sh" > /dev/null
check "rotate.sh: el par vencido desaparece completo (dump, checksum, meta, archivo de /data y su checksum)" \
  "abdb_20410309T200000Z.dump abdb_20410309T200000Z.dump.sha256 abdb_20410309T200000Z.meta abdb_20410310T040000Z.dump.age abdb_20410310T040000Z.dump.age.sha256 abdb_20410310T040000Z.meta abdb_20410310T080000Z.dump.age abdb_20410310T080000Z.dump.age.sha256 abdb_20410310T080000Z.meta abfiles_20410309T200000Z.tar.zst abfiles_20410309T200000Z.tar.zst.sha256 abfiles_20410310T040000Z.tar.zst.age abfiles_20410310T040000Z.tar.zst.age.sha256 abfiles_20410310T080000Z.tar.zst.age abfiles_20410310T080000Z.tar.zst.age.sha256 " \
  "$(find "$work" -maxdepth 1 -type f -printf '%f\n' | sort | tr '\n' ' ')"

finish
