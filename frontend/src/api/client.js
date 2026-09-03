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

async function fetchWithAuth(path, options) {
  let response = await rawFetch(path, options);

  // El backend nunca manda body en sus errores, así que un token expirado y un rol
  // realmente insuficiente son indistinguibles acá: ambos casos caen en el mismo
  // JwtAuthenticationFilter (limpia el contexto ante un JWT inválido/vencido en vez de
  // cortar la request, ver JwtAuthenticationFilter.java) y terminan en 403 vía
  // @PreAuthorize, no en 401. Reintentar refresh también en 403 es lo único que
  // recupera la sesión vencida sin pedirle al usuario que vuelva a loguearse a mano;
  // el costo en el caso de rol insuficiente es un round-trip de más (refresh exitoso,
  // reintento que vuelve a dar 403 legítimo).
  if ((response.status === 401 || response.status === 403) && !path.startsWith('/auth/')) {
    const refreshed = await refreshSession();
    if (refreshed) {
      response = await rawFetch(path, options);
    } else if (response.status === 401) {
      onSessionExpired?.();
      throw new AuthError();
    }
  }

  if (response.status === 429) {
    throw new RateLimitedError();
  }

  if (!response.ok) {
    const error = new Error(messageForStatus(response.status));
    error.status = response.status;
    throw error;
  }

  return response;
}

export async function apiFetch(path, options = {}) {
  const response = await fetchWithAuth(path, options);
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

// Descarga de archivos (ej. exportar asistencia a PDF/XLS) — a diferencia de apiFetch, el body
// de la respuesta no es JSON, así que no puede pasar por el mismo parseo.
export async function apiFetchBlob(path, options = {}) {
  const response = await fetchWithAuth(path, options);
  const filename = response.headers.get('Content-Disposition')?.match(/filename="(.+)"/)?.[1] ?? 'archivo';
  const blob = await response.blob();
  return { blob, filename };
}
