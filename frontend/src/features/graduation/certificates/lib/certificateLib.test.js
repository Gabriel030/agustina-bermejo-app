import { describe, expect, it } from 'vitest';
import {
  formatCertificateCode,
  isWellFormedCertificateCode,
  normalizeCertificateCode,
} from '@/features/graduation/certificates/lib/certificateCode';
import { detailRows, formatLongDay, verdictOf } from '@/features/graduation/certificates/lib/certificateView';

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
  const regular = {
    documentType: 'REGULAR_STUDENT_CERTIFICATE',
    status: 'VALID',
    holderFullName: 'A*** P**',
    maskedDocument: '***.***.222',
    issuedAt: '2026-11-21T10:00:00',
    expiresAt: '2026-12-21T10:00:00',
    revokedAt: null,
    replacedAt: null,
    registryNumber: null,
  };

  it('formatsLongSpanishDatesWithoutTimezoneShift', () => {
    expect(formatLongDay('2026-12-05T23:59:59')).toBe('5 de diciembre de 2026');
    expect(formatLongDay(null)).toBe('');
  });

  it('buildsRowsWithoutRegistryWhenItIsMissing', () => {
    expect(detailRows(regular).map((row) => row.label)).toEqual(['Documento', 'A nombre de', 'DNI', 'Emitido el', 'Vigencia']);
  });

  it('addsRegistryRowWhenPresent', () => {
    const rows = detailRows({ ...regular, registryNumber: '0004217/2026' });
    expect(rows.find((row) => row.label === 'Registro').value).toBe('0004217/2026');
  });

  it('describesValidityPerStatus', () => {
    const validity = (overrides) => detailRows({ ...regular, ...overrides }).find((row) => row.label === 'Vigencia').value;
    expect(validity({})).toBe('Vigente hasta el 21 de diciembre de 2026');
    expect(validity({ expiresAt: null })).toBe('Vigente');
    expect(validity({ status: 'EXPIRED' })).toBe('Venció el 21 de diciembre de 2026');
    expect(validity({ status: 'REVOKED', revokedAt: '2026-12-05T08:00:00' })).toBe('Revocada el 5 de diciembre de 2026');
    expect(validity({ status: 'REPLACED', replacedAt: '2026-12-02T08:00:00' })).toBe('Reemplazada el 2 de diciembre de 2026');
  });

  it('toleratesMissingDatesInTheVerdict', () => {
    expect(verdictOf({ ...regular, status: 'REVOKED' }).message).toBe('No tiene validez.');
    expect(verdictOf({ ...regular, status: 'EXPIRED', expiresAt: null }).message).toBe(
      'Pedí a la persona interesada una constancia nueva.',
    );
  });
});
