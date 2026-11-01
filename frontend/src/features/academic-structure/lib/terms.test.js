import { describe, expect, it } from 'vitest';
import {
  buildTermDiff,
  buildTermPayload,
  daysBetween,
  formatDayDifference,
  formatTermDate,
  termErrorMessage,
  validateTermForm,
} from './terms';

const VALID = {
  year: '2027',
  type: 'ANNUAL',
  description: 'Anual 2027',
  startDate: '2027-03-01',
  endDate: '2027-11-30',
  enrollmentStart: '2027-02-01',
  enrollmentEnd: '2027-02-28',
};

describe('terms form validation', () => {
  it('accepts a complete form', () => {
    expect(validateTermForm(VALID, { editing: false })).toEqual({});
  });

  it('rejects an end date before the start date', () => {
    const errors = validateTermForm({ ...VALID, endDate: '2027-02-01' }, { editing: false });
    expect(errors.endDate).toBe('El fin no puede ser anterior al inicio.');
  });

  it('requires both enrollment dates', () => {
    const errors = validateTermForm({ ...VALID, enrollmentStart: '', enrollmentEnd: '' }, { editing: false });
    expect(Object.keys(errors)).toEqual(['enrollmentStart', 'enrollmentEnd']);
  });

  it('rejects an enrollment window that ends before it starts', () => {
    const errors = validateTermForm({ ...VALID, enrollmentStart: '2027-02-10', enrollmentEnd: '2027-02-01' }, { editing: false });
    expect(errors.enrollmentEnd).toBe('La ventana de inscripción no es válida.');
  });

  it('requires type and description on creation only', () => {
    const empty = { ...VALID, type: '', description: ' ' };
    expect(Object.keys(validateTermForm(empty, { editing: false }))).toEqual(['type', 'description']);
    expect(Object.keys(validateTermForm(empty, { editing: true }))).toEqual(['description']);
  });
});

describe('terms payload', () => {
  it('sends year and type only on creation', () => {
    expect(buildTermPayload(VALID, { editing: false })).toEqual({
      description: 'Anual 2027',
      startDate: '2027-03-01T00:00:00',
      endDate: '2027-11-30T23:59:59',
      enrollmentStart: '2027-02-01T00:00:00',
      enrollmentEnd: '2027-02-28T23:59:59',
      year: 2027,
      type: 'ANNUAL',
    });
    expect(buildTermPayload(VALID, { editing: true })).not.toHaveProperty('type');
  });
});

describe('terms diff', () => {
  it('lists only changed fields with day differences', () => {
    const rows = buildTermDiff(VALID, { ...VALID, endDate: '2027-12-05', description: 'Anual' });
    expect(rows.map((row) => row.key)).toEqual(['description', 'endDate']);
    expect(rows[1]).toMatchObject({ today: '30/11/2027', next: '05/12/2027', difference: '+ 5 días' });
    expect(rows[0].difference).toBe('Modificado');
  });

  it('formats negative and single day differences', () => {
    expect(formatDayDifference(daysBetween('2027-03-02', '2027-03-01'))).toBe('− 1 día');
    expect(formatDayDifference(0)).toBe('Sin cambios');
    expect(formatTermDate('2027-03-01T00:00:00')).toBe('01/03/2027');
  });
});

describe('terms error messages', () => {
  it('explains the active term uniqueness conflict', () => {
    const message = termErrorMessage({ status: 409, code: 'ACTIVE_TERM_ALREADY_EXISTS' }, 'x');
    expect(message).toMatch(/Ya hay un período activo de este año y tipo/);
  });

  it('falls back for unknown errors', () => {
    expect(termErrorMessage({ status: 500 }, 'Falló')).toBe('Falló');
  });
});
