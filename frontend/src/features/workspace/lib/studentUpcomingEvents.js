import { upcomingWindow } from '@/features/workspace/lib/upcomingEvents';

const MAX_EVENTS = 4;
const CALENDAR_DESTINATION = '/alumno/calendario';

const EVENT_TYPE_BY_CALENDAR_TYPE = {
  CLASS: 'class',
  HOLIDAY: 'holiday',
  EXAM_BOARD: 'exam',
  DUE_DATE: 'due',
  ASSIGNMENT: 'assignment',
};

function hasEnded(event, now) {
  const closing = event.endTime ?? event.startTime;
  if (!closing || event.date !== upcomingWindow(now).from) return false;
  const current = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  return closing.slice(0, 5) < current;
}

function subtitleOf(event) {
  return [event.startTime?.slice(0, 5), event.place].filter(Boolean).join(' · ') || null;
}

export function studentUpcomingEvents(calendarEvents, now = new Date()) {
  const { from } = upcomingWindow(now);
  return calendarEvents
    .filter((event) => event.status !== 'CANCELED' && event.date >= from && EVENT_TYPE_BY_CALENDAR_TYPE[event.type] && !hasEnded(event, now))
    .sort((a, b) => a.date.localeCompare(b.date) || (a.startTime ?? '').localeCompare(b.startTime ?? '') || a.id.localeCompare(b.id))
    .slice(0, MAX_EVENTS)
    .map((event) => ({
      id: event.id,
      type: EVENT_TYPE_BY_CALENDAR_TYPE[event.type],
      date: event.date,
      title: event.title,
      subtitle: subtitleOf(event),
      to: CALENDAR_DESTINATION,
    }));
}
