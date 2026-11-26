const TIME_ZONE = 'America/Argentina/Buenos_Aires';

const DAY_FORMAT = new Intl.DateTimeFormat('es-AR', { day: '2-digit', timeZone: TIME_ZONE });
const MONTH_SHORT_FORMAT = new Intl.DateTimeFormat('es-AR', { month: 'short', timeZone: TIME_ZONE });
const MONTH_LONG_FORMAT = new Intl.DateTimeFormat('es-AR', { month: 'long', timeZone: TIME_ZONE });
const TIME_FORMAT = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: TIME_ZONE });

export const ALL_PROGRAMS_LABEL = 'Todas las carreras';

export function describeSessionDate(scheduledAt) {
  const date = new Date(scheduledAt);
  const day = DAY_FORMAT.format(date);
  return {
    day,
    monthShort: MONTH_SHORT_FORMAT.format(date).replace('.', ''),
    accessibleLabel: `${Number(day)} de ${MONTH_LONG_FORMAT.format(date)}`,
    time: `${TIME_FORMAT.format(date)} h`,
  };
}

export function describeSessionPlace(session) {
  const { time } = describeSessionDate(session.scheduledAt);
  if (session.modality === 'ONLINE') return `Online · ${time}`;
  return session.location ? `Presencial · ${session.location} · ${time}` : `Presencial · ${time}`;
}

export function remainingSeats(session) {
  if (session.capacity == null) return null;
  return Math.max(session.capacity - session.registrationCount, 0);
}

export function isSessionFull(session) {
  return remainingSeats(session) === 0;
}

export function describeRemainingSeats(session) {
  const remaining = remainingSeats(session);
  if (remaining == null) return 'Sin límite de lugares';
  if (remaining === 0) return 'No quedan lugares';
  return remaining === 1 ? 'Queda 1 lugar' : `Quedan ${remaining} lugares`;
}
