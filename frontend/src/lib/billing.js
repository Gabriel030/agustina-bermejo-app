import { formatMoney as formatMoneyOrEmpty, moneyValue } from '@/lib/utils';

export const FEE_TYPE_LABELS = {
  MONTHLY_INSTALLMENT: 'Cuota mensual',
  ENROLLMENT_FEE: 'Matrícula',
  EXAM_FEE: 'Derecho a examen',
  RETAKEN_SUBJECT_FEE: 'Recurse',
  DEGREE_ISSUANCE_FEE: 'Emisión de título',
  ADMINISTRATIVE_FEE: 'Gestión administrativa',
  LATE_FEE: 'Recargo por mora',
  ANNUAL_UPFRONT: 'Pago anual',
};

export const INVOICE_PAYMENT_STATUS_CONFIG = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  PAYMENT_UNDER_REVIEW: { label: 'En revisión', tone: 'neutral' },
  OVERDUE: { label: 'Vencida', tone: 'danger' },
  PAID: { label: 'Pagada', tone: 'success' },
  CANCELLED: { label: 'Cuota sin efecto', tone: 'neutral' },
};

export const INVOICE_DOCUMENT_STATUS_LABELS = {
  DRAFT: 'Borrador',
  AWAITING_PAYMENT: 'A la espera del pago',
  ISSUED: 'Emitida',
  DISCARDED: 'Factura anulada',
};

const PAYABLE_PAYMENT_STATUSES = ['PENDING', 'OVERDUE'];

export function isInvoicePayable(invoice) {
  return invoice.documentStatus === 'ISSUED' && PAYABLE_PAYMENT_STATUSES.includes(invoice.paymentStatus);
}

export function hasPendingReceipt(invoice, latestReceipt) {
  if (latestReceipt) return latestReceipt.status === 'PENDING_REVIEW';
  return (invoice.receipts ?? []).some((receipt) => receipt.status === 'PENDING_REVIEW');
}

export function invoiceDisplayStatus(invoice, latestReceipt) {
  if (invoice.paymentStatus === 'PAYMENT_UNDER_REVIEW') return 'PAYMENT_UNDER_REVIEW';
  if (isInvoicePayable(invoice) && hasPendingReceipt(invoice, latestReceipt)) return 'PAYMENT_UNDER_REVIEW';
  return invoice.paymentStatus;
}

export function invoiceStatusInfo(status) {
  return INVOICE_PAYMENT_STATUS_CONFIG[status] ?? { label: status, tone: 'neutral' };
}

export function discountLabel(discount) {
  const name = discount.discountName || ADJUSTMENT_TYPE_LABELS[discount.discountSource] || discount.discountSource;
  if (discount.installmentNumber == null) return name;
  const progress = discount.installmentsTotal == null
    ? `cuota ${discount.installmentNumber}`
    : `cuota ${discount.installmentNumber} de ${discount.installmentsTotal}`;
  return `${name} (${progress})`;
}

export const ADJUSTMENT_TYPE_LABELS = {
  SCHOLARSHIP: 'Beca',
  PROMOTION: 'Promoción',
  ANNUAL_UPFRONT: 'Pago anual',
  COHORT_ENTRY: 'Ingreso de cohorte',
  MANUAL: 'Manual',
  DISCOUNT: 'Descuento',
};

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export function formatPeriod(period) {
  if (!period) return '-';
  const [year, month] = period.split('-');
  return `${MONTHS[parseInt(month, 10) - 1]} ${year}`;
}

export function formatDate(value) {
  if (!value) return '';
  const [year, month, day] = value.split('T')[0].split('-');
  return `${day}/${month}/${year}`;
}

export function formatDateTime(value) {
  if (!value) return '';
  const time = value.includes('T') ? value.split('T')[1].slice(0, 5) : '';
  return time ? `${formatDate(value)} ${time}` : formatDate(value);
}

export const PAYMENT_METHOD_LABELS = {
  CASH: 'Efectivo',
  TRANSFER: 'Transferencia bancaria',
  CARD: 'Tarjeta',
  MERCADO_PAGO: 'Mercado Pago',
  CREDIT_BALANCE: 'Saldo a favor',
};

const CARD_TYPE_LABELS = {
  CREDIT: 'Tarjeta de crédito',
  DEBIT: 'Tarjeta de débito',
};

export function paymentMethodLabel(method, cardType, cardInstallments) {
  const parts = [PAYMENT_METHOD_LABELS[method] ?? method];
  if (cardType) {
    parts.push(CARD_TYPE_LABELS[cardType]);
  }
  if (cardType !== 'DEBIT' && cardInstallments) {
    parts.push(cardInstallments === 1 ? '1 cuota' : `${cardInstallments} cuotas`);
  }
  return parts.join(' · ');
}

export const RECEIPT_STATUS_CONFIG = {
  PENDING_REVIEW: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  APPLIED_TO_DEBT: { label: 'Aprobado, aplicado a deuda anterior', tone: 'warning' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
};

export function hasInvoiceDetail(invoice) {
  return (
    (invoice.payments?.length ?? 0) > 0 ||
    (invoice.receipts?.length ?? 0) > 0 ||
    (invoice.lines ?? []).some((line) => line.discount != null) ||
    (invoice.lines ?? []).length > 1 ||
    invoice.documentStatus === 'DISCARDED'
  );
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

export function invoicePrimaryPeriod(invoice) {
  return invoice.lines?.[0]?.period ?? null;
}

export function invoiceTitle(invoice) {
  return invoiceConcept(invoice.invoiceType, invoicePrimaryPeriod(invoice));
}

export function negatedMoney(money) {
  return { amount: (-moneyValue(money)).toFixed(2), currency: money.currency };
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
