import { describe, expect, it } from 'vitest';
import { API_BASE } from './server';
import { takeContractDeviations } from '../contract/withContract';

const putConsent = (body) =>
  fetch(`${API_BASE}/me/contact-consents`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ channel: 'EMAIL', category: 'INSTITUTIONAL', granted: true, ...body }),
  });

describe('handler de /me/contact-consents', () => {
  it('rechaza con 400 CONSENT_SOURCE_NOT_ACCEPTED un origen distinto de PROFILE', async () => {
    const response = await putConsent({ source: 'IMPORT' });

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe('CONSENT_SOURCE_NOT_ACCEPTED');
    expect(takeContractDeviations().length).toBeGreaterThan(0);
  });

  it('acepta el PUT sin origen', async () => {
    const response = await putConsent({});

    expect(response.status).toBe(200);
  });

  it('acepta el origen PROFILE', async () => {
    const response = await putConsent({ source: 'PROFILE' });

    expect(response.status).toBe(200);
  });
});
