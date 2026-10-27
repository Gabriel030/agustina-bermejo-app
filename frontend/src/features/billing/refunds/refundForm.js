export const ACCOUNT_MAX_LENGTH = 50;
export const HOLDER_MAX_LENGTH = 120;
export const PREVIEW_DEBOUNCE_MS = 400;

const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;
const MAX_AMOUNT_CENTS = 999999999999;

export function parseAmountInput(text) {
  const normalized = String(text ?? '')
    .replace(/[\s$]/g, '')
    .replace(/\./g, '')
    .replace(',', '.');
  if (!AMOUNT_PATTERN.test(normalized)) return null;
  const cents = Math.round(Number(normalized) * 100);
  if (cents <= 0 || cents > MAX_AMOUNT_CENTS) return null;
  return (cents / 100).toFixed(2);
}

export function toCents(money) {
  return money ? Math.round(Number(money.amount) * 100) : 0;
}

export function fromCents(cents, currency) {
  return { amount: (cents / 100).toFixed(2), currency };
}

export function refundableNowCap(eligibility) {
  const sumOfPayments = eligibility.payments.reduce((total, payment) => total + toCents(payment.refundableNow), 0);
  return fromCents(Math.max(0, Math.min(toCents(eligibility.availableToRefund), sumOfPayments)), eligibility.currency);
}

export function describeRefundBlock(eligibility) {
  if (eligibility.hasOverdueDebt || eligibility.blockedBy?.code === 'REFUND_BLOCKED_BY_OVERDUE_DEBT') {
    return {
      tone: 'danger',
      title: 'Tiene deuda vencida',
      description: eligibility.blockedBy?.message ?? 'Mientras haya deuda vencida no se puede pedir un reembolso.',
    };
  }
  if (eligibility.blockedBy) {
    return { tone: 'danger', title: 'No se puede pedir un reembolso', description: eligibility.blockedBy.message };
  }
  if (toCents(refundableNowCap(eligibility)) <= 0) {
    return {
      tone: 'neutral',
      title: 'No tiene saldo a favor disponible',
      description: 'El saldo a favor está reservado en otros reembolsos, es cero o viene de pagos que no se pueden devolver.',
    };
  }
  return null;
}

export function validateRefundForm({ amount, account, holder }) {
  const errors = {};
  if (!amount.trim()) errors.amount = 'Ingresá el monto a devolver.';
  else if (!parseAmountInput(amount)) errors.amount = 'Ingresá un monto mayor a cero, con hasta dos decimales.';
  if (!account.trim()) errors.account = 'Indicá el CBU o alias de destino.';
  else if (account.trim().length > ACCOUNT_MAX_LENGTH) errors.account = `Máximo ${ACCOUNT_MAX_LENGTH} caracteres.`;
  if (!holder.trim()) errors.holder = 'Indicá el titular de la cuenta.';
  else if (holder.trim().length > HOLDER_MAX_LENGTH) errors.holder = `Máximo ${HOLDER_MAX_LENGTH} caracteres.`;
  return errors;
}

export const AMOUNT_CODES = ['REFUND_AMOUNT_INVALID', 'INSUFFICIENT_ACCOUNT_CREDIT', 'REFUND_EXCEEDS_AVAILABLE_AMOUNT'];
export const DESTINATION_CODES = ['REFUND_DESTINATION_REQUIRED', 'REFUND_DESTINATION_INVALID'];

export const REQUEST_REFUND_ERRORS = {
  REFUND_AMOUNT_INVALID: 'El monto tiene que ser mayor a cero y no superar 9.999.999.999,99.',
  INSUFFICIENT_ACCOUNT_CREDIT: 'El alumno no tiene saldo a favor suficiente para este monto. Actualizamos el máximo.',
  REFUND_EXCEEDS_AVAILABLE_AMOUNT: 'El monto supera lo que se puede devolver ahora. Actualizamos el máximo.',
  REFUND_BLOCKED_BY_OVERDUE_DEBT: 'No se puede devolver saldo a favor mientras haya deuda vencida.',
  REFUND_DESTINATION_REQUIRED: 'Indicá el CBU o alias y el titular de la cuenta a la que se devuelve el dinero.',
  REFUND_DESTINATION_INVALID: `El CBU o alias tiene hasta ${ACCOUNT_MAX_LENGTH} caracteres y el titular hasta ${HOLDER_MAX_LENGTH}.`,
};

export function requestRefundErrorMessage(error) {
  return REQUEST_REFUND_ERRORS[error?.code] ?? error?.message ?? 'No pudimos pedir el reembolso. Probá de nuevo.';
}
