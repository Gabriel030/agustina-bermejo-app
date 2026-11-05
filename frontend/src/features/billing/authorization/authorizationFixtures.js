export const API = 'http://localhost:8080';

export function money(amount) {
  return { amount, currency: 'ARS' };
}

export function isoDateInDays(days) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function listItem(overrides = {}) {
  return {
    id: 11,
    operationType: 'ISSUE_REFUND',
    operationLabel: 'Reembolso',
    family: 'REFUNDS',
    familyLabel: 'Reembolsos',
    status: 'PENDING',
    resumeOutcome: null,
    summary: 'Devolver saldo a favor, 2 pagos',
    studentName: 'Ledesma, Florencia',
    amount: money('120000.00'),
    requestedBy: { id: 30, name: 'Romina Castro', roles: ['ADMINISTRATIVO'] },
    requestedAt: '2026-10-22T14:00:00Z',
    expiresOn: isoDateInDays(5),
    requiredRole: 'DIRECTOR',
    decidedAt: null,
    ...overrides,
  };
}

export function page(items, overrides = {}) {
  return { items, page: 0, size: 50, totalElements: items.length, ...overrides };
}

export function detail(requestOverrides = {}, overrides = {}) {
  return {
    request: listItem(requestOverrides),
    reason: 'La alumna dejó la carrera y pidió que le devuelvan el saldo.',
    decidedBy: null,
    decisionReason: null,
    requestLines: [
      { label: 'Alumno', value: 'Ledesma, Florencia · DNI 43.051.776' },
      { label: 'Pedido', value: 'Reembolso de $120.000' },
    ],
    effectLines: [
      { tone: 'SUCCESS', text: 'Las 2 partes pasan a Aprobado y quedan listas para transferir.' },
      { tone: 'NEUTRAL', text: 'Si rechazás, el monto vuelve a estar disponible.' },
    ],
    detailBlock: {
      type: 'REFUND_PARTS',
      refundParts: [
        { refundId: 1, paymentId: 55, methodType: 'BANK_TRANSFER', status: 'REQUESTED', amount: money('70000.00'), destinationAccount: '0170099220000067890123', destinationHolder: 'Florencia Ledesma' },
        { refundId: 2, paymentId: 40, methodType: 'CASH', status: 'REQUESTED', amount: money('50000.00'), destinationAccount: '0170099220000067890123', destinationHolder: 'Florencia Ledesma' },
      ],
      changeRows: [],
    },
    statusChanges: [
      { fromStatus: null, toStatus: 'PENDING', event: 'OPEN', actorPersonId: 30, actorName: 'Romina Castro', actorProcess: null, changedAt: '2026-10-22T14:00:00Z', reason: null },
    ],
    ...overrides,
  };
}
