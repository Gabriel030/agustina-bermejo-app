import { describe, expect, it } from 'vitest';
import {
  buildEnrollmentOptions,
  buildSubjectRows,
  describeCurricularPeriod,
  describeSectionStatus,
  formatAttendancePercent,
  formatGradeDate,
  parseEnrollmentKey,
} from './academicRecord';

describe('academicRecord', () => {
  it('joins the attendance percentage to each subject by name', () => {
    const rows = buildSubjectRows(
      [{ id: 1, subjectName: 'Historia' }, { id: 2, subjectName: 'Teoría' }],
      [{ subjectName: 'Historia', attendancePercent: 92.4 }],
    );

    expect(rows.map((row) => row.attendancePercent)).toEqual([92.4, null]);
  });

  it('rounds attendance and shows a dash when there is none', () => {
    expect(formatAttendancePercent(92.4)).toBe('92%');
    expect(formatAttendancePercent(null)).toBe('—');
  });

  it('builds distinguishable keys for program and course enrollments with the same id', () => {
    const options = buildEnrollmentOptions([{ id: 4, programName: 'Tec' }], [{ id: 4, courseName: 'Foto' }]);

    expect(options.map((option) => option.key)).toEqual(['program-4', 'course-4']);
    expect(parseEnrollmentKey('course-4')).toEqual({ kind: 'course', id: 4 });
  });

  it('describes the curricular period and unknown statuses without inventing labels', () => {
    expect(describeCurricularPeriod(2, 'SECOND')).toBe('2.º · 2C');
    expect(describeSectionStatus('FAILED')).toEqual({ tone: 'danger', label: 'Desaprobada' });
    expect(describeSectionStatus('SOMETHING_NEW')).toEqual({ tone: 'neutral', label: 'SOMETHING_NEW' });
  });

  it('formats the grade date without shifting the day', () => {
    expect(formatGradeDate('2026-04-15T00:00:00')).toBe('15/04/2026');
  });
});
