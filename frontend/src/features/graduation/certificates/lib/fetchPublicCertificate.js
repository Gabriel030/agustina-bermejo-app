import { API_BASE_URL } from '@/api/client';

const TIMEOUT_MS = 15000;
const NOT_FOUND = 404;
const RATE_LIMITED = 429;

export class CertificateLookupError extends Error {
  constructor(kind, retryAfterSeconds = null) {
    super(kind);
    this.name = 'CertificateLookupError';
    this.kind = kind;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

function retryAfterOf(response) {
  const seconds = Number(response.headers.get('Retry-After'));
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null;
}

export async function fetchPublicCertificate(normalizedCode, { signal } = {}) {
  const timeout = AbortSignal.timeout(TIMEOUT_MS);
  let response;
  try {
    response = await fetch(`${API_BASE_URL}/public/certificates/${normalizedCode}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new CertificateLookupError('FAILED');
  }
  if (response.status === NOT_FOUND) throw new CertificateLookupError('NOT_FOUND');
  if (response.status === RATE_LIMITED) throw new CertificateLookupError('RATE_LIMITED', retryAfterOf(response));
  if (!response.ok) throw new CertificateLookupError('FAILED');
  try {
    return await response.json();
  } catch {
    throw new CertificateLookupError('FAILED');
  }
}
