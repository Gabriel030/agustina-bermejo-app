import { http, HttpResponse } from 'msw';
import { springPage } from '@/test/fixtures/certificates';
import { errorBody } from '@/test/fixtures/errors';
import {
  certificateExceptionSummary,
  examCertificateCandidates,
  ineligibleProcedure,
  publicDocumentVerification,
  regularCertificateRequest,
  regularCertificateRequestList,
} from '@/test/fixtures/constancias';

const API = 'http://localhost:8080';

const failure = (status, code) => HttpResponse.json(errorBody(code), { status });

export const publicDocumentExpiredHandler = http.get(`${API}/public/documents/:code`, () =>
  HttpResponse.json(publicDocumentVerification({ status: 'EXPIRED' })),
);

export const publicDocumentRevokedHandler = http.get(`${API}/public/documents/:code`, () =>
  HttpResponse.json(publicDocumentVerification({ status: 'REVOKED', revokedAt: '2026-11-20T10:00:00' })),
);

export const publicDocumentReplacedHandler = http.get(`${API}/public/documents/:code`, () =>
  HttpResponse.json(publicDocumentVerification({ status: 'REPLACED', replacedAt: '2026-11-20T10:00:00' })),
);

export const publicDocumentNotFoundHandler = http.get(`${API}/public/documents/:code`, () =>
  failure(404, 'CERTIFICATE_NOT_FOUND'),
);

export const publicDocumentRateLimitedHandler = http.get(`${API}/public/documents/:code`, () =>
  failure(429, 'RATE_LIMIT_EXCEEDED'),
);

export const procedureIneligibleHandler = http.get(`${API}/me/procedures/:type/eligibility`, ({ params }) =>
  HttpResponse.json(ineligibleProcedure({ type: params.type === 'SECTION_CHANGE' ? 'SECTION_CHANGE' : 'REGULAR_STUDENT_CERTIFICATE' })),
);

export const procedureEligibilityErrorHandler = http.get(`${API}/me/procedures/:type/eligibility`, () =>
  failure(500, 'INTERNAL_ERROR'),
);

export const approvedProceduresEmptyHandler = http.get(`${API}/me/procedures/approved`, () => HttpResponse.json([]));

export const approvedProceduresErrorHandler = http.get(`${API}/me/procedures/approved`, () =>
  failure(500, 'INTERNAL_ERROR'),
);

export const approvedProcedureNotFoundHandler = http.get(`${API}/me/procedures/approved/:documentType/:id/pdf`, () =>
  failure(404, 'PROCEDURE_DOCUMENT_NOT_FOUND'),
);

export const procedureNotEligibleHandler = http.post(`${API}/me/approved-subjects-certificates`, () =>
  failure(422, 'PROCEDURE_NOT_ELIGIBLE'),
);

export const examCertificateCandidatesEmptyHandler = http.get(`${API}/exam-boards/:id/exam-certificates/candidates`, () =>
  HttpResponse.json([]),
);

export const examCertificateCandidatesOnlyIssuedHandler = http.get(`${API}/exam-boards/:id/exam-certificates/candidates`, () =>
  HttpResponse.json(examCertificateCandidates().map((candidate) => ({ ...candidate, availability: 'ALREADY_ISSUED' }))),
);

export const examBoardOfAnotherTeacherHandler = http.get(`${API}/exam-boards/:id/exam-certificates/candidates`, () =>
  failure(404, 'EXAM_BOARD_NOT_FOUND'),
);

export const examCertificateRegistrationNotEligibleHandler = http.post(`${API}/exam-boards/:id/exam-certificates/batch`, () =>
  failure(422, 'EXAM_CERTIFICATE_REGISTRATION_NOT_ELIGIBLE'),
);

export const examCertificateNotRevocableHandler = http.post(`${API}/exam-certificates/:id/revoke`, () =>
  failure(409, 'EXAM_CERTIFICATE_NOT_REVOCABLE'),
);

export const changeSectionCandidatesEmptyHandler = http.get(`${API}/me/section-enrollments/:id/change-section-candidates`, () =>
  HttpResponse.json([]),
);

export const changeSectionCandidatesNotFoundHandler = http.get(`${API}/me/section-enrollments/:id/change-section-candidates`, () =>
  failure(404, 'SECTION_ENROLLMENT_NOT_FOUND'),
);

export const changeSectionHasRecordsHandler = http.post(`${API}/me/section-enrollments/:id/change-section`, () =>
  failure(409, 'SECTION_HAS_RECORDS'),
);

export const documentTemplateForbiddenHandler = http.put(`${API}/certificate-templates/documents/:documentType/draft`, () =>
  failure(403, 'FORBIDDEN'),
);

export const documentTemplatePublishIncompleteHandler = http.post(`${API}/certificate-templates/documents/:documentType/publish`, () =>
  failure(422, 'TEMPLATE_INCOMPLETE'),
);

export const documentTemplatePublishSignatureMissingHandler = http.post(`${API}/certificate-templates/documents/:documentType/publish`, () =>
  failure(422, 'TEMPLATE_SIGNATURE_MISSING'),
);

export const ownDocumentLimitReachedHandler = http.post(`${API}/me/documents/:type`, () =>
  failure(429, 'ISSUANCE_LIMIT_REACHED'),
);

export const ownDocumentNotEligibleHandler = http.post(`${API}/me/documents/:type`, () =>
  failure(422, 'PROCEDURE_NOT_ELIGIBLE'),
);

export const ownDocumentExamNotHeldHandler = http.post(`${API}/me/documents/:type`, () =>
  failure(422, 'EXAM_CERTIFICATE_BOARD_NOT_HELD'),
);

export const documentSettingsConflictHandler = http.put(`${API}/certificate-settings/documents`, () =>
  failure(409, 'OPTIMISTIC_LOCK_CONFLICT'),
);

export const ownDocumentRequiresPaymentsHandler = http.post(`${API}/me/documents/:type`, () =>
  failure(409, 'DOCUMENT_REQUIRES_PAYMENTS_UP_TO_DATE'),
);

export const debtExceptionsEmptyHandler = http.get(`${API}/document-debt-exceptions`, () => HttpResponse.json(springPage([])));

export const debtExceptionAlreadyUsedHandler = http.get(`${API}/me/procedures/approved/:documentType/:id/pdf`, () =>
  failure(409, 'DEBT_EXCEPTION_ALREADY_USED'),
);

export const debtExceptionExpiredHandler = http.get(`${API}/me/procedures/approved/:documentType/:id/pdf`, () =>
  failure(409, 'DEBT_EXCEPTION_EXPIRED'),
);

export const debtExceptionNotFoundHandler = http.get(`${API}/document-debt-exceptions/:id/pdf`, () =>
  failure(404, 'DEBT_EXCEPTION_NOT_FOUND'),
);

export const debtExceptionForbiddenHandler = http.post(`${API}/document-debt-exceptions`, () => failure(403, 'FORBIDDEN'));

export const regularRequestsWithDebtDataHandler = http.get(`${API}/regular-certificate-requests`, () =>
  HttpResponse.json(regularCertificateRequestList()),
);

export const regularRequestsBillingOffHandler = http.get(`${API}/regular-certificate-requests`, () =>
  HttpResponse.json([regularCertificateRequest({ totalDue: null, overdueInstallments: null, hasDebt: null })]),
);

export const certificateExceptionSummaryEmptyHandler = http.get(`${API}/certificate-exceptions/summary`, () =>
  HttpResponse.json(certificateExceptionSummary({ pending: 0, approved: 0, rejected: 0, withdrawn: 0, expired: 0, consumed: 0 })),
);
