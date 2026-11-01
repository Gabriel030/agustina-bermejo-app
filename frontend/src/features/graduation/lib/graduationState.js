export const GRADUATION_STATE = {
  NOT_ELIGIBLE: 'NOT_ELIGIBLE',
  ELIGIBLE: 'ELIGIBLE',
  REQUESTED: 'REQUESTED',
  ADMIN_VERIFIED: 'ADMIN_VERIFIED',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
};

export const GRADUATION_STATE_PILL = {
  NOT_ELIGIBLE: { tone: 'neutral', label: 'Todavía no elegible' },
  ELIGIBLE: { tone: 'success', label: 'Elegible' },
  REQUESTED: { tone: 'warning', label: 'Solicitada' },
  ADMIN_VERIFIED: { tone: 'accent', label: 'En revisión de Dirección' },
  APPROVED: { tone: 'success', label: 'Egreso aprobado' },
  REJECTED: { tone: 'danger', label: 'Rechazada' },
};

export function deriveGraduationState(own) {
  const status = own.request?.status;
  if (own.graduated || status === 'APPROVED') return GRADUATION_STATE.APPROVED;
  if (status === 'REQUESTED') return GRADUATION_STATE.REQUESTED;
  if (status === 'ADMIN_VERIFIED') return GRADUATION_STATE.ADMIN_VERIFIED;
  if (status === 'REJECTED') return GRADUATION_STATE.REJECTED;
  return own.eligible ? GRADUATION_STATE.ELIGIBLE : GRADUATION_STATE.NOT_ELIGIBLE;
}

export function canRequestGraduation(own) {
  const state = deriveGraduationState(own);
  return state === GRADUATION_STATE.ELIGIBLE || (state === GRADUATION_STATE.REJECTED && own.eligible);
}

export function missingCredits(own) {
  return Math.max(0, own.requiredCredits - own.accumulatedCredits);
}

export function graduationRequestErrorMessage(error) {
  if (error?.status === 409) return 'Ya tenés una solicitud de egreso activa para esta carrera.';
  if (error?.status === 400) return 'Todavía no cumplís los requisitos del plan para solicitar el egreso.';
  return error?.message ?? 'No pudimos enviar tu solicitud. Probá de nuevo.';
}
