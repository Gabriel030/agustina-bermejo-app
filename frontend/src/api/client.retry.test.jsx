import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { QueryClientProvider, useMutation, useQuery } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { server } from '@/test/mocks/server';
import {
  CircuitOpenError,
  RateLimitedError,
  apiFetch,
  setAccessToken,
  setCircuitOptions,
  setRetrySleep,
} from '@/api/client';
import { createQueryClient } from '@/api/queryClient';

const API = 'http://localhost:8080';

describe('apiFetch retry policy', () => {
  let waits;

  beforeEach(() => {
    setAccessToken(null);
    localStorage.clear();
    setCircuitOptions({});
    waits = [];
    setRetrySleep(async (ms) => {
      waits.push(ms);
    });
  });

  afterEach(() => {
    setRetrySleep(undefined);
  });

  it('retries a GET on 502 and fails after the third attempt', async () => {
    let hits = 0;
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        hits += 1;
        return HttpResponse.json({ code: 'EXTERNAL_SERVICE_UNAVAILABLE', retryable: true, requestId: 'req-1' }, { status: 502 });
      }),
    );

    await expect(apiFetch('/academic-offering-cycles')).rejects.toMatchObject({
      status: 502,
      message: 'El servicio no está disponible por ahora. Probá de nuevo en unos minutos.',
    });
    expect(hits).toBe(3);
    expect(waits).toHaveLength(2);
  });

  it('recovers when the second attempt succeeds', async () => {
    let hits = 0;
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        hits += 1;
        return hits === 1 ? new HttpResponse(null, { status: 502 }) : HttpResponse.json([]);
      }),
    );

    await expect(apiFetch('/academic-offering-cycles')).resolves.toEqual([]);
    expect(hits).toBe(2);
  });

  it('does not retry a POST without Idempotency-Key', async () => {
    let hits = 0;
    server.use(
      http.post(`${API}/academic-offering-cycles/1/approve`, () => {
        hits += 1;
        return new HttpResponse(null, { status: 502 });
      }),
    );

    await expect(apiFetch('/academic-offering-cycles/1/approve', { method: 'POST', body: '{}' })).rejects.toMatchObject({ status: 502 });
    expect(hits).toBe(1);
  });

  it('retries a POST with Idempotency-Key reusing the same key', async () => {
    const keys = [];
    server.use(
      http.post(`${API}/academic-offering-cycles/1/approve`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return keys.length === 1 ? new HttpResponse(null, { status: 502 }) : new HttpResponse(null, { status: 204 });
      }),
    );

    await apiFetch('/academic-offering-cycles/1/approve', { method: 'POST', body: '{}', headers: { 'Idempotency-Key': 'key-1' } });

    expect(keys).toEqual(['key-1', 'key-1']);
  });

  it('does not retry when the backend says retryable is false', async () => {
    let hits = 0;
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        hits += 1;
        return HttpResponse.json({ code: 'PAYMENT_PROVIDER_REJECTED', retryable: false, requestId: 'req-1' }, { status: 502 });
      }),
    );

    await expect(apiFetch('/academic-offering-cycles')).rejects.toMatchObject({ status: 502 });
    expect(hits).toBe(1);
  });

  it('waits the Retry-After of a 429 and then succeeds', async () => {
    let hits = 0;
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        hits += 1;
        return hits === 1
          ? HttpResponse.json({ code: 'TOO_MANY_REQUESTS', retryable: true, requestId: 'req-1' }, { status: 429, headers: { 'Retry-After': '2' } })
          : HttpResponse.json([]);
      }),
    );

    await expect(apiFetch('/academic-offering-cycles')).resolves.toEqual([]);
    expect(waits).toEqual([2000]);
  });

  it('tells the user how many seconds to wait when the limit is too long to wait for', async () => {
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => new HttpResponse(null, { status: 429, headers: { 'Retry-After': '30' } })),
    );

    const error = await apiFetch('/academic-offering-cycles').catch((caught) => caught);

    expect(error).toBeInstanceOf(RateLimitedError);
    expect(error.message).toBe('Demasiados intentos, probá en 30 segundos.');
  });

  it('retries network errors and reports a clear message at the end', async () => {
    let hits = 0;
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        hits += 1;
        return HttpResponse.error();
      }),
    );

    await expect(apiFetch('/academic-offering-cycles')).rejects.toMatchObject({
      name: 'NetworkError',
      message: 'No pudimos conectarnos con el servidor. Revisá tu conexión y probá de nuevo.',
    });
    expect(hits).toBe(3);
  });

  it('does not retry once the caller aborted', async () => {
    let hits = 0;
    const controller = new AbortController();
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        hits += 1;
        controller.abort();
        return new HttpResponse(null, { status: 502 });
      }),
    );
    setRetrySleep(async (ms, signal) => {
      if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
      waits.push(ms);
    });

    await expect(apiFetch('/academic-offering-cycles', { signal: controller.signal })).rejects.toMatchObject({ name: 'AbortError' });
    expect(hits).toBe(1);
  });

  it('opens the circuit after repeated transient failures and fails fast with a clear message', async () => {
    let hits = 0;
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        hits += 1;
        return new HttpResponse(null, { status: 502 });
      }),
    );

    await expect(apiFetch('/academic-offering-cycles')).rejects.toMatchObject({ status: 502 });
    await expect(apiFetch('/academic-offering-cycles')).rejects.toBeInstanceOf(CircuitOpenError);
    const hitsWhenOpened = hits;
    const error = await apiFetch('/academic-offering-cycles').catch((caught) => caught);

    expect(error.message).toMatch(/^El servicio no está respondiendo\. Probá de nuevo en \d+ segundos\.$/);
    expect(hits).toBe(hitsWhenOpened);
  });
});

describe('React Query does not multiply the client retries', () => {
  beforeEach(() => {
    setAccessToken(null);
    setCircuitOptions({});
    setRetrySleep(async () => {});
  });

  afterEach(() => {
    setRetrySleep(undefined);
  });

  function Query() {
    const { isError } = useQuery({ queryKey: ['unstable'], queryFn: () => apiFetch('/academic-offering-cycles') });
    return <p>{isError ? 'failed' : 'loading'}</p>;
  }

  function Mutation() {
    const { mutate, isError } = useMutation({
      mutationFn: () => apiFetch('/academic-offering-cycles/1/approve', { method: 'POST', body: '{}' }),
    });
    return (
      <>
        <button onClick={() => mutate()}>send</button>
        <p>{isError ? 'failed' : 'idle'}</p>
      </>
    );
  }

  it('a failing query hits the backend exactly three times', async () => {
    let hits = 0;
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        hits += 1;
        return new HttpResponse(null, { status: 502 });
      }),
    );

    render(
      <QueryClientProvider client={createQueryClient()}>
        <Query />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByText('failed')).toBeInTheDocument());
    expect(hits).toBe(3);
  });

  it('a failing POST mutation without key hits the backend once', async () => {
    let hits = 0;
    server.use(
      http.post(`${API}/academic-offering-cycles/1/approve`, () => {
        hits += 1;
        return new HttpResponse(null, { status: 502 });
      }),
    );

    render(
      <QueryClientProvider client={createQueryClient()}>
        <Mutation />
      </QueryClientProvider>,
    );
    screen.getByText('send').click();

    await waitFor(() => expect(screen.getByText('failed')).toBeInTheDocument());
    expect(hits).toBe(1);
  });

  it('the shared query client disables its own retries', () => {
    const client = createQueryClient();
    expect(client.getDefaultOptions().queries.retry).toBe(false);
    expect(client.getDefaultOptions().mutations.retry).toBe(false);
  });
});
