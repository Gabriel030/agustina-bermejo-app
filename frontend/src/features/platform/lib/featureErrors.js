export const STALE_FEATURE_VERSION = 'STALE_FEATURE_VERSION';

const MESSAGE_BY_CODE = {
  FEATURE_ALREADY_IN_REQUESTED_STATE: 'Este módulo ya estaba en ese estado.',
  INVALID_BILLING_MODE_TRANSITION: 'Ese cambio de modo no está permitido todavía. Requiere un paso previo que aún no está disponible.',
  INVALID_TRANSITION_CONFIRMATION: 'La verificación previa venció. Volvé a empezar el cambio.',
  FEATURE_CHANGE_REASON_INVALID: 'El motivo debe tener entre 5 y 500 caracteres.',
  FEATURE_VALUE_INVALID: 'El valor pedido no es válido para este módulo.',
  FEATURE_NOT_PROVISIONED: 'Este módulo todavía no está configurado en el instituto.',
};

export function isStaleFeature(error) {
  return error?.status === 409 && (error.code === STALE_FEATURE_VERSION || error.code === 'FEATURE_ALREADY_IN_REQUESTED_STATE');
}

export function featureErrorMessage(error) {
  if (error?.code === STALE_FEATURE_VERSION) return 'Alguien cambió este módulo mientras lo editabas.';
  return MESSAGE_BY_CODE[error?.code] ?? error?.message ?? 'No pudimos guardar el cambio. Probá de nuevo.';
}
