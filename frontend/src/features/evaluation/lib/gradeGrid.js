export const GRID_CONDITION = {
  IN_PROGRESS: { tone: 'accent', label: 'Cursando' },
  REGULAR: { tone: 'success', label: 'Regular' },
  FREE: { tone: 'danger', label: 'Libre' },
  APPROVED: { tone: 'success', label: 'Aprobado' },
  FAILED: { tone: 'danger', label: 'Desaprobado' },
};

export function gradeColumnKey(type, sequenceNumber) {
  return `${type}-${sequenceNumber}`;
}

export function gradeColumnLabel({ type, sequenceNumber }) {
  if (type === 'EXAM') return `Parcial ${sequenceNumber}`;
  if (type === 'TP') return `TP ${sequenceNumber}`;
  return sequenceNumber > 1 ? `Final ${sequenceNumber}` : 'Final';
}

export function draftKey(enrollmentId, type, sequenceNumber) {
  return `${enrollmentId}:${gradeColumnKey(type, sequenceNumber)}`;
}

export function formatScore(score) {
  return score == null ? '' : String(score).replace('.', ',');
}

export function parseScore(text) {
  const trimmed = String(text ?? '').trim();
  if (trimmed === '') return null;
  const value = Number(trimmed.replace(',', '.'));
  return Number.isNaN(value) ? Number.NaN : value;
}

export function isValidScore(value) {
  return value >= 1 && value <= 10 && Math.abs(value * 100 - Math.round(value * 100)) < 1e-9;
}

export function scoreTextState(text) {
  const value = parseScore(text);
  if (value === null) return 'empty';
  return isValidScore(value) ? 'valid' : 'invalid';
}

function savedDay(saved) {
  return saved?.date ? saved.date.slice(0, 10) : '';
}

function seedDraft(saved) {
  return { score: formatScore(saved?.score), comments: saved?.comments ?? '', date: savedDay(saved) };
}

function differsFromSaved(draft, saved) {
  const seed = seedDraft(saved);
  return draft.score !== seed.score || draft.comments !== seed.comments || draft.date !== seed.date;
}

export function patchDraft(drafts, key, saved, patch) {
  const next = { ...(drafts[key] ?? seedDraft(saved)), ...patch };
  const rest = { ...drafts };
  if (!differsFromSaved(next, saved)) {
    delete rest[key];
    return rest;
  }
  rest[key] = next;
  return rest;
}

export function settleDraft(drafts, key) {
  const draft = drafts[key];
  if (!draft || scoreTextState(draft.score) !== 'empty') return drafts;
  const rest = { ...drafts };
  delete rest[key];
  return rest;
}

export function correctionOf(draft, saved) {
  if (!draft || saved?.score == null) return null;
  const value = parseScore(draft.score);
  if (value === null || !isValidScore(value) || value === saved.score) return null;
  return { previous: saved.score, next: value };
}

export function nowLocalDateTime(now = new Date()) {
  const pad = (number) => String(number).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

export function buildGradePayload({ column, draft, saved, now }) {
  const day = savedDay(saved);
  const date = draft.date && draft.date !== day ? `${draft.date}T12:00:00` : (saved?.date ?? nowLocalDateTime(now));
  return {
    type: column.type,
    sequenceNumber: column.sequenceNumber,
    score: parseScore(draft.score),
    date,
    comments: draft.comments.trim() === '' ? null : draft.comments.trim(),
  };
}

export function findSavedCell(student, column) {
  return student.grades.find((grade) => grade.type === column.type && grade.sequenceNumber === column.sequenceNumber);
}

export function collectPendingJobs({ students, columns, drafts, statuses, now }) {
  const jobs = [];
  for (const student of students) {
    for (const column of columns) {
      const key = draftKey(student.enrollmentId, column.type, column.sequenceNumber);
      const draft = drafts[key];
      if (!draft || statuses[key] === 'saving' || scoreTextState(draft.score) !== 'valid') continue;
      const saved = findSavedCell(student, column);
      jobs.push({ key, enrollmentId: student.enrollmentId, payload: buildGradePayload({ column, draft, saved, now }) });
    }
  }
  return jobs;
}

export function hasInvalidDraft(drafts) {
  return Object.values(drafts).some((draft) => scoreTextState(draft.score) === 'invalid');
}

export function summarizeSave(saved, failed) {
  if (failed > 0) return `${saved} guardadas, ${failed} con error`;
  return saved === 1 ? '1 nota guardada' : `${saved} notas guardadas`;
}

export function schemeSummary(grid) {
  const parts = [];
  if (grid.minRegularGrade != null) parts.push(`Regular desde ${formatScore(grid.minRegularGrade)}`);
  if (grid.minApprovalGrade != null) parts.push(`aprueba con ${formatScore(grid.minApprovalGrade)}`);
  if (grid.promotionMinGrade != null) parts.push(`promoción desde ${formatScore(grid.promotionMinGrade)}`);
  return parts.join(', ');
}
