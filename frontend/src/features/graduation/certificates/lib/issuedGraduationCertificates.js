export const ISSUED_KIND_TABS = [
  { value: 'PROGRAM', label: 'Carrera' },
  { value: 'COURSE', label: 'Curso' },
];

export const ISSUED_STATUS_OPTIONS = [
  { value: 'ALL', label: 'Todos los estados' },
  { value: 'VALID', label: 'Emitidos' },
  { value: 'REVOKED', label: 'Revocados' },
];

export const REASON_MIN_LENGTH = 10;
export const REASON_MAX_LENGTH = 500;

const ISSUED_STATUS = {
  VALID: { label: 'Emitido', tone: 'success' },
  REVOKED: { label: 'Revocado', tone: 'danger' },
};

export function issuedStatusOf(status) {
  return ISSUED_STATUS[status] ?? { label: status, tone: 'neutral' };
}

export function isDownloadable(certificate) {
  return certificate.status === 'VALID';
}

export function isRevocable(certificate) {
  return certificate.status === 'VALID';
}

export function reasonLengthOf(reason) {
  return reason.trim().length;
}

export function reasonProblemOf(reason) {
  const length = reasonLengthOf(reason);
  if (length < REASON_MIN_LENGTH || length > REASON_MAX_LENGTH) {
    return `El motivo debe tener entre ${REASON_MIN_LENGTH} y ${REASON_MAX_LENGTH} caracteres.`;
  }
  return null;
}

export function excessCharactersOf(reason) {
  return Math.max(0, reasonLengthOf(reason) - REASON_MAX_LENGTH);
}

export function reasonCounterOf(reason) {
  return `${reasonLengthOf(reason)}/${REASON_MAX_LENGTH}`;
}

export const ACTION_COPY = {
  revoke: {
    title: 'Revocar certificado',
    confirmTitle: 'Confirmar revocación',
    confirmLabel: 'Revocar certificado',
    confirmVariant: 'destructive',
    resultTitle: 'Certificado revocado',
    description: 'El certificado deja de verificar y ya no se puede descargar.',
    confirmDescription: 'Su código deja de verificar. Esta acción no se puede deshacer.',
    fieldLabel: 'Motivo de la revocación',
  },
  reissue: {
    title: 'Reemitir certificado',
    confirmTitle: 'Confirmar reemisión',
    confirmLabel: 'Reemitir certificado',
    confirmVariant: 'gradient',
    resultTitle: 'Certificado reemitido',
    description: 'Se emite un certificado nuevo que reemplaza a este.',
    confirmDescription: 'Se emite un certificado nuevo con la plantilla vigente y queda vinculado a este.',
    fieldLabel: 'Motivo de la reemisión',
  },
};
