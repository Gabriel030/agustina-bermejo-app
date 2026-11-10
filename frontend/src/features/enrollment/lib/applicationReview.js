export const APPLICATION_REVIEW_PATH = 'solicitudes-de-ingreso';

export const APPLICATION_STATUS = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  EXPIRED: { label: 'Vencida', tone: 'neutral' },
};

export const HISTORY_STATUS_OPTIONS = [
  { value: 'ALL', label: 'Todos los estados' },
  { value: 'APPROVED', label: 'Aprobadas' },
  { value: 'REJECTED', label: 'Rechazadas' },
  { value: 'EXPIRED', label: 'Vencidas' },
];

export const DOCUMENT_TYPE_LABEL = {
  DNI_FRENTE: 'DNI (frente)',
  DNI_DORSO: 'DNI (dorso)',
  ANALITICO_SECUNDARIO: 'Analítico',
};

const DOCUMENT_SHORT_LABEL = {
  DNI_FRENTE: 'DNI frente',
  DNI_DORSO: 'DNI dorso',
  ANALITICO_SECUNDARIO: 'analítico',
};

export const REQUIRED_DOCUMENT_TYPES = ['DNI_FRENTE', 'DNI_DORSO', 'ANALITICO_SECUNDARIO'];

const INTAKE_LABEL = { FIRST: 'Primer ingreso', SECOND: 'Segundo ingreso' };

const DEBT_VIOLATION_LABEL = {
  OVERDUE_DEBT: 'Tiene cuotas vencidas sin pagar.',
  OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT: 'Tiene deuda de una inscripción anterior que dio de baja.',
};

export const GENERIC_DEBT_MESSAGE = 'Tiene deuda pendiente que bloquea una nueva inscripción.';

export const ALREADY_RESOLVED_CODE = 'INVALID_PROGRAM_ENROLLMENT_APPLICATION_STATE';

export function cohortLabel(cohortName) {
  if (!cohortName) return null;
  const match = /^(FIRST|SECOND)\s+(\d{4})$/.exec(cohortName.trim());
  return match ? `${INTAKE_LABEL[match[1]]} ${match[2]}` : cohortName;
}

export function missingDocumentsText(missingTypes) {
  const labels = (missingTypes ?? []).map((type) => DOCUMENT_SHORT_LABEL[type] ?? type);
  if (labels.length <= 1) return labels.join('');
  return `${labels.slice(0, -1).join(', ')} y ${labels[labels.length - 1]}`;
}

export function documentationLabel(documentation) {
  if (!documentation) return '—';
  if (documentation.complete) return 'Completa';
  return `Falta ${missingDocumentsText(documentation.missingTypes)}`;
}

export function documentationFilterValue(documentation) {
  return documentation?.complete ? 'Completa' : 'Incompleta';
}

export function debtMessages(debt) {
  if (!debt?.blocksApproval) return [];
  const codes = debt.violationCodes ?? [];
  if (!codes.length) return [GENERIC_DEBT_MESSAGE];
  return codes.map((code) => DEBT_VIOLATION_LABEL[code] ?? GENERIC_DEBT_MESSAGE);
}

export function approvalBlockReason(check) {
  if (!check || check.canApprove) return null;
  const reasons = [];
  if (check.documentation && !check.documentation.complete) {
    reasons.push(`falta ${missingDocumentsText(check.documentation.missingTypes)}`);
  }
  if (check.debt?.blocksApproval) reasons.push('el alumno tiene deuda');
  return reasons.length
    ? `Aprobar está deshabilitado: ${reasons.join(' y ')}.`
    : 'Aprobar está deshabilitado: hay condiciones sin cumplir.';
}

export function isApplicationAlreadyResolved(error) {
  return error?.status === 409 && error.code === ALREADY_RESOLVED_CODE;
}

export function sortByRequestedAt(applications, direction = 'asc') {
  const sign = direction === 'asc' ? 1 : -1;
  return [...applications].sort((a, b) => sign * (new Date(a.requestedAt) - new Date(b.requestedAt)));
}

export function nextPendingId(pending, currentId) {
  const next = sortByRequestedAt((pending ?? []).filter((application) => application.id !== currentId))[0];
  return next?.id ?? null;
}
