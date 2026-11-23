const ISSUER_ROLES = ['docente', 'administrativo', 'director'];

const STATES = {
  AVAILABLE: { label: 'Para emitir', tone: 'accent', selectable: true },
  ALREADY_ISSUED: { label: 'Emitida', tone: 'success', selectable: false },
  ABSENT: { label: 'Ausente', tone: 'neutral', selectable: false },
};

const UNKNOWN_STATE = { label: 'No disponible', tone: 'neutral', selectable: false };

const OMISSION_REASONS = {
  ALREADY_ISSUED: 'ya tenía una constancia vigente y no se emitió otra.',
  ABSENT: 'figura como ausente y no se emitió.',
  NOT_REGISTERED: 'ya no está inscripto y no se emitió.',
};

const DEFAULT_OMISSION_REASON = 'no se pudo emitir su constancia.';

export function certificateCaps(role) {
  return { issues: ISSUER_ROLES.includes(role), revokes: role === 'director' };
}

export function candidateState(candidate) {
  return STATES[candidate.availability] ?? UNKNOWN_STATE;
}

export function isSelectable(candidate) {
  return candidateState(candidate).selectable;
}

export function hasIssuedCertificate(candidate) {
  return candidate.availability === 'ALREADY_ISSUED' && candidate.certificateId != null;
}

export function issueTitle(count) {
  return count === 1 ? 'Emitir 1 constancia' : `Emitir ${count} constancias`;
}

export function summarizeBatch(response) {
  const omitted = response.items
    .filter((item) => item.outcome !== 'ISSUED')
    .map((item) => ({
      registrationId: item.registrationId,
      studentName: item.studentName,
      reason: OMISSION_REASONS[item.outcome] ?? DEFAULT_OMISSION_REASON,
    }));
  return { issued: response.issuedCount, omitted };
}

export function issuedHeadline({ issued, omitted }) {
  const issuedText = issued === 1 ? '1 emitida' : `${issued} emitidas`;
  if (omitted.length === 0) return `${issuedText}.`;
  return `${issuedText}, ${omitted.length === 1 ? '1 omitida' : `${omitted.length} omitidas`}.`;
}
