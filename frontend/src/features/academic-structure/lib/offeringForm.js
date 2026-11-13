export function fieldProps(id, error, required) {
  return {
    id,
    'aria-invalid': error ? 'true' : undefined,
    'aria-describedby': error ? `${id}-error` : undefined,
    'aria-required': required ? 'true' : undefined,
  };
}

function isWholeNumber(value) {
  return /^-?\d+$/.test(String(value).trim());
}

function validateRequiredPositiveAmount(value, label) {
  const text = String(value ?? '').trim();
  if (text === '') return `${label} es obligatoria y debe ser mayor que cero. Para ofrecerla gratis, usá un descuento.`;
  if (Number.isNaN(Number(text)) || Number(text) <= 0) return `${label} debe ser mayor que cero. Para ofrecerla gratis, usá un descuento.`;
  return null;
}

export function validateProgramFields(values, { requireCredits, requirePrices = false }) {
  const errors = {};
  if (!values.name.trim()) errors.name = 'El nombre es obligatorio.';
  if (!isWholeNumber(values.totalYears) || Number(values.totalYears) < 1 || Number(values.totalYears) > 10) {
    errors.totalYears = 'La duración debe ser un número entero de 1 a 10 años.';
  }
  if (values.totalCreditsRequired === '') {
    if (requireCredits) errors.totalCreditsRequired = 'Los créditos requeridos son obligatorios.';
  } else if (!isWholeNumber(values.totalCreditsRequired) || Number(values.totalCreditsRequired) < 0) {
    errors.totalCreditsRequired = 'Los créditos deben ser un número entero mayor o igual a 0.';
  }
  if (values.totalHoursRequired !== '' && (!isWholeNumber(values.totalHoursRequired) || Number(values.totalHoursRequired) < 0)) {
    errors.totalHoursRequired = 'Las horas deben ser un número entero mayor o igual a 0.';
  }
  if (requirePrices) {
    const priceError = validateRequiredPositiveAmount(values.price, 'La cuota');
    if (priceError) errors.price = priceError;
    const enrollmentFeeError = validateRequiredPositiveAmount(values.enrollmentFee, 'La matrícula');
    if (enrollmentFeeError) errors.enrollmentFee = enrollmentFeeError;
  }
  return errors;
}

export function validateCourseBasics(values) {
  const errors = {};
  if (!values.name.trim()) errors.name = 'El nombre es obligatorio.';
  if (values.termId !== undefined && !values.termId) errors.termId = 'El período académico es obligatorio.';
  if (!isWholeNumber(values.capacity) || Number(values.capacity) < 1) {
    errors.capacity = 'El cupo debe ser un número entero de al menos 1.';
  }
  const priceError = validateRequiredPositiveAmount(values.price, 'La cuota');
  if (priceError) errors.price = priceError;
  return errors;
}

export function validateCourseDetail(values) {
  const errors = {};
  if (!isWholeNumber(values.durationMonths) || Number(values.durationMonths) < 1) {
    errors.durationMonths = 'La duración en meses es obligatoria y tiene que ser al menos 1.';
  }
  if (values.registrationStart && values.registrationEnd && values.registrationEnd < values.registrationStart) {
    errors.registrationEnd = 'El cierre de inscripción no puede ser anterior a la apertura.';
  }
  return errors;
}

export function countErrors(errors) {
  return Object.keys(errors).length;
}

export function invalidFieldsSummary(errors) {
  const total = countErrors(errors);
  return total === 1 ? 'Corregí el campo marcado para continuar.' : `Corregí los ${total} campos marcados para continuar.`;
}

const SAVE_ERRORS = {
  INVALID_REGISTRATION_WINDOW: { field: 'registrationEnd', message: 'El cierre de inscripción no puede ser anterior a la apertura.' },
  TERM_NOT_FOUND: { field: 'termId', message: 'El período académico elegido ya no existe: elegí otro.' },
  OFFERING_LIST_PRICE_NOT_POSITIVE: { message: 'El precio de la cuota y de la matrícula es obligatorio y debe ser mayor que cero. Para ofrecerlo gratis, usá un descuento.' },
  VALIDATION_FAILED: { message: 'El servidor rechazó los datos: revisá que los números sean enteros y no negativos.' },
};

export function describeSaveError(err, fallback) {
  const known = SAVE_ERRORS[err?.code];
  if (known) return known;
  if (err?.status === 409) return { message: 'Ya existe un registro con esos datos. Revisá el nombre.' };
  if (err?.status === 403) return { message: 'No tenés permiso para guardar estos cambios.' };
  return { message: fallback };
}
