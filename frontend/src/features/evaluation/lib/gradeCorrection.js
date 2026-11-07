import { formatScore, isValidScore, parseScore } from './gradeGrid';

export const CORRECTION_REASON_MIN = 5;
export const CORRECTION_REASON_MAX = 500;

export function isCorrectableCell(cell) {
  return Boolean(cell) && cell.published === true && cell.gradeItemId != null && cell.score != null;
}

export function correctionDifference(previous, next) {
  const delta = Math.round((next - previous) * 100) / 100;
  const sign = delta > 0 ? '+' : delta < 0 ? '−' : '';
  return `${sign}${formatScore(Math.abs(delta))}`;
}

export function correctionFormState({ scoreText, reasonText, currentScore }) {
  const value = parseScore(scoreText);
  const reason = reasonText.trim();
  const scoreInvalid = value !== null && !isValidScore(value);
  const unchanged = value !== null && isValidScore(value) && value === currentScore;
  const reasonTooShort = reasonText.length > 0 && reason.length < CORRECTION_REASON_MIN;
  const reasonTooLong = reasonText.length > CORRECTION_REASON_MAX;
  const ready = value !== null && !scoreInvalid && !unchanged && reason.length >= CORRECTION_REASON_MIN && !reasonTooLong;
  return { value, reason, scoreInvalid, unchanged, reasonTooShort, reasonTooLong, ready };
}

const ERROR_OUTCOMES = {
  GRADE_CORRECTION_PERIOD_ENDED: { message: 'No se pudo corregir: el cuatrimestre ya terminó.', periodEnded: true },
  GRADE_ITEM_NOT_PUBLISHED: { message: 'Esa nota ya no está publicada. Actualizamos la grilla.', periodEnded: false },
  GRADE_CORRECTION_NO_CHANGE: { message: 'La nota nueva es igual a la vigente. No se guardó nada.', periodEnded: false },
};

export function correctionErrorOutcome(error) {
  return ERROR_OUTCOMES[error?.code] ?? null;
}

export function sortAuditNewestFirst(entries) {
  return [...entries].sort((a, b) => (a.changedAt < b.changedAt ? 1 : a.changedAt > b.changedAt ? -1 : b.id - a.id));
}

export function formatAuditDate(isoDateTime) {
  const [date, time] = String(isoDateTime).split('T');
  const [year, month, day] = date.split('-');
  return `${day}/${month}/${year} ${(time ?? '').slice(0, 5)}`.trim();
}
