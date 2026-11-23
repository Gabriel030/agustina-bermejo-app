import { http, HttpResponse } from 'msw';
import { annualTerms, auditedAnnualTerms } from '@/test/fixtures/annualTerms';
import { backupStatusForPlatformAdmin } from '@/test/fixtures/backupStatus';
import {
  certificateTemplate,
  certificateTemplateImage,
  certificateTemplateVersion,
  eligibleGraduate,
  graduationCertificateBatch,
  graduationCertificateDetail,
  graduationCertificateReview,
  graduationCertificateSummary,
  ownGraduationCertificate,
  springPage,
} from '@/test/fixtures/certificates';
import { sectionEnrollment } from '@/test/fixtures/studentHome';
import { signatoryDetail, signatoryList } from '@/test/fixtures/signatories';
import { constanciasHandlers } from '@/test/mocks/constanciasHandlers';

import { permissionCatalog, permissionDiscrepancy, permissionDiscrepancySummary, permissionGrid } from '@/test/fixtures/permissions';

const API = 'http://localhost:8080';

const graduationProjection = (programEnrollmentId) => ({
  programEnrollmentId,
  status: 'PROJECTED',
  estimatedPeriods: 8,
  range: { minPeriods: 6, maxPeriods: 8 },
  estimatedGraduationYear: 2030,
  periodUnit: 'SEMESTER',
  method: 'HISTORICAL_PACE_WITH_PREREQUISITES',
  confidence: 'MEDIUM',
  basis: {
    remainingCredits: 66,
    remainingMandatorySubjects: 16,
    remainingElectiveSubjects: 2,
    creditsPerPeriod: 9.0,
    creditsPerPeriodSource: 'STUDENT_HISTORY',
    closedPeriods: 6,
    criticalPathPeriods: 5,
  },
  assumptions: [{ code: 'PACE_CONSTANT', message: 'Se asume que mantenés el ritmo de créditos por cuatrimestre.' }],
  warnings: [],
  reasons: [],
});

const curriculumProgress = (programEnrollmentId) => ({
  programEnrollmentId,
  programName: 'Tecnicatura de prueba',
  summary: {
    completionPercent: 50,
    accumulatedCredits: 3,
    requiredCredits: 6,
    remainingCredits: 3,
    mandatoryTotal: 2,
    mandatoryApproved: 1,
    counts: { approved: 1, inProgress: 0, regularPendingFinal: 0, failed: 0, available: 0, blocked: 1 },
    electiveGroups: [],
  },
  subjects: [
    {
      programSubjectId: 1,
      subjectId: 10,
      subjectCode: 'MAT101',
      subjectName: 'Matemática I',
      credits: 3,
      curricularYear: 1,
      curricularTerm: 'FIRST',
      category: 'MANDATORY',
      electiveGroupId: null,
      state: 'APPROVED',
      finalScore: 8,
      attempts: 1,
      blockedBy: [],
      prerequisites: [],
      currentEnrollmentId: null,
      corequisites: [],
    },
    {
      programSubjectId: 2,
      subjectId: 11,
      subjectCode: 'MAT102',
      subjectName: 'Matemática II',
      credits: 3,
      curricularYear: 1,
      curricularTerm: 'SECOND',
      category: 'MANDATORY',
      electiveGroupId: null,
      state: 'BLOCKED',
      finalScore: null,
      attempts: 0,
      blockedBy: [
        { subjectId: 12, subjectCode: 'MAT100', subjectName: 'Álgebra', requirementType: 'APPROVED', standing: 'NOT_TAKEN' },
      ],
      prerequisites: [
        { subjectId: 12, subjectCode: 'MAT100', subjectName: 'Álgebra', requirementType: 'APPROVED', standing: 'NOT_TAKEN' },
      ],
      currentEnrollmentId: null,
      corequisites: [],
    },
  ],
  warnings: [],
});

