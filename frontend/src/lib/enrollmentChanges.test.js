import { describe, expect, it } from 'vitest';
import {
  buildChangeRows,
  describeOverlap,
  describeSlot,
  matchesConfirmation,
  permissionsFor,
  reasonError,
  sectionActionsOf,
  splitCandidates,
  withdrawalDateError,
  withdrawalDateRange,
} from './enrollmentChanges';

describe('enrollmentChanges', () => {
  it('keeps whole program and course withdrawals away from the preceptor', () => {
    expect(permissionsFor('preceptor')).toMatchObject({ canWithdrawSubject: true, canWithdrawProgram: false, canWithdrawCourse: false });
    expect(permissionsFor('director')).toMatchObject({ canWithdrawProgram: true, canWithdrawModule: true });
    expect(permissionsFor('alumno').canWithdrawSubject).toBe(false);
  });

  it('disables the section change for a waitlisted subject and hides actions for closed ones', () => {
    const actions = sectionActionsOf({ status: 'WAITLISTED' }, permissionsFor('preceptor'));
    expect(actions.find((action) => action.kind === 'change-section').disabledReason).toMatch(/lista de espera/);
    expect(actions.find((action) => action.kind === 'withdraw-subject').disabledReason).toBeNull();
    expect(sectionActionsOf({ status: 'WITHDRAWN' }, permissionsFor('preceptor'))).toEqual([]);
  });

  it('compares the confirmation word ignoring case and accents', () => {
    expect(matchesConfirmation(' baja ', 'BAJA')).toBe(true);
    expect(matchesConfirmation('bája', 'BAJA')).toBe(true);
    expect(matchesConfirmation('bajas', 'BAJA')).toBe(false);
  });

  it('requires a reason of at least ten characters', () => {
    expect(reasonError('')).toMatch(/obligatorio/);
    expect(reasonError('corto')).toMatch(/10/);
    expect(reasonError('  motivo largo  ')).toBeNull();
  });

  it('limits the withdrawal date from the previous month start (or enrollment) to today', () => {
    const today = new Date(2026, 10, 14);
    expect(withdrawalDateRange('2026-03-01', today)).toEqual({ min: '2026-10-01', max: '2026-11-14' });
    expect(withdrawalDateRange('2026-11-05', today)).toEqual({ min: '2026-11-05', max: '2026-11-14' });
    const range = withdrawalDateRange(null, today);
    expect(withdrawalDateError('2026-09-30', range)).toMatch(/fuera del rango/);
    expect(withdrawalDateError('2026-11-15', range)).toMatch(/fuera del rango/);
    expect(withdrawalDateError('2026-11-01', range)).toBeNull();
  });

  it('splits candidates and describes slots and the overlap error', () => {
    const { available, unavailable } = splitCandidates([{ sectionId: 1, selectable: true }, { sectionId: 2, selectable: false }]);
    expect(available).toHaveLength(1);
    expect(unavailable).toHaveLength(1);
    expect(describeSlot({ dayOfWeek: 'MONDAY', startTime: '08:00:00', endTime: '10:00:00', classroomName: 'Aula 3' })).toBe('lunes 08:00 a 10:00 · Aula 3');
    expect(
      describeOverlap({ code: 'SECTION_SCHEDULE_OVERLAP', details: { conflictingSubject: 'Álgebra', day: 'lunes', from: '08:00', to: '10:00' } }),
    ).toBe('Se superpone con Álgebra (lunes de 08:00 a 10:00).');
  });

  it('builds the Hoy / Nuevo diff with attendance kept', () => {
    const rows = buildChangeRows(
      { sectionName: 'A', schedule: 'Lun 18:30' },
      { name: 'B', availableSeats: 3, slots: [{ dayOfWeek: 'TUESDAY', startTime: '10:00:00', endTime: '12:00:00' }] },
    );
    expect(rows.map((row) => row.key)).toEqual(['section', 'schedule', 'seats', 'attendance']);
    expect(rows[2].next).toBe('3 lugares libres');
    expect(rows[3].unchanged).toBe(true);
  });
});
