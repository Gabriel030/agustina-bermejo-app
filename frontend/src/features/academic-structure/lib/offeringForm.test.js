import { describe, expect, it } from 'vitest';
import {
  describeSaveError,
  validateCourseBasics,
  validateCourseDetail,
  validateProgramFields,
} from './offeringForm';

const PROGRAM = { name: 'Enfermería', totalYears: '3', totalCreditsRequired: '180', totalHoursRequired: '' };

describe('describeSaveError', () => {
  it('places the registration window rejection on the closing date field', () => {
    expect(describeSaveError({ status: 400, code: 'INVALID_REGISTRATION_WINDOW' }, 'x')).toEqual({
      field: 'registrationEnd',
      message: 'El cierre de inscripción no puede ser anterior a la apertura.',
    });
  });

  it('places a missing term on the term field', () => {
    expect(describeSaveError({ status: 404, code: 'TERM_NOT_FOUND' }, 'x').field).toBe('termId');
  });

  it('explains a generic validation rejection', () => {
    expect(describeSaveError({ status: 400, code: 'VALIDATION_FAILED' }, 'x').message).toMatch(/enteros y no negativos/);
  });

  it('explains a conflict without a known code', () => {
    expect(describeSaveError({ status: 409 }, 'x').message).toBe('Ya existe un registro con esos datos. Revisá el nombre.');
  });

  it('falls back to the given message for unknown failures', () => {
    expect(describeSaveError({ status: 500 }, 'Falló')).toEqual({ message: 'Falló' });
    expect(describeSaveError(undefined, 'Falló')).toEqual({ message: 'Falló' });
  });
});

describe('validateProgramFields with prices', () => {
  it('requires a monthly fee and an enrollment fee greater than zero when asked', () => {
    const errors = validateProgramFields({ ...PROGRAM, price: '0', enrollmentFee: '' }, { requireCredits: true, requirePrices: true });
    expect(Object.keys(errors).sort()).toEqual(['enrollmentFee', 'price']);
    expect(validateProgramFields({ ...PROGRAM, price: '48000', enrollmentFee: '95000' }, { requireCredits: true, requirePrices: true })).toEqual({});
  });
});

describe('validateProgramFields', () => {
  it('accepts a complete program', () => {
    expect(validateProgramFields(PROGRAM, { requireCredits: true })).toEqual({});
  });

  it('rejects fractional, out of range and negative numbers', () => {
    const errors = validateProgramFields(
      { ...PROGRAM, totalYears: '11', totalCreditsRequired: '-3', totalHoursRequired: '2.5' },
      { requireCredits: true },
    );
    expect(Object.keys(errors).sort()).toEqual(['totalCreditsRequired', 'totalHoursRequired', 'totalYears']);
    expect(validateProgramFields({ ...PROGRAM, totalYears: '2.5' }, { requireCredits: true }).totalYears).toBeDefined();
  });

  it('requires credits only when asked', () => {
    expect(validateProgramFields({ ...PROGRAM, totalCreditsRequired: '' }, { requireCredits: true }).totalCreditsRequired).toBeDefined();
    expect(validateProgramFields({ ...PROGRAM, totalCreditsRequired: '' }, { requireCredits: false })).toEqual({});
  });
});

describe('course validation', () => {
  it('requires name, term and a whole capacity of at least 1', () => {
    const errors = validateCourseBasics({ name: ' ', termId: '', capacity: '1.5', price: '-1' });
    expect(Object.keys(errors).sort()).toEqual(['capacity', 'name', 'price', 'termId']);
  });

  it('requires a monthly fee greater than zero for a course', () => {
    expect(validateCourseBasics({ name: 'Excel', capacity: '20', price: '' }).price).toMatch(/obligatoria y debe ser mayor que cero/);
    expect(validateCourseBasics({ name: 'Excel', capacity: '20', price: '0' }).price).toMatch(/mayor que cero/);
    expect(validateCourseBasics({ name: 'Excel', capacity: '20', price: '-5' }).price).toMatch(/mayor que cero/);
    expect(validateCourseBasics({ name: 'Excel', capacity: '20', price: '0.01' }).price).toBeUndefined();
  });

  it('does not require a term when none is being edited', () => {
    expect(validateCourseBasics({ name: 'Excel', capacity: '20', price: '15000' })).toEqual({});
  });

  it('requires the duration and a coherent registration window', () => {
    expect(validateCourseDetail({ durationMonths: '', registrationStart: '', registrationEnd: '' }).durationMonths).toBeDefined();
    expect(
      validateCourseDetail({ durationMonths: '3', registrationStart: '2027-02-10T00:00', registrationEnd: '2027-01-10T00:00' })
        .registrationEnd,
    ).toBeDefined();
    expect(validateCourseDetail({ durationMonths: '3', registrationStart: '', registrationEnd: '' })).toEqual({});
  });
});
