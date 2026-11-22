const INSTITUTION_TIME_ZONE = 'America/Argentina/Buenos_Aires';
const dayFormat = new Intl.DateTimeFormat('es-AR', {
  timeZone: INSTITUTION_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

export function formatFeedDate(iso) {
  if (!iso) return '-';
  return dayFormat.format(new Date(iso));
}

export function formatFeedLastRead(iso, now = Date.now()) {
  if (!iso) return 'todavía no lo leyó ningún calendario';
  const elapsed = Math.max(0, now - new Date(iso).getTime());
  if (elapsed < MINUTE_MS) return 'hace instantes';
  if (elapsed < HOUR_MS) return `hace ${Math.floor(elapsed / MINUTE_MS)} min`;
  if (elapsed < DAY_MS) return `hace ${Math.floor(elapsed / HOUR_MS)} h`;
  const days = Math.floor(elapsed / DAY_MS);
  return days === 1 ? 'hace 1 día' : `hace ${days} días`;
}

export function formatEventHours(event) {
  if (event.startTime && event.endTime) return `${event.startTime} a ${event.endTime} h`;
  if (event.startTime) return `${event.startTime} h`;
  return null;
}
