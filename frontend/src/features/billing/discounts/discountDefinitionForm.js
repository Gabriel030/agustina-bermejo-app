import { todayIsoDate } from '@/lib/billing';

export const NAME_MAX_LENGTH = 200;
export const CURRENCY = 'ARS';
export const VALIDATION_DEBOUNCE_MS = 450;

export const SAMPLE_PRICES = {
  MONTHLY_INSTALLMENT: 550000,
  ENROLLMENT_FEE: 320000,
  RETAKEN_SUBJECT_FEE: 90000,
  ANNUAL_UPFRONT: 6000000,
};

export const SCHOLARSHIP_TYPE_HINT = 'Una plantilla: cada alumno recibe su propio porcentaje.';
export const ANNUAL_TYPE_HINT = 'Solo para la factura de pago anual.';

export function initialFormValues(mode) {
  const promotion = mode === 'promotion';
  return {
    name: '',
    discountType: promotion ? 'COHORT_PROMOTION' : 'ANNUAL_UPFRONT',
    applicableInvoiceType: promotion ? 'MONTHLY_INSTALLMENT' : 'ANNUAL_UPFRONT',
    calcKind: 'PERCENT',
    value: '',
    installmentsTotal: '',
    availableFrom: todayIsoDate(),
    availableUntil: '',
    priority: '1',
    capPerInvoice: '',
    scopeKind: 'PROGRAM',
    scopeId: '',
    cohortId: '',
    enrollmentWindowFrom: '',
    enrollmentWindowTo: '',
    quota: '',
  };
}

export function appliesOptionsFor(discountType, promotionOptions) {
  if (discountType === 'SCHOLARSHIP') return ['MONTHLY_INSTALLMENT'];
  if (discountType === 'ANNUAL_UPFRONT') return ['ANNUAL_UPFRONT'];
  return promotionOptions;
}

export function isPromotion(values) {
  return values.discountType === 'COHORT_PROMOTION';
}

export function hasCalculation(values) {
  return values.discountType !== 'SCHOLARSHIP';
}

export function supportsInstallments(values) {
  return isPromotion(values) && values.applicableInvoiceType === 'MONTHLY_INSTALLMENT';
}

export function applyFieldChange(values, field, value) {
  const next = { ...values, [field]: value };
  if (field === 'discountType') {
    next.applicableInvoiceType = appliesOptionsFor(value, ['MONTHLY_INSTALLMENT'])[0];
    if (value === 'SCHOLARSHIP') {
      next.value = '';
      next.installmentsTotal = '';
    }
  }
  if (field === 'applicableInvoiceType' && value !== 'MONTHLY_INSTALLMENT') next.installmentsTotal = '';
  if (field === 'scopeKind') {
    next.scopeId = '';
    next.cohortId = '';
  }
  if (field === 'scopeId') next.cohortId = '';
  return next;
}

function toNumber(text) {
  const normalized = String(text).trim().replace(',', '.');
  return normalized === '' ? Number.NaN : Number(normalized);
}

function isPositiveInteger(text) {
  const number = toNumber(text);
  return Number.isInteger(number) && number >= 1;
}

