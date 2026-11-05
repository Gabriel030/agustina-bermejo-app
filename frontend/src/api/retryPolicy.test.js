import { describe, expect, it, vi } from 'vitest';
import {
  CircuitOpenError,
  NetworkError,
  backoffDelayMs,
  createCircuitBreaker,
  isReplayable,
  parseRetryAfterMs,
  runWithRetry,
} from '@/api/retryPolicy';

function responseOf(status, { body = null, headers = {} } = {}) {
  return new Response(body == null ? null : JSON.stringify(body), { status, headers });
}

function sequence(...outcomes) {
  const send = vi.fn(async () => {
    const outcome = outcomes[Math.min(send.mock.calls.length - 1, outcomes.length - 1)];
    if (outcome instanceof Error) throw outcome;
    return outcome();
  });
  return send;
}

const noSleep = () => vi.fn(async () => {});

describe('runWithRetry', () => {
  it('retries a GET on 502 and gives up after the third attempt', async () => {
    const send = sequence(() => responseOf(502));
    const sleep = noSleep();

    const response = await runWithRetry({ send, method: 'GET', circuit: createCircuitBreaker({ threshold: 99 }), sleep });

    expect(response.status).toBe(502);
    expect(send).toHaveBeenCalledTimes(3);
    expect(sleep).toHaveBeenCalledTimes(2);
  });

  it('succeeds when a later attempt works', async () => {
    const send = sequence(() => responseOf(503), () => responseOf(200));

    const response = await runWithRetry({ send, method: 'PUT', circuit: createCircuitBreaker(), sleep: noSleep() });

    expect(response.status).toBe(200);
    expect(send).toHaveBeenCalledTimes(2);
  });

  it('retries a network failure on GET and reports a NetworkError at the end', async () => {
    const send = sequence(new TypeError('Failed to fetch'));

    await expect(
      runWithRetry({ send, method: 'DELETE', circuit: createCircuitBreaker({ threshold: 99 }), sleep: noSleep() }),
    ).rejects.toBeInstanceOf(NetworkError);
    expect(send).toHaveBeenCalledTimes(3);
  });

  it('never retries a POST without Idempotency-Key', async () => {
    const send = sequence(() => responseOf(502));

    const response = await runWithRetry({ send, method: 'POST', circuit: createCircuitBreaker(), sleep: noSleep() });

    expect(response.status).toBe(502);
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('does not retry a POST network failure without Idempotency-Key', async () => {
    const send = sequence(new TypeError('Failed to fetch'));

    await expect(
      runWithRetry({ send, method: 'POST', circuit: createCircuitBreaker(), sleep: noSleep() }),
    ).rejects.toBeInstanceOf(NetworkError);
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('retries a POST that carries an Idempotency-Key', async () => {
    const send = sequence(() => responseOf(502), () => responseOf(201));

    const response = await runWithRetry({
      send,
      method: 'POST',
      hasIdempotencyKey: true,
      circuit: createCircuitBreaker(),
      sleep: noSleep(),
    });

    expect(response.status).toBe(201);
    expect(send).toHaveBeenCalledTimes(2);
  });

  it.each([400, 401, 403, 404, 409, 500])('never retries a %i', async (status) => {
    const send = sequence(() => responseOf(status));

    const response = await runWithRetry({ send, method: 'GET', circuit: createCircuitBreaker(), sleep: noSleep() });

    expect(response.status).toBe(status);
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('never retries when the error body says retryable is false', async () => {
    const send = sequence(() => responseOf(502, { body: { code: 'PAYMENT_PROVIDER_DOWN', retryable: false } }));

    await runWithRetry({ send, method: 'GET', circuit: createCircuitBreaker(), sleep: noSleep() });

    expect(send).toHaveBeenCalledTimes(1);
  });

  it('still retries when the body says retryable is true', async () => {
    const send = sequence(
      () => responseOf(502, { body: { code: 'PAYMENT_PROVIDER_DOWN', retryable: true } }),
      () => responseOf(200),
    );

    await runWithRetry({ send, method: 'GET', circuit: createCircuitBreaker(), sleep: noSleep() });

    expect(send).toHaveBeenCalledTimes(2);
  });

  it('waits the Retry-After of a 429 instead of the backoff', async () => {
    const send = sequence(() => responseOf(429, { headers: { 'Retry-After': '2' } }), () => responseOf(200));
    const sleep = noSleep();

    await runWithRetry({ send, method: 'GET', circuit: createCircuitBreaker(), sleep });

    expect(sleep).toHaveBeenCalledWith(2000, undefined);
  });

  it('does not wait for a Retry-After longer than the cap and surfaces the 429', async () => {
    const send = sequence(() => responseOf(429, { headers: { 'Retry-After': '60' } }));
    const sleep = noSleep();

    const response = await runWithRetry({ send, method: 'GET', circuit: createCircuitBreaker(), sleep });

    expect(response.status).toBe(429);
    expect(send).toHaveBeenCalledTimes(1);
    expect(sleep).not.toHaveBeenCalled();
  });

  it('does not retry a 429 on a POST without key', async () => {
    const send = sequence(() => responseOf(429, { headers: { 'Retry-After': '1' } }));

    await runWithRetry({ send, method: 'POST', circuit: createCircuitBreaker(), sleep: noSleep() });

    expect(send).toHaveBeenCalledTimes(1);
  });

  it('stops without retrying when the request was aborted', async () => {
    const controller = new AbortController();
    const send = vi.fn(async () => {
      controller.abort();
      throw new DOMException('Aborted', 'AbortError');
    });

    await expect(
      runWithRetry({ send, method: 'GET', signal: controller.signal, circuit: createCircuitBreaker(), sleep: noSleep() }),
    ).rejects.toMatchObject({ name: 'AbortError' });
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('stops retrying when the signal aborts during the wait', async () => {
    const controller = new AbortController();
    const send = sequence(() => responseOf(502));
    const sleep = vi.fn(async () => {
      controller.abort();
      throw new DOMException('Aborted', 'AbortError');
    });

    await expect(
      runWithRetry({ send, method: 'GET', signal: controller.signal, circuit: createCircuitBreaker(), sleep }),
    ).rejects.toMatchObject({ name: 'AbortError' });
    expect(send).toHaveBeenCalledTimes(1);
  });
});

describe('circuit breaker', () => {
  function clock() {
    const state = { value: 1_000 };
    return { now: () => state.value, advance: (ms) => { state.value += ms; } };
  }

  it('opens after the threshold of consecutive failures and fails fast without calling the backend', async () => {
    const time = clock();
    const circuit = createCircuitBreaker({ threshold: 3, openMs: 10_000, now: time.now });
    const send = sequence(() => responseOf(502));

    await runWithRetry({ send, method: 'GET', circuit, sleep: noSleep() });
    expect(send).toHaveBeenCalledTimes(3);

    await expect(runWithRetry({ send, method: 'GET', circuit, sleep: noSleep() })).rejects.toBeInstanceOf(CircuitOpenError);
    expect(send).toHaveBeenCalledTimes(3);
  });

  it('reports the seconds left until it closes', () => {
    const time = clock();
    const circuit = createCircuitBreaker({ threshold: 1, openMs: 10_000, now: time.now });
    circuit.recordFailure();
    time.advance(4_000);

    expect(() => circuit.assertClosed()).toThrow(/6 segundos/);
  });

  it('lets a probe through after the open period and closes again on success', async () => {
    const time = clock();
    const circuit = createCircuitBreaker({ threshold: 2, openMs: 10_000, now: time.now });
    circuit.recordFailure();
    circuit.recordFailure();
    expect(() => circuit.assertClosed()).toThrow(CircuitOpenError);

    time.advance(10_001);
    const send = sequence(() => responseOf(200));
    await runWithRetry({ send, method: 'GET', circuit, sleep: noSleep() });

    circuit.recordFailure();
    expect(() => circuit.assertClosed()).not.toThrow();
  });

  it('reopens immediately when the probe after the open period fails', async () => {
    const time = clock();
    const circuit = createCircuitBreaker({ threshold: 2, openMs: 10_000, now: time.now });
    circuit.recordFailure();
    circuit.recordFailure();
    time.advance(10_001);

    await runWithRetry({ send: sequence(() => responseOf(502)), method: 'POST', circuit, sleep: noSleep() });

    expect(() => circuit.assertClosed()).toThrow(CircuitOpenError);
  });

  it('a success between failures resets the count', () => {
    const circuit = createCircuitBreaker({ threshold: 3 });
    circuit.recordFailure();
    circuit.recordFailure();
    circuit.recordSuccess();
    circuit.recordFailure();

    expect(() => circuit.assertClosed()).not.toThrow();
  });

  it('does not count 4xx or 429 as host failures', async () => {
    const circuit = createCircuitBreaker({ threshold: 2 });
    for (const status of [429, 404, 429, 404]) {
      await runWithRetry({ send: sequence(() => responseOf(status)), method: 'POST', circuit, sleep: noSleep() });
    }

    expect(() => circuit.assertClosed()).not.toThrow();
  });
});

describe('backoff and helpers', () => {
  it('grows exponentially, with jitter between half and the full ceiling', () => {
    expect(backoffDelayMs(1, () => 0)).toBe(150);
    expect(backoffDelayMs(1, () => 1)).toBe(300);
    expect(backoffDelayMs(2, () => 1)).toBe(600);
    expect(backoffDelayMs(3, () => 1)).toBe(1200);
  });

  it('caps the delay at five seconds', () => {
    expect(backoffDelayMs(10, () => 1)).toBe(5000);
  });

  it('parses Retry-After in seconds and as an HTTP date', () => {
    expect(parseRetryAfterMs('3')).toBe(3000);
    expect(parseRetryAfterMs(null)).toBeNull();
    expect(parseRetryAfterMs('Thu, 01 Jan 1970 00:00:10 GMT', () => 4000)).toBe(6000);
    expect(parseRetryAfterMs('garbage')).toBeNull();
  });

  it('classifies methods', () => {
    expect(isReplayable('get', false)).toBe(true);
    expect(isReplayable('PUT', false)).toBe(true);
    expect(isReplayable('DELETE', false)).toBe(true);
    expect(isReplayable('POST', false)).toBe(false);
    expect(isReplayable('PATCH', false)).toBe(false);
    expect(isReplayable('POST', true)).toBe(true);
  });
});
