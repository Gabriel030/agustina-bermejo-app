import { describe, expect, it } from 'vitest';
import { server } from './server';
import {
  discardNothingHandler,
  publishIncompleteHandler,
  revokedOwnCertificatePdfHandler,
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
});
