import { formatDate, formatMoney, hasPendingReceipt, isInvoicePayable } from '@/lib/billing';
import { fromCents, parseAmountInput, toCents } from '../refunds/refundForm';

export const JUSTIFICATION_MAX_LENGTH = 1000;

export const STAFF_REASON_LABELS = {
  BILLING_ERROR: 'Error de facturación',
  CANCELLATION: 'Cancelación',
  LATE_FEE_ANNULMENT: 'Anulación de recargo',
  WITHDRAWAL: 'Baja de la inscripción',
  LATE_FEE_CORRECTION: 'Corrección de recargo',
};

export const STUDENT_REASON_LABELS = {
  BILLING_ERROR: 'Corrección de la facturación',
  CANCELLATION: 'Cuota anulada',
  LATE_FEE_ANNULMENT: 'Se anuló un recargo',
  WITHDRAWAL: 'Baja de la inscripción',
  LATE_FEE_CORRECTION: 'Ajuste de recargo',
};

export const CANCELLATION_REASONS = [
  { value: 'BILLING_ERROR', title: 'Error de facturación', description: 'La cuota se emitió mal. Se puede volver a emitir.' },
  { value: 'CANCELLATION', title: 'Cancelación', description: 'La cuota ya no corresponde. No se vuelve a emitir.' },
];

export const PENDING_OPERATION_LABELS = {
  CANCEL_INVOICE: 'la cancelación',
  ISSUE_CREDIT_NOTE: 'la nota de crédito',
  ISSUE_LATE_FEE_CORRECTION_CREDIT_NOTE: 'la corrección del recargo',
};

export const PENDING_BANK_TRANSFER_CODE = 'PENDING_BANK_TRANSFER_ON_INVOICE';
export const LATE_FEE_REVIEW_CODE = 'LATE_FEE_UNDER_PAYMENT_REVIEW';
export const JUSTIFICATION_REQUIRED_CODE = 'CREDIT_NOTE_JUSTIFICATION_REQUIRED';
export const FULL_DEBT_CODE = 'CREDIT_NOTE_FULL_DEBT_REQUIRES_CANCELLATION';
export const RECEIPT_REVIEW_CODES = [PENDING_BANK_TRANSFER_CODE, LATE_FEE_REVIEW_CODE];
export const STALE_INVOICE_CODES = [
  'ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION',
  'CREDIT_NOTE_DISTRIBUTION_MISMATCH',
  'CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT',
  'CREDIT_NOTE_LINES_INVALID',
];

export const CREDIT_NOTE_ERROR_MESSAGES = {
  [PENDING_BANK_TRANSFER_CODE]: {
    title: 'La cuota tiene un comprobante de transferencia esperando revisión.',
    detail: 'No se hizo ningún cambio. Resolvé el comprobante y volvé a intentar.',
  },
  [LATE_FEE_REVIEW_CODE]: {
    title: 'Un recargo de la cuota tiene un comprobante en revisión.',
    detail: 'No se puede cancelar la cuota hasta que se decida. No se canceló nada, tampoco los otros recargos.',
  },
  ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION: {
    title: 'La cuota cambió y ya no se puede cancelar.',
    detail: 'Puede que se haya pagado o que tenga pagos. Recargá la cuota para ver su estado actual.',
  },
  CREDIT_NOTE_DISTRIBUTION_MISMATCH: {
    title: 'La cuota cambió mientras armabas la nota.',
    detail: 'Los importes pueden haber quedado desactualizados. Recargá la cuota y volvé a cargarlos.',
  },
  CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT: {
    title: 'La nota supera lo que se puede acreditar.',
    detail: 'Alguien acreditó un importe antes. Recargá la cuota para ver el máximo vigente.',
  },
  CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT: {
    title: 'La nota supera lo que se pagó de la cuota y todavía no se acreditó.',
    detail: 'Bajá los importes y volvé a intentar.',
  },
  [FULL_DEBT_CODE]: {
    title: 'La nota cubre toda la deuda de la cuota.',
    detail: 'Para dejar la cuota sin efecto usá Cancelar cuota.',
  },
  CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED: {
    title: 'Esta operación necesita la autorización de la dirección.',
    detail: 'Actualizá la pantalla y volvé a pedirla.',
  },
  CREDIT_NOTE_LINES_INVALID: {
    title: 'Los renglones de la nota no son válidos.',
    detail: 'Recargá la cuota y volvé a cargar los importes.',
  },
  INVALID_CREDIT_NOTE_REASON: {
    title: 'Ese motivo no se puede usar desde acá.',
    detail: 'Elegí otro motivo.',
  },
  [JUSTIFICATION_REQUIRED_CODE]: {
    title: 'La justificación es obligatoria.',
    detail: 'Contá por qué se hace. Queda en el registro de auditoría.',
  },
};

