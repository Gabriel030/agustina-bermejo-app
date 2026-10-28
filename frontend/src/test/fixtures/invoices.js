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
  creditable = null,
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
        creditableAmount: money(creditable ?? linesTotal),
      },
    ],
    issuingReason: 'MONTHLY_RUN',
    discardedReason: null,
    paymentDate: null,
    paymentMethod: null,
    pendingAuthorizations: [],
    ...overrides,
  };
}

export function creditNoteFixture(overrides = {}) {
  return {
    id: 1,
    creditNoteNumber: 'NC-0000098',
    invoiceId: 61,
    invoiceNumber: 'F-00000061',
    reason: 'BILLING_ERROR',
    justification: 'Se cobró de más el seguro escolar.',
    offsetAmount: money('0.00'),
    freeCreditAmount: money('6000.00'),
    totalAmount: money('6000.00'),
    issueDate: '2026-09-02',
    factDate: '2026-09-02',
    authorizationRequestId: 12,
    lines: [{ invoiceLineNumber: 1, amount: money('6000.00'), offsetPart: money('0.00'), freePart: money('6000.00') }],
    requestedByName: 'Marcela Ibarra',
    authorizedByName: 'Rodolfo Fernández',
    authorizedAt: '2026-09-02T12:00:00Z',
    ...overrides,
  };
}

export function creditBalanceFixture(available = '0.00') {
  return {
    currency: 'ARS',
    unallocatedCredit: money(available),
    reservedByPendingRefunds: money('0.00'),
    availableCredit: money(available),
  };
}

export function pendingAuthorizationFixture(overrides = {}) {
  return {
    id: 90,
    type: 'CANCEL_INVOICE',
    requestedByPersonId: 10,
    requestedByName: 'Marcela Ibarra',
    requestedAt: '2026-10-25T12:00:00Z',
    ...overrides,
  };
}
