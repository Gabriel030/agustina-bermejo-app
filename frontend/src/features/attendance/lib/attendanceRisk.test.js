import { describe, expect, it } from 'vitest';
import { attendanceRisk } from './attendanceRisk';

function entry(standing) {
  return {
    absenceStanding: {
      classesHeld: 12,
      countableAbsences: 0,
      pendingJustificationAbsences: 0,
      absencePercent: 0,
      maxAbsencePercent: 25,
      allowedAbsences: 3,
      remainingAbsences: 3,
      exceedsAbsenceLimit: false,
      ...standing,
    },
  };
}

describe('attendanceRisk', () => {
  it('has no pill while the student is far from the limit', () => {
    expect(attendanceRisk(entry({}))).toMatchObject({ level: 'ok', text: '0 de 3 inasistencias permitidas', pill: null });
  });

  it('uses the singular when a single absence is permitted', () => {
    const result = attendanceRisk(entry({ countableAbsences: 2, allowedAbsences: 1, remainingAbsences: 0, exceedsAbsenceLimit: true }));
    expect(result.text).toBe('2 de 1 inasistencia permitida');
  });

  it('warns when one absence is left', () => {
    expect(attendanceRisk(entry({ countableAbsences: 2, remainingAbsences: 1 }))).toMatchObject({
      level: 'warning',
      text: '2 de 3 inasistencias permitidas',
      pill: 'Queda 1 inasistencia',
    });
  });

  it('warns at the limit', () => {
    expect(attendanceRisk(entry({ countableAbsences: 3, remainingAbsences: 0 }))).toMatchObject({ level: 'warning', pill: 'Al límite' });
  });

  it('flags danger only when the backend says the limit is exceeded', () => {
    expect(
      attendanceRisk(entry({ countableAbsences: 4, remainingAbsences: 0, exceedsAbsenceLimit: true })),
    ).toMatchObject({ level: 'danger', pill: 'Superó el máximo' });
  });

  it('uses the permitted absences from the backend instead of recomputing them from the rate', () => {
    const result = attendanceRisk(entry({ classesHeld: 100, maxAbsencePercent: 29, allowedAbsences: 7, remainingAbsences: 7 }));
    expect(result.permitted).toBe(7);
    expect(result.text).toBe('0 de 7 inasistencias permitidas');
  });

  it('trusts the backend flag even when the counts look under the limit', () => {
    expect(attendanceRisk(entry({ countableAbsences: 1, remainingAbsences: 2, exceedsAbsenceLimit: true })).level).toBe('danger');
  });

  it('shows only the count when the section has no limit', () => {
    expect(attendanceRisk(entry({ countableAbsences: 1, allowedAbsences: null, remainingAbsences: null, maxAbsencePercent: null }))).toEqual({
      permitted: null,
      level: 'ok',
      text: '1 inasistencia',
      pill: null,
      hint: null,
    });
    expect(attendanceRisk(entry({ allowedAbsences: null, remainingAbsences: null })).text).toBe('0 inasistencias');
  });
});
