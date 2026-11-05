import { CURRENCY } from './discountDefinitionForm';
import { formatMonth, formatMonthRange } from './discountLabels';

export const REASON_MAX_LENGTH = 450;
export const PREVIEW_DEBOUNCE_MS = 450;

export function currentMonth(now = new Date()) {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

export function shiftMonth(month, delta) {
  const [year, number] = month.split('-').map(Number);
  const index = year * 12 + number - 1 + delta;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`;
}

export function monthsBetween(from, to) {
  const [fromYear, fromMonth] = from.split('-').map(Number);
  const [toYear, toMonth] = to.split('-').map(Number);
  return (toYear - fromYear) * 12 + (toMonth - fromMonth) + 1;
}

export function initialGrantValues(now) {
  const month = currentMonth(now);
  return {
    type: 'INDIVIDUAL',
    definitionId: '',
    invoiceType: 'MONTHLY_INSTALLMENT',
    calcKind: 'FIXED',
    value: '',
    fromPeriod: month,
    toPeriod: month,
    reason: '',
  };
}

export function isMonthly(values) {
  return values.invoiceType === 'MONTHLY_INSTALLMENT';
}

export function applyGrantChange(values, field, value) {
  const next = { ...values, [field]: value };
  if (field === 'type' && value === 'SCHOLARSHIP') next.invoiceType = 'MONTHLY_INSTALLMENT';
  if (field === 'type' && value === 'INDIVIDUAL') next.definitionId = '';
  if (field === 'fromPeriod' && next.toPeriod < value) next.toPeriod = value;
  return next;
}

function toNumber(text) {
  const normalized = String(text).trim().replace(',', '.');
  return normalized === '' ? Number.NaN : Number(normalized);
}

export function validateGrantValues(values) {
  const errors = {};
  const value = toNumber(values.value);
  if (!(value > 0)) errors.value = 'El porcentaje o el monto debe ser mayor a cero.';
  else if (values.calcKind === 'PERCENT' && value > 100) errors.value = 'El porcentaje va de 0 a 100.';
  if (values.type === 'SCHOLARSHIP' && !values.definitionId) errors.definitionId = 'Elegí la beca del catálogo.';
  if (!values.fromPeriod) errors.fromPeriod = 'Elegí el mes.';
  if (isMonthly(values) && values.toPeriod < values.fromPeriod) errors.toPeriod = 'La cuota final no puede ser anterior a la inicial.';
  if (!values.reason.trim() || values.reason.length > REASON_MAX_LENGTH) {
    errors.reason = `El motivo es obligatorio y admite hasta ${REASON_MAX_LENGTH} caracteres.`;
  }
  return errors;
}

export function buildGrantPayload(values) {
  const value = toNumber(values.value);
  return {
    type: values.type,
    discountDefinitionId: values.type === 'SCHOLARSHIP' ? Number(values.definitionId) : null,
    percent: values.calcKind === 'PERCENT' ? value : null,
    amount: values.calcKind === 'FIXED' ? { amount: value.toFixed(2), currency: CURRENCY } : null,
    invoiceType: values.invoiceType,
    fromPeriod: values.fromPeriod,
    toPeriod: isMonthly(values) ? values.toPeriod : null,
    reason: values.reason.trim() || null,
  };
}

export function previewReady(values) {
  const errors = validateGrantValues({ ...values, reason: 'x' });
  return Object.keys(errors).length === 0;
}

export function describeGrantEffect(preview, values) {
  const lines = [];
  if (!preview) return lines;
  if (values.invoiceType === 'MONTHLY_INSTALLMENT') {
    const covered = preview.coveredPeriods;
    lines.push({
      tone: 'success',
      text: `Rige desde la cuota de ${formatMonth(preview.fromPeriod)} hasta ${formatMonth(preview.toPeriod)}${covered ? ` (${covered} ${covered === 1 ? 'cuota' : 'cuotas'})` : ''}. Las cuotas ya emitidas no cambian.`,
    });
    lines.push({ tone: 'warning', text: 'Si en un mes otro descuento ahorra más, ese mes se pierde y no se corre.' });
  } else if (values.invoiceType === 'ENROLLMENT_FEE') {
    lines.push({ tone: 'success', text: `Solo la primera matrícula del año lectivo de ${formatMonth(preview.fromPeriod)} que se emita lleva el descuento. La segunda paga completa.` });
    lines.push({ tone: 'warning', text: 'Si otro descuento gana esa matrícula, este no se usa ese año.' });
  } else {
    lines.push({ tone: 'success', text: `Rige para la factura del período ${formatMonthRange(preview.fromPeriod, preview.toPeriod)}.` });
  }
  if (preview.annualUpfrontCoversRange) {
    lines.push({ tone: 'warning', text: 'El pago anual confirmado cubre ese rango: el descuento no cambia lo que se paga.' });
  }
  return lines;
}

export function grantErrorMessage(error) {
  if (error?.status === 404) return 'La inscripción ya no existe. Volvé al legajo.';
  return 'No pudimos otorgar el descuento. Probá de nuevo.';
}
