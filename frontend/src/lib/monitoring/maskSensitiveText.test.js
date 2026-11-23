import { describe, expect, it } from 'vitest';
import { maskSensitiveText } from './maskSensitiveText';

describe('maskSensitiveText', () => {
  it('masksEmailAddresses', () => {
    expect(maskSensitiveText('Mail a ana.perez+ley@colegio.edu.ar')).toBe('Mail a [email]');
  });

  it('masksJwtTokens', () => {
    expect(maskSensitiveText('auth eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl')).toBe('auth [jwt]');
  });

  it('masksDocumentNumbers', () => {
    expect(maskSensitiveText('DNI 35.123.456 y 35123456')).toBe('DNI [dni] y [dni]');
  });

  it('dropsQueryStringsFromUrls', () => {
    expect(maskSensitiveText('https://app.test/verificar-email?token=abc123&x=1 fallo')).toBe('https://app.test/verificar-email?[query] fallo');
  });

  it('redactsTokenParametersOutsideUrls', () => {
    expect(maskSensitiveText('reset token=abc123 ok')).toBe('reset token=[redactado] ok');
  });

  it('truncatesLongTextToTwoThousandCharacters', () => {
    const masked = maskSensitiveText('a'.repeat(5000));

    expect(masked).toHaveLength(2000);
    expect(masked.endsWith('…')).toBe(true);
  });

  it('returnsNonStringValuesUnchanged', () => {
    expect(maskSensitiveText(undefined)).toBeUndefined();
  });
});
