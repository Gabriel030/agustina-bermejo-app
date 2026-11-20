import { describe, expect, it } from 'vitest';
import { describeRegularity } from './regularity';

function regularity(overrides) {
  return { institutionallyRegular: true, paymentStanding: 'VERIFIED', reasons: [], ...overrides };
}

describe('describeRegularity', () => {
  it('shows Alumno regular with its payment reason when payments are verified', () => {
    expect(describeRegularity(regularity())).toEqual({ label: 'Alumno regular', tone: 'success', details: ['Pagos al día'] });
  });

  it('marks the regular student as a confirmar when payments cannot be verified', () => {
    expect(describeRegularity(regularity({ paymentStanding: 'TO_CONFIRM' }))).toEqual({
      label: 'Alumno regular · a confirmar',
      tone: 'warning',
      details: ['Pagos a confirmar'],
    });
  });

  it('explains every reason when the student is not regular', () => {
    const result = describeRegularity(
      regularity({ institutionallyRegular: false, reasons: ['FREE_IN_ACTIVE_SECTION'], paymentStanding: 'OVERDUE' }),
    );
    expect(result.label).toBe('No es alumno regular');
    expect(result.details).toEqual(['Libre por inasistencias en una cursada activa', 'Pagos vencidos']);
  });
});
