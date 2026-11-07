import { describe, expect, it } from 'vitest';
import {
  formatCertificateCode,
  isWellFormedCertificateCode,
  normalizeCertificateCode,
} from '@/features/graduation/certificates/lib/certificateCode';
import { detailRows, verdictOf } from '@/features/graduation/certificates/lib/certificateView';

describe('certificateCode', () => {
  it('stripsSeparatorsAndUppercases', () => {
    expect(normalizeCertificateCode(' 7qk3m-9xh2v \tbn4t8 ')).toBe('7QK3M9XH2VBN4T8');
  });

  it('dropsNonAsciiCharacters', () => {
    expect(normalizeCertificateCode('7QK3Ñ9X')).toBe('7QK39X');
  });

  it('formatsInGroupsOfFiveFiveFiveFiveSix', () => {
    expect(formatCertificateCode('7qk3m9xh2vbn4t8rgjda1b2c3x')).toBe('7QK3M-9XH2V-BN4T8-RGJDA-1B2C3X');
    expect(formatCertificateCode('7QK3M9')).toBe('7QK3M-9');
    expect(formatCertificateCode('')).toBe('');
  });

  it('cutsTheFormattedCodeAtTwentySixCharacters', () => {
    expect(formatCertificateCode('7QK3M9XH2VBN4T8RGJDA1B2C3XEXTRA')).toBe('7QK3M-9XH2V-BN4T8-RGJDA-1B2C3X');
  });

  it('acceptsOnlyTwentySixCharactersOfTheCrockfordAlphabet', () => {
    expect(isWellFormedCertificateCode('7QK3M9XH2VBN4T8RGJDA1B2C3X')).toBe(true);
    expect(isWellFormedCertificateCode('7QK3M9XH2VBN4T8RGJDA1B2C3')).toBe(false);
    expect(isWellFormedCertificateCode('7QK3M9XH2VBN4T8RGJDA1B2C3U')).toBe(false);
    expect(isWellFormedCertificateCode('7QK3M9XH2VBN4T8RGJDA1B2C3I')).toBe(false);
  });
});

describe('certificateView', () => {
  const graduation = {
    documentKind: 'GRADUATION',
    status: 'VALID',
    maskedDocument: '*****678',
    titleName: 'Tecnicatura',
    graduationDate: '2026-07-15',
    issuedAt: '2026-07-20T10:00:00',
    expiresAt: null,
    revokedAt: null,
    registryNumber: '1284',
    hours: 1860,
  };

  it('buildsGraduationRowsWithoutRevocationWhenFieldsAreNull', () => {
    expect(detailRows(graduation).map((row) => row.label)).toEqual([
      'Documento',
      'Título',
      'Fecha de egreso',
      'Registro',
    ]);
  });

  it('omitsHoursWhenTheyAreMissing', () => {
    const rows = detailRows({ ...graduation, hours: null });
    expect(rows.find((row) => row.label === 'Registro').value).toBe('N.º 1284');
  });

  it('describesExpiredGraduationWithoutExpirationDate', () => {
    expect(verdictOf({ ...graduation, status: 'EXPIRED' }).message).toBe('Ya no acredita el título.');
  });

  it('hidesValidityRowForRevokedRegularCertificate', () => {
    const rows = detailRows({
      documentKind: 'REGULAR',
      status: 'REVOKED',
      maskedDocument: '**1',
      titleName: 'Carrera',
      issuedAt: '2026-11-06T09:15:00',
      expiresAt: '2026-12-06T23:59:59',
      revokedAt: '2026-11-07T08:00:00',
    });
    expect(rows.map((row) => row.label)).toEqual(['Documento', 'Carrera', 'Emitida', 'Revocado el']);
  });
});
