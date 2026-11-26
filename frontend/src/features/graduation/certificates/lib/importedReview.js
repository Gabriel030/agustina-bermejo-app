import { DOMAIN_ERROR_MESSAGES } from '@/api/domainErrorMessages';

export const REVIEW_BATCH_LIMIT = 50;
export const REVIEW_PAGE_SIZE = 25;
export const REASON_MIN_LENGTH = 10;
export const REASON_MAX_LENGTH = 500;
export const LOCKING_ERROR_CODE = 'INVALID_GRADUATION_CERTIFICATE_REVIEW_DECISION';

export const REVIEW_STATUS_TABS = [
  { value: 'PENDING_REVIEW', label: 'Pendientes' },
  { value: 'ENABLED', label: 'Habilitados' },
  { value: 'DISABLED', label: 'Deshabilitados' },
];

const REVIEW_STATUS = {
  PENDING_REVIEW: { label: 'Pendiente', tone: 'warning' },
  ENABLED: { label: 'Habilitado', tone: 'success' },
  DISABLED: { label: 'Deshabilitado', tone: 'danger' },
  NOT_REQUIRED: { label: 'No requiere revisión', tone: 'neutral' },
};

export const LOCKED_LABEL = 'Certificado emitido';
export const LOCKED_EXPLANATION = 'Este egreso ya no admite cambios de revisión.';

export function reviewStatusOf(status) {
  return REVIEW_STATUS[status] ?? { label: status, tone: 'neutral' };
}

export function canEnable(row) {
  return row.reviewStatus === 'PENDING_REVIEW' || row.reviewStatus === 'DISABLED';
}

export function canDisable(row) {
  return row.reviewStatus === 'PENDING_REVIEW' || row.reviewStatus === 'ENABLED';
}

export function reviewFailureMessage(errorCode) {
  return DOMAIN_ERROR_MESSAGES[errorCode] ?? 'No pudimos actualizar este certificado.';
}

export function batchDecisionTitle(decision, count) {
  const verb = decision === 'ENABLE' ? 'habilitar' : 'deshabilitar';
  return count === 1 ? `Confirmar y ${verb} certificado` : `Confirmar y ${verb} ${count} certificados`;
}

export function batchDecisionDescription(decision, count) {
  if (decision === 'ENABLE') {
    return count === 1
      ? 'El certificado de este egreso queda habilitado para emitirse.'
      : `Los ${count} certificados quedan habilitados para emitirse. Los que fallen se muestran al terminar, con su motivo.`;
  }
  return `Se deshabilitan ${count} certificados con el mismo motivo. Los que fallen se muestran al terminar, con su motivo.`;
}
