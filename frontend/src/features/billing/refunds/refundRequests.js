export const REFUND_PART_STATUS = {
  REQUESTED: { label: 'Pendiente de aprobación', tone: 'warning' },
  APPROVED: { label: 'Listo para transferir', tone: 'accent' },
  COMPLETED: { label: 'Hecho', tone: 'success' },
  FAILED: { label: 'No se pudo', tone: 'danger' },
  REJECTED: { label: 'Rechazado', tone: 'neutral' },
};

export const REFUND_METHOD_LABELS = {
  CASH: 'Efectivo',
  BANK_TRANSFER: 'Transferencia',
  CARD: 'Tarjeta',
  WALLET: 'Billetera virtual',
};

export const REFUND_REQUEST_STATUS = {
  ...REFUND_PART_STATUS,
  PROGRESS: { label: 'En curso', tone: 'warning' },
  PARTIAL: { label: 'Hecho con fallas', tone: 'warning' },
};

const OPEN_PART_STATUSES = ['REQUESTED', 'APPROVED'];

export function refundMethodLabel(methodType) {
  return REFUND_METHOD_LABELS[methodType] ?? methodType;
}

function sumAmounts(parts) {
  const cents = parts.reduce((total, part) => total + Math.round(Number(part.amount.amount) * 100), 0);
  return { amount: (cents / 100).toFixed(2), currency: parts[0].amount.currency };
}

export function summarizeRequestStatus(parts) {
  const statuses = new Set(parts.map((part) => part.status));
  if (statuses.size === 1) {
    const status = parts[0].status;
    return { key: status, label: REFUND_PART_STATUS[status]?.label ?? status, tone: REFUND_PART_STATUS[status]?.tone ?? 'neutral' };
  }
  if (parts.some((part) => OPEN_PART_STATUSES.includes(part.status))) {
    const completed = parts.filter((part) => part.status === 'COMPLETED').length;
    return { key: 'PROGRESS', label: `En curso · ${completed} de ${parts.length} hechas`, tone: 'warning' };
  }
  return { key: 'PARTIAL', label: REFUND_REQUEST_STATUS.PARTIAL.label, tone: REFUND_REQUEST_STATUS.PARTIAL.tone };
}

export function groupRefundsByRequest(refunds) {
  const groups = new Map();
  refunds.forEach((refund) => {
    const key = refund.refundRequestId ?? `refund-${refund.id}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(refund);
  });
  return Array.from(groups.entries()).map(([id, parts]) => {
    const status = summarizeRequestStatus(parts);
    return {
      id,
      requestedOn: parts[0].requestedOn,
      parts,
      total: sumAmounts(parts),
      statusKey: status.key,
      status,
    };
  });
}

export const REFUND_ERROR_MESSAGES = {
  REFUND_COMPLETION_INVALID:
    'La fecha no puede ser futura ni anterior al pedido, y la referencia es obligatoria (hasta 120 caracteres).',
  REFUND_METHOD_MISMATCH: 'El método no coincide con el del pago original. Actualizamos la lista, revisá la parte y probá de nuevo.',
  INSUFFICIENT_ACCOUNT_CREDIT: 'El alumno no tiene saldo a favor suficiente para descontar este reembolso.',
  REFUND_FAILURE_REASON_INVALID: 'El motivo es obligatorio y tiene hasta 500 caracteres.',
  ILLEGAL_REFUND_STATUS_TRANSITION: 'Este reembolso ya no está listo para transferir. Actualizamos la lista.',
  REFUND_NOT_FOUND: 'El reembolso ya no existe. Actualizamos la lista.',
};

export const STALE_REFUND_CODES = ['ILLEGAL_REFUND_STATUS_TRANSITION', 'REFUND_NOT_FOUND', 'REFUND_METHOD_MISMATCH'];

export function refundErrorMessage(error) {
  return REFUND_ERROR_MESSAGES[error?.code] ?? error?.message ?? 'No pudimos completar la operación. Probá de nuevo.';
}
