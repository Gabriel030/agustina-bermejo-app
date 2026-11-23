import { http, HttpResponse } from 'msw';
import {
  certificateTemplateImage,
  certificateTemplateVersion,
  springPage,
} from '@/test/fixtures/certificates';
import {
  PUBLIC_DOCUMENT_VERIFICATION_BY_TYPE,
  approvedProcedureList,
  approvedSubjectsCertificate,
  certificateException,
  certificateExceptionSummary,
  changeSectionCandidateList,
  debtException,
  debtExceptionList,
  documentIssuanceSettings,
  examCertificate,
  ownDocument,
  examCertificateBatch,
  examCertificateCandidates,
  procedureEligibility,
  publicDocumentVerification,
} from '@/test/fixtures/constancias';

const API = 'http://localhost:8080';

const pdfResponse = () =>
  new HttpResponse(new Uint8Array([0x25, 0x50, 0x44, 0x46]), { headers: { 'Content-Type': 'application/pdf' } });
const pngResponse = () =>
  new HttpResponse(new Uint8Array([0x89, 0x50, 0x4e, 0x47]), { headers: { 'Content-Type': 'image/png' } });

const COMMON_MARKERS = ['holder', 'documentType', 'documentNumber', 'issuedOn', 'registryNumber'];
const ALLOWED_MARKERS = {
  REGULAR_STUDENT_CERTIFICATE: [...COMMON_MARKERS, 'titleName', 'recipient'],
  APPROVED_SUBJECTS_CERTIFICATE: [...COMMON_MARKERS, 'titleName', 'average', 'completionPercent'],
  EXAM_REGISTRATION_CERTIFICATE: [...COMMON_MARKERS, 'subjectName', 'examDate', 'recipient'],
  EXAM_CERTIFICATE: [...COMMON_MARKERS, 'subjectName', 'examDate', 'recipient'],
};

const documentTemplate = (documentType, overrides = {}) => ({
  documentType,
  published: certificateTemplateVersion(),
  draft: null,
  hasUnpublishedChanges: false,
  allowedMarkers: ALLOWED_MARKERS[documentType] ?? COMMON_MARKERS,
  ...overrides,
});

const draftVersion = () =>
  certificateTemplateVersion({ id: 2, versionNumber: 4, status: 'DRAFT', basedOnVersionNumber: 3, publishedAt: null, publishedBy: null });

const PROCEDURE_TYPES = ['REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE', 'GRADUATION_CERTIFICATE', 'SECTION_CHANGE'];
const DOCUMENT_TYPES = ['REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE'];

const OWN_TYPES = ['REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE'];

const TEMPLATES = `${API}/certificate-templates/documents/:documentType`;

export const constanciasHandlers = [
  http.get(`${API}/certificate-exceptions/summary`, () => HttpResponse.json(certificateExceptionSummary())),
  http.get(`${API}/certificate-exceptions/:id`, ({ params }) => HttpResponse.json(certificateException({ id: Number(params.id) }))),
  http.get(`${API}/public/documents/:code`, ({ params }) => {
    const byType = Object.values(PUBLIC_DOCUMENT_VERIFICATION_BY_TYPE);
    const index = Math.abs([...String(params.code)].reduce((sum, char) => sum + char.charCodeAt(0), 0)) % byType.length;
    return HttpResponse.json(byType[index]());
  }),
  http.get(`${API}/me/procedures/approved`, () => HttpResponse.json(approvedProcedureList())),
  http.get(`${API}/me/procedures/approved/:documentType/:id/pdf`, () => pdfResponse()),
  http.get(`${API}/me/procedures/:type/eligibility`, ({ params }) =>
    HttpResponse.json(procedureEligibility({ type: PROCEDURE_TYPES.includes(params.type) ? params.type : 'REGULAR_STUDENT_CERTIFICATE' })),
  ),
  http.post(`${API}/me/approved-subjects-certificates`, () => HttpResponse.json(approvedSubjectsCertificate(), { status: 201 })),
  http.post(`${API}/approved-subjects-certificates/:id/revoke`, ({ params }) =>
    HttpResponse.json(approvedSubjectsCertificate({ id: Number(params.id), status: 'REVOKED' })),
  ),
  http.post(`${API}/me/documents/:type`, ({ params }) =>
    HttpResponse.json(
      ownDocument({ documentType: OWN_TYPES.includes(params.type) ? params.type : 'EXAM_REGISTRATION_CERTIFICATE' }),
      { status: 201 },
    ),
  ),
  http.get(`${API}/exam-boards/:id/exam-certificates/candidates`, () => HttpResponse.json(examCertificateCandidates())),
  http.post(`${API}/exam-boards/:id/exam-certificates/batch`, () => HttpResponse.json(examCertificateBatch())),
  http.get(`${API}/exam-certificates/:id/pdf`, () => pdfResponse()),
  http.post(`${API}/exam-certificates/:id/revoke`, ({ params }) =>
    HttpResponse.json(examCertificate({ id: Number(params.id), status: 'REVOKED' })),
  ),
  http.get(`${API}/certificate-settings/documents`, () => HttpResponse.json(documentIssuanceSettings())),
  http.put(`${API}/certificate-settings/documents`, async ({ request }) => {
    const body = await request.json().catch(() => ({}));
    return HttpResponse.json(documentIssuanceSettings({ ...body, version: (body.version ?? 0) + 1 }));
  }),
  http.post(`${API}/document-debt-exceptions`, () => HttpResponse.json(debtException(), { status: 201 })),
  http.get(`${API}/document-debt-exceptions/:id/pdf`, () => pdfResponse()),
  http.get(`${API}/document-debt-exceptions`, () => HttpResponse.json(springPage(debtExceptionList()))),
  http.get(`${API}/me/section-enrollments/:id/change-section-candidates`, () => HttpResponse.json(changeSectionCandidateList())),
  http.get(`${TEMPLATES}/versions/:number`, ({ params }) =>
    HttpResponse.json(certificateTemplateVersion({ versionNumber: Number(params.number) })),
  ),
  http.get(`${TEMPLATES}/versions`, () =>
    HttpResponse.json([{ versionNumber: 3, publishedAt: '2026-11-18T09:05:00', publishedBy: 'Director Prueba' }]),
  ),
  http.get(`${TEMPLATES}/images/:id`, () => pngResponse()),
  http.post(`${TEMPLATES}/images`, () => HttpResponse.json(certificateTemplateImage(), { status: 201 })),
  http.put(`${TEMPLATES}/draft`, () => HttpResponse.json(draftVersion())),
  http.delete(`${TEMPLATES}/draft`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${TEMPLATES}/publish`, () => HttpResponse.json(certificateTemplateVersion({ versionNumber: 4 }))),
  http.post(`${TEMPLATES}/preview`, () => pdfResponse()),
  http.get(TEMPLATES, ({ params }) => HttpResponse.json(documentTemplate(DOCUMENT_TYPES.includes(params.documentType) ? params.documentType : 'EXAM_CERTIFICATE'))),
];

export { publicDocumentVerification };
