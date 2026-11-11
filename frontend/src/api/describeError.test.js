import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { ApiError, NetworkError } from '@/api/client';
import { ERROR_MESSAGES, describeError } from '@/api/describeError';

function transversalCodes() {
  const catalog = readFileSync(resolve(import.meta.dirname, '../../../docs/api/error-catalog.md'), 'utf8');
  const section = catalog.split('## Errores transversales')[1].split('\n## ')[0];
  return [...section.matchAll(/^\| `([A-Z_]+)` \|/gm)].map((match) => match[1]);
}

function domainCodes() {
  const catalog = readFileSync(resolve(import.meta.dirname, '../../../docs/api/error-catalog.md'), 'utf8');
  const section = catalog.split('## Errores de dominio')[1].split('\n## ')[0];
  return [...new Set([...section.matchAll(/^\| `([A-Z][A-Z_0-9]+)` \|/gm)].map((match) => match[1]))];
}

describe('describeError', () => {
  it('mapsEveryTransversalCodeOfTheBackendCatalog', () => {
    const codes = transversalCodes();

    expect(codes.length).toBeGreaterThan(10);
    const missing = codes.filter((code) => !ERROR_MESSAGES[code]);
    expect(missing).toEqual([]);
  });

  it('mapsEveryDomainCodeOfTheBackendCatalog', () => {
    const codes = domainCodes();

    expect(codes.length).toBeGreaterThan(300);
    const missing = codes.filter((code) => !ERROR_MESSAGES[code]);
    expect(missing).toEqual([]);
  });

  it('mapsNoCodeThatTheCatalogDoesNotKnow', () => {
    const known = new Set([...transversalCodes(), ...domainCodes()]);

    const stale = Object.keys(ERROR_MESSAGES).filter((code) => !known.has(code));
    expect(stale).toEqual([]);
  });

  it('everyMessageIsUserFacingSpanishWithoutTechnicalText', () => {
    const technical = /[A-Z]{2,}_[A-Z_]+|exception|stack|null|undefined|sql|constraint|\bhttp\b|\bjson\b|\bapi\b|[{}<>]/i;

    const offenders = Object.entries(ERROR_MESSAGES).filter(
      ([, message]) => typeof message !== 'string' || message.length < 10 || !/[.]$/.test(message) || technical.test(message),
    );
    expect(offenders).toEqual([]);
  });

  it('backendMessageNeverReachesTheUser', () => {
    const error = new ApiError('No pudimos completar la operación.', { status: 409, code: 'SOMETHING_NEW' });
    error.backendDetail = 'org.postgresql.util.PSQLException: duplicate key';

    expect(describeError(error).message).not.toContain('PSQLException');
  });

  it('networkErrorIsRetryableWithoutRequestId', () => {
    const described = describeError(new NetworkError());

    expect(described).toMatchObject({ kind: 'network', retryable: true, requestId: null });
  });

  it('unknownCodeFallsBackToTheStatusMessage', () => {
    const error = new ApiError('No se encontró lo que buscabas.', { status: 404, code: 'SOMETHING_NEW' });

    expect(describeError(error)).toMatchObject({ kind: 'not-found', message: 'No se encontró lo que buscabas.' });
  });

  it('knownCodeUsesTheCatalogMessageAndKeepsTheRequestId', () => {
    const error = new ApiError('x', { status: 409, code: 'CONCURRENT_MODIFICATION', requestId: 'req-1' });

    expect(describeError(error)).toEqual({
      kind: 'conflict',
      message: ERROR_MESSAGES.CONCURRENT_MODIFICATION,
      retryable: false,
      requestId: 'req-1',
    });
  });

  it('serverErrorKindIsServer', () => {
    expect(describeError(new ApiError('x', { status: 500, retryable: true })).kind).toBe('server');
  });

  it('plainErrorWithoutMessageUsesTheFallback', () => {
    expect(describeError({}, { fallback: 'Fallo' }).message).toBe('Fallo');
  });
});
