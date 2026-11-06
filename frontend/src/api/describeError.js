import { CircuitOpenError, NetworkError, RateLimitedError } from '@/api/client';

export const ERROR_MESSAGES = {
  VALIDATION_FAILED: 'Revisá los datos ingresados.',
  MALFORMED_REQUEST_BODY: 'No pudimos procesar lo que enviaste. Recargá e intentá de nuevo.',
  INVALID_PARAMETER_TYPE: 'El enlace o el dato indicado no es válido.',
  MAX_UPLOAD_SIZE_EXCEEDED: 'El archivo supera el tamaño permitido.',
  TOKEN_EXPIRED: 'Tu sesión venció. Ingresá de nuevo.',
  UNAUTHENTICATED: 'Tenés que iniciar sesión para continuar.',
  FORBIDDEN: 'No tenés permiso para hacer esto.',
  CONCURRENT_MODIFICATION: 'Otra persona modificó este registro. Recargá y volvé a intentar.',
  DATA_INTEGRITY_VIOLATION: 'La operación choca con datos existentes.',
  ILLEGAL_STATE: 'La operación no es posible en el estado actual.',
  ILLEGAL_ARGUMENT: 'Algún dato no es válido.',
  INTERNAL_ERROR: 'Tuvimos un problema de nuestro lado. Probá de nuevo en unos minutos.',
  REQUEST_RATE_LIMIT_EXCEEDED: 'Demasiados pedidos seguidos. Esperá unos segundos.',
  EXTERNAL_SERVICE_UNAVAILABLE: 'Un servicio externo no está disponible. Probá en unos minutos.',
  GRADE_ITEM_PUBLISHED_LOCKED: 'Esa evaluación ya está publicada y no se puede cargar ni editar.',
  NO_PENDING_GRADES_TO_PUBLISH: 'No hay notas cargadas para publicar en esta evaluación.',
  JUSTIFICATION_DEADLINE_EXPIRED: 'Venció el plazo para justificar esta falta. Si corresponde, la resuelve el preceptor como excepción.',
  INVALID_JUSTIFICATION_INPUT: 'El motivo de la justificación supera los 500 caracteres. Acortalo e intentá de nuevo.',
  CANNOT_JUSTIFY: 'Esta falta no se puede justificar en su estado actual.',
  MERCADO_PAGO_GATEWAY: 'Mercado Pago no respondió. Probá en unos minutos.',
  GOOGLE_SERVICE_UNAVAILABLE: 'Google no está disponible ahora. Probá más tarde.',
};

const DEFAULT_FALLBACK = 'Ocurrió un error inesperado. Probá de nuevo.';

function kindOf(status) {
  if (status === 401) return 'auth';
  if (status === 403) return 'forbidden';
  if (status === 404) return 'not-found';
  if (status === 409) return 'conflict';
  if (status >= 500) return 'server';
  if (status >= 400) return 'validation';
  return 'unknown';
}

export function describeError(error, { fallback = DEFAULT_FALLBACK } = {}) {
  if (error instanceof NetworkError) return { kind: 'network', message: error.message, retryable: true, requestId: null };
  if (error instanceof CircuitOpenError) return { kind: 'circuit', message: error.message, retryable: true, requestId: null };
  if (error instanceof RateLimitedError) return { kind: 'rate-limit', message: error.message, retryable: true, requestId: null };
  const code = error?.code ?? null;
  return {
    kind: kindOf(error?.status),
    message: (code && ERROR_MESSAGES[code]) || error?.message || fallback,
    retryable: error?.retryable === true,
    requestId: error?.requestId ?? null,
  };
}
