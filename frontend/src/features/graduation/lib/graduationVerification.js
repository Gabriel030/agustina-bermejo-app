export const GRADUATION_VERIFICATION_TRAY = '/administrativo/egresos-por-verificar';

export const VERIFICATION_STATUS_LABEL = {
  REQUESTED: { label: 'Por verificar', tone: 'warning' },
  ADMIN_VERIFIED: { label: 'En el Director', tone: 'neutral' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
};

export function verificationStatusLabel(status) {
  return VERIFICATION_STATUS_LABEL[status] ?? { label: status, tone: 'neutral' };
}

export function isPendingDebtFailure(error) {
  return error?.code === 'PENDING_DEBT';
}
