import { afterEach, describe, expect, it, vi } from 'vitest';
import { DRAFT_TTL_MS, clearAttendanceDraft, loadAttendanceDraft, saveAttendanceDraft } from '@/features/attendance/lib/attendanceDraft';

afterEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('attendanceDraft', () => {
  it('recovers what was saved for the same section and date', () => {
    saveAttendanceDraft('PROGRAM', 5, '2026-11-04', { estados: { 11: 'ABSENT' }, topic: 'Tema', observations: '' }, 1000);
    expect(loadAttendanceDraft('PROGRAM', 5, '2026-11-04', 2000)).toEqual({ estados: { 11: 'ABSENT' }, topic: 'Tema', observations: '' });
    expect(loadAttendanceDraft('PROGRAM', 5, '2026-11-05', 2000)).toBeNull();
    expect(loadAttendanceDraft('COURSE', 5, '2026-11-04', 2000)).toBeNull();
  });

  it('discards and removes an expired draft', () => {
    saveAttendanceDraft('PROGRAM', 5, '2026-11-04', { estados: { 11: 'LATE' } }, 1000);
    expect(loadAttendanceDraft('PROGRAM', 5, '2026-11-04', 1000 + DRAFT_TTL_MS + 1)).toBeNull();
    expect(localStorage.getItem('attendanceDraft:PROGRAM:5:2026-11-04')).toBeNull();
  });

  it('clears the draft', () => {
    saveAttendanceDraft('PROGRAM', 5, '2026-11-04', { estados: { 11: 'LATE' } });
    clearAttendanceDraft('PROGRAM', 5, '2026-11-04');
    expect(loadAttendanceDraft('PROGRAM', 5, '2026-11-04')).toBeNull();
  });

  it('tolerates corrupt data and unavailable storage', () => {
    localStorage.setItem('attendanceDraft:PROGRAM:5:2026-11-04', '{no json');
    expect(loadAttendanceDraft('PROGRAM', 5, '2026-11-04')).toBeNull();
    vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });
    vi.spyOn(localStorage, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    expect(() => saveAttendanceDraft('PROGRAM', 5, '2026-11-04', { estados: { 1: 'LATE' } })).not.toThrow();
    expect(loadAttendanceDraft('PROGRAM', 5, '2026-11-04')).toBeNull();
  });
});
