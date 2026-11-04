import { CircuitOpenError, NetworkError, RateLimitedError } from '@/api/client';

export const GENERIC_ERROR_MESSAGE = 'Algo salió mal. Probá de nuevo.';
export const INVALID_CREDENTIALS_MESSAGE = 'Usuario o contraseña incorrectos.';
export const RATE_LIMITED_MESSAGE = 'Demasiados intentos. Esperá para volver a probar.';
export const SERVICE_UNAVAILABLE_MESSAGE = 'El servicio no está disponible. Probá en unos minutos.';
export const NETWORK_MESSAGE = 'No pudimos conectar. Revisá tu conexión.';
export const GOOGLE_UNAVAILABLE_MESSAGE = 'Google no está disponible ahora. Probá más tarde.';
export const GOOGLE_REJECTED_MESSAGE = 'No pudimos validar tu cuenta de Google.';
export const DEFAULT_COOLDOWN_SECONDS = 10;

const CODE_MESSAGES = {
  INVALID_CREDENTIALS: INVALID_CREDENTIALS_MESSAGE,
  INVALID_GOOGLE_TOKEN: GOOGLE_REJECTED_MESSAGE,
  RATE_LIMIT_EXCEEDED: RATE_LIMITED_MESSAGE,
  REQUEST_RATE_LIMIT_EXCEEDED: RATE_LIMITED_MESSAGE,
  EXTERNAL_SERVICE_UNAVAILABLE: SERVICE_UNAVAILABLE_MESSAGE,
};

export function isGoogleUnavailable(err) {
  return err instanceof NetworkError || err instanceof CircuitOpenError || [502, 503, 504].includes(err?.status);
}

export function messageForAuthError(err, { fallback = GENERIC_ERROR_MESSAGE, google = false } = {}) {
  if (err instanceof RateLimitedError) return RATE_LIMITED_MESSAGE;
  if (google && isGoogleUnavailable(err)) return GOOGLE_UNAVAILABLE_MESSAGE;
  if (err instanceof CircuitOpenError) return SERVICE_UNAVAILABLE_MESSAGE;
  if (err instanceof NetworkError) return NETWORK_MESSAGE;
  if (err?.code && CODE_MESSAGES[err.code]) return CODE_MESSAGES[err.code];
  if (err?.status === 503) return SERVICE_UNAVAILABLE_MESSAGE;
  if (err?.status >= 500) return GENERIC_ERROR_MESSAGE;
  return fallback;
}

export function cooldownSecondsFor(err) {
  if (!(err instanceof RateLimitedError)) return 0;
  return err.retryAfterSeconds ?? DEFAULT_COOLDOWN_SECONDS;
}
