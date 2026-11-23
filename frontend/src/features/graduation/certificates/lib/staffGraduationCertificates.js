import { DOMAIN_ERROR_MESSAGES } from '@/api/domainErrorMessages';

export const BATCH_LIMIT = 50;

export const KIND_TABS = [
  { value: 'PROGRAM', label: 'Carrera' },
  { value: 'COURSE', label: 'Curso' },
];

const ELIGIBLE_STATUS = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  BLOCKED_BY_DEBT: { label: 'Bloqueado por deuda', tone: 'danger' },
  PENDING_REVIEW: { label: 'Pendiente de revisión', tone: 'warning' },
  DISABLED: { label: 'Certificado deshabilitado', tone: 'danger' },
};

export function eligibleStatusOf(status) {
  return ELIGIBLE_STATUS[status] ?? { label: status, tone: 'neutral' };
}

export function eligibleKey(row) {
  return `${row.kind}-${row.enrollmentId}`;
}

export function isSelectable(row) {
  return row.status === 'PENDING';
}

export function batchItemOf(row) {
  return { enrollmentId: row.enrollmentId, kind: row.kind };
}

export function batchFailureMessage(errorCode) {
  return DOMAIN_ERROR_MESSAGES[errorCode] ?? 'No pudimos emitir este certificado.';
}

export function issuanceTitle(count) {
  return count === 1 ? 'Confirmar y emitir certificado' : `Confirmar y emitir ${count} certificados`;
}

export function issuanceDescription(rows) {
  if (rows.length === 1) {
    return `Se emite el certificado de egreso de ${rows[0].studentFullName}. Vas a ver el resultado al terminar.`;
  }
  return `Se emiten ${rows.length} certificados de egreso. Los que fallen se muestran al terminar, con su motivo.`;
}

export function previewLabel(row) {
  return `Vista previa del certificado de ${row.studentFullName}`;
}
