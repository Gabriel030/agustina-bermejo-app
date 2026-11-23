import { describe, expect, it } from 'vitest';
import { maskSensitiveText } from './maskSensitiveText';

describe('maskSensitiveText', () => {
  it('masksEmailAddresses', () => {
    expect(maskSensitiveText('Mail a ana.perez+ley@colegio.edu.ar')).toBe('Mail a [email]');
  });

  it('masksUnicodeEmailAddresses', () => {
    expect(maskSensitiveText('josé.pérez@dominio.com.ar')).toBe('[email]');
    expect(maskSensitiveText('ñandú@dominio.com')).toBe('[email]');
  });

  it('masksJwtTokens', () => {
    expect(maskSensitiveText('auth eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl')).toBe('auth [jwt]');
  });

  it('masksDocumentNumbersWithSeparators', () => {
    expect(maskSensitiveText('DNI 35.123.456')).toBe('DNI [dni]');
    expect(maskSensitiveText('DNI 35 123 456')).toBe('DNI [dni]');
    expect(maskSensitiveText('DNI 35-123-456')).toBe('DNI [dni]');
    expect(maskSensitiveText('DNI 35123456')).toBe('DNI [dni]');
  });

  it('masksCuilAndCuitNumbers', () => {
    expect(maskSensitiveText('CUIT 20-35123456-3')).toBe('CUIT [dni]');
  });

  it('masksArgentinePhoneNumbers', () => {
    expect(maskSensitiveText('tel +54 9 11 5555-1234')).toBe('tel [telefono]');
    expect(maskSensitiveText('tel 11-5555-1234')).toBe('tel [telefono]');
  });

  it('masksEveryQueryStringIncludingRelativeUrls', () => {
    expect(maskSensitiveText('GET /api/alumnos?apellido=Pérez&nombre=Ana fallo')).toBe('GET /api/alumnos?[redactado] fallo');
    expect(maskSensitiveText('https://app.test/verificar-email?token=abc123&x=1 fallo')).toBe('https://app.test/verificar-email?[redactado] fallo');
  });

  it('redactsTokenParametersOutsideUrls', () => {
    expect(maskSensitiveText('reset token=abc123 ok')).toBe('reset token=[redactado] ok');
  });

  it('masksLooseOpaqueTokensInPaths', () => {
    const token = 'Qx9_vT2-LmN8pR4sW7yZ1aB3cD5eF6gH';
    expect(maskSensitiveText(`/activar/${token}`)).toBe('/activar/[token]');
  });

  it('masksNumericIdsInPaths', () => {
    expect(maskSensitiveText('falla en /alumnos/123 al guardar')).toBe('falla en /alumnos/[id] al guardar');
  });

  it('truncatesLongTextToTwoThousandCharacters', () => {
    const masked = maskSensitiveText('a '.repeat(5000));

    expect(masked).toHaveLength(2000);
    expect(masked.endsWith('…')).toBe(true);
  });

  it('keepsMatchingTimeBoundedOnHugeInputWithoutSpaces', () => {
    const hostile = `${'a'.repeat(100000)}@${'b'.repeat(100000)}`;
    const startedAt = performance.now();

    maskSensitiveText(hostile);

    expect(performance.now() - startedAt).toBeLessThan(500);
  });

  it('keepsMatchingTimeBoundedOnHugeTokenLikeInput', () => {
    const hostile = `${'x-'.repeat(100000)}?${'y'.repeat(100000)}`;
    const startedAt = performance.now();

    maskSensitiveText(hostile);

    expect(performance.now() - startedAt).toBeLessThan(500);
  });

  it('returnsNonStringValuesUnchanged', () => {
    expect(maskSensitiveText(undefined)).toBeUndefined();
  });
});
