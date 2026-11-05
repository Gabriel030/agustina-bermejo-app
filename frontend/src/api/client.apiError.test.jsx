import { beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { ApiError, NetworkError, apiFetch, setAccessToken, setCircuitOptions, setRetrySleep } from '@/api/client';

const API = 'http://localhost:8080';

describe('apiFetch errors', () => {
  beforeEach(() => {
    setAccessToken(null);
    localStorage.clear();
    setCircuitOptions({});
    setRetrySleep(() => Promise.resolve());
  });

  it('apiFetchRejectsWithAnApiErrorCarryingCodeRetryableAndRequestId', async () => {
    server.use(http.get(`${API}/academic-offering-cycles`, () => HttpResponse.json(errorBody('CONCURRENT_MODIFICATION', { requestId: 'req-9' }), { status: 409 })));

    const error = await apiFetch('/academic-offering-cycles').catch((caught) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.code).toBe('CONCURRENT_MODIFICATION');
    expect(error.requestId).toBe('req-9');
    expect(error.retryable).toBe(false);
  });

  it('apiFetchAbortsARequestThatExceedsTheTimeout', async () => {
    server.use(http.get(`${API}/academic-offering-cycles`, async () => {
      await delay('infinite');
      return HttpResponse.json({});
    }));

    await expect(apiFetch('/academic-offering-cycles', { timeoutMs: 20 })).rejects.toBeInstanceOf(NetworkError);
  });
});
