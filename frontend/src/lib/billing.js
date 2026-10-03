import { formatMoney as formatMoneyOrEmpty, moneyValue } from '@/lib/utils';

export const FEE_TYPE_LABELS = {
  RECURRING_FEE: 'Cuota mensual',
  ENROLLMENT_FEE: 'Matrícula',
  SUBSCRIPTION_FEE: 'Cuota anual',
  EXAM_RIGHT: 'Derecho a examen',
  RETAKEN_SUBJECT_FEE: 'Recurse',
  DEGREE_ISSUANCE: 'Emisión de título',
  ADMINISTRATIVE_FEE: 'Gestión administrativa',
};

export const ADJUSTMENT_TYPE_LABELS = {
  SCHOLARSHIP: 'Beca',
  PROMOTION: 'Promoción',
  DISCOUNT: 'Descuento',
  SURCHARGE: 'Recargo por mora',
  WAIVE_DEBT: 'Condonación de deuda',
};

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export function formatPeriod(period) {
  if (!period) return '-';
  const [year, month] = period.split('-');
  return `${MONTHS[parseInt(month, 10) - 1]} ${year}`;
}

export function formatMoney(money) {
  return formatMoneyOrEmpty(money) || '-';
}

export function formatSignedMoney(money) {
  const value = moneyValue(money);
  const sign = value < 0 ? '−' : '+';
  return `${sign} ${formatMoney({ amount: Math.abs(value).toFixed(2), currency: money.currency })}`;
}

export function invoiceConcept(feeType, period) {
  const label = FEE_TYPE_LABELS[feeType] ?? feeType;
  return period ? `${label} · ${formatPeriod(period)}` : label;
}

export function signedAdjustmentAmount(adjustment) {
  const value = moneyValue(adjustment.resolvedAmount);
  const signed = adjustment.type === 'SURCHARGE' ? value : -value;
  return { amount: signed.toFixed(2), currency: adjustment.resolvedAmount.currency };
}

export const RECEIPT_MAX_BYTES = 5 * 1024 * 1024;

const RECEIPT_ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];

export function receiptFileError(file) {
  if (!RECEIPT_ALLOWED_TYPES.includes(file.type)) {
    return 'El comprobante tiene que ser un PDF, JPG o PNG.';
  }
  if (file.size > RECEIPT_MAX_BYTES) {
    return 'El archivo pesa más de 5 MB. Subí uno más liviano.';
  }
  return null;
}

const RECEIPT_UPLOAD_ERRORS = {
  PAYMENT_RECEIPT_ALREADY_PENDING: 'Ya enviaste un comprobante para esta cuota y está en revisión.',
  UNSUPPORTED_FILE_TYPE: 'El comprobante tiene que ser un PDF, JPG o PNG.',
  MAX_UPLOAD_SIZE_EXCEEDED: 'El archivo pesa más de 5 MB. Subí uno más liviano.',
  INVOICE_NOT_PAYABLE: 'Esta cuota ya no admite pagos: puede que ya esté saldada.',
};

export function receiptUploadErrorMessage(error) {
  return RECEIPT_UPLOAD_ERRORS[error?.code] ?? error?.message ?? 'No pudimos enviar el comprobante. Probá de nuevo.';
}
