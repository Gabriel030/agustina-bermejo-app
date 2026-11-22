import { http, HttpResponse } from 'msw';
import { contractList, contractObject } from '@/test/fixtures/fromContract';
import { calendarHandler, calendarResponse } from '@/test/fixtures/studentCalendar';

const API = 'http://localhost:8080';

export function programEnrollment(overrides = {}) {
  return contractList('/me/program-enrollments', [
    {
      id: 1,
      programId: 10,
      programName: 'Tecnicatura en Desarrollo de Software',
      status: 'ACTIVE',
      studentId: 1042,
      ...overrides,
    },
  ])[0];
}

export function sectionEnrollment(overrides = {}) {
  return contractList('/me/section-enrollments', [
    {
      id: 100,
      programEnrollmentId: 1,
      subjectId: 5,
      subjectName: 'Bases de Datos',
      sectionName: 'Comisión B',
      schedule: 'Lun y Mié 19:00–21:00',
      instructorTitular: 'Marcos Giménez',
      status: 'IN_PROGRESS',
      ...overrides,
    },
  ])[0];
}

export function studentHomeHandlers({
  enrollments = [programEnrollment()],
  sectionEnrollments = [],
  progress = contractObject('/me/program-enrollments/{id}/progress', { creditosAcumulados: 30, creditosRequeridos: 60 }),
  regularity = contractObject('/me/regular-certificates/eligibility', { institutionallyRegular: true, paymentStanding: 'VERIFIED', reasons: [] }),
  calendar = calendarHandler(() => HttpResponse.json(calendarResponse({ events: [] }))),
} = {}) {
  return [
    http.get(`${API}/me/program-enrollments`, () => HttpResponse.json(enrollments)),
    http.get(`${API}/me/section-enrollments`, () => HttpResponse.json(sectionEnrollments)),
    http.get(`${API}/me/program-enrollments/:id/progress`, () => HttpResponse.json(progress)),
    http.get(`${API}/me/regular-certificates/eligibility`, () => HttpResponse.json(regularity)),
    calendar,
  ];
}
