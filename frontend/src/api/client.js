/*
 * Wrapper fino sobre fetch — no se suma Axios/React Query en esta fase
 * (ver Spec - Fundación técnica y login local, decisión 5).
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export class RateLimitedError extends Error {
  constructor() {
    super('Demasiados intentos, esperá un momento antes de reintentar.');
    this.name = 'RateLimitedError';
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
};

function messageForStatus(status) {
  return STATUS_MESSAGES[status] ?? 'Ocurrió un error inesperado. Probá de nuevo.';
}

let accessToken = null;
let onSessionExpired = null;

export function setAccessToken(token) {
  accessToken = token;
}

export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

async function rawFetch(path, options) {
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
  return fetch(`${API_BASE_URL}${path}`, { ...options, headers });
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

export async function apiFetch(path, options = {}) {
  let response = await rawFetch(path, options);

  if (response.status === 401 && !path.startsWith('/auth/')) {
    const refreshed = await refreshSession();
    if (refreshed) {
      response = await rawFetch(path, options);
    } else {
      onSessionExpired?.();
      throw new AuthError();
    }
  }

  if (response.status === 429) {
    throw new RateLimitedError();
  }

  if (!response.ok) {
    throw new Error(messageForStatus(response.status));
  }

  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
