function days_from_civil(y, m, d,    era, yoe, doy, doe) {
  if (m <= 2) y -= 1
  era = int(y / 400)
  yoe = y - era * 400
  doy = int((153 * (m + (m > 2 ? -3 : 9)) + 2) / 5) + d - 1
  doe = yoe * 365 + int(yoe / 4) - int(yoe / 100) + doy
  return era * 146097 + doe - 719468
}
{
  files[NR] = $0
  s = $0
  sub(/^[^_]*_/, "", s)
  y = substr(s, 1, 4) + 0
  m = substr(s, 5, 2) + 0
  d = substr(s, 7, 2) + 0
  day[NR] = sprintf("%04d%02d%02d", y, m, d)
  week[NR] = int((days_from_civil(y, m, d) + 3) / 7)
  month[NR] = sprintf("%04d%02d", y, m)
}
END {
  for (i = NR; i >= 1; i--) {
    if (recent < keep_recent) { keep[i] = 1; recent++ }
    if (!(day[i] in seenday) && ndays < keep_daily) { seenday[day[i]] = 1; ndays++; keep[i] = 1 }
    if (!(week[i] in seenweek) && nweeks < keep_weekly) { seenweek[week[i]] = 1; nweeks++; keep[i] = 1 }
    if (!(month[i] in seenmonth) && nmonths < keep_monthly) { seenmonth[month[i]] = 1; nmonths++; keep[i] = 1 }
  }
  for (i = 1; i <= NR; i++) if (!(i in keep)) print files[i]
}
