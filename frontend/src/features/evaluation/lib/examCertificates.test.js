import { describe, expect, it } from 'vitest';
import { displayName, summarizeBatch } from '@/features/evaluation/lib/examCertificates';

describe('displayName', () => {
  it.each([null, undefined, '', '   '])('falls back to a generic name for %j', (value) => {
    expect(displayName(value)).toBe('Alumno sin nombre');
  });

  it('keeps a real name', () => {
    expect(displayName('Ana Paz')).toBe('Ana Paz');
  });
});

describe('summarizeBatch', () => {
  const candidates = [{ registrationId: 60, studentName: 'Juan Pérez' }];

  it('names a nameless debt item from the candidate list', () => {
    const summary = summarizeBatch({ issuedCount: 0, skippedCount: 1, items: [{ registrationId: 60, outcome: 'DEBT', certificateId: null }] }, candidates);

    expect(summary.omitted).toEqual([{ registrationId: 60, studentName: 'Juan Pérez', reason: 'debe regularizar sus pagos y no se emitió.' }]);
  });

  it('uses a generic name when the item is nameless and unknown to the list', () => {
    const summary = summarizeBatch({ issuedCount: 0, skippedCount: 1, items: [{ registrationId: 999, studentName: null, outcome: 'DEBT' }] }, candidates);

    expect(summary.omitted[0].studentName).toBe('Alumno sin nombre');
  });
});
