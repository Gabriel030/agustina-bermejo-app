import { moneyValue } from '@/lib/utils';

export const REQUEST_STATUS = {
  AWAITING_PAYMENT: { label: 'Esperando el pago', tone: 'warning' },
  PAYMENT_UNDER_REVIEW: { label: 'Pago en revisión', tone: 'warning' },
  CONFIRMED: { label: 'Año confirmado', tone: 'success' },
  DISCARDED: { label: 'Descartado', tone: 'neutral' },
};

export const DISCARD_REASON_LABELS = {
  DEADLINE_EXPIRED: 'Venció el plazo para pagar',
  PAYMENT_REJECTED: 'Rechazamos el comprobante',
  WITHDRAWAL: 'Baja de la inscripción',
  CANCELLED_BY_STUDENT: 'Descartado por el alumno',
};

export const INELIGIBLE_REASONS = {
  NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT: { state: 'UNAVAILABLE', label: 'Ya no disponible', tone: 'neutral' },
  ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP: { state: 'SCHOLARSHIP', label: 'Con beca', tone: 'neutral' },
  OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT: { state: 'DEBT', label: 'Con deuda', tone: 'danger' },
  ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED: { state: 'WINDOW_CLOSED', label: 'Fuera de fecha', tone: 'neutral' },
  NO_EFFECTIVE_PRICE_FOR_ANNUAL_UPFRONT: { state: 'NO_PROMOTION', label: 'Sin promoción', tone: 'neutral' },
};

export function liveRequestOf(requests) {
  return (requests ?? []).find((request) => request.status !== 'DISCARDED') ?? null;
}

export function lastDiscardedRequestOf(requests) {
  const discarded = (requests ?? []).filter((request) => request.status === 'DISCARDED');
  return discarded.reduce((latest, request) => (!latest || request.id > latest.id ? request : latest), null);
}

export function installmentsSummary(lines) {
  if (!lines?.length) return null;
  const first = lines[0].finalAmount;
  const uniform = lines.every((line) => moneyValue(line.finalAmount) === moneyValue(first));
  return { count: lines.length, amount: uniform ? first : null };
}

export function periodLabel(period) {
  const [year, month] = String(period).split('-');
  const date = new Date(Number(year), Number(month) - 1, 1);
  return new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric' }).format(date);
}

export function isAnnualUpfrontActionable(eligibility, requests) {
  const live = liveRequestOf(requests);
  if (live) return live.status === 'AWAITING_PAYMENT';
  return Boolean(eligibility?.eligible && eligibility?.quote);
}
