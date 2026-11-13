import { describe, expect, it } from 'vitest';
import { REGULAR_VIEW, deriveRegularView, pastCertificates } from './regularCertificateState';

const expired = { id: 1, status: 'EXPIRED', issuedAt: '2026-10-01T10:00:00', expiresAt: '2026-10-31T10:00:00' };
const revoked = { id: 2, status: 'REVOKED', issuedAt: '2026-09-01T10:00:00', expiresAt: '2026-09-30T10:00:00' };

describe('deriveRegularView', () => {
  it.each([
    [{ state: 'VALID' }, REGULAR_VIEW.VALID],
    [{ state: 'PENDING' }, REGULAR_VIEW.PENDING],
    [{ state: 'AVAILABLE', history: [] }, REGULAR_VIEW.AVAILABLE],
    [{ state: 'AVAILABLE', history: [expired] }, REGULAR_VIEW.EXPIRED],
    [{ state: 'AVAILABLE', history: [expired, revoked] }, REGULAR_VIEW.EXPIRED],
    [{ state: 'AVAILABLE', history: [revoked] }, REGULAR_VIEW.AVAILABLE],
    [{ state: 'UNAVAILABLE', unavailableType: 'ATTENDANCE' }, REGULAR_VIEW.UNAVAILABLE_ATTENDANCE],
    [{ state: 'UNAVAILABLE', unavailableType: 'PAYMENTS' }, REGULAR_VIEW.UNAVAILABLE_PAYMENTS],
    [{ state: 'UNAVAILABLE', unavailableType: 'ENROLLMENT' }, REGULAR_VIEW.UNAVAILABLE_ENROLLMENT],
    [{ state: 'UNAVAILABLE', unavailableType: 'REJECTED' }, REGULAR_VIEW.UNAVAILABLE_REJECTED],
    [{ state: 'UNAVAILABLE', unavailableType: 'SOMETHING_NEW' }, REGULAR_VIEW.UNAVAILABLE_ENROLLMENT],
  ])('maps %j to %s', (state, view) => {
    expect(deriveRegularView(state)).toBe(view);
  });
});

describe('pastCertificates', () => {
  it('keeps only expired and revoked certificates, newest first', () => {
    const valid = { id: 3, status: 'VALID', issuedAt: '2026-11-04T10:00:00' };

    expect(pastCertificates({ history: [revoked, valid, expired] }).map((certificate) => certificate.id)).toEqual([1, 2]);
  });
});
