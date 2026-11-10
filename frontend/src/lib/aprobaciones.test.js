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
});
