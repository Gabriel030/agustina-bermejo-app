import { TERM_TYPE_LABELS } from '@/features/academic-structure';

const WINDOW_DAYS = 30;
const MAX_EVENTS = 5;

function dayKey(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function dateOnly(value) {
  return value.slice(0, 10);
}

function examEvents(boards) {
  return boards.map((board) => ({
    id: `exam-${board.id}`,
    kind: 'exam',
    date: dateOnly(board.examDate),
    title: `Mesa de ${board.subjectName}`,
  }));
}

function holidayEvents(holidays) {
  return holidays.map((holiday) => ({
    id: `holiday-${holiday.id}`,
    kind: 'holiday',
    date: holiday.date,
    title: holiday.name,
  }));
}

function termEvents(terms) {
  return terms.flatMap((term) => {
    const label = TERM_TYPE_LABELS[term.type] ?? 'Período';
    return [
      { id: `term-start-${term.id}`, kind: 'termStart', date: dateOnly(term.startDate), title: `Inicio del período: ${label}` },
      { id: `term-end-${term.id}`, kind: 'termEnd', date: dateOnly(term.endDate), title: `Fin del período: ${label}` },
      { id: `enrollment-start-${term.id}`, kind: 'enrollmentStart', date: dateOnly(term.enrollmentStart), title: `Abren inscripciones: ${label}` },
      { id: `enrollment-end-${term.id}`, kind: 'enrollmentEnd', date: dateOnly(term.enrollmentEnd), title: `Cierran inscripciones: ${label}` },
    ];
  });
}

export function upcomingWindow(now = new Date()) {
  const from = dayKey(now);
  const to = dayKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() + WINDOW_DAYS));
  return { from, to };
}

export function upcomingEvents({ boards = [], holidays = [], terms = [] }, now = new Date()) {
  const { from, to } = upcomingWindow(now);
  return [...examEvents(boards), ...holidayEvents(holidays), ...termEvents(terms)]
    .filter((event) => event.date >= from && event.date <= to)
    .sort((a, b) => a.date.localeCompare(b.date) || a.kind.localeCompare(b.kind) || a.title.localeCompare(b.title) || a.id.localeCompare(b.id))
    .slice(0, MAX_EVENTS);
}