export function validateDefinitionForm(values) {
  const errors = {};
  const cross = [];
  if (!values.name.trim() || values.name.length > NAME_MAX_LENGTH) {
    errors.name = `El nombre es obligatorio y admite hasta ${NAME_MAX_LENGTH} caracteres.`;
  }
  if (hasCalculation(values)) {
    const value = toNumber(values.value);
    if (!(value > 0)) errors.value = 'El porcentaje o el monto debe ser mayor a cero.';
    else if (values.calcKind === 'PERCENT' && value > 100) errors.value = 'El porcentaje va de 0 a 100.';
  }
  if (supportsInstallments(values) && values.installmentsTotal !== '' && !isPositiveInteger(values.installmentsTotal)) {
    errors.installmentsTotal = 'La cantidad de cuotas es de al menos 1.';
  }
  if (!values.availableFrom) errors.availableFrom = 'La fecha de inicio es obligatoria.';
  if (values.availableUntil && values.availableFrom && values.availableUntil < values.availableFrom) {
    errors.availableUntil = 'La disponibilidad hasta no puede ser anterior a la disponibilidad desde.';
  }
  if (!isPositiveInteger(values.priority)) errors.priority = 'La prioridad empieza en 1.';
  if (values.capPerInvoice !== '' && !(toNumber(values.capPerInvoice) > 0)) {
    errors.capPerInvoice = 'El tope por factura debe ser mayor a cero.';
  }
  if (isPromotion(values)) {
    if (!values.scopeId) errors.scopeId = values.scopeKind === 'PROGRAM' ? 'Elegí la carrera.' : 'Elegí el curso.';
    if (!values.enrollmentWindowFrom) errors.enrollmentWindowFrom = 'La ventana de inscripción es obligatoria.';
    if (!values.enrollmentWindowTo) errors.enrollmentWindowTo = 'La ventana de inscripción es obligatoria.';
    if (values.enrollmentWindowFrom && values.enrollmentWindowTo && values.enrollmentWindowTo < values.enrollmentWindowFrom) {
      errors.enrollmentWindowTo = 'La ventana de inscripción termina antes de empezar.';
    }
    if (!isPositiveInteger(values.quota)) errors.quota = 'El cupo es de al menos una persona.';
    if (values.enrollmentWindowTo && values.availableUntil && values.enrollmentWindowTo > values.availableUntil) {
      cross.push('La ventana de inscripción termina después de la disponibilidad de la promoción: los alumnos que se inscriban al final no llegarían a usarlo.');
    }
  }
  return { errors, cross };
}

function buildCalculation(values) {
  if (!hasCalculation(values)) return null;
  const value = toNumber(values.value);
  if (values.calcKind === 'PERCENT') return { type: 'PROPORTIONAL', ratePercent: value };
  return { type: 'NOMINAL', amount: { amount: value.toFixed(2), currency: CURRENCY } };
}

export function buildDefinitionPayload(values) {
  const promotion = isPromotion(values);
  return {
    name: values.name.trim(),
    discountType: values.discountType,
    applicableInvoiceType: values.applicableInvoiceType,
    calculation: buildCalculation(values),
    installmentsTotal: supportsInstallments(values) && values.installmentsTotal !== '' ? toNumber(values.installmentsTotal) : null,
    availableFrom: values.availableFrom,
    availableUntil: values.availableUntil || null,
    priority: toNumber(values.priority),
    capPerInvoice: values.capPerInvoice !== '' ? { amount: toNumber(values.capPerInvoice).toFixed(2), currency: CURRENCY } : null,
    programId: promotion && values.scopeKind === 'PROGRAM' ? Number(values.scopeId) : null,
    programCohortId: promotion && values.scopeKind === 'PROGRAM' && values.cohortId ? Number(values.cohortId) : null,
    courseId: promotion && values.scopeKind === 'COURSE' ? Number(values.scopeId) : null,
    enrollmentWindowFrom: promotion ? values.enrollmentWindowFrom : null,
    enrollmentWindowTo: promotion ? values.enrollmentWindowTo : null,
    quota: promotion ? toNumber(values.quota) : null,
  };
}

export function sampleOutcome(values) {
  if (!hasCalculation(values)) return null;
  const value = toNumber(values.value);
  if (!(value > 0) || (values.calcKind === 'PERCENT' && value > 100)) return null;
  const price = SAMPLE_PRICES[values.applicableInvoiceType] ?? SAMPLE_PRICES.MONTHLY_INSTALLMENT;
  const raw = values.calcKind === 'PERCENT' ? (price * value) / 100 : value;
  const cap = values.capPerInvoice !== '' ? toNumber(values.capPerInvoice) : null;
  const capped = cap != null && cap > 0 && raw > cap;
  const discount = Math.min(raw, cap > 0 ? cap : raw, price);
  return { price, discount, capped, total: price - discount };
}

export function definitionErrorMessage(error) {
  if (error?.status === 404) return 'La carrera, el curso o la cohorte elegidos ya no existen. Actualizá la pantalla.';
  if (error?.status === 400) return 'Revisá los datos: alguna regla del catálogo no se cumple.';
  return 'No pudimos guardar. Probá de nuevo.';
}
