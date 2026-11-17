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
  http.get(`${API}/regular-certificate-requests`, () => HttpResponse.json([])),
  http.get(`${API}/regular-certificates`, () => HttpResponse.json([])),
  http.get(`${API}/me/attendance-change-requests`, () => HttpResponse.json([])),
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
  http.get(`${API}/me/contact-consents`, () =>
    HttpResponse.json(
      ['EMAIL', 'WHATSAPP'].flatMap((channel) =>
        ['INSTITUTIONAL', 'PROMOTIONAL'].map((category) => ({
          channel,
          category,
          granted: false,
          recordedAt: '2026-11-01T10:00:00Z',
        })),
      ),
    ),
  ),
  http.put(`${API}/me/contact-consents`, () =>
    HttpResponse.json({
      channel: 'EMAIL',
      category: 'INSTITUTIONAL',
      granted: true,
      recordedAt: '2026-11-17T10:00:00Z',
    }),
  ),
  http.get(`${API}/public/unsubscribe/:token`, () =>
    HttpResponse.json({ channel: 'EMAIL', category: 'PROMOTIONAL' }),
  ),
  http.post(`${API}/public/unsubscribe/:token`, () => new HttpResponse(null, { status: 200 })),
  http.get(`${API}/broadcasts`, () =>
    HttpResponse.json({
      items: [
        {
          id: 1,
          name: 'Aviso de inicio de clases',
          category: 'INSTITUTIONAL',
          channel: 'EMAIL',
          status: 'CONFIRMED',
          createdAt: '2026-11-17T10:00:00Z',
          confirmedAt: '2026-11-17T10:05:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    }),
  ),
  http.get(`${API}/broadcasts/:id`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.id),
      name: 'Aviso de inicio de clases',
      category: 'INSTITUTIONAL',
      channel: 'EMAIL',
      status: 'CONFIRMED',
      subject: 'Inicio de clases',
      bodyText: 'Las clases comienzan el lunes.',
      createdAt: '2026-11-17T10:00:00Z',
      confirmedAt: '2026-11-17T10:05:00Z',
      recipientCounts: { PENDING: 0, QUEUED: 1, SENT: 0, FAILED: 0, EXCLUDED: 0, DONE: 0, SKIPPED: 0, INVALID_NUMBER: 0 },
    }),
  ),
  http.get(`${API}/broadcasts/:id/recipients`, () =>
    HttpResponse.json({
      items: [{ personId: 10, fullName: 'Ana Pérez', status: 'QUEUED', addressMasked: 'a***@example.com' }],
      page: 0,
      size: 50,
      totalElements: 1,
    }),
  ),
  http.post(`${API}/broadcasts/:id/confirm`, ({ params }) =>
    HttpResponse.json({ id: Number(params.id), status: 'CONFIRMED' }),
  ),
  http.post(`${API}/broadcasts/:id/cancel`, ({ params }) =>
    HttpResponse.json({ id: Number(params.id), status: 'CANCELLED' }),
  ),
  http.get(`${API}/me`, () => HttpResponse.json(personResponse({ dni: '' }))),
  http.put(`${API}/me`, () => HttpResponse.json(personResponse())),
  http.post(`${API}/auth/register`, () =>
    HttpResponse.json({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 }, { status: 201 }),
  ),
  http.get(`${API}/legal/documents/current`, () =>
    HttpResponse.json({
      documents: [
        { type: 'TERMS', version: '2026-11-01', path: '/legal/terminos' },
        { type: 'PRIVACY', version: '2026-11-01', path: '/legal/privacidad' },
      ],
    }),
  ),
  http.get(`${API}/me/legal-acceptances/pending`, () =>
    HttpResponse.json({
      documents: [],
      suggestedDeadline: '2026-11-30',
      gracePeriodExpired: false,
      guardianConfirmationRequired: false,
    }),
  ),
  http.post(`${API}/me/legal-acceptances`, () =>
    HttpResponse.json({
      documents: [],
      suggestedDeadline: '2026-11-30',
      gracePeriodExpired: false,
      guardianConfirmationRequired: false,
    }),
  ),
  http.post(`${API}/me/legal-acceptances/guardian-confirmation/resend`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API}/auth/legal/guardian-confirmation`, () => new HttpResponse(null, { status: 204 })),
  http.get(`${API}/me/newsletter-preference`, () =>
    HttpResponse.json({ optedIn: false, updatedAt: '2026-11-01T12:00:00Z' }),
  ),
  http.put(`${API}/me/newsletter-preference`, async ({ request }) => {
    const body = await request.json().catch(() => ({}));
    const optedIn = body.optedIn ?? false;
    return HttpResponse.json({ optedIn, updatedAt: '2026-11-13T12:00:00Z' });
  }),
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
  http.get(`${API}/waitlists`, () => HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0 })),
  http.get(`${API}/waitlists/:kind/:id`, () => HttpResponse.json({ code: 'WAITLIST_QUEUE_NOT_FOUND', requestId: 'req-test-0001', retryable: false, violations: [] }, { status: 404 })),
  http.get(`${API}/waitlists/:kind/:id/history`, () => HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0 })),
  http.get(`${API}/program-enrollment-applications/pending`, () => HttpResponse.json([])),
  http.get(`${API}/program-enrollment-applications/history`, () => HttpResponse.json({ items: [], totalElements: 0 })),
  http.get(`${API}/program-enrollment-applications/:id`, () => HttpResponse.json({ code: 'PROGRAM_ENROLLMENT_APPLICATION_NOT_FOUND', requestId: 'req-test-0001', retryable: false, violations: [] }, { status: 404 })),
  http.get(`${API}/program-enrollment-applications/:id/approval-check`, () => HttpResponse.json({ code: 'PROGRAM_ENROLLMENT_APPLICATION_NOT_FOUND', requestId: 'req-test-0001', retryable: false, violations: [] }, { status: 404 })),
  http.get(`${API}/me/program-enrollments`, () => HttpResponse.json([])),
  http.get(`${API}/me/program-enrollments/:id/annual-upfront/quote`, () =>
    HttpResponse.json({ eligible: false, reasonCode: 'ENROLLMENT_NOT_ACTIVE', reasonMessage: 'La inscripción no está activa.' }),
  ),
  http.get(`${API}/me/program-enrollments/:id/annual-upfront-requests`, () => HttpResponse.json([])),
  http.get(`${API}/program-enrollments/:id/annual-upfront/quote`, () =>
    HttpResponse.json({ eligible: false, reasonCode: 'ENROLLMENT_NOT_ACTIVE', reasonMessage: 'La inscripción no está activa.' }),
  ),
  http.get(`${API}/program-enrollments/:id/annual-upfront-requests`, () => HttpResponse.json([])),
  http.get(`${API}/me/program-enrollments/:id/enrollment-fee-status`, () => HttpResponse.json([])),
  http.get(`${API}/program-enrollments/:id/enrollment-fee-status`, () => HttpResponse.json([])),
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
        studentFullName: null,
        studentEmail: null,
        studentDni: null,
        programCohortName: null,
        decidedByName: null,
        documentation: null,
      },
      { status: 201 },
    ),
  ),
  http.post(`${API}/me/program-enrollment-applications/:id/documents`, () => new HttpResponse(null, { status: 201 })),
  http.get(`${API}/me/course-enrollment-applications`, () => HttpResponse.json([])),
  http.post(`${API}/me/course-enrollments`, () =>
    HttpResponse.json({ id: 1, paymentStatus: 'PENDING_REVIEW' }, { status: 201 }),
  ),
  http.get(`${API}/me/regular-certificates/eligibility`, ({ request }) => {
    const url = new URL(request.url);
    return HttpResponse.json({
      kind: url.searchParams.get('kind') ?? 'PROGRAM',
      enrollmentId: Number(url.searchParams.get('enrollmentId') ?? 1),
      regular: true,
      reasons: [],
      attendanceControlled: true,
      billingMode: 'OFF',
      institutionallyRegular: true,
      paymentStanding: 'VERIFIED',
    });
  }),
  http.get(`${API}/me/regular-certificates`, ({ request }) => {
    const url = new URL(request.url);
    return HttpResponse.json({
      kind: url.searchParams.get('kind') ?? 'PROGRAM',
      enrollmentId: Number(url.searchParams.get('enrollmentId') ?? 1),
      state: 'AVAILABLE',
      staffConfirmationRequired: true,
      certificate: null,
      pendingRequest: null,
      unavailableType: null,
      rejectionReason: null,
      reasons: [],
      history: [],
    });
  }),
  http.get(`${API}/me/notifications`, () => HttpResponse.json({ items: [], page: 0, size: 10, totalElements: 0 })),
  http.get(`${API}/me/notifications/unread-count`, () => HttpResponse.json({ count: 0 })),
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
  http.get(`${API}/me/course-enrollments/:id`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.id),
      courseId: 1,
      courseName: 'Curso de prueba',
      status: 'IN_PROGRESS',
      enrollmentDate: '2026-03-01T10:00:00',
      completionDate: null,
      subjects: [],
    }),
  ),
  http.get(`${API}/students`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })),
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
