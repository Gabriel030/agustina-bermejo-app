import { describe, expect, it } from 'vitest';
import {
  DOCUMENT_TYPES,
  buildRequest,
  canSubmit,
  isReasonValid,
  parseValidityDays,
  sanitizeReason,
  validityError,
} from '@/features/graduation/debt-exceptions/lib/debtException';

const student = { id: 7, apellido: 'Pérez', nombre: 'Camila', dni: '38512774' };

describe('debt exception form rules', () => {
  it('rejectsReasonOfNineteenCharactersAndAcceptsTwenty', () => {
    expect(isReasonValid('a'.repeat(19))).toBe(false);
    expect(isReasonValid('a'.repeat(20))).toBe(true);
  });

  it('countsReasonWithoutSurroundingSpaces', () => {
    expect(isReasonValid(`   ${'a'.repeat(19)}   `)).toBe(false);
    expect(isReasonValid(`  ${'a'.repeat(20)}  `)).toBe(true);
  });

  it('replacesLineBreaksAndControlCharactersInReason', () => {
    expect(sanitizeReason('uno\ndos\u0007tres')).toBe('uno dos tres');
  });

  it.each([['1', 1], ['90', 90], ['7', 7], [' 15 ', 15]])('acceptsValidityDays %s', (text, expected) => {
    expect(parseValidityDays(text)).toBe(expected);
    expect(validityError(text)).toBeNull();
  });

  it.each(['0', '91', '-3', '2.5', 'abc', '', '  '])('rejectsValidityDays "%s"', (text) => {
    expect(parseValidityDays(text)).toBeNull();
    expect(validityError(text)).toEqual(expect.any(String));
  });

  it('needsStudentReasonAndValidityToSubmit', () => {
    const base = { student, reason: 'a'.repeat(20), validityDays: '7' };
    expect(canSubmit(base)).toBe(true);
    expect(canSubmit({ ...base, student: null })).toBe(false);
    expect(canSubmit({ ...base, reason: 'a'.repeat(19) })).toBe(false);
    expect(canSubmit({ ...base, validityDays: '91' })).toBe(false);
  });

  it('buildsTheContractRequest', () => {
    expect(buildRequest({ student, documentType: 'EXAM_CERTIFICATE', reason: `  ${'a'.repeat(20)} `, validityDays: '15' })).toEqual({
      studentId: 7,
      documentType: 'EXAM_CERTIFICATE',
      reason: 'a'.repeat(20),
      validityDays: 15,
    });
  });

  it('offersEveryContractDocumentType', () => {
    expect(DOCUMENT_TYPES.map((type) => type.value).sort()).toEqual([
      'APPROVED_SUBJECTS_CERTIFICATE',
      'EXAM_CERTIFICATE',
      'EXAM_REGISTRATION_CERTIFICATE',
      'GRADUATION_CERTIFICATE',
      'REGULAR_STUDENT_CERTIFICATE',
    ]);
  });
});
