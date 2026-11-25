import { formatIsoDate } from '@/features/enrollment/lib/courseSubjectDetail';

export const QUEUE_BAR_MAX_SEGMENTS = 12;
export const EXPIRY_ALERT_DAYS = 3;

const MS_PER_DAY = 24 * 60 * 60 * 1000;

export function daysUntil(isoDate, today = new Date()) {
  const [year, month, day] = isoDate.slice(0, 10).split('-').map(Number);
  const target = Date.UTC(year, month - 1, day);
  const current = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((target - current) / MS_PER_DAY);
}

export function describeWaitlistExpiry(expiresOn, today = new Date()) {
  const days = daysUntil(expiresOn, today);
  const urgent = days <= EXPIRY_ALERT_DAYS;
  let label = `Vence el ${formatIsoDate(expiresOn)}`;
  if (urgent) {
    if (days <= 0) label = 'Vence hoy';
    else if (days === 1) label = 'Vence mañana';
    else label = `Vence en ${days} días`;
  }
  return { label, urgent };
}

export function findOwnWaitlistPosition(positions, kind, enrollmentId) {
  return (positions ?? []).find((entry) => entry.kind === kind && entry.enrollmentId === enrollmentId) ?? null;
}
