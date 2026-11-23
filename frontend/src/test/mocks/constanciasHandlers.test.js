import { describe, expect, it } from 'vitest';
import { certificateTemplateContent } from '@/test/fixtures/certificates';
import { server } from './server';
import * as states from './constanciasStateHandlers';

const API = 'http://localhost:8080';
const json = { 'Content-Type': 'application/json' };

async function call(method, path, body) {
  const response = await fetch(`${API}${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await response.text();
  return { status: response.status, body: text && response.headers.get('content-type')?.includes('json') ? JSON.parse(text) : text };
}

describe('constancias default handlers', () => {
  it('publicDocumentCoversEveryDocumentType', async () => {
    const types = new Set();
    for (const code of 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')) {
      const { status, body } = await call('GET', `/public/documents/${code}`);
      expect(status).toBe(200);
      expect(body).not.toHaveProperty('grade');
      types.add(body.documentType);
    }
    expect(types.size).toBe(5);
  });

  it('studentProceduresEndpointsAnswerAgainstTheContract', async () => {
    expect((await call('GET', '/me/procedures/REGULAR_STUDENT_CERTIFICATE/eligibility?enrollmentId=7')).body.eligible).toBe(true);
    expect((await call('GET', '/me/procedures/approved')).body).toHaveLength(3);
    expect((await call('GET', '/me/procedures/approved/EXAM_CERTIFICATE/12/pdf')).status).toBe(200);
    expect((await call('POST', '/me/approved-subjects-certificates', { enrollmentId: 7 })).status).toBe(201);
    expect((await call('POST', '/approved-subjects-certificates/8/revoke', { reason: 'Motivo suficiente' })).body.status).toBe('REVOKED');
  });

  it('examCertificateEndpointsAnswerAgainstTheContract', async () => {
    for (const type of ['REGULAR_STUDENT_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE']) {
      const generated = await call('POST', `/me/documents/${type}`, { registrationId: 58, recipientType: 'OTHER', recipientText: 'Club deportivo' });
      expect(generated.status).toBe(201);
      expect(generated.body.documentType).toBe(type);
    }
    expect((await call('GET', '/exam-boards/9/exam-certificates/candidates')).body).toHaveLength(5);
    expect((await call('GET', '/exam-certificates/12/pdf')).status).toBe(200);
    expect((await call('POST', '/exam-boards/9/exam-certificates/batch', { registrationIds: [55] })).body.issuedCount).toBe(1);
    expect((await call('POST', '/exam-certificates/12/revoke', { reason: 'Motivo suficiente' })).body.status).toBe('REVOKED');
  });

  it('documentIssuanceSettingsAnswerAgainstTheContract', async () => {
    expect((await call('GET', '/certificate-settings/documents')).body.dailyIssuanceLimitPerType).toBe(1);
    const saved = await call('PUT', '/certificate-settings/documents', {
      examRegistrationAdvanceDays: 10,
      examAttendanceValidityDays: 30,
      approvedSubjectsValidityDays: 30,
      dailyIssuanceLimitPerType: 2,
      version: 0,
    });
    expect(saved.body.version).toBe(1);
  });

  it('debtExceptionsAnswerAgainstTheContract', async () => {
    const issued = await call('POST', '/document-debt-exceptions', {
      studentId: 3,
      documentType: 'REGULAR_STUDENT_CERTIFICATE',
      reason: 'Cuota abonada en efectivo pendiente de acreditar',
      validityDays: 7,
    });
    expect(issued.status).toBe(201);
    expect((await call('GET', '/document-debt-exceptions?status=ACTIVE')).body.content).toHaveLength(3);
    expect((await call('GET', '/document-debt-exceptions/5/pdf')).status).toBe(200);
  });

  it('changeSectionCandidatesIncludeASelectableSectionWithoutBlockFields', async () => {
    const { body } = await call('GET', '/me/section-enrollments/3/change-section-candidates');
    expect(body.map((candidate) => candidate.blockReason)).toEqual(['CURRENT_SECTION', null, 'SECTION_FULL', 'SCHEDULE_OVERLAP']);
  });

  it('documentTemplatesEndpointsAnswerAgainstTheContract', async () => {
    const base = '/certificate-templates/documents/EXAM_CERTIFICATE';
    expect((await call('GET', base)).body.documentType).toBe('EXAM_CERTIFICATE');
    expect((await call('GET', `${base}/versions`)).body).toHaveLength(1);
    expect((await call('GET', `${base}/versions/3`)).body.versionNumber).toBe(3);
    expect((await call('GET', `${base}/images/1`)).status).toBe(200);
    expect((await call('PUT', `${base}/draft`, { content: certificateTemplateContent() })).body.status).toBe('DRAFT');
    expect((await call('DELETE', `${base}/draft`)).status).toBe(204);
    expect((await call('POST', `${base}/publish`)).body.versionNumber).toBe(4);
    expect((await call('POST', `${base}/preview`, { source: 'PUBLISHED' })).status).toBe(200);
  });
});

describe('constancias state handlers', () => {
  const cases = [
    ['publicDocumentExpiredHandler', 'GET', '/public/documents/X', 200, 'status', 'EXPIRED'],
    ['publicDocumentRevokedHandler', 'GET', '/public/documents/X', 200, 'status', 'REVOKED'],
    ['publicDocumentReplacedHandler', 'GET', '/public/documents/X', 200, 'status', 'REPLACED'],
    ['publicDocumentNotFoundHandler', 'GET', '/public/documents/X', 404, 'code', 'CERTIFICATE_NOT_FOUND'],
    ['publicDocumentRateLimitedHandler', 'GET', '/public/documents/X', 429, 'code', 'RATE_LIMIT_EXCEEDED'],
    ['procedureIneligibleHandler', 'GET', '/me/procedures/REGULAR_STUDENT_CERTIFICATE/eligibility', 200, 'eligible', false],
    ['procedureEligibilityErrorHandler', 'GET', '/me/procedures/REGULAR_STUDENT_CERTIFICATE/eligibility', 500, 'code', 'INTERNAL_ERROR'],
    ['approvedProceduresErrorHandler', 'GET', '/me/procedures/approved', 500, 'code', 'INTERNAL_ERROR'],
    ['approvedProcedureNotFoundHandler', 'GET', '/me/procedures/approved/EXAM_CERTIFICATE/1/pdf', 404, 'code', 'PROCEDURE_DOCUMENT_NOT_FOUND'],
    ['procedureNotEligibleHandler', 'POST', '/me/approved-subjects-certificates', 422, 'code', 'PROCEDURE_NOT_ELIGIBLE', { enrollmentId: 7 }],
    ['examBoardOfAnotherTeacherHandler', 'GET', '/exam-boards/1/exam-certificates/candidates', 404, 'code', 'EXAM_BOARD_NOT_FOUND'],
    ['ownDocumentLimitReachedHandler', 'POST', '/me/documents/EXAM_CERTIFICATE', 429, 'code', 'ISSUANCE_LIMIT_REACHED', { registrationId: 1 }],
    ['ownDocumentNotEligibleHandler', 'POST', '/me/documents/REGULAR_STUDENT_CERTIFICATE', 422, 'code', 'PROCEDURE_NOT_ELIGIBLE', {}],
    ['ownDocumentExamNotHeldHandler', 'POST', '/me/documents/EXAM_CERTIFICATE', 422, 'code', 'EXAM_CERTIFICATE_BOARD_NOT_HELD', { registrationId: 1 }],
    ['documentSettingsConflictHandler', 'PUT', '/certificate-settings/documents', 409, 'code', 'OPTIMISTIC_LOCK_CONFLICT', { examRegistrationAdvanceDays: 15, examAttendanceValidityDays: 30, approvedSubjectsValidityDays: 30, dailyIssuanceLimitPerType: 1, version: 0 }],
    ['ownDocumentRequiresPaymentsHandler', 'POST', '/me/documents/REGULAR_STUDENT_CERTIFICATE', 409, 'code', 'DOCUMENT_REQUIRES_PAYMENTS_UP_TO_DATE', {}],
    ['debtExceptionAlreadyUsedHandler', 'GET', '/me/procedures/approved/REGULAR_STUDENT_CERTIFICATE/31/pdf', 409, 'code', 'DEBT_EXCEPTION_ALREADY_USED'],
    ['debtExceptionExpiredHandler', 'GET', '/me/procedures/approved/REGULAR_STUDENT_CERTIFICATE/31/pdf', 409, 'code', 'DEBT_EXCEPTION_EXPIRED'],
    ['debtExceptionNotFoundHandler', 'GET', '/document-debt-exceptions/9/pdf', 404, 'code', 'DEBT_EXCEPTION_NOT_FOUND'],
    ['debtExceptionForbiddenHandler', 'POST', '/document-debt-exceptions', 403, 'code', 'FORBIDDEN', { studentId: 3, documentType: 'REGULAR_STUDENT_CERTIFICATE', reason: 'Cuota abonada en efectivo pendiente de acreditar', validityDays: 7 }],
    ['examCertificateRegistrationNotEligibleHandler', 'POST', '/exam-boards/1/exam-certificates/batch', 422, 'code', 'EXAM_CERTIFICATE_REGISTRATION_NOT_ELIGIBLE', { registrationIds: [1] }],
    ['examCertificateNotRevocableHandler', 'POST', '/exam-certificates/1/revoke', 409, 'code', 'EXAM_CERTIFICATE_NOT_REVOCABLE', { reason: 'Motivo suficiente' }],
    ['changeSectionCandidatesNotFoundHandler', 'GET', '/me/section-enrollments/1/change-section-candidates', 404, 'code', 'SECTION_ENROLLMENT_NOT_FOUND'],
    ['changeSectionHasRecordsHandler', 'POST', '/me/section-enrollments/1/change-section', 409, 'code', 'SECTION_HAS_RECORDS', { newSectionId: 2 }],
    ['documentTemplateForbiddenHandler', 'PUT', '/certificate-templates/documents/EXAM_CERTIFICATE/draft', 403, 'code', 'FORBIDDEN', { content: certificateTemplateContent() }],
    ['documentTemplatePublishIncompleteHandler', 'POST', '/certificate-templates/documents/EXAM_CERTIFICATE/publish', 422, 'code', 'TEMPLATE_INCOMPLETE'],
  ];

  it.each(cases)('%s', async (name, method, path, status, field, value, body) => {
    server.use(states[name]);
    const response = await call(method, path, body);
    expect(response.status).toBe(status);
    expect(response.body[field]).toBe(value);
  });

  it.each([
    ['approvedProceduresEmptyHandler', '/me/procedures/approved', (body) => body],
    ['examCertificateCandidatesEmptyHandler', '/exam-boards/1/exam-certificates/candidates', (body) => body],
    ['debtExceptionsEmptyHandler', '/document-debt-exceptions', (body) => body.content],
    ['changeSectionCandidatesEmptyHandler', '/me/section-enrollments/1/change-section-candidates', (body) => body],
  ])('%s', async (name, path, pick) => {
    server.use(states[name]);
    expect(pick((await call('GET', path)).body)).toEqual([]);
  });

  it('examCertificateCandidatesOnlyIssuedHandlerDisablesEveryone', async () => {
    server.use(states.examCertificateCandidatesOnlyIssuedHandler);
    const { body } = await call('GET', '/exam-boards/1/exam-certificates/candidates');
    expect(body.every((candidate) => candidate.availability === 'ALREADY_ISSUED')).toBe(true);
  });
});
