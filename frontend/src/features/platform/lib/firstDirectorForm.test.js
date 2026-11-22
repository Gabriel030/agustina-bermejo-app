import { describe, expect, it } from 'vitest';
import { EMPTY_FORM, firstInvalidField, missingFieldLabels, toRequest, validateForm } from './firstDirectorForm';

const validForm = {
  institutionName: ' Instituto Nuevo Horizonte ',
  firstName: 'Carolina',
  lastName: 'Ledesma',
  email: 'carolina@nuevohorizonte.edu.ar',
  documentType: 'DNI',
  documentNumber: '28441907',
  reason: 'Alta inicial',
};

describe('firstDirectorForm', () => {
  it('acceptsAFullyValidForm', () => {
    expect(validateForm(validForm)).toEqual({});
  });

  it('validatesTheDocumentAgainstItsType', () => {
    expect(validateForm({ ...validForm, documentNumber: '12.345' }).documentNumber).toMatch(/DNI/);
    expect(validateForm({ ...validForm, documentType: 'PASSPORT', documentNumber: 'AB123456' }).documentNumber).toBeUndefined();
  });

  it('requiresAReasonBetween5And500Characters', () => {
    expect(validateForm({ ...validForm, reason: 'ok' }).reason).toBeDefined();
    expect(validateForm({ ...validForm, reason: 'a'.repeat(500) }).reason).toBeUndefined();
    expect(validateForm({ ...validForm, reason: 'a'.repeat(501) }).reason).toBeDefined();
  });

  it('listsTheMissingFieldsInScreenOrderAndFindsTheFirstInvalid', () => {
    const errors = validateForm(EMPTY_FORM);

    expect(missingFieldLabels(errors)).toEqual(['Institución', 'Nombre', 'Apellido', 'Email', 'Documento', 'Motivo']);
    expect(firstInvalidField(errors)).toBe('institutionName');
    expect(firstInvalidField({})).toBeNull();
  });

  it('trimsTheRequestPayload', () => {
    expect(toRequest(validForm).institutionName).toBe('Instituto Nuevo Horizonte');
  });
});