const pdfResponse = () =>
  new HttpResponse(new Uint8Array([0x25, 0x50, 0x44, 0x46]), { headers: { 'Content-Type': 'application/pdf' } });
const pngResponse = () =>
  new HttpResponse(new Uint8Array([0x89, 0x50, 0x4e, 0x47]), { headers: { 'Content-Type': 'image/png' } });
const draftVersion = () =>
  certificateTemplateVersion({ id: 2, versionNumber: 4, status: 'DRAFT', basedOnVersionNumber: 3, publishedAt: null, publishedBy: null });

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

export const curriculumProgressHandlers = [
  http.get(`${API}/me/program-enrollments/:id/progress/subjects`, ({ params }) =>
    HttpResponse.json(curriculumProgress(Number(params.id))),
  ),
  http.get(`${API}/me/program-enrollments/:id/progress/projection`, ({ params }) =>
    HttpResponse.json(graduationProjection(Number(params.id))),
  ),
];

/*
 * Handlers "felices" por default — cada test sobreescribe con server.use(...)
 * el caso puntual que quiere probar (401, 429, etc.), ver
 * docs/context/testing.md. Las respuestas de error del backend real no llevan
 * body (ver docs/context/backend.md) — acá se replica ese contrato a propósito.
 */
export const handlers = [
  ...curriculumProgressHandlers,
  http.get(`${API}/regular-certificate-requests`, () => HttpResponse.json([])),
  http.get(`${API}/graduation-requests`, () => HttpResponse.json([])),
  http.get(`${API}/graduation-certificates/eligible`, () => HttpResponse.json(springPage([eligibleGraduate()]))),
  http.get(`${API}/graduation-certificates`, () => HttpResponse.json(springPage([graduationCertificateSummary()]))),
  http.get(`${API}/graduation-certificates/:id`, ({ params }) =>
    HttpResponse.json(graduationCertificateDetail({ id: Number(params.id) })),
  ),
  http.get(`${API}/graduation-certificates/:id/pdf`, () => pdfResponse()),
  http.post(`${API}/graduation-certificates/preview`, () => pdfResponse()),
  http.post(`${API}/graduation-certificates/batch`, () => HttpResponse.json(graduationCertificateBatch())),
  http.post(`${API}/graduation-certificates/:id/revoke`, ({ params }) =>
    HttpResponse.json(
      graduationCertificateDetail({
        id: Number(params.id),
        status: 'REVOKED',
        revokedAt: '2026-11-21T09:00:00',
        revocationReason: 'Error en los datos del alumno',
      }),
    ),
  ),
  http.get(`${API}/graduation-certificate-reviews`, () => HttpResponse.json(springPage([graduationCertificateReview()]))),
  http.post(`${API}/graduation-certificate-reviews/batch`, async ({ request }) => {
    const { graduationRequestIds = [31], decision = 'ENABLE' } = await request.json().catch(() => ({}));
    const reviewStatus = decision === 'ENABLE' ? 'ENABLED' : 'DISABLED';
    return HttpResponse.json({
      requested: graduationRequestIds.length,
      decided: graduationRequestIds.length,
      failed: 0,
      results: graduationRequestIds.map((graduationRequestId) => ({
        graduationRequestId,
        outcome: 'DECIDED',
        review: graduationCertificateReview({ graduationRequestId, reviewStatus }),
        errorCode: null,
      })),
    });
  }),
  http.post(`${API}/graduation-certificate-reviews/:graduationRequestId/enable`, ({ params }) =>
    HttpResponse.json(graduationCertificateReview({ graduationRequestId: Number(params.graduationRequestId), reviewStatus: 'ENABLED' })),
  ),
  http.post(`${API}/graduation-certificate-reviews/:graduationRequestId/disable`, ({ params }) =>
    HttpResponse.json(graduationCertificateReview({ graduationRequestId: Number(params.graduationRequestId), reviewStatus: 'DISABLED' })),
  ),
  http.post(`${API}/graduation-certificates/:id/reissue`, ({ params }) =>
    HttpResponse.json(graduationCertificateDetail({ id: Number(params.id) + 1, replacesCertificateId: Number(params.id) })),
  ),
  http.get(`${API}/me/calendar/feed`, () => HttpResponse.json({ active: true, createdAt: '2026-11-18T10:00:00', lastUsedAt: '2026-11-21T08:00:00' })),
  http.post(`${API}/me/calendar/feed/token`, () =>
    HttpResponse.json(
      { url: 'http://localhost:8080/public/calendar/feed.ics?token=abc123', token: 'abc123', createdAt: '2026-11-21T10:00:00' },
      { status: 201 },
    ),
  ),
  http.delete(`${API}/me/calendar/feed/token`, () => new HttpResponse(null, { status: 204 })),
  http.get(`${API}/me/calendar/export.ics`, () =>
    new HttpResponse('BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n', {
      headers: { 'Content-Type': 'text/calendar', 'Content-Disposition': 'attachment; filename="calendario.ics"' },
    }),
  ),
  http.get(`${API}/me/certificates`, () => HttpResponse.json([ownGraduationCertificate()])),
  http.get(`${API}/me/certificates/:id/pdf`, () => pdfResponse()),
  http.get(`${API}/certificate-signatories`, ({ request }) => {
    const params = new URL(request.url).searchParams;
    const active = params.get('active');
    const documentType = params.get('documentType');
    const query = (params.get('q') ?? '').toLowerCase();
    const content = signatoryList().filter(
      (signatory) =>
        (active === null || String(signatory.active) === active) &&
        (documentType === null || signatory.allowedDocumentTypes.includes(documentType)) &&
        (query === '' || `${signatory.name} ${signatory.role}`.toLowerCase().includes(query)),
    );
    return HttpResponse.json(springPage(content));
  }),
  http.post(`${API}/certificate-signatories`, async ({ request }) => {
    const body = await request.json().catch(() => ({}));
    return HttpResponse.json(
      signatoryDetail({ id: 5, hasSignature: false, usedInTemplates: [], ...body }),
      { status: 201 },
    );
  }),
  http.get(`${API}/certificate-signatories/:id/signature`, () => pngResponse()),
  http.put(`${API}/certificate-signatories/:id/signature`, ({ params }) =>
    HttpResponse.json(signatoryDetail({ id: Number(params.id), hasSignature: true })),
  ),
  http.delete(`${API}/certificate-signatories/:id/signature`, ({ params }) =>
    HttpResponse.json(signatoryDetail({ id: Number(params.id), hasSignature: false })),
  ),
  http.post(`${API}/certificate-signatories/:id/deactivate`, ({ params }) =>
    HttpResponse.json(signatoryDetail({ id: Number(params.id), active: false })),
  ),
  http.post(`${API}/certificate-signatories/:id/activate`, ({ params }) =>
    HttpResponse.json(signatoryDetail({ id: Number(params.id), active: true })),
  ),
  http.get(`${API}/certificate-signatories/:id`, ({ params }) =>
    HttpResponse.json(signatoryDetail({ id: Number(params.id) })),
  ),
  http.put(`${API}/certificate-signatories/:id`, async ({ params, request }) => {
    const body = await request.json().catch(() => ({}));
    return HttpResponse.json(signatoryDetail({ id: Number(params.id), ...body }));
  }),
  http.get(`${API}/certificate-templates/:kind`, () => HttpResponse.json(certificateTemplate())),
  http.get(`${API}/certificate-templates/:kind/versions`, () =>
    HttpResponse.json([{ versionNumber: 3, publishedAt: '2026-11-18T09:05:00', publishedBy: 'Director Prueba' }]),
  ),
  http.get(`${API}/certificate-templates/:kind/versions/:number`, ({ params }) =>
    HttpResponse.json(certificateTemplateVersion({ versionNumber: Number(params.number) })),
  ),
  http.put(`${API}/certificate-templates/:kind/draft`, () => HttpResponse.json(draftVersion())),
  http.delete(`${API}/certificate-templates/:kind/draft`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API}/certificate-templates/:kind/publish`, () =>
    HttpResponse.json(certificateTemplateVersion({ id: 2, versionNumber: 4, basedOnVersionNumber: 3 })),
  ),
  http.post(`${API}/certificate-templates/:kind/images`, () =>
    HttpResponse.json(certificateTemplateImage(), { status: 201 }),
  ),
  http.get(`${API}/certificate-templates/:kind/images/:id`, () => pngResponse()),
  http.post(`${API}/certificate-templates/:kind/preview`, () => pdfResponse()),
  http.get(`${API}/regular-certificates`, () => HttpResponse.json([])),
  http.get(`${API}/me/attendance-change-requests`, () => HttpResponse.json([])),
  http.get(`${API}/institution/academic-settings`, () =>
    HttpResponse.json({
      announcementEmailEnabled: false,
      evaluationConfigDueDays: 30,
      justificationDeadlineBusinessDays: 5,
    }),
  ),
  http.post(`${API}/cancel-email-change`, () => new HttpResponse(null, { status: 200 })),
  http.post(`${API}/email-change-preview`, () =>
    HttpResponse.json({
      currentEmailMasked: 'm•••a@gmail.com',
      newEmailMasked: 'm•••z@alumnos.instituto.edu.ar',
      requestedByRole: 'ADMINISTRATIVO',
      requestedAt: '2026-11-19T20:41:00',
      expiresAt: '2026-11-21T20:41:00',
    }),
  ),
  http.post(`${API}/approve-email-change`, () => new HttpResponse(null, { status: 200 })),
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
          hasRecord: true,
        })),
      ),
    ),
  ),
  http.put(`${API}/me/contact-consents`, async ({ request }) => {
    const body = await request.json().catch(() => ({}));
    if (body.source !== undefined && body.source !== null && body.source !== 'PROFILE') {
      return HttpResponse.json(
        { code: 'CONSENT_SOURCE_NOT_ACCEPTED', requestId: 'req-test-0001', retryable: false, violations: [] },
        { status: 400 },
      );
    }
    return HttpResponse.json({
      channel: 'EMAIL',
      category: 'INSTITUTIONAL',
      granted: true,
      recordedAt: '2026-11-17T10:00:00Z',
      hasRecord: true,
    });
  }),
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
          createdByName: 'Laura Gómez',
          confirmedByName: 'Laura Gómez',
          recipientCounts: { PENDING: 0, QUEUED: 1, SENT: 0, FAILED: 0, EXCLUDED: 0, DONE: 0, SKIPPED: 0, INVALID_NUMBER: 0 },
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
      createdByName: 'Laura Gómez',
      confirmedByName: 'Laura Gómez',
      estimatedCompletionDate: '2026-11-18',
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
  http.post(`${API}/waitlists/:kind/entries/:enrollmentId/promote`, () => HttpResponse.json({})),
  http.post(`${API}/waitlists/:kind/entries/:enrollmentId/prioritize`, () => HttpResponse.json({})),
  http.post(`${API}/waitlists/:kind/entries/:enrollmentId/remove`, () => HttpResponse.json({})),
  http.get(`${API}/waitlists/:kind/:id/history`, () => HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0 })),
  http.post(`${API}/waitlists/:kind/entries/:enrollmentId/promote`, () => new HttpResponse(null, { status: 200 })),
  http.post(`${API}/waitlists/:kind/entries/:enrollmentId/prioritize`, () => new HttpResponse(null, { status: 200 })),
  http.post(`${API}/waitlists/:kind/entries/:enrollmentId/remove`, () => new HttpResponse(null, { status: 200 })),
  http.get(`${API}/program-enrollment-applications/pending`, () => HttpResponse.json([])),
  http.get(`${API}/program-enrollment-applications/history`, () => HttpResponse.json({ items: [], totalElements: 0 })),
  http.get(`${API}/program-enrollment-applications/:id`, () => HttpResponse.json({ code: 'PROGRAM_ENROLLMENT_APPLICATION_NOT_FOUND', requestId: 'req-test-0001', retryable: false, violations: [] }, { status: 404 })),
  http.get(`${API}/program-enrollment-applications/:id/approval-check`, () => HttpResponse.json({ code: 'PROGRAM_ENROLLMENT_APPLICATION_NOT_FOUND', requestId: 'req-test-0001', retryable: false, violations: [] }, { status: 404 })),
  http.get(`${API}/me/program-enrollments`, () => HttpResponse.json([])),
  http.get(`${API}/program-enrollments/:id/progress/subjects`, ({ params }) =>
    HttpResponse.json(curriculumProgress(Number(params.id))),
  ),
  http.get(`${API}/program-enrollments/:id/progress/projection`, ({ params }) =>
    HttpResponse.json(graduationProjection(Number(params.id))),
  ),
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
  http.get(`${API}/holidays`, () => HttpResponse.json([])),
  http.get(`${API}/subjects`, () => HttpResponse.json([])),
  http.get(`${API}/programs/:id/elective-groups`, () => HttpResponse.json([])),
  http.get(`${API}/elective-groups`, () => HttpResponse.json([])),
  http.get(`${API}/programs/:id/year-billing-templates`, () => HttpResponse.json([])),
  http.get(`${API}/me/section-evaluation-configs`, () => HttpResponse.json([])),
  http.get(`${API}/section-evaluation-configs`, () => HttpResponse.json([])),
  http.get(`${API}/me/section-enrollments`, () => HttpResponse.json([])),
  http.post(`${API}/me/section-enrollments/:id/change-section`, async ({ params, request }) => {
    const { newSectionId } = await request.json().catch(() => ({}));
    return HttpResponse.json(sectionEnrollment({ id: Number(params.id), sectionId: newSectionId ?? 1 }));
  }),
  http.get(`${API}/billing/configuration-alerts`, () => HttpResponse.json([])),
  http.get(`${API}/announcements/:id/recipients`, () => HttpResponse.json([])),
  http.post(`${API}/me/notifications/:id/read`, () => new HttpResponse(null, { status: 200 })),
  http.post(`${API}/students/:id/refund-requests/preview`, () =>
    HttpResponse.json({ allowed: true, blockedBy: null, parts: [] }),
  ),
  http.get(`${API}/backups/status`, () => HttpResponse.json(backupStatusForPlatformAdmin())),
  http.get(`${API}/section-modification-requests/pending`, () => HttpResponse.json([])),
  http.post(`${API}/subject-sections/:id/modification-request/preview`, () => HttpResponse.json({ conflicts: [] })),
  http.post(`${API}/subject-sections/:id/modification-request/approve`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API}/subject-sections/:id/modification-request/reject`, () => new HttpResponse(null, { status: 204 })),
  http.get(`${API}/permissions/catalog`, () => HttpResponse.json(permissionCatalog())),
  http.get(`${API}/permissions/grid`, () => HttpResponse.json(permissionGrid())),
  http.put(`${API}/permissions/grid`, () => HttpResponse.json(permissionGrid())),
  http.put(`${API}/permissions/modes`, () => HttpResponse.json(permissionGrid())),
  http.get(`${API}/permissions/discrepancies/summary`, () => HttpResponse.json(permissionDiscrepancySummary())),
  http.get(`${API}/permissions/discrepancies`, () =>
    HttpResponse.json({ items: [permissionDiscrepancy()], page: 0, size: 50, totalElements: 1 }),
  ),
  ...constanciasHandlers,
];
