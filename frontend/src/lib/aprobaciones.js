export const MIN_REJECTION_REASON_LENGTH = 10;

export const ESTADO_TRAMITE_LABEL = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  REQUESTED: { label: 'Pendiente', tone: 'warning' },
  ADMIN_VERIFIED: { label: 'Pendiente', tone: 'warning' },
  PENDING_APPROVAL: { label: 'Pendiente', tone: 'warning' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
};

export const TIPO_EXCEPCION_LABEL = {
  LATE_EXAM: 'Examen fuera de término',
  PREREQUISITE_WAIVER: 'Condonación de correlatividad',
};

const ALREADY_DECIDED_CODES = ['INVALID_DIRECTOR_APPROVAL_REQUEST_STATE', 'INVALID_GRADUATION_REQUEST_STATE', 'CYCLE_NOT_PENDING_APPROVAL'];

const DECISION_ERROR_MESSAGES = {
  GRADUATION_REQUIREMENTS_NOT_MET: 'No se puede aprobar: el alumno todavía no cumple los requisitos para egresar.',
  PENDING_DEBT: 'No se puede aprobar: el alumno tiene cuotas impagas.',
  PROGRAM_ENROLLMENT_REQUIRED: 'No se puede aprobar: la matrícula del alumno ya no está activa.',
};

export function isAlreadyDecided(error) {
  return error?.status === 409 && (!error.code || ALREADY_DECIDED_CODES.includes(error.code));
}

export function decisionErrorMessage(error) {
  if (isAlreadyDecided(error)) {
    return 'Otra persona de dirección ya decidió este trámite.';
  }
  return DECISION_ERROR_MESSAGES[error?.code] ?? 'No se pudo guardar la decisión. Probá de nuevo.';
}

export function daysSince(date, now = new Date()) {
  if (!date) {
    return null;
  }
  return Math.max(0, Math.floor((now - new Date(date)) / 86400000));
}

export function ageLabel(date, now = new Date()) {
  const days = daysSince(date, now);
  if (days === null) {
    return null;
  }
  if (days === 0) {
    return 'Hoy';
  }
  return days === 1 ? 'Hace 1 día' : `Hace ${days} días`;
}
