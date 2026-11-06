import { money } from '@/test/fixtures/invoices';

export function receiptInstallmentFixture({
  invoiceId = 61,
  period = '2026-09',
  feeType = 'MONTHLY_INSTALLMENT',
  total = '45000.00',
  outstanding = '45000.00',
} = {}) {
  return { invoiceId, period, feeType, totalAmount: money(total), outstandingAtSubmission: money(outstanding) };
}

export function receiptFixture(overrides = {}) {
  return {
    id: 7,
    invoiceId: 61,
    filePath: 'students/2/comprobantes_1.pdf',
    status: 'PENDING_REVIEW',
    rejectionReason: null,
    rejectionCode: null,
    uploadedAt: '2026-10-02T10:00:00',
    studentId: 2,
    studentName: 'Martina Acosta',
    studentDni: '28016166',
    invoicePeriod: '2026-09',
    invoiceFeeType: 'MONTHLY_INSTALLMENT',
    productName: 'Profesorado de Educación Inicial',
    invoiceIds: [61],
    declaredAmount: money('45000.00'),
    transferDate: '2026-10-01',
    operationNumber: 'OP-889',
    originAccount: '0170000000000000000001',
    holderName: 'Martina Acosta',
    invoices: [receiptInstallmentFixture()],
    pendingBusinessDays: 2,
    reviewAlertSent: true,
    reviewAlertSentAt: '2026-10-03T09:00:00',
    uploadedBy: { personId: 20, name: 'Martina Acosta', role: 'ALUMNO', internal: false },
    ...overrides,
  };
}
