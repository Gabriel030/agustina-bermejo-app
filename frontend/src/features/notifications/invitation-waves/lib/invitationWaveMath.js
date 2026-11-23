const DEFAULT_ZONE = 'America/Argentina/Buenos_Aires';
const MINUTES_PER_HOUR = 60;

export function invitedCount(wave) {
  return (wave.sentTotal ?? 0) + (wave.activatedTotal ?? 0);
}

export function progressPercent(wave) {
  if (!wave.invitableCount) return 0;
  return Math.min(100, Math.round((invitedCount(wave) * 100) / wave.invitableCount));
}

export function parseClockMinutes(clock) {
  const [hours, minutes] = String(clock ?? '').split(':').map(Number);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null;
  return hours * MINUTES_PER_HOUR + minutes;
}

export function windowMinutes(sendWindow) {
  const start = parseClockMinutes(sendWindow?.start);
  const end = parseClockMinutes(sendWindow?.end);
  if (start == null || end == null || end <= start) return null;
  return end - start;
}

export function shortClock(clock) {
  return String(clock ?? '').slice(0, 5);
}

export function windowLabel(sendWindow) {
  if (!sendWindow?.start || !sendWindow?.end) return '';
  return `${shortClock(sendWindow.start)} a ${shortClock(sendWindow.end)}`;
}

export function estimateSchedule({ recipients, batchSize, intervalMinutes, dailyCap, sendWindow }) {
  const total = Number(recipients) || 0;
  const size = Number(batchSize) || 0;
  const interval = Number(intervalMinutes) || 0;
  const cap = Number(dailyCap) || 0;
  if (total <= 0 || size <= 0 || interval <= 0 || cap <= 0) return { batches: 0, days: 0 };
  const batches = Math.ceil(total / size);
  const minutesOpen = windowMinutes(sendWindow);
  const batchesPerDay = minutesOpen == null ? Infinity : Math.floor(minutesOpen / interval) + 1;
  const perDay = Math.max(1, Math.min(cap, size * batchesPerDay));
  return { batches, days: Math.ceil(total / perDay) };
}

export function formatInstant(value, zone = DEFAULT_ZONE) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const parts = new Intl.DateTimeFormat('es-AR', {
    timeZone: zone || DEFAULT_ZONE,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const part = (type) => parts.find((entry) => entry.type === type)?.value ?? '';
  return `${part('day')}/${part('month')}/${part('year')} ${part('hour')}:${part('minute')}`;
}

export function formatInstantDate(value, zone = DEFAULT_ZONE) {
  return formatInstant(value, zone).split(' ')[0] ?? '';
}

export function formatInstantTime(value, zone = DEFAULT_ZONE) {
  return formatInstant(value, zone).split(' ')[1] ?? '';
}

export function reportFilename(waveId, now = new Date()) {
  const pad = (number) => String(number).padStart(2, '0');
  return `invitaciones-oleada-${waveId}-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}.csv`;
}
