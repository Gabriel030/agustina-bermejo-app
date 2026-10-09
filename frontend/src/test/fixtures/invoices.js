export const money = (amount, currency = 'ARS') => ({ amount, currency });

export function discountFixture(overrides = {}) {
  return {
    discountName: 'Beca 50 %',
    discountSource: 'SCHOLARSHIP',
    baseAmount: money('48000.00'),
    discountAmount: money('24000.00'),
    installmentNumber: 1,
    installmentsTotal: 3,
    ...overrides,
  };
}

export function invoiceFixture({
  id = 1,
  period = '2026-09',
  dueDate = '2026-09-10',
  total = '48000.00',
  paid = '0.00',
  credited = '0.00',
  discount = null,
  invoiceType = 'MONTHLY_INSTALLMENT',
  documentStatus = 'ISSUED',
  paymentStatus = 'PENDING',
  ...overrides
} = {}) {
  const totalValue = parseFloat(total);
  const discountValue = discount ? parseFloat(discount.discountAmount.amount) : 0;
  const linesTotal = (totalValue + discountValue).toFixed(2);
  const outstanding = (totalValue - parseFloat(paid) - parseFloat(credited)).toFixed(2);
  return {
    id,
    invoiceNumber: `F-${String(id).padStart(8, '0')}`,
    invoiceType,
    documentType: 'INVOICE',
    documentStatus,
    paymentStatus,
    issueDate: dueDate,
    dueDate,
    currency: 'ARS',
    linesTotalAmount: money(linesTotal),
    discountsTotalAmount: money(discountValue.toFixed(2)),
    totalAmount: money(total),
    paidAmount: money(paid),
    creditedAmount: money(credited),
    outstandingAmount: money(outstanding),
    lines: [
      {
        lineNumber: 1,
        feeType: invoiceType,
        period,
        description: 'Cuota',
        amount: money(linesTotal),
        discount,
      },
    ],
    issuingReason: 'MONTHLY_RUN',
    discardedReason: null,
    paymentDate: null,
    paymentMethod: null,
    ...overrides,
  };
}
