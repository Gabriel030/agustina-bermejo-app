import { describe, expect, it } from 'vitest';
import { attendanceRisk, permittedAbsences } from './attendanceRisk';

function entry(overrides = {}) {
  return { absenceCount: 0, classesHeld: 12, maxAbsenceRate: 25, remainingAbsences: 3, ...overrides };
}

describe('permittedAbsences', () => {
  it('derives the allowance from the rate and the classes held', () => {
    expect(permittedAbsences(entry())).toBe(3);
  });

  it('keeps the allowance when the student is over the maximum and remaining is clamped to zero', () => {
    expect(permittedAbsences(entry({ absenceCount: 4, remainingAbsences: 0 }))).toBe(3);
  });

  it('does not lose an absence to floating point error', () => {
    expect(permittedAbsences(entry({ classesHeld: 100, maxAbsenceRate: 29, remainingAbsences: 29 }))).toBe(29);
  });

  it('falls back to absences plus remaining when the rate is missing', () => {
    expect(permittedAbsences(entry({ maxAbsenceRate: null, absenceCount: 2, remainingAbsences: 1 }))).toBe(3);
  });

  it('is null when the backend sends no remaining absences', () => {
    expect(permittedAbsences(entry({ remainingAbsences: null }))).toBeNull();
  });
});

describe('attendanceRisk', () => {
  it('shows no pill when the student is comfortably inside the limit', () => {
    expect(attendanceRisk(entry())).toMatchObject({ level: 'ok', text: '0 de 3 faltas permitidas', pill: null });
  });

  it('warns when one absence is left', () => {
    expect(attendanceRisk(entry({ absenceCount: 2, remainingAbsences: 1 }))).toMatchObject({
      level: 'warning',
      pill: 'Queda 1 falta',
      text: '2 de 3 faltas permitidas',
    });
  });

  it('flags the limit when no absences are left', () => {
    expect(attendanceRisk(entry({ absenceCount: 3, remainingAbsences: 0 }))).toMatchObject({
      level: 'warning',
      pill: 'Al límite',
    });
  });

  it('flags over the maximum with the real allowance, never as 4 of 4', () => {
    expect(attendanceRisk(entry({ absenceCount: 4, remainingAbsences: 0 }))).toMatchObject({
      level: 'danger',
      pill: 'Superó el máximo',
      text: '4 de 3 faltas permitidas',
    });
  });

  it('counts a tentative absence on top of the stored ones', () => {
    expect(attendanceRisk(entry({ absenceCount: 3, remainingAbsences: 0 }), 4)).toMatchObject({ level: 'danger' });
  });

  it('shows only the absence count when the limit is unknown', () => {
    expect(attendanceRisk(entry({ remainingAbsences: null, maxAbsenceRate: null, absenceCount: 1 }))).toEqual({
      permitted: null,
      level: 'ok',
      text: '1 falta',
      pill: null,
      hint: null,
    });
    expect(attendanceRisk(entry({ remainingAbsences: null, absenceCount: 0 })).text).toBe('0 faltas');
  });
});
