export function advanceView(overrides = {}) {
  return {
    available: true,
    reasonCode: 'AVAILABLE',
    opensOn: '2026-09-01',
    amount: { amount: '45000.00', currency: 'ARS' },
    dueDate: '2027-02-12',
    invoiceId: 9,
    ...overrides,
  };
}

export function obligationWith(advance) {
  return [
    {
      templateId: 1,
      slot: 1,
      slotCount: 1,
      periodFrom: '2026-12',
      periodTo: '2027-11',
      status: 'NOT_ISSUED',
      invoiceId: 1,
      dueDate: '2026-12-10',
      remainingBalance: { amount: '0.00', currency: 'ARS' },
      satisfied: false,
      ...(advance ? { advance } : {}),
    },
  ];
}
