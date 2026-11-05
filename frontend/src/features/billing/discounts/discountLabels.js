import { formatMoney } from '@/lib/billing';

export const DEFINITION_TYPE_LABELS = {
  SCHOLARSHIP: 'Beca',
  COHORT_PROMOTION: 'Promoción de cohorte',
  ANNUAL_UPFRONT: 'Pago anual',
  PAYMENT_METHOD: 'Medio de pago',
  COHORT_ENTRY: 'Ingreso de cohorte',
};

export const CATALOG_DISCOUNT_TYPES = ['SCHOLARSHIP', 'ANNUAL_UPFRONT'];

export const APPLIES_TO_LABELS = {
  MONTHLY_INSTALLMENT: 'Cuota mensual',
  ENROLLMENT_FEE: 'Matrícula',
  ANNUAL_UPFRONT: 'Pago anual',
  EXAM_FEE: 'Examen final',
  RETAKEN_SUBJECT_FEE: 'Materia recursada',
  DEGREE_ISSUANCE_FEE: 'Emisión de título',
  ADMINISTRATIVE_FEE: 'Trámite administrativo',
};

export const PROMOTION_APPLIES_TO = ['MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE', 'RETAKEN_SUBJECT_FEE'];
export const INDIVIDUAL_APPLIES_TO = [
  'MONTHLY_INSTALLMENT',
  'ENROLLMENT_FEE',
  'ANNUAL_UPFRONT',
  'EXAM_FEE',
  'RETAKEN_SUBJECT_FEE',
  'DEGREE_ISSUANCE_FEE',
  'ADMINISTRATIVE_FEE',
];

export const APPLIED_TYPE_LABELS = {
  SCHOLARSHIP: 'Beca',
  PROMOTION: 'Promoción',
  COHORT_PROMOTION: 'Promoción de cohorte',
  ANNUAL_UPFRONT: 'Pago anual',
  COHORT_ENTRY: 'Ingreso de cohorte',
  INDIVIDUAL: 'Descuento individual',
};

export const COHORT_PROMOTION_STATUS = {
  UPCOMING: { label: 'Próxima', tone: 'accent' },
  OPEN: { label: 'Abierta', tone: 'success' },
  QUOTA_FULL: { label: 'Cupo completo', tone: 'warning' },
  ENROLLMENT_CLOSED: { label: 'Inscripción cerrada', tone: 'warning' },
  FINISHED: { label: 'Finalizada', tone: 'neutral' },
  CLOSED: { label: 'Cerrada', tone: 'neutral' },
};

export const STUDENT_DISCOUNT_STATUS = {
  ACTIVE: { label: 'Vigente', tone: 'success' },
  EXHAUSTED: { label: 'Agotado', tone: 'neutral' },
  ENDED: { label: 'Finalizado', tone: 'neutral' },
  REVOKED: { label: 'Revocado', tone: 'danger' },
};

export const DEFINITION_STATUS = {
  ACTIVE: { label: 'Vigente', tone: 'success' },
  UPCOMING: { label: 'Próxima', tone: 'accent' },
  EXPIRED: { label: 'Vencida', tone: 'neutral' },
  CLOSED: { label: 'Cerrada', tone: 'neutral' },
};

export function definitionStatusKey(entry) {
  if (entry.status === 'CLOSED') return 'CLOSED';
  if (entry.availability === 'UPCOMING') return 'UPCOMING';
  if (entry.availability === 'EXPIRED') return 'EXPIRED';
  return 'ACTIVE';
}

export function appliesToLabel(invoiceType) {
  return APPLIES_TO_LABELS[invoiceType] ?? invoiceType;
}

export function typeLabel(type) {
  return DEFINITION_TYPE_LABELS[type] ?? type;
}

export function formatPercent(points) {
  return `${Number(points).toLocaleString('es-AR', { maximumFractionDigits: 2 })} %`;
}

export function formatCalculation(calculation) {
  if (!calculation) return 'Lo fija cada otorgamiento';
  if (calculation.type === 'PROPORTIONAL') return formatPercent(calculation.ratePercent);
  return `${formatMoney(calculation.amount)} fijo`;
}

export function formatCalculationWithInstallments(entry) {
  const base = formatCalculation(entry.calculation);
  if (entry.installmentsTotal == null) return base;
  return `${base} · ${entry.installmentsTotal} ${entry.installmentsTotal === 1 ? 'cuota' : 'cuotas'}`;
}

export function formatQuota(entry) {
  if (entry.quota == null) return null;
  return `${entry.quotaUsed ?? 0} de ${entry.quota}`;
}

export function formatIsoDate(value) {
  if (!value) return '';
  const [year, month, day] = value.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

export function formatRange(from, until) {
  return `${formatIsoDate(from)} – ${until ? formatIsoDate(until) : 'sin fin'}`;
}

const MONTH_NAMES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

export function formatMonth(yearMonth) {
  if (!yearMonth) return '';
  const [year, month] = yearMonth.split('-');
  return `${MONTH_NAMES[Number(month) - 1]} ${year}`;
}

export function formatMonthRange(from, to) {
  if (!from) return '';
  if (!to || from === to) return formatMonth(from);
  return `${formatMonth(from)} – ${formatMonth(to)}`;
}

export function isAwaitingAuthorization(result) {
  return result?.id == null && result?.authorizationRequestId != null;
}
