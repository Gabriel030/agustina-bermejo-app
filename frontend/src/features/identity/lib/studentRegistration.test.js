import { describe, expect, it } from 'vitest';
import { RateLimitedError } from '@/api/client';
import {
  buildProspectDraft,
  describeRegistrationError,
  missingDocumentLabels,
  missingDocumentsSummary,
} from './studentRegistration';

describe('describeRegistrationError', () => {
  it('maps a duplicated email to the email field of the first step', () => {
    expect(describeRegistrationError({ status: 409, code: 'EMAIL_ALREADY_EXISTS' }, 'x')).toMatchObject({ step: 0, field: 'email' });
  });

  it('maps a duplicated document to the document field of the personal data step', () => {
    expect(describeRegistrationError({ status: 409, code: 'DNI_ALREADY_EXISTS' }, 'x')).toMatchObject({ step: 2, field: 'dni' });
  });

  it('explains the rate limit without blaming a field', () => {
    const failure = describeRegistrationError(new RateLimitedError(30), 'x');
    expect(failure.field).toBeUndefined();
    expect(failure.message).toBe(new RateLimitedError(30).message);
  });

  it('falls back to the given message for unknown failures', () => {
    expect(describeRegistrationError({ status: 500 }, 'No se pudo.').message).toBe('No se pudo.');
  });
});

describe('missing documents', () => {
  it('translates the document types to labels', () => {
    expect(missingDocumentLabels(['DNI_FRENTE', 'ANALITICO_SECUNDARIO'])).toEqual(['DNI frente', 'Analítico secundario']);
  });

  it('says the documentation is complete when nothing is missing', () => {
    expect(missingDocumentsSummary([])).toBe('Documentación completa');
  });

  it('lists what is missing', () => {
    expect(missingDocumentsSummary(['DNI_DORSO'])).toBe('Falta: DNI dorso');
  });
});

describe('buildProspectDraft', () => {
  it('sends null for blank optional fields', () => {
    const draft = buildProspectDraft({ documentType: 'DNI', dni: '30111222', street: 'Calle', number: '', city: 'CABA' });
    expect(draft).toMatchObject({ number: null, floor: null, city: 'CABA' });
  });
});
