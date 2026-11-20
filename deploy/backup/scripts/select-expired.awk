function days_from_civil(y, m, d,    era, yoe, doy, doe) {
  if (m <= 2) y -= 1
  era = int(y / 400)
  yoe = y - era * 400
  doy = int((153 * (m + (m > 2 ? -3 : 9)) + 2) / 5) + d - 1
  doe = yoe * 365 + int(yoe / 4) - int(yoe / 100) + doy
  return era * 146097 + doe - 719468
}
function civil_from_days(z,    era, doe, yoe, doy, mp) {
  z += 719468
  era = int((z >= 0 ? z : z - 146096) / 146097)
  doe = z - era * 146097
  yoe = int((doe - int(doe / 1460) + int(doe / 36524) - int(doe / 146096)) / 365)
  doy = doe - (365 * yoe + int(yoe / 4) - int(yoe / 100))
  mp = int((5 * doy + 2) / 153)
  civ_month = mp + (mp < 10 ? 3 : -9)
  civ_year = yoe + era * 400 + (civ_month <= 2 ? 1 : 0)
}
{
  files[NR] = $0
  s = $0
  sub(/^[^_]*_/, "", s)
  y = substr(s, 1, 4) + 0
  m = substr(s, 5, 2) + 0
  d = substr(s, 7, 2) + 0
  seconds = substr(s, 10, 2) * 3600 + substr(s, 12, 2) * 60 + substr(s, 14, 2)
  local_epoch = days_from_civil(y, m, d) * 86400 + seconds + tz_offset
  dayn = int(local_epoch / 86400)
  civil_from_days(dayn)
  day[NR] = dayn
  week[NR] = int((dayn + 3) / 7)
  quarter[NR] = civ_year * 4 + int((civ_month - 1) / 3)
}
END {
  today = int((now_epoch + tz_offset) / 86400)
  for (i = NR; i >= 1; i--) {
    if (day[i] == today) keep[i] = 1
    if (!(day[i] in seenday) && ndays < keep_daily) { seenday[day[i]] = 1; ndays++; keep[i] = 1 }
    if (!(week[i] in seenweek) && nweeks < keep_weekly) { seenweek[week[i]] = 1; nweeks++; keep[i] = 1 }
    if (!(quarter[i] in seenquarter) && (keep_quarterly == 0 || nquarters < keep_quarterly)) { seenquarter[quarter[i]] = 1; nquarters++; keep[i] = 1 }
  }
  for (i = 1; i <= NR; i++) if (!(i in keep)) print files[i]
}
