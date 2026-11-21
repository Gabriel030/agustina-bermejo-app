import { describe, expect, it } from 'vitest';
import { server } from './server';
import {
  discardNothingHandler,
  publishIncompleteHandler,
  revokedOwnCertificatePdfHandler,
  saveDraftSignatoryDuplicatedHandler,
  saveDraftSignatoryInactiveHandler,
  saveDraftSignatoryNotAllowedHandler,
  signatoryListEmptyHandler,
  signatoryListForbiddenHandler,
  signatoryNotFoundHandler,
  signatoryRoleInvalidHandler,
  signatorySignatureInvalidHandler,
  unavailableOwnCertificatePdfHandler,
} from './certificateErrorHandlers';

const API = 'http://localhost:8080';

describe('certificate error handlers', () => {
  it('revokedCertificatePdfAnswersConflictWithRevokedCode', async () => {
    server.use(revokedOwnCertificatePdfHandler);
    const response = await fetch(`${API}/me/certificates/1/pdf`);
    expect(response.status).toBe(409);
    expect((await response.json()).code).toBe('GRADUATION_CERTIFICATE_REVOKED');
  });

  it('unavailableCertificatePdfAnswersConflictWithNotAvailableCode', async () => {
    server.use(unavailableOwnCertificatePdfHandler);
    const response = await fetch(`${API}/me/certificates/1/pdf`);
    expect(response.status).toBe(409);
    expect((await response.json()).code).toBe('GRADUATION_CERTIFICATE_NOT_AVAILABLE');
  });

  it('discardWithoutDraftAnswersConflict', async () => {
    server.use(discardNothingHandler);
    const response = await fetch(`${API}/certificate-templates/PROGRAM/draft`, { method: 'DELETE' });
    expect(response.status).toBe(409);
    expect((await response.json()).code).toBe('TEMPLATE_NOTHING_TO_DISCARD');
  });

  it('publishIncompleteTemplateAnswersUnprocessable', async () => {
    server.use(publishIncompleteHandler);
    const response = await fetch(`${API}/certificate-templates/PROGRAM/publish`, { method: 'POST' });
    expect(response.status).toBe(422);
    expect((await response.json()).code).toBe('TEMPLATE_INCOMPLETE');
  });

  it.each([
    ['signatoryNotFoundHandler', signatoryNotFoundHandler, 'GET', '/certificate-signatories/99', 404, 'SIGNATORY_NOT_FOUND'],
    ['signatoryListForbiddenHandler', signatoryListForbiddenHandler, 'GET', '/certificate-signatories', 403, 'FORBIDDEN'],
    ['signatoryRoleInvalidHandler', signatoryRoleInvalidHandler, 'POST', '/certificate-signatories', 422, 'SIGNATORY_ROLE_INVALID'],
    ['signatorySignatureInvalidHandler', signatorySignatureInvalidHandler, 'PUT', '/certificate-signatories/1/signature', 422, 'SIGNATORY_SIGNATURE_INVALID'],
    ['saveDraftSignatoryInactiveHandler', saveDraftSignatoryInactiveHandler, 'PUT', '/certificate-templates/PROGRAM/draft', 409, 'SIGNATORY_INACTIVE'],
    ['saveDraftSignatoryNotAllowedHandler', saveDraftSignatoryNotAllowedHandler, 'PUT', '/certificate-templates/PROGRAM/draft', 409, 'SIGNATORY_NOT_ALLOWED_FOR_DOCUMENT_TYPE'],
    ['saveDraftSignatoryDuplicatedHandler', saveDraftSignatoryDuplicatedHandler, 'PUT', '/certificate-templates/PROGRAM/draft', 422, 'SIGNATORY_DUPLICATED_IN_TEMPLATE'],
  ])('%s answers the signatory error code', async (_name, handler, method, path, status, code) => {
    server.use(handler);
    const response = await fetch(`${API}${path}`, { method });
    expect(response.status).toBe(status);
    expect((await response.json()).code).toBe(code);
  });

  it('signatoryListEmptyHandlerAnswersAnEmptyPage', async () => {
    server.use(signatoryListEmptyHandler);
    const body = await (await fetch(`${API}/certificate-signatories`)).json();
    expect(body.content).toEqual([]);
    expect(body.empty).toBe(true);
  });
});
