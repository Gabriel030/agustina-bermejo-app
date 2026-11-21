import { http, HttpResponse } from 'msw';
import { withContract } from '@/test/contract/withContract';

const API = 'http://localhost:8080';

export function classEvent(overrides = {}) {
  return {
    id: 'class-PROGRAM-1-2026-11-20-18:00',
    type: 'CLASS',
    date: '2026-11-20',
    startTime: '18:00',
    endTime: '20:00',
    title: 'Programación II',
    subjectName: 'Programación II',
    sectionName: 'Comisión A',
    place: 'Aula 12',
    status: 'SCHEDULED',
    reason: null,
    link: null,
    ...overrides,
  };
}

export function holidayEvent(overrides = {}) {
  return {
    id: 'holiday-2026-11-23',
    type: 'HOLIDAY',
    date: '2026-11-23',
    startTime: null,
    endTime: null,
    title: 'Día de la Soberanía Nacional',
    subjectName: null,
    sectionName: null,
    place: null,
    status: 'SCHEDULED',
    reason: null,
    link: null,
    ...overrides,
  };
}

export function examBoardEvent(overrides = {}) {
  return {
    id: 'exam-7',
    type: 'EXAM_BOARD',
    date: '2026-11-26',
    startTime: '09:00',
    endTime: null,
    title: 'Mesa de Matemática',
    subjectName: 'Matemática',
    sectionName: null,
    place: 'Aula 3',
    status: 'REGISTERED',
    reason: null,
    link: '/alumno/mis-carreras',
    ...overrides,
  };
}

export function canceledClassEvent(overrides = {}) {
  return classEvent({
    id: 'class-PROGRAM-2-2026-11-24-10:00',
    date: '2026-11-24',
    startTime: '10:00',
    endTime: '12:00',
    title: 'Matemática',
    subjectName: 'Matemática',
    status: 'CANCELED',
    ...overrides,
  });
}

export function defaultCalendarEvents() {
  return [
    classEvent(),
    classEvent({ id: 'class-PROGRAM-1-2026-11-27-18:00', date: '2026-11-27' }),
    canceledClassEvent(),
    holidayEvent(),
    holidayEvent({ id: 'holiday-2026-12-08', date: '2026-12-08', title: 'Inmaculada Concepción' }),
    examBoardEvent(),
    examBoardEvent({ id: 'exam-8', date: '2026-11-30', status: 'OPEN_FOR_REGISTRATION', title: 'Mesa de Didáctica', subjectName: 'Didáctica' }),
  ];
}

export function calendarResponse(overrides = {}) {
  return {
    generatedAt: '2026-11-21T09:30:00',
    events: defaultCalendarEvents(),
    ...overrides,
  };
}

export function calendarHandler(resolve = () => HttpResponse.json(calendarResponse())) {
  return withContract(http.get(`${API}/me/calendar`, resolve), {
    apiBase: API,
    baseline: { 'GET /me/calendar': true },
  });
}
