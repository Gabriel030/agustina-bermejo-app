export const CERTIFICATE_CODE = '7QK3M9XH2VBN4T8RGJDA1B2C3X';
export const CERTIFICATE_CODE_PRINTED = '7QK3M-9XH2V-BN4T8-RGJDA-1B2C3X';

export function graduationCertificate(overrides = {}) {
  return {
    documentKind: 'GRADUATION',
    status: 'VALID',
    holderFullName: 'Pérez, Ana',
    maskedDocument: '*****678',
    titleKind: 'TECNICATURA',
    titleName: 'Tecnicatura Superior en Enfermería',
    graduationDate: '2026-07-15',
    issuedAt: '2026-07-20T10:00:00',
    expiresAt: '2036-07-20T23:59:59',
    revokedAt: '2026-11-02T08:30:00',
    registryNumber: '1284',
    hours: 1860,
    ...overrides,
  };
}

export function regularCertificate(overrides = {}) {
  return {
    documentKind: 'REGULAR',
    status: 'VALID',
    holderFullName: 'Pérez, Ana',
    maskedDocument: '*****678',
    titleKind: 'TECNICATURA',
    titleName: 'Tecnicatura Superior en Enfermería',
    graduationDate: '2026-07-15',
    issuedAt: '2026-11-06T09:15:00',
    expiresAt: '2026-12-06T23:59:59',
    revokedAt: '2026-11-02T08:30:00',
    registryNumber: '77',
    hours: 0,
    ...overrides,
  };
}
