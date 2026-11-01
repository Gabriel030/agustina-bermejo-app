import { describe, expect, it } from 'vitest';
import { canRequestGraduation, deriveGraduationState, graduationRequestErrorMessage, missingCredits } from './graduationState';

const base = {
  graduated: false,
  eligible: true,
  accumulatedCredits: 100,
  requiredCredits: 144,
  pendingMandatorySubjects: [],
  unfulfilledElectiveGroups: [],
  request: null,
};

describe('graduationState', () => {
  it('derives each state from the request status and eligibility', () => {
    expect(deriveGraduationState({ ...base, eligible: false })).toBe('NOT_ELIGIBLE');
    expect(deriveGraduationState(base)).toBe('ELIGIBLE');
    expect(deriveGraduationState({ ...base, request: { status: 'REQUESTED' } })).toBe('REQUESTED');
    expect(deriveGraduationState({ ...base, request: { status: 'ADMIN_VERIFIED' } })).toBe('ADMIN_VERIFIED');
    expect(deriveGraduationState({ ...base, request: { status: 'APPROVED' } })).toBe('APPROVED');
    expect(deriveGraduationState({ ...base, graduated: true, request: { status: 'APPROVED' } })).toBe('APPROVED');
    expect(deriveGraduationState({ ...base, request: { status: 'REJECTED' } })).toBe('REJECTED');
  });

  it('only allows requesting when eligible without an active request, or after a rejection while still eligible', () => {
    expect(canRequestGraduation(base)).toBe(true);
    expect(canRequestGraduation({ ...base, eligible: false })).toBe(false);
    expect(canRequestGraduation({ ...base, request: { status: 'REQUESTED' } })).toBe(false);
    expect(canRequestGraduation({ ...base, request: { status: 'ADMIN_VERIFIED' } })).toBe(false);
    expect(canRequestGraduation({ ...base, request: { status: 'APPROVED' } })).toBe(false);
    expect(canRequestGraduation({ ...base, request: { status: 'REJECTED' } })).toBe(true);
    expect(canRequestGraduation({ ...base, eligible: false, request: { status: 'REJECTED' } })).toBe(false);
  });

  it('never reports negative missing credits', () => {
    expect(missingCredits(base)).toBe(44);
    expect(missingCredits({ ...base, accumulatedCredits: 150 })).toBe(0);
  });

  it('maps request errors to Spanish messages by status', () => {
    expect(graduationRequestErrorMessage({ status: 409 })).toMatch(/solicitud de egreso activa/);
    expect(graduationRequestErrorMessage({ status: 400 })).toMatch(/requisitos/);
    expect(graduationRequestErrorMessage({ status: 500, message: 'Ocurrió un error inesperado. Probá de nuevo.' })).toBe(
      'Ocurrió un error inesperado. Probá de nuevo.',
    );
  });
});
