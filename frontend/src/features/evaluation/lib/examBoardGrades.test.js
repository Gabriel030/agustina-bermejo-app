import { describe, expect, it } from 'vitest';
import {
  boardAccess,
  boardChangeRows,
  boardRowResult,
  buildFinalGradesPayload,
  hasInvalidBoardDraft,
  patchBoardDraft,
  summarizeBoardSave,
} from './examBoardGrades';

function row(id, extra = {}) {
  return { id, studentName: `Alumno ${id}`, status: 'REGISTERED', finalScore: null, priorCondition: 'REGULAR', minApprovalGrade: 6, result: null, ...extra };
}

describe('examBoardGrades', () => {
  it('drops a draft that matches the saved state', () => {
    const typed = patchBoardDraft({}, row(1), { text: '7' });
    expect(typed[1]).toEqual({ text: '7', absent: false });
    expect(patchBoardDraft(typed, row(1), { text: '' })).toEqual({});
  });

  it('keeps a saved absent row as no change until a grade is typed', () => {
    const saved = row(1, { status: 'ABSENT', result: 'ABSENT' });
    const toggled = patchBoardDraft({}, saved, { absent: false });
    expect(buildFinalGradesPayload([saved], toggled)).toEqual({ items: [] });
    const graded = patchBoardDraft(toggled, saved, { text: '8,5' });
    expect(buildFinalGradesPayload([saved], graded)).toEqual({ items: [{ registrationId: 1, score: 8.5 }] });
  });

  it('builds the payload with a numeric score or the absent mark and skips invalid drafts', () => {
    const rows = [row(1), row(2), row(3)];
    let drafts = patchBoardDraft({}, rows[0], { text: '7,5' });
    drafts = patchBoardDraft(drafts, rows[1], { absent: true, text: '' });
    drafts = patchBoardDraft(drafts, rows[2], { text: '11' });
    expect(hasInvalidBoardDraft(drafts)).toBe(true);
    expect(buildFinalGradesPayload(rows, drafts)).toEqual({ items: [{ registrationId: 1, score: 7.5 }, { registrationId: 2, absent: true }] });
  });

  it('derives the result from the approval threshold of each row', () => {
    const strict = row(1, { minApprovalGrade: 7 });
    expect(boardRowResult(strict, { text: '6,5', absent: false })).toBe('FAILED');
    expect(boardRowResult(strict, { text: '7', absent: false })).toBe('PASSED');
    expect(boardRowResult(row(2, { priorCondition: 'FREE' }))).toBe('NOT_REGULAR');
    expect(boardRowResult(row(3, { result: 'PASSED', finalScore: 8 }))).toBe('PASSED');
  });

  it('describes each change with the before and after result', () => {
    const rows = [row(1)];
    const drafts = patchBoardDraft({}, rows[0], { text: '4' });
    expect(boardChangeRows(rows, drafts)).toEqual([
      { key: 1, label: 'Alumno 1', today: 'Sin nota', next: '4', difference: 'Pendiente → Desaprobado' },
    ]);
  });

  it('allows loading only to the tribunal teacher once the exam date has passed', () => {
    const board = { status: 'SCHEDULED', examDate: '2026-11-05T18:00:00' };
    expect(boardAccess({ role: 'docente', mesa: board, now: new Date('2026-11-06T10:00:00') })).toBe('edit');
    expect(boardAccess({ role: 'docente', mesa: board, now: new Date('2026-11-04T10:00:00') })).toBe('locked');
    expect(boardAccess({ role: 'preceptor', mesa: board, now: new Date('2026-11-06T10:00:00') })).toBe('read');
    expect(boardAccess({ role: 'docente', mesa: { ...board, status: 'CLOSED' }, now: new Date('2026-11-06T10:00:00') })).toBe('read');
  });

  it('summarizes what was saved', () => {
    expect(summarizeBoardSave([{ absent: false }, { absent: false }, { absent: true }])).toBe('Se guardaron 2 notas y 1 ausente.');
  });
});
