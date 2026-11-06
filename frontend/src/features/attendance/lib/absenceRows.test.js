import { describe, expect, it } from 'vitest';
import {
  buildJustificationImpact,
  canSubmitJustification,
  countComputedAbsences,
  countRemainingChars,
  describeAbsenceStatus,
  describeDeadline,
  formatIsoDate,
  listAbsences,
} from './absenceRows';

describe('absenceRows', () => {
  it('formats an iso date or date-time as dd/mm/yyyy', () => {
    expect(formatIsoDate('2026-11-05')).toBe('05/11/2026');
    expect(formatIsoDate('2026-11-05T10:00:00')).toBe('05/11/2026');
  });

  it('keeps only absences, newest first', () => {
    const entries = [
      { id: 1, classDate: '2026-10-01', status: 'PRESENT' },
      { id: 2, classDate: '2026-10-02', status: 'ABSENT' },
      { id: 3, classDate: '2026-10-09', status: 'JUSTIFIED' },
      { id: 4, classDate: '2026-10-05', status: 'LATE' },
    ];
    expect(listAbsences(entries).map((e) => e.id)).toEqual([3, 2]);
  });

  it('counts absent and pending absences but not justified ones', () => {
    const entries = [{ status: 'ABSENT' }, { status: 'JUSTIFICATION_PENDING' }, { status: 'JUSTIFIED' }, { status: 'PRESENT' }];
    expect(countComputedAbsences(entries)).toBe(2);
  });

  it('tells a rejected absence from a plain one', () => {
    expect(describeAbsenceStatus({ status: 'ABSENT', justificationRejectionReason: 'x' }).label).toBe('Justificación rechazada');
    expect(describeAbsenceStatus({ status: 'ABSENT', justificationRejectionReason: '' }).label).toBe('Ausente');
  });

  it('derives the deadline from the justifiable flag and hides it when missing', () => {
    expect(describeDeadline({ status: 'ABSENT', justifiable: true, justificationDeadline: '2026-11-12' })).toMatchObject({ open: true });
    expect(describeDeadline({ status: 'ABSENT', justifiable: false, justificationDeadline: '2026-11-05' })).toMatchObject({ open: false });
    expect(describeDeadline({ status: 'ABSENT', justifiable: null, justificationDeadline: null })).toBeNull();
    expect(describeDeadline({ status: 'JUSTIFIED', justifiable: null, justificationDeadline: '2026-11-05' })).toBeNull();
  });

  it('allows exactly 500 shared characters and rejects 501', () => {
    expect(countRemainingChars('a'.repeat(300), 'b'.repeat(200))).toBe(0);
    expect(canSubmitJustification({ reason: 'a'.repeat(300), exceptionReason: 'b'.repeat(200), expired: true })).toBe(true);
    expect(canSubmitJustification({ reason: 'a'.repeat(300), exceptionReason: 'b'.repeat(201), expired: true })).toBe(false);
  });

  it('ignores the exception text when the deadline is open', () => {
    expect(canSubmitJustification({ reason: 'a'.repeat(500), exceptionReason: 'zzz', expired: false })).toBe(true);
    expect(canSubmitJustification({ reason: 'a', exceptionReason: '', expired: true })).toBe(false);
    expect(canSubmitJustification({ reason: '  ', exceptionReason: 'b', expired: true })).toBe(false);
  });

  it('builds the before and after rows from the whole subject count', () => {
    const rows = buildJustificationImpact(3);
    expect(rows[1]).toMatchObject({ today: 3, next: 2, difference: '−1' });
    expect(rows[0]).toMatchObject({ today: 'Ausente', next: 'Justificada' });
  });
});
