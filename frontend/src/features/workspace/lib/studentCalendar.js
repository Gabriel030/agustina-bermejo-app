export const CALENDAR_EVENT_TYPES = ['CLASS', 'HOLIDAY', 'EXAM_BOARD'];

const WEEKDAYS_LONG = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MONTHS_LONG = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];
export const WEEKDAY_INITIALS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

const pad = (value) => String(value).padStart(2, '0');

export function toDateKey(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function parseDateKey(key) {
  const [year, month, day] = key.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function addDays(date, amount) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + amount);
}

export function startOfWeek(date) {
  return addDays(date, -((date.getDay() + 6) % 7));
}

export function weekDayKeys(date) {
  const start = startOfWeek(date);
  return Array.from({ length: 7 }, (_, index) => toDateKey(addDays(start, index)));
}

export function monthRange(date) {
  const first = new Date(date.getFullYear(), date.getMonth(), 1);
  const last = new Date(date.getFullYear(), date.getMonth() + 1, 0);
  const start = startOfWeek(first);
  const end = addDays(startOfWeek(last), 6);
  const dayCount = Math.round((end - start) / 86_400_000) + 1;
  const days = Array.from({ length: dayCount }, (_, index) => toDateKey(addDays(start, index)));
  return { from: days[0], to: days[days.length - 1], days };
}

export function monthKeyOf(date) {
  return toDateKey(date).slice(0, 7);
}

export function shiftMonth(date, amount, today) {
  const target = new Date(date.getFullYear(), date.getMonth() + amount, 1);
  return monthKeyOf(target) === monthKeyOf(today) ? today : target;
}

export function shiftSelection(date, view, amount, today) {
  if (view === 'week') return addDays(date, amount * 7);
  return shiftMonth(date, amount, today);
}

export function formatMonthTitle(date) {
  return `${MONTHS_LONG[date.getMonth()]} de ${date.getFullYear()}`;
}

export function formatWeekTitle(date) {
  const days = weekDayKeys(date).map(parseDateKey);
  const first = days[0];
  const last = days[6];
  if (first.getMonth() === last.getMonth()) {
    return `${first.getDate()} al ${last.getDate()} de ${MONTHS_LONG[last.getMonth()]} de ${last.getFullYear()}`;
  }
  return `${first.getDate()} de ${MONTHS_LONG[first.getMonth()]} al ${last.getDate()} de ${MONTHS_LONG[last.getMonth()]} de ${last.getFullYear()}`;
}

export function formatDayLong(key, todayKey) {
  const date = parseDateKey(key);
  const base = `${WEEKDAYS_LONG[date.getDay()]} ${date.getDate()} de ${MONTHS_LONG[date.getMonth()]}`;
  return key === todayKey ? `${base}, hoy` : base;
}

export function formatAgendaDayTitle(key, todayKey) {
  const base = formatDayLong(key, null);
  return key === todayKey ? `${base} · hoy` : base;
}

export function formatUpdatedAt(generatedAt, todayKey) {
  if (!generatedAt) return null;
  const time = generatedAt.slice(11, 16);
  const day = generatedAt.slice(0, 10);
  if (day === todayKey) return `Actualizado hoy ${time}`;
  const [, month, dayOfMonth] = day.split('-');
  return `Actualizado el ${dayOfMonth}/${month} ${time}`;
}

export function filterCalendarEvents(events, { types, subject }) {
  return events.filter((event) => {
    if (!types.has(event.type)) return false;
    if (event.type === 'HOLIDAY' || !subject) return true;
    return event.subjectName === subject;
  });
}

export function groupEventsByDay(events) {
  return events.reduce((groups, event) => {
    const list = groups.get(event.date) ?? [];
    list.push(event);
    groups.set(event.date, list);
    return groups;
  }, new Map());
}

export function subjectNamesOf(events) {
  return [...new Set(events.map((event) => event.subjectName).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es'));
}

export function countEventsByType(events, monthKey, subject) {
  const counts = { CLASS: 0, HOLIDAY: 0, EXAM_BOARD: 0 };
  events.forEach((event) => {
    if (!event.date.startsWith(monthKey)) return;
    if (event.type !== 'HOLIDAY' && subject && event.subjectName !== subject) return;
    if (event.type in counts) counts[event.type] += 1;
  });
  return counts;
}

export function hasOnlyHolidays(events) {
  return events.every((event) => event.type === 'HOLIDAY');
}
