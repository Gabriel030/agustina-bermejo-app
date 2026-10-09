export function isAdmissionInvoicePending(solicitud) {
  return solicitud?.status === 'APPROVED' && Boolean(solicitud.admissionInvoice) && solicitud.admissionInvoice.paymentStatus !== 'PAID';
}

export function admissionPaymentPhase(solicitud) {
  const invoice = solicitud?.admissionInvoice;
  if (solicitud?.status !== 'APPROVED' || !invoice) return null;
  if (invoice.paymentStatus === 'PAID') return solicitud.programEnrollmentId ? 'ENROLLED' : 'CONFIRMING_ENROLLMENT';
  if (invoice.receiptStatus === 'PENDING_REVIEW') return 'UNDER_REVIEW';
  if (invoice.paymentStatus === 'OVERDUE') return 'OVERDUE';
  return 'PENDING';
}

export function isAdmissionInvoiceActionable(solicitud) {
  const fase = admissionPaymentPhase(solicitud);
  return fase === 'PENDING' || fase === 'OVERDUE';
}

function startOfLocalDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function daysUntilDate(isoDate, today = new Date()) {
  if (!isoDate) return null;
  const [year, month, day] = String(isoDate).slice(0, 10).split('-').map(Number);
  const due = new Date(year, month - 1, day);
  return Math.round((due - startOfLocalDay(today)) / 86_400_000);
}