export function creditNoteErrorOf(error) {
  const known = CREDIT_NOTE_ERROR_MESSAGES[error?.code];
  if (known) return { code: error.code, ...known };
  if (error?.status === 403) {
    return { code: null, title: 'No tenés permiso para hacer esto.', detail: 'Consultá con la dirección.' };
  }
  return {
    code: error?.code ?? null,
    title: error?.message ?? 'No pudimos completar la operación.',
    detail: 'No se hizo ningún cambio. Probá de nuevo.',
  };
}

export function blockOf(violation) {
  const known = CREDIT_NOTE_ERROR_MESSAGES[violation.code];
  return {
    code: violation.code,
    title: known?.title ?? violation.message ?? 'No se puede hacer esta operación.',
    detail: known?.detail ?? 'No se hizo ningún cambio.',
  };
}

export function isLateFeeInvoice(invoice) {
  return invoice.invoiceType === 'LATE_FEE';
}

export function isAuthorizationAck(result) {
  return result?.authorizationRequestId != null && result.creditNoteNumber == null;
}

export function pendingAuthorizationOf(invoice) {
  return invoice.pendingAuthorizations?.[0] ?? null;
}

export function pendingOperationLabel(pending) {
  return PENDING_OPERATION_LABELS[pending?.type] ?? 'el pedido';
}

export function pendingOperationTitle(pending) {
  const label = pendingOperationLabel(pending);
  return `${label.charAt(0).toUpperCase()}${label.slice(1)}`;
}

export function firstPendingInvoice(invoices) {
  return invoices.find((invoice) => (invoice.pendingAuthorizations?.length ?? 0) > 0) ?? null;
}

export function creditableCentsOf(invoice) {
  return (invoice.lines ?? []).reduce((total, line) => total + toCents(line.creditableAmount), 0);
}

export function invoiceActionsOf(invoice, { invoices }) {
  const lateFee = isLateFeeInvoice(invoice);
  const none = { lateFee, canCredit: false, canCancel: false, note: null, tip: null, linkToReceipts: false, pending: null };
  if (invoice.paymentStatus === 'CANCELLED') {
    return { ...none, note: lateFee ? 'El recargo quedó anulado. No admite más acciones.' : 'La cuota quedó sin efecto. No admite más acciones.' };
  }
  if (invoice.documentStatus !== 'ISSUED') return none;
  const pending = pendingAuthorizationOf(invoice);
  if (pending) return { ...none, pending };
  const otherPending = firstPendingInvoice(invoices.filter((candidate) => candidate.id !== invoice.id));
  if (otherPending) {
    return {
      ...none,
      showButtons: true,
      tip: 'Ya hay un pedido pendiente de autorización en otra cuota de este alumno. Resolvelo primero.',
    };
  }
  if (invoice.paymentStatus === 'PAYMENT_UNDER_REVIEW' || hasPendingReceipt(invoice)) {
    return {
      ...none,
      showButtons: true,
      linkToReceipts: true,
      tip: 'Tiene un comprobante de transferencia esperando revisión. Resolvelo antes.',
    };
  }
  if (lateFee) return { ...none, showButtons: true, canCancel: true };
  const paid = invoice.paymentStatus === 'PAID';
  const hasPayments = toCents(invoice.paidAmount) > 0;
  const fullyCredited = creditableCentsOf(invoice) <= 0;
  return {
    ...none,
    showButtons: true,
    canCredit: (isInvoicePayable(invoice) || paid) && !fullyCredited,
    canCancel: isInvoicePayable(invoice) && !hasPayments,
    creditTip: fullyCredited ? 'Ya se acreditó todo el importe de esta cuota.' : null,
    note: paid || hasPayments ? 'Una cuota con pagos no se cancela: para devolver un importe se emite una nota de crédito.' : null,
  };
}

export const STAFF_EFFECT_LABELS = {
  FREE_CREDIT: { label: 'Crédito a favor', tone: 'accent' },
  DEBT_OFFSET: { label: 'Reduce deuda', tone: 'neutral' },
};

export function creditNoteEffectOf(note) {
  return toCents(note.freeCreditAmount) > 0 ? 'FREE_CREDIT' : 'DEBT_OFFSET';
}

export function studentEffectOf(note) {
  if (toCents(note.freeCreditAmount) > 0) return { label: 'A tu favor', tone: 'accent' };
  if (note.reason === 'CANCELLATION') return { label: 'Sin saldo a pagar', tone: 'neutral' };
  return { label: 'Descontado de la cuota', tone: 'neutral' };
}

export function creditNoteAuthorshipOf(note) {
  const requestedBy = note.requestedByName ?? 'Sistema';
  let authorizedBy = '—';
  if (note.authorizedByName) {
    authorizedBy = `${note.authorizedByName}${note.authorizedAt ? ` · ${formatDate(note.authorizedAt)}` : ''}`;
  } else if (note.authorizationRequestId == null) {
    authorizedBy = 'No requirió autorización';
  }
  return { requestedBy, authorizedBy };
}

