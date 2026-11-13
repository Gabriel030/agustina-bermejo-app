export const MAX_JUSTIFICATION_CHARS = 500;

const ABSENCE_STATUSES = ['ABSENT', 'JUSTIFICATION_PENDING', 'JUSTIFIED'];
const COMPUTED_STATUSES = ['ABSENT', 'JUSTIFICATION_PENDING'];

export function formatIsoDate(isoDate) {
  const [year, month, day] = isoDate.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

export function listAbsences(entries = []) {
  return entries
    .filter((entry) => ABSENCE_STATUSES.includes(entry.status))
    .sort((a, b) => b.classDate.localeCompare(a.classDate));
}

export function countComputedAbsences(entries = []) {
  return entries.filter((entry) => COMPUTED_STATUSES.includes(entry.status)).length;
}

export function describeAbsenceStatus(entry) {
  if (entry.status === 'JUSTIFIED') return { label: 'Justificada', tone: 'success' };
  if (entry.status === 'JUSTIFICATION_PENDING') return { label: 'Justificación pendiente', tone: 'accent' };
  if (entry.justificationRejectionReason) return { label: 'Justificación rechazada', tone: 'danger' };
  return { label: 'Ausente', tone: 'danger' };
}

export function describeDeadline(entry) {
  if (entry.status !== 'ABSENT' || !entry.justificationDeadline) return null;
  const until = formatIsoDate(entry.justificationDeadline);
  return entry.justifiable === true
    ? { open: true, label: 'Dentro de plazo', detail: `hasta ${until}` }
    : { open: false, label: 'Plazo vencido', detail: `el ${until}` };
}

export function countRemainingChars(reason, exceptionReason) {
  return MAX_JUSTIFICATION_CHARS - reason.length - exceptionReason.length;
}

export function canSubmitJustification({ reason, exceptionReason, expired }) {
  if (!reason.trim()) return false;
  if (expired && !exceptionReason.trim()) return false;
  return countRemainingChars(reason, expired ? exceptionReason : '') >= 0;
}

export function buildJustificationImpact(computedAbsences) {
  return [
    { key: 'status', label: 'Estado', today: 'Ausente', next: 'Justificada', difference: 'Cambia' },
    {
      key: 'computed',
      label: 'Inasistencias computadas (materia)',
      today: computedAbsences,
      next: computedAbsences - 1,
      difference: '−1',
    },
  ];
}
