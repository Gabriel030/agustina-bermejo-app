export const WAITLIST_BASE_SLUG = 'inscripciones/listas-de-espera';
export const WAITLIST_REASON_MIN_LENGTH = 10;
export const WAITLIST_REASON_MAX_LENGTH = 500;

export const WAITLIST_KIND_LABEL = {
  PROGRAM_SECTION: 'Materia',
  COURSE_SECTION: 'Módulo de curso',
  COURSE: 'Curso',
};

export const WAITLIST_KIND_OPTIONS = [
  { value: 'ALL', label: 'Todos los tipos' },
  { value: 'PROGRAM_SECTION', label: 'Materia' },
  { value: 'COURSE_SECTION', label: 'Módulo de curso' },
  { value: 'COURSE', label: 'Curso' },
];

export const EXPIRING_WITHIN_DAYS = 7;

export const VERDICT_REASON = {
  ALREADY_PASSED: {
    label: 'Salteado: ya aprobó',
    tip: 'Conserva su lugar. Ya aprobó la materia. Sigue en la cola pero no se lo promueve.',
  },
  PREREQUISITES_NOT_MET: {
    label: 'Salteado: correlativa',
    tip: 'Conserva su lugar. Le falta una correlativa. Sigue en la cola pero no se lo promueve.',
  },
  SCHEDULE_OVERLAP: {
    label: 'Salteado: horarios',
    tip: 'Conserva su lugar. Sus horarios se superponen con otra cursada. Sigue en la cola pero no se lo promueve.',
  },
  ADMISSION_RECEIPT_PENDING: {
    label: 'Salteado: comprobante',
    tip: 'Conserva su lugar. Su comprobante de admisión está pendiente de revisión. Se lo promueve cuando se apruebe.',
  },
};

export const ELIGIBLE_VERDICT = {
  label: 'Cumple',
  tip: 'Cumple los requisitos para ser promovido.',
};

export function verdictPresentation(verdict) {
  if (verdict?.eligible) return { ...ELIGIBLE_VERDICT, tone: 'success' };
  const reason = VERDICT_REASON[verdict?.reason];
  return { label: reason?.label ?? 'Salteado', tip: reason?.tip ?? 'Conserva su lugar y no se lo promueve.', tone: 'warning' };
}

export const HISTORY_ACTION = {
  ENROLLMENT_WITHDRAWN_BY_STAFF: { label: 'Baja registrada por el personal', tone: 'danger' },
  ENROLLMENT_SECTION_CHANGED_BY_STAFF: { label: 'Cambio de comisión por el personal', tone: 'accent' },
};

export function historyActionPresentation(action) {
  return HISTORY_ACTION[action] ?? { label: 'Movimiento registrado', tone: 'neutral' };
}

export function firstEligiblePosition(entries) {
  const index = (entries ?? []).findIndex((entry) => entry.verdict?.eligible);
  return index < 0 ? null : entries[index].position;
}

export function waitlistPath(kind, id) {
  return `${WAITLIST_BASE_SLUG}/${kind}/${id}`;
}

export function promoteBlockedReason(entry, free) {
  if (free != null && free < 1) return 'No hay lugares libres. Ampliá el cupo.';
  if (!entry.verdict?.eligible) return 'No cumple los requisitos. Conserva su lugar en la cola.';
  return null;
}
