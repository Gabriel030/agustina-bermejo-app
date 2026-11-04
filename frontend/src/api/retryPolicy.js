export const MAX_ATTEMPTS = 3;
export const BASE_DELAY_MS = 300;
export const MAX_DELAY_MS = 5000;
export const CIRCUIT_FAILURE_THRESHOLD = 5;
export const CIRCUIT_OPEN_MS = 10000;

const REPLAYABLE_METHODS = new Set(['GET', 'HEAD', 'PUT', 'DELETE']);
const TRANSIENT_STATUSES = new Set([502, 503, 504]);
const RATE_LIMITED_STATUS = 429;

export class NetworkError extends Error {
  constructor() {
    super('No pudimos conectarnos con el servidor. Revisá tu conexión y probá de nuevo.');
    this.name = 'NetworkError';
  }
}

export class CircuitOpenError extends Error {
  constructor(secondsLeft) {
    super(`El servicio no está respondiendo. Probá de nuevo en ${secondsLeft} segundos.`);
    this.name = 'CircuitOpenError';
    this.secondsLeft = secondsLeft;
  }
}

export function rateLimitMessage(retryAfterSeconds) {
  return retryAfterSeconds == null
    ? 'Demasiados intentos, probá de nuevo en unos segundos.'
    : `Demasiados intentos, probá en ${retryAfterSeconds} segundos.`;
}

export function isReplayable(method, hasIdempotencyKey) {
  return REPLAYABLE_METHODS.has(String(method).toUpperCase()) || hasIdempotencyKey;
}

export function backoffDelayMs(attempt, random = Math.random) {
  const ceiling = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** (attempt - 1));
  return Math.round(ceiling / 2 + (random() * ceiling) / 2);
}

export function parseRetryAfterMs(headerValue, now = Date.now) {
  if (headerValue == null || headerValue === '') return null;
  const seconds = Number(headerValue);
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000);
  const date = Date.parse(headerValue);
  return Number.isNaN(date) ? null : Math.max(0, date - now());
}

export function createCircuitBreaker({ threshold = CIRCUIT_FAILURE_THRESHOLD, openMs = CIRCUIT_OPEN_MS, now = Date.now } = {}) {
  let failures = 0;
  let openUntil = 0;
  return {
    assertClosed() {
      const remaining = openUntil - now();
      if (remaining > 0) throw new CircuitOpenError(Math.ceil(remaining / 1000));
    },
    recordFailure() {
      failures += 1;
      if (failures >= threshold) openUntil = now() + openMs;
    },
    recordSuccess() {
      failures = 0;
      openUntil = 0;
    },
  };
}

export function abortableSleep(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'));
      return;
    }
    const onAbort = () => {
      clearTimeout(timer);
      reject(new DOMException('Aborted', 'AbortError'));
    };
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

async function bodyForbidsRetry(response) {
  try {
    const body = await response.clone().json();
    return body?.retryable === false;
  } catch {
    return false;
  }
}

export async function runWithRetry({
  send,
  method = 'GET',
  hasIdempotencyKey = false,
  signal,
  circuit,
  sleep = abortableSleep,
  random = Math.random,
  now = Date.now,
}) {
  const replayable = isReplayable(method, hasIdempotencyKey);
  for (let attempt = 1; ; attempt += 1) {
    circuit.assertClosed();
    let response;
    try {
      response = await send();
    } catch (error) {
      if (signal?.aborted || error?.name === 'AbortError') throw error;
      circuit.recordFailure();
      if (!replayable || attempt >= MAX_ATTEMPTS) throw new NetworkError();
      await sleep(backoffDelayMs(attempt, random), signal);
      continue;
    }

    const transient = TRANSIENT_STATUSES.has(response.status);
    const rateLimited = response.status === RATE_LIMITED_STATUS;
    if (!transient && !rateLimited) {
      circuit.recordSuccess();
      return response;
    }
    if (transient) circuit.recordFailure();

    if (!replayable || attempt >= MAX_ATTEMPTS || (await bodyForbidsRetry(response))) return response;

    let waitMs = backoffDelayMs(attempt, random);
    if (rateLimited) {
      const retryAfterMs = parseRetryAfterMs(response.headers.get('Retry-After'), now);
      if (retryAfterMs != null) {
        if (retryAfterMs > MAX_DELAY_MS) return response;
        waitMs = retryAfterMs;
      }
    }
    await sleep(waitMs, signal);
  }
}
