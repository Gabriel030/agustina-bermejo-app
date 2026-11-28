import { describe, expect, it } from 'vitest';
import { decisionErrorMessage, isAlreadyDecided } from '@/lib/aprobaciones';

describe('aprobaciones decision errors', () => {
  it('treats a 409 with CYCLE_NOT_PENDING_APPROVAL as already decided', () => {
    const error = { status: 409, code: 'CYCLE_NOT_PENDING_APPROVAL' };

    expect(isAlreadyDecided(error)).toBe(true);
    expect(decisionErrorMessage(error)).toBe('Otra persona de dirección ya decidió este trámite.');
  });

  it('does not treat other 409 codes as already decided', () => {
    expect(isAlreadyDecided({ status: 409, code: 'CYCLE_DECISION_IDEMPOTENCY_KEY_CONFLICT' })).toBe(false);
  });

  it('names the course sections that still need a preceptor', () => {
    const error = {
      status: 409,
      code: 'COURSE_CYCLE_NOT_READY',
      details: { reasons: ['Primeros auxilios (Comisión A): sin preceptor', 'Primeros auxilios (Comisión B): sin preceptor'] },
    };

    expect(decisionErrorMessage(error)).toBe(
      'No se puede aprobar: hay comisiones del curso sin preceptor. Falta en Primeros auxilios (Comisión A), Primeros auxilios (Comisión B).',
    );
    expect(isAlreadyDecided(error)).toBe(false);
  });

  it('falls back to the generic course message when the details are missing', () => {
    expect(decisionErrorMessage({ status: 409, code: 'COURSE_CYCLE_NOT_READY' })).toBe(
      'No se puede aprobar: hay comisiones del curso sin preceptor.',
    );
  });
});
