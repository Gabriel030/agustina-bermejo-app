import { describe, expect, it } from 'vitest';
import { sumMoney } from './dashboardMetrics';

const ars = (amount) => ({ amount, currency: 'ARS' });

describe('sumMoney', () => {
  it('adds cents exactly where floating point would drift', () => {
    expect(sumMoney([ars('0.10'), ars('0.20')])).toEqual(ars('0.30'));
    expect(sumMoney([ars('1.15'), ars('2.30'), ars('3.45')])).toEqual(ars('6.90'));
  });

  it('keeps every cent of amounts beyond the safe integer range of a double', () => {
    expect(sumMoney([ars('90071992547409.93'), ars('0.01')])).toEqual(ars('90071992547409.94'));
    expect(sumMoney([ars('123456789012345678.99'), ars('0.02')])).toEqual(ars('123456789012345679.01'));
  });

  it('skips missing months and returns null when there is nothing to add', () => {
    expect(sumMoney([null, ars('5.00'), null])).toEqual(ars('5.00'));
    expect(sumMoney([null, null])).toBeNull();
    expect(sumMoney([ars('0.00'), ars('0.00')])).toEqual(ars('0.00'));
  });
});
