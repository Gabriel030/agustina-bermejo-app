import { describe, expect, it } from 'vitest';
import { ADJUSTMENT_TYPE_LABELS } from '@/lib/billing';

describe('ADJUSTMENT_TYPE_LABELS', () => {
  it.each([
    ['SCHOLARSHIP', 'Beca'],
    ['PROMOTION', 'Promoción'],
    ['ANNUAL_UPFRONT', 'Pago anual'],
    ['COHORT_ENTRY', 'Ingreso de cohorte'],
    ['MANUAL', 'Manual'],
  ])('traduce el tipo de descuento %s', (type, label) => {
    expect(ADJUSTMENT_TYPE_LABELS[type]).toBe(label);
  });
});
