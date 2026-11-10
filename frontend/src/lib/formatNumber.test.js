import { describe, expect, it } from 'vitest';
import { formatDecimal, formatPercent } from '@/lib/formatNumber';

describe('formatNumber', () => {
  it('formatsDecimalsWithCommaAndThousandsDots', () => {
    expect(formatDecimal(1234.5, { minimumFractionDigits: 2 })).toBe('1.234,50');
  });

  it('dropsTrailingZerosByDefault', () => {
    expect(formatDecimal(7)).toBe('7');
    expect(formatDecimal(7.5)).toBe('7,5');
  });

  it('canDisableGrouping', () => {
    expect(formatDecimal(10000, { useGrouping: false })).toBe('10000');
  });

  it('returnsEmptyForNonFiniteValues', () => {
    expect(formatDecimal(Number.NaN)).toBe('');
    expect(formatPercent(undefined)).toBe('');
  });

  it('formatsPercentWithSpaceBeforeSymbol', () => {
    expect(formatPercent(87.5)).toBe('87,5 %');
  });
});
