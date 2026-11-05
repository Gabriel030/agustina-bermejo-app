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

describe('describeError', () => {
  it('mapsEveryTransversalCodeOfTheBackendCatalog', () => {
    const codes = transversalCodes();

    expect(codes.length).toBeGreaterThan(10);
    const missing = codes.filter((code) => !ERROR_MESSAGES[code]);
    expect(missing).toEqual([]);
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
