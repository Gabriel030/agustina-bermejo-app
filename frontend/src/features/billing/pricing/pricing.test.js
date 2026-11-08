import { describe, expect, it } from 'vitest';
import {
  DISCOUNT_KIND,
  buildPriceChangePayload,
  computeFinalAmount,
  countByPresence,
  isFormComplete,
  lectiveYearOptions,
  priceStatusKey,
  priceStatusKeys,
  whatHappens,
} from './pricing';

const money = (amount) => ({ amount, currency: 'ARS' });
const version = (amount, extra = {}) => ({ id: 1, finalAmount: money(amount), ...extra });

describe('priceStatusKey', () => {
  it('flags a concept without a current version as missing', () => {
    expect(priceStatusKey({ currentVersion: null })).toBe('NONE');
  });

  it('prefers the open proposal over the scheduled change', () => {
    expect(priceStatusKey({ currentVersion: version('100.00'), openProposal: version('120.00'), nextScheduledVersion: version('130.00') })).toBe('PROPOSAL');
  });

  it('flags a zero final amount as free', () => {
    expect(priceStatusKey({ currentVersion: version('0.00') })).toBe('FREE');
  });

  it('counts a concept in every card it belongs to', () => {
    const price = { currentVersion: version('100.00'), openProposal: version('120.00'), nextScheduledVersion: version('130.00') };
    expect(priceStatusKeys(price)).toEqual(['PROPOSAL', 'SCHEDULED']);
    expect(countByPresence([price, { currentVersion: null }])).toEqual({ PROPOSAL: 1, SCHEDULED: 1, NONE: 1 });
  });
});

describe('computeFinalAmount', () => {
  it('applies a percentage discount', () => {
    expect(computeFinalAmount({ amount: '1000', discountKind: DISCOUNT_KIND.PERCENTAGE, discountValue: '15' })).toEqual({ base: 1000, discount: 150, final: 850 });
  });

  it('never goes below zero with a fixed discount larger than the price', () => {
    expect(computeFinalAmount({ amount: '100', discountKind: DISCOUNT_KIND.FIXED, discountValue: '500' }).final).toBe(0);
  });
});

describe('buildPriceChangePayload', () => {
  const base = { amount: '52000', currency: 'ARS', discountKind: DISCOUNT_KIND.NONE, discountValue: '', effectiveFrom: '2027-01-01', reason: ' ajuste ' };

  it('omits the lective year and the discount when they are not chosen', () => {
    expect(buildPriceChangePayload({ ...base, appliesToLectiveYear: null })).toEqual({
      amount: { amount: '52000.00', currency: 'ARS' },
      effectiveFrom: '2027-01-01',
      changeReason: 'ajuste',
    });
  });

  it('sends the lective year as a number and the percentage discount', () => {
    const payload = buildPriceChangePayload({ ...base, discountKind: DISCOUNT_KIND.PERCENTAGE, discountValue: '10', appliesToLectiveYear: '2027' });
    expect(payload.appliesToLectiveYear).toBe(2027);
    expect(payload.marketingDiscount).toEqual({ type: 'PERCENTAGE', percent: 10 });
  });
});

describe('form rules', () => {
  it('requires amount, date and reason', () => {
    const form = { amount: '10', discountKind: DISCOUNT_KIND.NONE, discountValue: '', effectiveFrom: '2026-11-08', reason: 'x' };
    expect(isFormComplete(form)).toBe(true);
    expect(isFormComplete({ ...form, reason: '  ' })).toBe(false);
    expect(isFormComplete({ ...form, amount: '' })).toBe(false);
    expect(isFormComplete({ ...form, discountKind: DISCOUNT_KIND.FIXED })).toBe(false);
  });

  it('enables only the lective years that are loaded', () => {
    expect(lectiveYearOptions('2026-11-08', [2027])).toEqual([
      { year: 2027, enabled: true },
      { year: 2028, enabled: false },
    ]);
  });

  it('explains an advance price differently from a regular one', () => {
    const advance = whatHappens({ finalAmount: 52000, currentFinal: 45000, effectiveFrom: '2027-01-01', appliesToLectiveYear: '2027' });
    expect(advance.map((line) => line.kind)).toEqual(['advance', 'difference', 'issued']);
    expect(advance[1].difference).toBe(7000);
    expect(whatHappens({ finalAmount: 10, currentFinal: null, effectiveFrom: '2026-11-08', appliesToLectiveYear: null }).map((line) => line.kind)).toEqual(['regular', 'issued']);
  });
});
