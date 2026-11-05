import { describe, expect, it } from 'vitest';
import { applyGrantChange, buildGrantPayload, currentMonth, initialGrantValues, monthsBetween, shiftMonth, validateGrantValues } from './grantForm';

describe('grantForm', () => {
  const now = new Date(2026, 9, 28);

  it('starts on the current month', () => {
    expect(initialGrantValues(now)).toMatchObject({ fromPeriod: '2026-10', toPeriod: '2026-10', invoiceType: 'MONTHLY_INSTALLMENT' });
    expect(currentMonth(now)).toBe('2026-10');
  });

  it('shifts and counts months across years', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
    expect(shiftMonth('2026-01', -1)).toBe('2025-12');
    expect(monthsBetween('2026-03', '2026-12')).toBe(10);
  });

  it('pushes the end of the range with the start and fixes the invoice type for scholarships', () => {
    const base = { ...initialGrantValues(now), toPeriod: '2026-10' };
    expect(applyGrantChange(base, 'fromPeriod', '2026-12').toPeriod).toBe('2026-12');
    expect(applyGrantChange({ ...base, invoiceType: 'EXAM_FEE' }, 'type', 'SCHOLARSHIP').invoiceType).toBe('MONTHLY_INSTALLMENT');
  });

  it('validates value, scholarship definition, range and reason', () => {
    const values = { ...initialGrantValues(now), type: 'SCHOLARSHIP', calcKind: 'PERCENT', value: '120', toPeriod: '2026-09' };
    expect(validateGrantValues(values)).toEqual({
      value: 'El porcentaje va de 0 a 100.',
      definitionId: 'Elegí la beca del catálogo.',
      toPeriod: 'La cuota final no puede ser anterior a la inicial.',
      reason: 'El motivo es obligatorio y admite hasta 450 caracteres.',
    });
  });

  it('builds a percent payload in points and a fixed payload as money, without end for non monthly fees', () => {
    const percent = buildGrantPayload({ ...initialGrantValues(now), type: 'SCHOLARSHIP', definitionId: '5', calcKind: 'PERCENT', value: '12,5', reason: ' ok ' });
    expect(percent).toMatchObject({ discountDefinitionId: 5, percent: 12.5, amount: null, reason: 'ok', toPeriod: '2026-10' });
    const fixed = buildGrantPayload({ ...initialGrantValues(now), invoiceType: 'ENROLLMENT_FEE', calcKind: 'FIXED', value: '50000', reason: 'x' });
    expect(fixed).toMatchObject({ percent: null, amount: { amount: '50000.00', currency: 'ARS' }, toPeriod: null, discountDefinitionId: null });
  });
});
