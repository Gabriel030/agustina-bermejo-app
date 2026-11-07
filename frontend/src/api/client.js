/*
 * Wrapper fino sobre fetch — no se suma Axios/React Query en esta fase
 * (ver Spec - Fundación técnica y login local, decisión 5).
 */
import { NetworkError, createCircuitBreaker, rateLimitMessage, runWithRetry } from '@/api/retryPolicy';
import { ApiError } from '@/api/apiError';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export { CircuitOpenError, NetworkError } from '@/api/retryPolicy';
export { ApiError } from '@/api/apiError';

const DEFAULT_TIMEOUT_MS = 30000;
const UPLOAD_TIMEOUT_MS = 120000;

export class RateLimitedError extends Error {
  constructor(retryAfterSeconds = null) {
    super(rateLimitMessage(retryAfterSeconds));
    this.name = 'RateLimitedError';
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export class AuthError extends Error {
  constructor(message = 'Sesión inválida o expirada.') {
    super(message);
    this.name = 'AuthError';
  }
}

// El backend responde con body vacío en sus errores (GlobalExceptionHandler solo fija el
// status, sin JSON) — el mensaje al usuario se arma acá a partir del código HTTP, no del body.
const STATUS_MESSAGES = {
  400: 'Los datos ingresados no son válidos.',
  401: 'Usuario o contraseña incorrectos.',
  403: 'No tenés permiso para hacer esto.',
  404: 'No se encontró lo que buscabas.',
  409: 'La operación entra en conflicto con el estado actual.',
  502: 'El servicio no está disponible por ahora. Probá de nuevo en unos minutos.',
  503: 'El servicio no está disponible por ahora. Probá de nuevo en unos minutos.',
  504: 'El servicio no está disponible por ahora. Probá de nuevo en unos minutos.',
};

function messageForStatus(status) {
  return STATUS_MESSAGES[status] ?? 'Ocurrió un error inesperado. Probá de nuevo.';
}

async function readErrorBody(response) {
  try {
    const body = await response.json();
    return {
      code: body?.code ?? null,
      retryable: body?.retryable === true,
      requestId: body?.requestId ?? null,
      violations: Array.isArray(body?.violations) ? body.violations : [],
      details: body?.details && typeof body.details === 'object' ? body.details : null,
    };
  } catch {
    return { code: null, retryable: false, requestId: null, violations: [], details: null };
  }
}

let accessToken = null;
let onSessionExpired = null;
let offlineSessionMode = false;

export function setOfflineSessionMode(enabled) {
  offlineSessionMode = enabled;
}

export function setAccessToken(token) {
  accessToken = token;
  if (token) sessionExpiredNotified = false;
}

export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

function timeoutFor(options) {
  if (options.timeoutMs != null) return options.timeoutMs;
  return options.body instanceof FormData ? UPLOAD_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
}

async function rawFetch(path, options) {
  const fetchOptions = { ...options };
  delete fetchOptions.timeoutMs;
  const headers = new Headers(options.headers);
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  // FormData (multipart, ver DetalleSolicitud.jsx) nunca lleva Content-Type a mano —
  // el browser tiene que fijar el boundary por sí solo, forzar 'application/json' acá
  // rompería el parseo multipart del backend.
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const timeout = AbortSignal.timeout(timeoutFor(options));
  const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
  return fetch(`${API_BASE_URL}${path}`, { ...fetchOptions, headers, signal });
}

const circuits = new Map();
let retrySleep;
let circuitOptions = {};

export function setRetrySleep(sleep) {
  retrySleep = sleep;
}

export function setCircuitOptions(options) {
  circuitOptions = options;
  circuits.clear();
}

export function resetCircuits() {
  circuits.clear();
}

function circuitForHost() {
  const host = new URL(API_BASE_URL).host;
  if (!circuits.has(host)) circuits.set(host, createCircuitBreaker(circuitOptions));
  return circuits.get(host);
}

function sendWithRetryPolicy(path, options) {
  return runWithRetry({
    send: () => rawFetch(path, options),
    method: options.method ?? 'GET',
    hasIdempotencyKey: new Headers(options.headers).has('Idempotency-Key'),
    signal: options.signal,
    circuit: circuitForHost(),
    sleep: retrySleep,
  });
}

let refreshInFlight = null;
let sessionExpiredNotified = false;

function requestSessionRefresh() {
  if (!refreshInFlight) {
    refreshInFlight = refreshSession().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function refreshSession() {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return false;

  const response = await rawFetch('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) return false;

  const tokens = await response.json();
  setAccessToken(tokens.accessToken);
  localStorage.setItem('refreshToken', tokens.refreshToken);
  return true;
}

function expireSession() {
  if (!sessionExpiredNotified) {
    sessionExpiredNotified = true;
    onSessionExpired?.();
  }
  throw new AuthError();
}

async function fetchWithAuth(path, options) {
  if (offlineSessionMode && !path.startsWith('/auth/')) throw new NetworkError();
  const tokenSent = accessToken;
  let response = await sendWithRetryPolicy(path, options);

  if (response.status === 401 && !path.startsWith('/auth/')) {
    const alreadyRefreshed = accessToken !== null && accessToken !== tokenSent;
    const refreshed = alreadyRefreshed || (await requestSessionRefresh());
    if (!refreshed) expireSession();
    response = await sendWithRetryPolicy(path, options);
    if (response.status === 401) expireSession();
  }

  if (response.status === 429) {
    const retryAfter = Number(response.headers.get('Retry-After'));
    throw new RateLimitedError(Number.isFinite(retryAfter) && retryAfter > 0 ? Math.ceil(retryAfter) : null);
  }

  if (!response.ok) {
    const body = await readErrorBody(response);
    throw new ApiError(messageForStatus(response.status), { status: response.status, ...body });
  }

  return response;
}

export async function apiFetch(path, options = {}) {
  const response = await fetchWithAuth(path, options);
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export async function apiFetchWithTotal(path, options = {}) {
  const response = await fetchWithAuth(path, options);
  const text = await response.text();
  const items = text ? JSON.parse(text) : [];
  const header = response.headers.get('X-Total-Count');
  return { items, total: header == null ? items.length : Number(header) };
}

// Descarga de archivos (ej. exportar asistencia a PDF/XLS) — a diferencia de apiFetch, el body
// de la respuesta no es JSON, así que no puede pasar por el mismo parseo.
export async function apiFetchBlob(path, options = {}) {
  const response = await fetchWithAuth(path, { timeoutMs: UPLOAD_TIMEOUT_MS, ...options });
  const filename = response.headers.get('Content-Disposition')?.match(/filename="(.+)"/)?.[1] ?? 'archivo';
  const blob = await response.blob();
  return { blob, filename };
}
