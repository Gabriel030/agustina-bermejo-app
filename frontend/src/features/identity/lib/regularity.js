export const REGULARITY_REASON_LABEL = {
  PROGRAM_NOT_ENROLLED: 'No tiene la carrera activa',
  NO_ACTIVE_SECTION: 'Sin cursada activa',
  FREE_IN_ACTIVE_SECTION: 'Libre por inasistencias en una cursada activa',
  NOT_ENROLLED: 'No está inscripto',
  COURSE_FINISHED: 'El curso ya terminó',
};

export const PAYMENT_STANDING_LABEL = {
  VERIFIED: 'Pagos al día',
  OVERDUE: 'Pagos vencidos',
  TO_CONFIRM: 'Pagos a confirmar',
};

export function describeRegularity(regularity) {
  const payment = PAYMENT_STANDING_LABEL[regularity.paymentStanding] ?? null;
  if (regularity.institutionallyRegular) {
    const toConfirm = regularity.paymentStanding === 'TO_CONFIRM';
    return {
      label: toConfirm ? 'Alumno regular · a confirmar' : 'Alumno regular',
      tone: toConfirm ? 'warning' : 'success',
      details: [payment].filter(Boolean),
    };
  }
  const reasons = regularity.reasons.map((reason) => REGULARITY_REASON_LABEL[reason]).filter(Boolean);
  const overdue = regularity.paymentStanding === 'OVERDUE' ? [PAYMENT_STANDING_LABEL.OVERDUE] : [];
  return { label: 'No es alumno regular', tone: 'neutral', details: [...reasons, ...overdue] };
}
