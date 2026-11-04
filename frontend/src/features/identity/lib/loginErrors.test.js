import { describe, expect, it } from 'vitest';
import { CircuitOpenError, NetworkError, RateLimitedError } from '@/api/client';
import {
  DEFAULT_COOLDOWN_SECONDS,
  GENERIC_ERROR_MESSAGE,
  GOOGLE_REJECTED_MESSAGE,
  GOOGLE_UNAVAILABLE_MESSAGE,
  INVALID_CREDENTIALS_MESSAGE,
  NETWORK_MESSAGE,
  RATE_LIMITED_MESSAGE,
  SERVICE_UNAVAILABLE_MESSAGE,
  cooldownSecondsFor,
  messageForAuthError,
} from '@/features/identity/lib/loginErrors';

function apiError(status, code) {
  const error = new Error('x');
  error.status = status;
  error.code = code;
  return error;
}

describe('messageForAuthError', () => {
  it('maps stable codes to Spanish messages', () => {
    expect(messageForAuthError(apiError(401, 'INVALID_CREDENTIALS'))).toBe(INVALID_CREDENTIALS_MESSAGE);
    expect(messageForAuthError(apiError(503, 'EXTERNAL_SERVICE_UNAVAILABLE'))).toBe(SERVICE_UNAVAILABLE_MESSAGE);
    expect(messageForAuthError(apiError(401, 'INVALID_GOOGLE_TOKEN'))).toBe(GOOGLE_REJECTED_MESSAGE);
  });

  it('maps transport errors', () => {
    expect(messageForAuthError(new RateLimitedError(5))).toBe(RATE_LIMITED_MESSAGE);
    expect(messageForAuthError(new NetworkError())).toBe(NETWORK_MESSAGE);
    expect(messageForAuthError(new CircuitOpenError())).toBe(SERVICE_UNAVAILABLE_MESSAGE);
  });

  it('distinguishes Google unavailability from rejection', () => {
    expect(messageForAuthError(apiError(502, null), { google: true })).toBe(GOOGLE_UNAVAILABLE_MESSAGE);
    expect(messageForAuthError(apiError(503, null), { google: true })).toBe(GOOGLE_UNAVAILABLE_MESSAGE);
    expect(messageForAuthError(apiError(401, 'INVALID_GOOGLE_TOKEN'), { google: true })).toBe(GOOGLE_REJECTED_MESSAGE);
  });

  it('uses the fallback for unknown 4xx and a generic message for 5xx', () => {
    expect(messageForAuthError(apiError(400, 'OTHER'), { fallback: 'custom' })).toBe('custom');
    expect(messageForAuthError(apiError(500, 'INTERNAL_ERROR'), { fallback: 'custom' })).toBe(GENERIC_ERROR_MESSAGE);
  });
});

describe('cooldownSecondsFor', () => {
  it('uses Retry-After, a default when absent, and zero for other errors', () => {
    expect(cooldownSecondsFor(new RateLimitedError(7))).toBe(7);
    expect(cooldownSecondsFor(new RateLimitedError())).toBe(DEFAULT_COOLDOWN_SECONDS);
    expect(cooldownSecondsFor(apiError(401, 'INVALID_CREDENTIALS'))).toBe(0);
  });
});