export function parseLineAmount(text) {
  if (!String(text ?? '').trim()) return { cents: 0, error: null };
  const parsed = parseAmountInput(text);
  if (!parsed) return { cents: 0, error: 'Ingresá un importe mayor a cero, con hasta dos decimales.' };
  return { cents: Math.round(Number(parsed) * 100), error: null };
}

export function creditNoteDraftOf(invoice, form) {
  const currency = invoice.currency ?? invoice.totalAmount?.currency ?? 'ARS';
  const paid = invoice.paymentStatus === 'PAID';
  const outstandingCents = toCents(invoice.outstandingAmount);
  const lines = (invoice.lines ?? []).map((line) => {
    const maxCents = toCents(line.creditableAmount);
    const parsed = parseLineAmount(form.amounts[line.lineNumber]);
    const overMax = !parsed.error && parsed.cents > maxCents;
    return {
      lineNumber: line.lineNumber,
      maxCents,
      cents: parsed.cents,
      error: parsed.error ?? (overMax ? `Supera el máximo de ${formatMoney(fromCents(maxCents, currency))}.` : null),
    };
  });
  const totalCents = lines.reduce((total, line) => total + line.cents, 0);
  const equalsDebt = !paid && totalCents > 0 && totalCents === outstandingCents;
  const exceedsDebt = !paid && totalCents > outstandingCents;
  const hasLineError = lines.some((line) => line.error);
  let blockReason = null;
  if (hasLineError) blockReason = 'Revisá los importes marcados.';
  else if (totalCents === 0) blockReason = 'Ingresá al menos un importe.';
  else if (equalsDebt) blockReason = 'Cubre toda la deuda: usá Cancelar cuota.';
  else if (exceedsDebt) blockReason = 'Supera lo que debe la cuota.';
  else if (!form.justification.trim()) blockReason = 'Falta la justificación.';
  else if (form.justification.length > JUSTIFICATION_MAX_LENGTH) blockReason = 'La justificación es demasiado larga.';
  return {
    currency,
    paid,
    lines,
    totalCents,
    outstandingCents,
    offsetCents: paid ? 0 : Math.min(totalCents, outstandingCents),
    freeCents: paid ? totalCents : 0,
    equalsDebt,
    exceedsDebt,
    blockReason,
    blocked: blockReason != null,
    payload: {
      reason: 'BILLING_ERROR',
      justification: form.justification.trim(),
      lines: lines
        .filter((line) => line.cents > 0)
        .map((line) => ({ lineNumber: line.lineNumber, amount: fromCents(line.cents, currency) })),
    },
  };
}

export function creditNoteDiffRowsOf(draft, creditBalance) {
  if (!draft.paid) {
    return [
      {
        key: 'debt',
        label: 'Deuda de la cuota',
        today: fromCents(draft.outstandingCents, draft.currency),
        next: fromCents(draft.outstandingCents - draft.offsetCents, draft.currency),
        diff: fromCents(-draft.offsetCents, draft.currency),
      },
    ];
  }
  const todayCents = toCents(creditBalance?.unallocatedCredit);
  return [
    {
      key: 'credit',
      label: 'Crédito a favor del alumno',
      today: fromCents(todayCents, draft.currency),
      next: fromCents(todayCents + draft.freeCents, draft.currency),
      diff: fromCents(draft.freeCents, draft.currency),
    },
  ];
}

export function cancellationToastOf({ result, reason, lateFee, preview }) {
  let message = 'Cuota sin efecto. Esta cuota no se vuelve a emitir.';
  if (lateFee) message = 'Recargo anulado.';
  else if (reason === 'BILLING_ERROR') message = 'Cuota sin efecto. Ya se puede volver a emitir.';
  const parts = [`Nota de crédito ${result.creditNoteNumber}.`];
  if ((preview?.discountConsumptionReleased?.length ?? 0) > 0) parts.push('Se devolvió el consumo del descuento.');
  return { message, description: parts.join(' ') };
}

export function hasEnrollmentFeeWarning(preview, reason) {
  return reason === 'BILLING_ERROR' && Boolean(preview?.enrollmentFee?.reissueWouldLoseDiscount);
}

export function isHardBlock(violation) {
  return violation != null && violation.code !== JUSTIFICATION_REQUIRED_CODE;
}

export function billingPath(roleBase, { studentId, tab } = {}) {
  const params = new URLSearchParams();
  if (studentId) params.set('alumno', String(studentId));
  if (tab) params.set('tab', tab);
  const query = params.toString();
  return `/${roleBase}/facturacion${query ? `?${query}` : ''}`;
}

export function creditNotePath(roleBase, invoiceId, studentId) {
  return `/${roleBase}/facturacion/cuotas/${invoiceId}/nota-de-credito?alumno=${studentId}`;
}

export function receiptsLocation(pathname, studentId) {
  const params = new URLSearchParams();
  if (studentId) params.set('alumno', String(studentId));
  params.set('tab', 'comprobantes');
  return { pathname, search: `?${params.toString()}` };
}
