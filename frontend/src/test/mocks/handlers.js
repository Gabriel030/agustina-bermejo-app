import { http, HttpResponse } from 'msw';
import { annualTerms, auditedAnnualTerms } from '@/test/fixtures/annualTerms';

const API = 'http://localhost:8080';

const personResponse = (overrides = {}) => ({
  id: 1,
  nombre: 'Prueba',
  apellido: 'Login',
  email: 'prueba@example.com',
  dni: '30111222',
  documentType: 'DNI',
  legajo: 'L-0001',
  sexo: 'F',
  telefono: '1155550000',
  street: 'Calle Falsa',
  number: '123',
  floor: '',
  city: 'Buenos Aires',
  state: 'Buenos Aires',
  country: 'Argentina',
  postalCode: '1000',
  hasAvatar: false,
  roles: ['PRECEPTOR'],
  ...overrides,
});

/*
 * Handlers "felices" por default — cada test sobreescribe con server.use(...)
 * el caso puntual que quiere probar (401, 429, etc.), ver
 * docs/context/testing.md. Las respuestas de error del backend real no llevan
 * body (ver docs/context/backend.md) — acá se replica ese contrato a propósito.
 */
export const handlers = [
  http.get(`${API}/institution/academic-settings`, () =>
    HttpResponse.json({
      announcementEmailEnabled: false,
      evaluationConfigDueDays: 30,
      justificationDeadlineBusinessDays: 5,
    }),
  ),
  http.post(`${API}/auth/login`, () =>
    HttpResponse.json({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 }),
  ),
  http.post(`${API}/auth/refresh`, () =>
    HttpResponse.json({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 900 }),
  ),
  http.post(`${API}/auth/logout`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API}/auth/forgot-password`, () => new HttpResponse(null, { status: 202 })),
  http.post(`${API}/auth/verify-reset-code`, () => HttpResponse.json({ resetToken: 'reset-token-1' })),
  http.post(`${API}/auth/reset-password`, () => new HttpResponse(null, { status: 204 })),
  http.get(`${API}/me`, () => HttpResponse.json(personResponse({ dni: '' }))),
  http.put(`${API}/me`, () => HttpResponse.json(personResponse())),
  http.post(`${API}/auth/register`, () =>
    HttpResponse.json({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 }, { status: 201 }),
  ),
  http.get(`${API}/institutions`, () => HttpResponse.json([{ id: 1, name: 'Instituto de prueba' }])),
  http.get(`${API}/programs`, () => HttpResponse.json([])),
  http.get(`${API}/programs/:id`, ({ params }) =>
    HttpResponse.json({ id: Number(params.id), name: 'Carrera de prueba', totalYears: 3, totalCreditsRequired: 0 }),
  ),
  http.get(`${API}/programs/:id/pricing`, ({ params }) =>
    HttpResponse.json({ programId: Number(params.id), matriculaCohorte1: { amount: '50000.00', currency: 'ARS' }, matriculaCohorte2: { amount: '25000.00', currency: 'ARS' }, cuotaMensual: { amount: '15000.00', currency: 'ARS' } }),
  ),
  http.get(`${API}/programs/:id/cohorts`, ({ params }) =>
    HttpResponse.json([
      { id: 1, programId: Number(params.id), label: 'Marzo 2026', ordenEnAnio: 1, cursadaStart: '2026-03-01', admissionOpen: true },
      { id: 2, programId: Number(params.id), label: 'Agosto 2026', ordenEnAnio: 2, cursadaStart: '2026-08-01', admissionOpen: true },
    ]),
  ),
  http.get(`${API}/programs`, () => HttpResponse.json([])),
  http.get(`${API}/academic-offering-cycles`, () => HttpResponse.json([])),
  http.get(`${API}/academic-offering-cycles/:id/occupancy`, ({ params }) => HttpResponse.json({ cycleId: Number(params.id), sections: [] })),
  http.get(`${API}/course-subjects/:id/sections`, () => HttpResponse.json([])),
  http.post(`${API}/students/:id/program-enrollments`, ({ params }) =>
    HttpResponse.json(
      { id: 1, studentId: Number(params.id), programId: 1, programName: 'Carrera de prueba', status: 'ACTIVE', enrollmentDate: '2026-11-05T12:00:00', completionDate: null },
      { status: 201 },
    ),
  ),
  http.post(`${API}/students/:id/course-enrollments`, ({ params }) =>
    HttpResponse.json(
      { id: 1, studentId: Number(params.id), courseId: 1, courseName: 'Curso de prueba', status: 'ENROLLED', enrollmentDate: '2026-11-05T12:00:00', completionDate: null },
      { status: 201 },
    ),
  ),
  http.get(`${API}/programs/:id/subjects`, () => HttpResponse.json([])),
  http.get(`${API}/courses`, () => HttpResponse.json([])),
  http.get(`${API}/courses/:id`, ({ params }) =>
    HttpResponse.json({ id: Number(params.id), name: 'Curso de prueba', durationMonths: 3, registrationOpen: true, full: false }),
  ),
  http.get(`${API}/courses/:id/pricing`, ({ params }) => HttpResponse.json({ courseId: Number(params.id), cuotaMensual: { amount: '8000.00', currency: 'ARS' } })),
  http.get(`${API}/courses/:id/subjects`, () => HttpResponse.json([])),
  http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])),
  http.get(`${API}/me/program-enrollments`, () => HttpResponse.json([])),
  http.get(`${API}/me/program-enrollments/:id/annual-upfront/quote`, () =>
    HttpResponse.json({ eligible: false, reasonCode: 'ENROLLMENT_NOT_ACTIVE', reasonMessage: 'La inscripción no está activa.' }),
  ),
  http.get(`${API}/me/program-enrollments/:id/annual-upfront-requests`, () => HttpResponse.json([])),
  http.get(`${API}/program-enrollments/:id/annual-upfront/quote`, () =>
    HttpResponse.json({ eligible: false, reasonCode: 'ENROLLMENT_NOT_ACTIVE', reasonMessage: 'La inscripción no está activa.' }),
  ),
  http.get(`${API}/program-enrollments/:id/annual-upfront-requests`, () => HttpResponse.json([])),
  http.get(`${API}/annual-upfront-terms/current`, () => HttpResponse.json(annualTerms())),
  http.get(`${API}/annual-upfront-terms/versions/:versionNumber`, ({ params }) =>
    HttpResponse.json(annualTerms({ versionNumber: Number(params.versionNumber), version: `v${params.versionNumber}` })),
  ),
  http.get(`${API}/annual-upfront-terms/versions`, () => HttpResponse.json([auditedAnnualTerms()])),
  http.post(`${API}/me/program-enrollment-applications`, () =>
    HttpResponse.json({ id: 1, status: 'PENDING' }, { status: 201 }),
  ),
  http.post(`${API}/me/program-enrollment-applications/with-documents`, () =>
    HttpResponse.json(
      {
        id: 1,
        studentId: 1,
        programId: 1,
        programName: 'Carrera de prueba',
        programCohortId: 1,
        status: 'PENDING',
        requestedAt: '2026-03-02T10:00:00',
        decidedAt: null,
        rejectionReason: null,
        admissionInvoice: null,
        programEnrollmentId: 1,
      },
      { status: 201 },
    ),
  ),
  http.post(`${API}/me/program-enrollment-applications/:id/documents`, () => new HttpResponse(null, { status: 201 })),
  http.get(`${API}/me/course-enrollment-applications`, () => HttpResponse.json([])),
  http.post(`${API}/me/course-enrollments`, () =>
    HttpResponse.json({ id: 1, paymentStatus: 'PENDING_REVIEW' }, { status: 201 }),
  ),
  http.get(`${API}/me/notifications`, () => HttpResponse.json([])),
  http.get(`${API}/me/schedule`, () => HttpResponse.json({ generatedAt: '2026-11-05T12:00:00', items: [] })),
  http.get(`${API}/me/documents`, () => HttpResponse.json([])),
  http.get(`${API}/me/bank-transfer-receipts`, () => HttpResponse.json([])),
  http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([])),
  http.get(`${API}/payment-config`, () =>
    HttpResponse.json({ bankAlias: null, bankCbu: null, accountHolder: null, qrImageUrl: null, mpEnabled: false }),
  ),
  http.get(`${API}/me/modules`, () =>
    HttpResponse.json({
      billing: { mode: 'FULL' },
      fiscalInvoicing: { enabled: false },
      analytics: { enabled: false },
      paymentMethods: [
        { code: 'CASH', available: true },
        { code: 'BANK_TRANSFER', available: true },
        { code: 'GATEWAY', available: false },
      ],
      billingNotifications: { enabled: false },
      externalBilling: { enabled: false },
    }),
  ),
  http.get(`${API}/me/teaching-sections`, () => HttpResponse.json([])),
  http.get(`${API}/me/course-enrollments`, () => HttpResponse.json([])),
  http.get(`${API}/students/:id/documents`, () => HttpResponse.json([])),
  http.get(`${API}/terms`, () => HttpResponse.json([])),
  http.get(`${API}/subjects`, () => HttpResponse.json([])),
  http.get(`${API}/programs/:id/elective-groups`, () => HttpResponse.json([])),
  http.get(`${API}/elective-groups`, () => HttpResponse.json([])),
  http.get(`${API}/programs/:id/year-billing-templates`, () => HttpResponse.json([])),
  http.get(`${API}/me/section-evaluation-configs`, () => HttpResponse.json([])),
  http.get(`${API}/me/section-enrollments`, () => HttpResponse.json([])),
  http.get(`${API}/billing/configuration-alerts`, () => HttpResponse.json([])),
  http.get(`${API}/announcements/:id/recipients`, () => HttpResponse.json([])),
  http.post(`${API}/me/notifications/:id/read`, () => new HttpResponse(null, { status: 200 })),
  http.post(`${API}/students/:id/refund-requests/preview`, () =>
    HttpResponse.json({ allowed: true, blockedBy: null, parts: [] }),
  ),
];
