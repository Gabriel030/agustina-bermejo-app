export const REVERT_WINDOW_DAYS = 7;

const DAY_MS = 24 * 60 * 60 * 1000;

function utcDayKey(date) {
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${date.getUTCFullYear()}-${month}-${day}`;
}

function localDayKey(now) {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

export function revertDeadlineKey(decidedAt) {
  if (!decidedAt) return null;
  const [year, month, day] = decidedAt.slice(0, 10).split('-').map(Number);
  return utcDayKey(new Date(Date.UTC(year, month - 1, day) + REVERT_WINDOW_DAYS * DAY_MS));
}

export function isRevertible(decidedAt, now = new Date()) {
  const deadline = revertDeadlineKey(decidedAt);
  return deadline !== null && localDayKey(now) <= deadline;
}

export function formatDayKey(dayKey) {
  if (!dayKey) return '';
  const [year, month, day] = dayKey.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

const REGISTERED = 'Queda registrado quién la revirtió y por qué.';

export function revertDialogCopy({ kind, result, studentName, previousStatusLabel }) {
  const justification = kind === 'justification';
  if (result === 'APPROVED') {
    return {
      title: justification ? 'Revertir justificación aprobada' : 'Revertir aprobación',
      description: justification
        ? `La inasistencia de ${studentName} vuelve a «Ausente» y la justificación vuelve a Pendientes. ${REGISTERED}`
        : `La asistencia de ${studentName} vuelve a «${previousStatusLabel}» y la solicitud vuelve a Pendientes. ${REGISTERED}`,
      placeholder: 'Explicá por qué se revierte la aprobación',
    };
  }
  return {
    title: justification ? 'Revertir rechazo de justificación' : 'Revertir rechazo',
    description: justification
      ? `La justificación vuelve a Pendientes para que la decidas de nuevo. La inasistencia sigue como Ausente. ${REGISTERED}`
      : `La solicitud vuelve a Pendientes para que la decidas de nuevo. La asistencia no cambia. ${REGISTERED}`,
    placeholder: 'Explicá por qué se revierte el rechazo',
  };
}
