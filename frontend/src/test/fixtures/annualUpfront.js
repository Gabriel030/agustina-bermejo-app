const ars = (amount) => ({ amount, currency: 'ARS' });

export function annualQuote(overrides = {}) {
  const lines = Array.from({ length: 11 }, (_, index) => ({
    lineNumber: index + 1,
    period: `2027-${String(index + 2).padStart(2, '0')}`,
    baseAmount: ars('80000.00'),
    discountAmount: ars('8000.00'),
    finalAmount: ars('72000.00'),
  }));
  return {
    eligible: true,
    lectiveYear: 2027,
    requestDeadline: '2027-02-10',
    quote: {
      lines,
      discount: { definitionId: 1, name: 'Pago anual', calculation: { rate: 10 }, totalSaving: ars('88000.00') },
      total: ars('792000.00'),
      paymentDeadline: '2027-02-04T10:00:00',
    },
    ...overrides,
  };
}

export function annualRequest(overrides = {}) {
  return {
    id: 7,
    programEnrollmentId: 3,
    invoiceId: 55,
    lectiveYear: 2027,
    status: 'AWAITING_PAYMENT',
    paymentUnderReview: false,
    acceptedAt: '2027-02-02T10:00:00',
    acceptedTermsVersion: 'v1',
    paymentDeadline: '2027-02-04T10:00:00',
    total: ars('792000.00'),
    lines: [],
    ...overrides,
  };
}
