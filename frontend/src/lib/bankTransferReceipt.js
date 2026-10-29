import { formatMoney, invoiceTitle, isInvoicePayable } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';

export const CBU_PATTERN = /^\d{22}$/;
export const ALIAS_PATTERN = /^[a-zA-Z0-9.-]{6,20}$/;

export function todayIsoDate(now = new Date()) {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

export function toSelectableInvoice(invoice) {
  return {
    id: invoice.id,
    title: invoiceTitle(invoice),
    dueDate: invoice.dueDate ?? null,
    outstanding: invoice.outstandingAmount,
    paymentStatus: invoice.paymentStatus,
  };
}

export function orderedPayableInvoices(invoices) {
  const byDueDate = (a, b) => {
    if (a.dueDate !== b.dueDate) {
      if (!a.dueDate) return 1;
      if (!b.dueDate) return -1;
      return a.dueDate < b.dueDate ? -1 : 1;
    }
    return a.id - b.id;
  };
  const unique = new Map();
  for (const invoice of invoices) unique.set(invoice.id, invoice);
  const all = [...unique.values()];
  return {
    selectable: all
      .filter((invoice) => isInvoicePayable(invoice))
      .map(toSelectableInvoice)
      .sort(byDueDate),
    underReview: all
      .filter((invoice) => invoice.paymentStatus === 'PAYMENT_UNDER_REVIEW')
      .map(toSelectableInvoice)
      .sort(byDueDate),
  };
}

export function parseAmountInput(text) {
  const cleaned = String(text ?? '').replace(/[^\d.,]/g, '');
  if (!cleaned) return NaN;
  const hasComma = cleaned.includes(',');
  let normalized;
  if (hasComma) {
    normalized = cleaned.replace(/\./g, '').replace(',', '.');
  } else if (/^\d{1,3}(\.\d{3})+$/.test(cleaned)) {
    normalized = cleaned.replace(/\./g, '');
  } else {
    normalized = cleaned;
  }
  return Number(normalized);
}

export function formatAmountInput(value) {
  if (!Number.isFinite(value)) return '';
  return value.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function selectedTotal(invoices, count) {
  return invoices.slice(0, count).reduce((sum, invoice) => sum + moneyValue(invoice.outstanding), 0);
}

export function coverageOf(invoices, amount) {
  let remaining = amount;
  let covering = true;
  return invoices.map((invoice) => {
    const outstanding = moneyValue(invoice.outstanding);
    if (covering && remaining >= outstanding) {
      remaining -= outstanding;
      return { state: 'covered', missing: 0 };
    }
    const missing = covering && remaining > 0 ? outstanding - remaining : outstanding;
    const state = covering && remaining > 0 ? 'short' : 'none';
    covering = false;
    return { state, missing };
  });
}

export function receiptOutcomeLines({ invoices, count, amount, currency, audience = 'student' }) {
  const staff = audience === 'staff';
  const selected = invoices.slice(0, count);
  const coverage = coverageOf(selected, amount);
  const total = selectedTotal(invoices, count);
  const coveredRows = selected.filter((_, index) => coverage[index].state === 'covered');
  const lines = [];

  if (!(amount > 0) || coveredRows.length === 0) {
    lines.push({
      tone: 'warning',
      text: staff
        ? 'El monto no alcanza a cubrir la primera cuota. Corregí el monto.'
        : selected.length === 1
          ? 'El monto no alcanza a cubrir la cuota. Subí el monto.'
          : 'El monto no alcanza a cubrir la primera cuota. Subí el monto.',
    });
  } else if (coveredRows.length < selected.length) {
    lines.push({
      tone: 'warning',
      text: `${coveredRows.length} de ${selected.length} cuotas quedan en revisión. Las demás siguen pendientes.`,
    });
  } else {
    lines.push({
      tone: 'success',
      text: selected.length === 1 ? 'La cuota queda en revisión.' : `Las ${selected.length} cuotas quedan en revisión.`,
    });
  }

  const excess = amount - total;
  if (Number.isFinite(excess) && excess > 0.004) {
    lines.push({
      tone: 'warning',
      text: staff
        ? `Se transfirieron ${formatMoney({ amount: excess.toFixed(2), currency })} de más. Quedan como saldo a favor del alumno.`
        : `Transferiste ${formatMoney({ amount: excess.toFixed(2), currency })} de más. Queda a tu favor y se aplica a tu próxima cuota.`,
    });
  }

  const protectedRow = coveredRows.find((invoice) => invoice.paymentStatus === 'PENDING');
  if (protectedRow) {
    lines.push({
      tone: 'success',
      text: `${staff ? 'Cargado' : 'Subido'} a tiempo: ${protectedRow.title} no genera recargo mientras se revisa.`,
    });
  }
  if (coveredRows.some((invoice) => invoice.paymentStatus === 'OVERDUE')) {
    lines.push({
      tone: 'neutral',
      text: staff
        ? 'Las cuotas ya vencidas siguen vencidas y con recargo hasta que se apruebe.'
        : 'Las cuotas ya vencidas siguen vencidas hasta que se apruebe.',
    });
  }
  lines.push({
    tone: 'neutral',
    text: staff
      ? 'El comprobante queda pendiente en la pestaña Comprobantes. No se aprueba solo.'
      : 'Te avisamos cuando la revisen. Suele tardar 1 o 2 días hábiles.',
  });
  return lines;
}

export function receiptResultRows(invoices, amount) {
  const coverage = coverageOf(invoices, amount);
  return invoices.map((invoice, index) => {
    let outcome = 'uncovered';
    if (coverage[index].state === 'covered') outcome = invoice.paymentStatus === 'PENDING' ? 'protected' : 'overdue';
    return { id: invoice.id, title: invoice.title, dueDate: invoice.dueDate, outcome };
  });
}

export function receiptFormErrors({ file, operationNumber, originAccount, holderName, amount, firstInvoiceOutstanding, audience = 'student' }) {
  const staff = audience === 'staff';
  const errors = {};
  if (!file) errors.file = staff ? 'Subí el comprobante para poder cargarlo.' : 'Subí el comprobante para poder enviarlo.';
  if (!operationNumber.trim()) errors.operationNumber = 'Ingresá el número de operación que figura en el comprobante.';
  const origin = originAccount.trim();
  if (!origin) {
    errors.originAccount = staff
      ? 'Ingresá el CBU o alias de la cuenta de origen.'
      : 'Ingresá el CBU o alias de la cuenta desde la que transferiste.';
  } else if (!CBU_PATTERN.test(origin) && !ALIAS_PATTERN.test(origin)) {
    errors.originAccount = 'Revisá el CBU (22 números) o el alias (6 a 20 caracteres).';
  }
  if (!holderName.trim()) errors.holderName = 'Ingresá el titular de la cuenta de origen.';
  if (!(amount > 0)) {
    errors.amount = 'Ingresá un monto mayor a cero.';
  } else if (firstInvoiceOutstanding != null && amount < firstInvoiceOutstanding) {
    errors.amount = 'El monto no alcanza a cubrir la primera cuota.';
  }
  return errors;
}

export function buildReceiptSubmission({ file, invoiceIds, amount, currency, operationNumber, originAccount, holderName, bankName, transferDate, studentId }) {
  const params = new URLSearchParams();
  invoiceIds.forEach((id) => params.append('invoiceIds', String(id)));
  params.set('declaredAmount', amount.toFixed(2));
  params.set('currency', currency);
  params.set('transferDate', transferDate);
  params.set('operationNumber', operationNumber.trim());
  params.set('originAccount', originAccount.trim());
  params.set('holderName', holderName.trim());
  if (bankName?.trim()) params.set('bankName', bankName.trim());
  const body = new FormData();
  body.append('file', file);
  const base = studentId == null ? '/me/bank-transfer-receipts' : `/students/${studentId}/bank-transfer-receipts`;
  return { path: `${base}?${params.toString()}`, body };
}

const FIELD_BY_VIOLATION = {
  BANK_TRANSFER_AMOUNT_INVALID: 'amount',
  BANK_TRANSFER_OPERATION_NUMBER_REQUIRED: 'operationNumber',
  BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED: 'originAccount',
  BANK_TRANSFER_HOLDER_NAME_REQUIRED: 'holderName',
  BANK_TRANSFER_RECEIPT_FILE_REQUIRED: 'file',
  BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED: 'file',
  BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH: 'file',
};

const MESSAGE_BY_CODE = {
  BANK_TRANSFER_AMOUNT_INVALID: 'Ingresá un monto mayor a cero.',
  BANK_TRANSFER_OPERATION_NUMBER_REQUIRED: 'Ingresá el número de operación que figura en el comprobante.',
  BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED: 'Ingresá el CBU o alias de la cuenta desde la que transferiste.',
  BANK_TRANSFER_HOLDER_NAME_REQUIRED: 'Ingresá el titular de la cuenta de origen.',
  BANK_TRANSFER_RECEIPT_FILE_REQUIRED: 'Subí el comprobante para poder enviarlo.',
  BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED: 'El comprobante tiene que ser un PDF, JPG o PNG.',
  BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH: 'El contenido del archivo no coincide con su formato. Subí el PDF, JPG o PNG original.',
  BANK_TRANSFER_DATE_REQUIRED: 'No pudimos registrar la fecha de la transferencia. Recargá la página y probá de nuevo.',
  BANK_TRANSFER_DATE_IN_FUTURE: 'La fecha de la transferencia no puede ser posterior a hoy. Revisá la fecha de tu dispositivo.',
  BANK_TRANSFER_INVOICES_REQUIRED: 'Elegí al menos una cuota para cubrir.',
  BANK_TRANSFER_CURRENCY_REQUIRED: 'No pudimos determinar la moneda de la cuota. Recargá la página y probá de nuevo.',
  RECEIPT_INVOICE_CURRENCY_MISMATCH: 'Las cuotas elegidas están en monedas distintas. Elegí cuotas de una sola moneda.',
  RECEIPT_INVOICE_NOT_PAYABLE: 'Una de las cuotas ya no admite pagos: puede que ya esté saldada. Recargá la página.',
  IDEMPOTENCY_KEY_INVALID: 'No pudimos validar el envío. Recargá la página y probá de nuevo.',
  RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST: 'Las cuotas se pagan en orden, desde la más vencida. Revisá la lista: puede haber cambiado.',
  INVOICE_ALREADY_UNDER_REVIEW: 'Una de las cuotas elegidas ya está en un comprobante en revisión.',
  LEGACY_RECEIPT_PENDING_ON_INVOICE: 'Una de las cuotas ya tiene un comprobante anterior en revisión.',
  IDEMPOTENCY_KEY_CONFLICT: 'Ya estamos procesando este envío. Esperá un momento y revisá el seguimiento.',
  UNSUPPORTED_FILE_TYPE: 'El comprobante tiene que ser un PDF, JPG o PNG.',
  MAX_UPLOAD_SIZE_EXCEEDED: 'El archivo pesa más de 5 MB. Subí uno más liviano.',
};

export function receiptSubmissionFailure(error) {
  const codes = [error?.code, ...(error?.violations ?? []).map((violation) => violation.code)].filter(Boolean);
  if (codes.includes('POSSIBLE_DUPLICATE_BANK_TRANSFER')) {
    return { duplicate: true, fieldErrors: {}, message: null };
  }
  const fieldErrors = {};
  const generalMessages = [];
  for (const code of codes) {
    const field = FIELD_BY_VIOLATION[code];
    if (field && !fieldErrors[field]) {
      fieldErrors[field] = MESSAGE_BY_CODE[code];
    } else if (!field && MESSAGE_BY_CODE[code]) {
      generalMessages.push(MESSAGE_BY_CODE[code]);
    }
  }
  const message = generalMessages[0] ?? (Object.keys(fieldErrors).length ? null : 'No pudimos enviar el comprobante. Probá de nuevo.');
  return { duplicate: false, fieldErrors, message };
}

export function isUnconfirmedSubmission(error) {
  return error?.status == null || error.status >= 500;
}

export function receiptCoversInvoice(receipt, invoiceId) {
  if (receipt.invoiceId === invoiceId) return true;
  if ((receipt.invoiceIds ?? []).includes(invoiceId)) return true;
  return (receipt.invoices ?? []).some((invoice) => invoice.invoiceId === invoiceId);
}

export function latestReceiptCovering(receipts, invoiceId) {
  return (receipts ?? [])
    .filter((receipt) => receiptCoversInvoice(receipt, invoiceId))
    .sort((a, b) => String(b.uploadedAt).localeCompare(String(a.uploadedAt)))[0];
}

export function receiptTrackingRows(receipt) {
  const rows = (receipt.invoices ?? []).map((invoice) => ({
    id: invoice.invoiceId,
    title: invoiceTitle({ invoiceType: invoice.feeType, lines: [{ period: invoice.period }] }),
    outstanding: invoice.outstandingAtSubmission,
  }));
  const coverage = coverageOf(rows, moneyValue(receipt.declaredAmount));
  return rows.map((row, index) => {
    const covered = coverage[index].state === 'covered';
    let status = 'pending';
    if (receipt.status === 'PENDING_REVIEW' && covered) status = 'review';
    if ((receipt.status === 'APPROVED' || receipt.status === 'APPLIED_TO_DEBT') && covered) status = 'paid';
    return { ...row, status };
  });
}

export function isPartialReceipt(receipt) {
  const rows = receiptTrackingRows(receipt);
  if (rows.length === 0) return false;
  const coverage = coverageOf(rows, moneyValue(receipt.declaredAmount));
  return coverage.some((entry) => entry.state !== 'covered');
}
