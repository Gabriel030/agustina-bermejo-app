export const IMPACT_CARDS = [
  { field: 'openInvoices', label: 'Facturas abiertas' },
  { field: 'overdueInvoices', label: 'Facturas vencidas' },
  { field: 'invoicesAwaitingPayment', label: 'Esperando pago' },
  { field: 'invoicesUnderPaymentReview', label: 'Pagos en revisión' },
  { field: 'paymentsPendingReconciliation', label: 'Pagos por conciliar' },
  { field: 'pendingDraftInvoices', label: 'Borradores pendientes' },
  { field: 'refundsInProgress', label: 'Reembolsos en curso' },
  { field: 'runsInProgress', label: 'Procesos en curso' },
];

export function formatCountdown(seconds) {
  const minutes = String(Math.floor(seconds / 60)).padStart(2, '0');
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}
