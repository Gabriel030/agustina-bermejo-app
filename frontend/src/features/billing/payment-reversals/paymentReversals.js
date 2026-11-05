export const REVERSAL_REFERENCE_MAX_LENGTH = 120;
export const REVOCATION_REASON_MAX_LENGTH = 500;

export const PENDING_REFUND_CODE = 'PAYMENT_HAS_PENDING_REFUND';

export const PAYMENT_STATUS = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  AUTHORIZED: { label: 'Autorizado', tone: 'accent' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
  REFUNDED: { label: 'Reembolsado', tone: 'neutral' },
  CHARGED_BACK: { label: 'Contracargado', tone: 'danger' },
  CANCELLED: { label: 'Cancelado', tone: 'neutral' },
};

export const REVERSAL_KINDS = {
  chargeback: {
    actionKey: 'registerChargeback',
    menuLabel: 'Registrar contracargo del banco',
    title: 'Registrar contracargo',
    confirmLabel: 'Registrar contracargo',
    confirmVariant: 'destructive',
    group: 'bank',
    irreversible: true,
  },
  reversal: {
    actionKey: 'reverseChargeback',
    menuLabel: 'Revertir contracargo del banco',
    title: 'Revertir contracargo',
    confirmLabel: 'Revertir contracargo',
    confirmVariant: 'gradient',
    group: 'bank',
    irreversible: false,
  },
  revocation: {
    actionKey: 'revokeApproval',
    menuLabel: 'Revocar aprobación por error',
    title: 'Revocar aprobación',
    confirmLabel: 'Revocar aprobación',
    confirmVariant: 'destructive',
    group: 'internal',
    irreversible: true,
  },
};

export const GROUP_LABELS = {
  bank: 'Movimiento del banco',
  internal: 'Corrección interna',
};

export const FORM_FIELD_CODES = [
  'CHARGEBACK_REFERENCE_INVALID',
  'CHARGEBACK_DATE_INVALID',
  'PAYMENT_REJECTION_REASON_REQUIRED',
  'PAYMENT_REJECTION_REASON_TOO_LONG',
];

export const FIELD_BY_CODE = {
  CHARGEBACK_REFERENCE_INVALID: 'reference',
  CHARGEBACK_DATE_INVALID: 'date',
  PAYMENT_REJECTION_REASON_REQUIRED: 'reason',
  PAYMENT_REJECTION_REASON_TOO_LONG: 'reason',
};

export const BLOCKING_CODES = [PENDING_REFUND_CODE, 'PAYMENT_ALREADY_REFUNDED'];

export function validateReversalForm(kind, form, payment, today) {
  const errors = {};
  if (kind === 'chargeback') {
    if (!form.date) errors.date = 'Elegí la fecha del contracargo.';
    else if (payment.paidOn && form.date < payment.paidOn) errors.date = 'No puede ser anterior al pago.';
    else if (form.date > today) errors.date = 'No puede ser futura.';
    if (!form.reference.trim()) errors.reference = 'Ingresá la referencia que informa el banco.';
    else if (form.reference.trim().length > REVERSAL_REFERENCE_MAX_LENGTH) errors.reference = 'Hasta 120 caracteres.';
  }
  if (kind === 'revocation') {
    if (!form.reason.trim()) errors.reason = 'El motivo es obligatorio.';
    else if (form.reason.trim().length > REVOCATION_REASON_MAX_LENGTH) errors.reason = 'Hasta 500 caracteres.';
  }
  return errors;
}

export const STALE_PAYMENT_CODES = [
  'PAYMENT_NOT_FOUND',
  'CHARGEBACK_NOT_FOUND',
  'ILLEGAL_PAYMENT_STATUS_TRANSITION',
  'CHARGEBACK_ALREADY_REVERSED',
];

export const REVERSAL_ERROR_MESSAGES = {
  PAYMENT_NOT_FOUND: 'El pago ya no existe. Actualizamos la lista.',
  CHARGEBACK_NOT_FOUND: 'El contracargo ya no existe. Actualizamos la lista.',
  ILLEGAL_PAYMENT_STATUS_TRANSITION: 'El estado del pago cambió. Actualizamos la lista, revisá y probá de nuevo.',
  CHARGEBACK_ALREADY_REVERSED: 'Este contracargo ya fue revertido. Actualizamos la lista.',
  INSUFFICIENT_ACCOUNT_CREDIT: 'La cuenta del alumno no tiene crédito suficiente para hacer esta operación.',
  CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT: 'El contracargo tiene que ser por el total del pago.',
  PAYMENT_ALREADY_REFUNDED: 'El pago ya tiene dinero devuelto.',
  PAYMENT_HAS_PENDING_REFUND: 'El pago tiene reembolsos pendientes. Cerralos primero.',
  CHARGEBACK_REFERENCE_INVALID: 'Ingresá la referencia que informa el banco (hasta 120 caracteres).',
  CHARGEBACK_DATE_INVALID: 'La fecha del contracargo no es válida.',
  PAYMENT_REJECTION_REASON_REQUIRED: 'El motivo es obligatorio.',
  PAYMENT_REJECTION_REASON_TOO_LONG: 'El motivo no puede superar los 500 caracteres.',
};

export function reversalErrorMessage(error) {
  return REVERSAL_ERROR_MESSAGES[error?.code] ?? error?.message ?? 'No pudimos completar la operación. Probá de nuevo.';
}

function entryOf(kind, payment) {
  const definition = REVERSAL_KINDS[kind];
  const availability = payment.actions?.[definition.actionKey];
  const blockedBy = availability && !availability.allowed ? availability.blockedBy : null;
  const refundBlocked = blockedBy?.code === PENDING_REFUND_CODE;
  return {
    kind,
    label: definition.menuLabel,
    group: definition.group,
    blockedBy,
    refundBlocked,
    disabled: Boolean(availability) && !availability.allowed && !refundBlocked,
  };
}

export function menuEntriesOf(payment, isDirector) {
  const entries = [];
  if (payment.status === 'APPROVED') {
    entries.push(entryOf('chargeback', payment));
    if (isDirector && payment.methodType === 'BANK_TRANSFER') entries.push(entryOf('revocation', payment));
  }
  if (payment.status === 'CHARGED_BACK' && payment.chargeback && !payment.chargeback.reversedOn) {
    entries.push(entryOf('reversal', payment));
  }
  return entries;
}

export function countLabel(count, singular, plural) {
  return `${count} ${count === 1 ? singular : plural}`;
}

export function successMessage(kind, result) {
  const reopened = result.reopenedInvoiceIds?.length ?? 0;
  const settled = result.settledInvoiceIds?.length ?? 0;
  if (kind === 'reversal') return `Contracargo revertido. ${countLabel(settled, 'cuota saldada', 'cuotas saldadas')}.`;
  const reopenedText = countLabel(reopened, 'cuota reabierta', 'cuotas reabiertas');
  if (kind === 'chargeback') return `Contracargo ${result.reference} registrado. ${reopenedText}.`;
  return `Aprobación revocada. ${reopenedText}.`;
}

export function isHardBlock(preview) {
  return Boolean(preview) && preview.allowed === false && !FORM_FIELD_CODES.includes(preview.blockedBy?.code);
}
