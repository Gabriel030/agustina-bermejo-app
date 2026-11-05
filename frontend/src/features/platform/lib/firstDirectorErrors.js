export const FIELD_LIMITS = { institutionName: 255, firstName: 255, lastName: 255, email: 255, documentNumber: 50 };

export const DIRECTOR_ALREADY_EXISTS = 'DIRECTOR_ALREADY_EXISTS';

const FIELD_ERROR_BY_CODE = {
  EMAIL_ALREADY_EXISTS: { field: 'email', message: 'Ya hay una persona registrada con este email' },
  DNI_ALREADY_EXISTS: { field: 'documentNumber', message: 'Ya hay una persona registrada con este documento' },
};

const MESSAGE_BY_CODE = {
  VALIDATION_FAILED: 'Alguno de los datos no es válido. Revisá cada campo y volvé a intentar.',
  MALFORMED_REQUEST_BODY: 'Alguno de los datos no es válido. Revisá cada campo y volvé a intentar.',
  IDEMPOTENCY_KEY_REUSED: 'Ya se envió un pedido con otros datos. Recargá la página y volvé a intentar.',
};

export function fieldErrorOf(error) {
  return FIELD_ERROR_BY_CODE[error?.code] ?? null;
}

export function isDirectorAlreadyExists(error) {
  return error?.status === 409 && error.code === DIRECTOR_ALREADY_EXISTS;
}

export function isRetryableFailure(error) {
  return error?.status >= 500 || error?.status === 429;
}

export function failureMessageOf(error) {
  return MESSAGE_BY_CODE[error?.code] ?? error?.message;
}
