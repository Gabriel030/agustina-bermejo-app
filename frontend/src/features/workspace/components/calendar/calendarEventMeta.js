import { formatDayLong } from '@/features/workspace/lib/studentCalendar';

const TYPE_META = {
  CLASS: { label: 'Clase', glyph: '●', tone: 'text-brand', bar: 'border-l-brand' },
  HOLIDAY: { label: 'Feriado', glyph: '◆', tone: 'text-warning-ink', bar: 'border-l-warning' },
  EXAM_BOARD: { label: 'Mesa de examen', glyph: '■', tone: 'text-success-ink', bar: 'border-l-success' },
};

const CANCELED_META = { tone: 'text-danger-ink', bar: 'border-l-danger' };

export function isCanceled(event) {
  return event.status === 'CANCELED';
}

export function eventMeta(event) {
  const base = TYPE_META[event.type];
  return isCanceled(event) ? { ...base, ...CANCELED_META } : base;
}

function dotKey(event) {
  return isCanceled(event) ? 'CANCELED' : event.type;
}

export function dayMarks(events) {
  const marks = new Map();
  events.forEach((event) => {
    if (!marks.has(dotKey(event))) marks.set(dotKey(event), eventMeta(event));
  });
  return [...marks].map(([key, meta]) => ({ key, glyph: meta.glyph, tone: meta.tone }));
}

export function eventAriaLabel(event) {
  const details = [`${TYPE_META[event.type].label}: ${event.title}`, eventTimeLabel(event)];
  if (event.type !== 'HOLIDAY') details.push(eventStatusPill(event).label);
  return details.join(', ');
}

export function eventStatusPill(event) {
  if (event.type === 'HOLIDAY') return { tone: 'warning', label: 'Feriado' };
  if (event.type === 'CLASS') {
    return isCanceled(event) ? { tone: 'danger', label: 'Clase cancelada' } : { tone: 'neutral', label: 'Programada' };
  }
  if (event.status === 'CANCELED') return { tone: 'danger', label: 'Mesa cancelada' };
  if (event.status === 'REGISTERED') return { tone: 'success', label: 'Inscripto a la mesa' };
  if (event.status === 'OPEN_FOR_REGISTRATION') return { tone: 'accent', label: 'Inscripción abierta' };
  return { tone: 'neutral', label: 'Programada' };
}

export function eventTimeLabel(event) {
  return event.startTime ?? 'Todo el día';
}

function dayCountLabel(count) {
  return count === 1 ? '1 evento' : `${count} eventos`;
}

export function dayAriaLabel(dateKey, todayKey, events) {
  const count = events.length === 0 ? 'sin eventos' : dayCountLabel(events.length);
  return `${formatDayLong(dateKey, todayKey)}, ${count}`;
}
