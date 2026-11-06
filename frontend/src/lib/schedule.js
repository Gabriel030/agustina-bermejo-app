const DAY_INDEX = {
  domingo: 0, dom: 0,
  lunes: 1, lun: 1,
  martes: 2, mar: 2,
  miercoles: 3, mie: 3,
  jueves: 4, jue: 4,
  viernes: 5, vie: 5,
  sabado: 6, sab: 6,
}

const DAY_NAMES = 'domingo|dom|lunes|lun|martes|mar|miercoles|mie|jueves|jue|viernes|vie|sabado|sab'
const DAY_TOKEN = new RegExp(`\\b(${DAY_NAMES})\\b`, 'g')
const RANGE = new RegExp(`\\b(${DAY_NAMES})\\s+a\\s+(${DAY_NAMES})\\b`, 'g')
const CLOCK = /\b(\d{1,2})\s*[:.h]\s*(\d{2})\b/
const HOUR_ONLY = /\b(\d{1,2})\s*(?:hs|h)\b/

function normalize(text) {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

function expandRange(from, to) {
  const days = []
  for (let day = from; ; day = (day + 1) % 7) {
    days.push(day)
    if (day === to) return days
  }
}

export function parseSchedule(text) {
  if (typeof text !== 'string' || text.trim() === '') return null
  const normalized = normalize(text)
  const days = new Set()
  for (const match of normalized.matchAll(RANGE)) {
    expandRange(DAY_INDEX[match[1]], DAY_INDEX[match[2]]).forEach((day) => days.add(day))
  }
  const withoutRanges = normalized.replace(RANGE, ' ')
  for (const match of withoutRanges.matchAll(DAY_TOKEN)) days.add(DAY_INDEX[match[1]])
  if (days.size === 0) return null
  const clock = normalized.match(CLOCK)
  const hourOnly = clock ? null : normalized.match(HOUR_ONLY)
  const hour = clock ? Number(clock[1]) : hourOnly ? Number(hourOnly[1]) : null
  const minute = clock ? Number(clock[2]) : hourOnly ? 0 : null
  const validTime = hour !== null && hour < 24 && minute < 60
  return { days, hour: validTime ? hour : null, minute: validTime ? minute : null }
}

function startOf(date, parsed) {
  const start = new Date(date)
  if (parsed.hour === null) start.setHours(23, 59, 59, 999)
  else start.setHours(parsed.hour, parsed.minute, 0, 0)
  return start
}

export function isScheduledToday(scheduleText, now) {
  const parsed = parseSchedule(scheduleText)
  return parsed !== null && parsed.days.has(now.getDay())
}

export function findNextClass(items, now) {
  let best = null
  for (const item of items) {
    const parsed = parseSchedule(item.schedule)
    if (!parsed) continue
    for (let offset = 0; offset <= 7; offset += 1) {
      const day = new Date(now)
      day.setDate(day.getDate() + offset)
      if (!parsed.days.has(day.getDay())) continue
      const start = startOf(day, parsed)
      if (start < now) continue
      if (!best || start < best.start) {
        best = { item, start, offset, hasTime: parsed.hour !== null, hour: parsed.hour, minute: parsed.minute }
      }
      break
    }
  }
  return best
}
