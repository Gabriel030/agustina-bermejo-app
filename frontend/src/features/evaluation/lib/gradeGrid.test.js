import { describe, expect, it } from 'vitest';
import {
  buildGradePayload,
  collectPendingJobs,
  correctionOf,
  formatScore,
  hasInvalidDraft,
  isValidScore,
  parseScore,
  patchDraft,
  scoreTextState,
  settleDraft,
  summarizeSave,
} from './gradeGrid';

const exam1 = { type: 'EXAM', sequenceNumber: 1, date: '2026-09-15' };
const final = { type: 'FINAL', sequenceNumber: 1, date: null };
const saved = { gradeItemId: 5, type: 'EXAM', sequenceNumber: 1, score: 7, date: '2026-09-15T10:00:00', comments: 'ok' };
const now = new Date(2026, 10, 2, 9, 5, 7);

describe('score parsing', () => {
  it('accepts a decimal comma and rejects out of range or more than two decimals', () => {
    expect(parseScore('7,5')).toBe(7.5);
    expect(scoreTextState('7,5')).toBe('valid');
    expect(scoreTextState('')).toBe('empty');
    expect(scoreTextState('0,5')).toBe('invalid');
    expect(scoreTextState('10,5')).toBe('invalid');
    expect(scoreTextState('7,555')).toBe('invalid');
    expect(scoreTextState('abc')).toBe('invalid');
    expect(isValidScore(10)).toBe(true);
    expect(isValidScore(1)).toBe(true);
  });

  it('formats with a decimal comma', () => {
    expect(formatScore(8.5)).toBe('8,5');
    expect(formatScore(null)).toBe('');
  });
});

describe('drafts', () => {
  it('drops the draft when the text goes back to the saved value', () => {
    const edited = patchDraft({}, 'k', saved, { score: '8' });
    expect(edited.k.score).toBe('8');
    expect(patchDraft(edited, 'k', saved, { score: '7' })).toEqual({});
  });

  it('keeps an empty draft while typing and drops it on settle', () => {
    const cleared = patchDraft({}, 'k', saved, { score: '' });
    expect(cleared.k.score).toBe('');
    expect(settleDraft(cleared, 'k')).toEqual({});
    expect(settleDraft({ k: { score: '3', comments: '', date: '' } }, 'k').k.score).toBe('3');
  });

  it('shows previous to new only for a valid different score over a saved one', () => {
    expect(correctionOf({ score: '8' }, saved)).toEqual({ previous: 7, next: 8 });
    expect(correctionOf({ score: '7' }, saved)).toBeNull();
    expect(correctionOf({ score: '11' }, saved)).toBeNull();
    expect(correctionOf({ score: '8' }, undefined)).toBeNull();
  });

  it('flags invalid drafts', () => {
    expect(hasInvalidDraft({ a: { score: '11' } })).toBe(true);
    expect(hasInvalidDraft({ a: { score: '9' }, b: { score: '' } })).toBe(false);
  });
});

describe('payload', () => {
  it('reuses the saved date and trims comments for a correction', () => {
    const payload = buildGradePayload({ column: exam1, draft: { score: '8,5', comments: ' bien ', date: '2026-09-15' }, saved, now });
    expect(payload).toEqual({ type: 'EXAM', sequenceNumber: 1, score: 8.5, date: '2026-09-15T10:00:00', comments: 'bien' });
  });

  it('uses the current local date time for a new grade and null for blank comments', () => {
    const payload = buildGradePayload({ column: final, draft: { score: '9', comments: '', date: '' }, saved: undefined, now });
    expect(payload).toEqual({ type: 'FINAL', sequenceNumber: 1, score: 9, date: '2026-11-02T09:05:07', comments: null });
  });

  it('uses the chosen day when it differs from the saved one', () => {
    const payload = buildGradePayload({ column: exam1, draft: { score: '7', comments: 'ok', date: '2026-10-01' }, saved, now });
    expect(payload.date).toBe('2026-10-01T12:00:00');
  });
});

describe('pending jobs', () => {
  const students = [
    { enrollmentId: 1, grades: [saved] },
    { enrollmentId: 2, grades: [] },
  ];
  const draft = (score) => ({ score, comments: '', date: '' });

  it('collects only valid drafts that are not already saving', () => {
    const drafts = { '1:EXAM-1': draft('8'), '2:EXAM-1': draft('11') };
    expect(collectPendingJobs({ students, columns: [exam1], drafts, statuses: {}, now }).map((job) => job.enrollmentId)).toEqual([1]);
    expect(collectPendingJobs({ students, columns: [exam1], drafts, statuses: { '1:EXAM-1': 'saving' }, now })).toEqual([]);
  });
});

describe('summary', () => {
  it('pluralizes and reports failures', () => {
    expect(summarizeSave(1, 0)).toBe('1 nota guardada');
    expect(summarizeSave(3, 0)).toBe('3 notas guardadas');
    expect(summarizeSave(2, 1)).toBe('2 guardadas, 1 con error');
  });
});
