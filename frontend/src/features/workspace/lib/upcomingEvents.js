import { TERM_TYPE_LABELS } from '@/features/academic-structure';

const WINDOW_DAYS = 30;
const MAX_EVENTS = 5;

const DESTINATIONS_BY_ROLE = {
  director: {
    exam: '/director/mesas-de-examen',
    enrollment: '/director/mesas-de-examen',
    close: '/director/cierres-de-comision',
    start: '/director/periodos',
  },
  administrativo: {
    exam: '/administrativo/mesas-de-examen',
    holiday: '/administrativo/feriados',
    enrollment: '/administrativo/mesas-de-examen',
    close: '/administrativo/cierres-de-comision',
    start: '/administrativo/periodos',
  },
};

function dayKey(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function dateOnly(value) {
  return value.slice(0, 10);
}

function destinationOf(role, type) {
  return DESTINATIONS_BY_ROLE[role]?.[type] ?? null;
}

function examEvents(boards) {
  return boards.map((board) => ({
    id: `exam-${board.id}`,
    type: 'exam',
    date: dateOnly(board.examDate),
    title: board.subjectName,
  }));
}

function holidayEvents(holidays) {
  return holidays.map((holiday) => ({
    id: `holiday-${holiday.id}`,
    type: 'holiday',
    date: holiday.date,
    title: holiday.name,
  }));
}

function termEvents(terms) {
  return terms.flatMap((term) => {
    const label = TERM_TYPE_LABELS[term.type] ?? 'Período';
    return [
      { id: `term-start-${term.id}`, type: 'start', date: dateOnly(term.startDate), title: label },
      { id: `term-end-${term.id}`, type: 'close', date: dateOnly(term.endDate), title: label },
      { id: `enrollment-start-${term.id}`, type: 'enrollment', date: dateOnly(term.enrollmentStart), title: label },
      { id: `enrollment-end-${term.id}`, type: 'enrollment', date: dateOnly(term.enrollmentEnd), title: label },
    ];
  });
}

export function upcomingWindow(now = new Date()) {
  const from = dayKey(now);
  const to = dayKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() + WINDOW_DAYS));
  return { from, to };
}

export function upcomingEvents({ boards = [], holidays = [], terms = [], role }, now = new Date()) {
  const { from, to } = upcomingWindow(now);
  return [...examEvents(boards), ...holidayEvents(holidays), ...termEvents(terms)]
    .filter((event) => event.date >= from && event.date <= to)
    .sort((a, b) => a.date.localeCompare(b.date) || a.type.localeCompare(b.type) || a.title.localeCompare(b.title) || a.id.localeCompare(b.id))
    .slice(0, MAX_EVENTS)
    .map((event) => ({ ...event, to: destinationOf(role, event.type) }));
}
