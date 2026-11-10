import { formatScore, parseScore, scoreTextState } from './gradeGrid';

export const BOARD_RESULT = {
  PASSED: { tone: 'success', label: 'Aprobado' },
  FAILED: { tone: 'danger', label: 'Desaprobado' },
  ABSENT: { tone: 'neutral', label: 'Ausente' },
  PENDING: { tone: 'accent', label: 'Pendiente' },
  NOT_REGULAR: { tone: 'warning', label: 'No regular' },
};

export function isSavedAbsent(row) {
  return row.status === 'ABSENT';
}

export function isBlockedRow(row) {
  return row.priorCondition === 'FREE' && row.finalScore == null && !isSavedAbsent(row);
}

export function isGradableRow(row) {
  return row.status !== 'WITHDRAWN' && row.finalScore == null && !isBlockedRow(row);
}

export function patchBoardDraft(drafts, row, patch) {
  const next = { text: '', absent: isSavedAbsent(row), ...drafts[row.id], ...patch };
  const rest = { ...drafts };
  if (next.text === '' && next.absent === isSavedAbsent(row)) {
    delete rest[row.id];
    return rest;
  }
  rest[row.id] = next;
  return rest;
}

export function removeBoardDraft(drafts, registrationId) {
  const rest = { ...drafts };
  delete rest[registrationId];
  return rest;
}

export function isInvalidBoardDraft(draft) {
  return Boolean(draft) && !draft.absent && scoreTextState(draft.text) === 'invalid';
}

export function hasInvalidBoardDraft(drafts) {
  return Object.values(drafts).some(isInvalidBoardDraft);
}

function draftScore(draft) {
  if (!draft || draft.absent || scoreTextState(draft.text) !== 'valid') return null;
  return parseScore(draft.text);
}

export function boardChanges(rows, drafts) {
  return rows.flatMap((row) => {
    const draft = drafts[row.id];
    if (!draft || !isGradableRow(row)) return [];
    if (draft.absent && !isSavedAbsent(row)) return [{ row, absent: true, score: null }];
    const score = draftScore(draft);
    return score === null ? [] : [{ row, absent: false, score }];
  });
}

export function buildFinalGradesPayload(rows, drafts) {
  return {
    items: boardChanges(rows, drafts).map(({ row, absent, score }) =>
      absent ? { registrationId: row.id, absent: true } : { registrationId: row.id, score },
    ),
  };
}

export function boardRowResult(row, draft) {
  if (draft?.absent) return 'ABSENT';
  const score = draftScore(draft);
  if (score !== null) return score >= row.minApprovalGrade ? 'PASSED' : 'FAILED';
  if (draft && isSavedAbsent(row)) return 'PENDING';
  if (row.result === 'PASSED' || row.result === 'FAILED' || row.result === 'ABSENT') return row.result;
  if (isSavedAbsent(row)) return 'ABSENT';
  return isBlockedRow(row) ? 'NOT_REGULAR' : 'PENDING';
}

export function summarizeBoard(rows, drafts) {
  const counts = { total: rows.length, graded: 0, absent: 0, pending: 0 };
  rows.forEach((row) => {
    const result = boardRowResult(row, drafts[row.id]);
    if (result === 'PASSED' || result === 'FAILED') counts.graded += 1;
    else if (result === 'ABSENT') counts.absent += 1;
    else if (result === 'PENDING') counts.pending += 1;
  });
  return counts;
}

function savedLabel(row) {
  if (isSavedAbsent(row)) return 'Ausente';
  return row.finalScore == null ? 'Sin nota' : formatScore(row.finalScore);
}

export function boardChangeRows(rows, drafts) {
  return boardChanges(rows, drafts).map(({ row, absent, score }) => ({
    key: row.id,
    label: row.studentName,
    today: savedLabel(row),
    next: absent ? 'Ausente' : formatScore(score),
    difference: `${BOARD_RESULT[boardRowResult(row, undefined)].label} → ${BOARD_RESULT[boardRowResult(row, drafts[row.id])].label}`,
  }));
}

export function summarizeBoardSave(changes) {
  const absent = changes.filter((change) => change.absent).length;
  const graded = changes.length - absent;
  const parts = [];
  if (graded) parts.push(`${graded} ${graded === 1 ? 'nota' : 'notas'}`);
  if (absent) parts.push(`${absent} ${absent === 1 ? 'ausente' : 'ausentes'}`);
  return `Se guardaron ${parts.join(' y ')}.`;
}

export function boardAccess({ role, mesa, now = new Date() }) {
  if (mesa.status !== 'SCHEDULED' || role !== 'docente') return 'read';
  return now < new Date(mesa.examDate) ? 'locked' : 'edit';
}
