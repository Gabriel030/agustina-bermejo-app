import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';

export const API = 'http://localhost:8080';

export function money(amount, currency = 'ARS') {
  return { amount, currency };
}

export function definition(overrides) {
  return {
    id: 1,
    name: 'Beca',
    discountType: 'SCHOLARSHIP',
    applicableInvoiceType: 'MONTHLY_INSTALLMENT',
    calculation: null,
    installmentsTotal: null,
    availableFrom: '2026-01-01',
    availableUntil: null,
    priority: 1,
    capPerInvoice: null,
    programId: null,
    programCohortId: null,
    courseId: null,
    enrollmentWindowFrom: null,
    enrollmentWindowTo: null,
    quota: null,
    status: 'ACTIVE',
    directPublication: true,
    authorizationRequestId: null,
    createdById: 4,
    createdAt: '2026-01-02T10:00:00Z',
    closedById: null,
    closedAt: null,
    closureReason: null,
    availability: 'AVAILABLE',
    estimatedQuotaCost: null,
    estimatedQuotaCostStatus: 'NOT_APPLICABLE',
    quotaUsed: 0,
    cohortPromotionStatus: null,
    ...overrides,
  };
}

export function promotion(overrides) {
  return definition({
    id: 3,
    name: 'Cohorte agosto 2026',
    discountType: 'COHORT_PROMOTION',
    calculation: { type: 'PROPORTIONAL', ratePercent: 20 },
    installmentsTotal: 3,
    availableFrom: '2026-08-01',
    availableUntil: '2026-12-31',
    programId: 2,
    enrollmentWindowFrom: '2026-08-01',
    enrollmentWindowTo: '2026-09-30',
    quota: 20,
    quotaUsed: 18,
    cohortPromotionStatus: 'OPEN',
    ...overrides,
  });
}

export const CATALOG = [
  definition(),
  definition({
    id: 2,
    name: 'Pago anual anticipado 2027',
    discountType: 'ANNUAL_UPFRONT',
    applicableInvoiceType: 'ANNUAL_UPFRONT',
    calculation: { type: 'PROPORTIONAL', ratePercent: 15 },
    availableFrom: '2026-10-01',
    availableUntil: '2027-02-15',
    availability: 'UPCOMING',
  }),
  definition({ id: 9, name: 'Beca vieja', status: 'CLOSED', closedAt: '2026-05-01T10:00:00Z', closureReason: 'Se reemplazó.' }),
];

export const PROMOTIONS = [
  promotion(),
  promotion({
    id: 4,
    name: 'Matrícula ingresantes 2027',
    applicableInvoiceType: 'ENROLLMENT_FEE',
    calculation: { type: 'NOMINAL', amount: money('50000.00') },
    installmentsTotal: null,
    quota: 50,
    quotaUsed: 0,
    cohortPromotionStatus: 'UPCOMING',
    programId: null,
    courseId: 7,
  }),
  promotion({ id: 5, name: 'Reinscripción 2025', quota: 12, quotaUsed: 12, cohortPromotionStatus: 'QUOTA_FULL' }),
];

export const PROGRAMS = [
  { id: 2, name: 'Enfermería', status: 'ACTIVE' },
  { id: 3, name: 'Radiología', status: 'ACTIVE' },
  { id: 8, name: 'Programa viejo', status: 'INACTIVE' },
];

export const COURSES = [{ id: 7, name: 'Primeros Auxilios', status: 'ACTIVE' }];

export const COHORTS = [
  { id: 31, label: 'Febrero 2027', programId: 2 },
  { id: 32, label: 'Agosto 2027', programId: 2 },
];

export function mockOffers({ pendingRequests = 0 } = {}) {
  server.use(
    http.get(`${API}/programs`, () => HttpResponse.json(PROGRAMS)),
    http.get(`${API}/courses`, () => HttpResponse.json(COURSES)),
    http.get(`${API}/programs/2/cohorts`, () => HttpResponse.json(COHORTS)),
    http.get(`${API}/authorization-requests`, () =>
      HttpResponse.json({ items: [], page: 0, size: 1, totalElements: pendingRequests })),
  );
}
