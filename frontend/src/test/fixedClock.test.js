import { describe, expect, it } from 'vitest';
import { addDays, addYears, FIXED_NOW, installFixedClock } from './fixedClock';

describe('fixed clock helper', () => {
  describe('with the default clock', () => {
    installFixedClock();

    it('freezes the current instant and the time zone', () => {
      expect(new Date().toISOString()).toBe(new Date(FIXED_NOW).toISOString());
      expect(new Date().getHours()).toBe(12);
      expect(new Date().getDate()).toBe(5);
      expect(Date.now()).toBe(new Date(FIXED_NOW).getTime());
    });

    it('keeps real timers working', async () => {
      await new Promise((resolve) => setTimeout(resolve, 5));
      expect(new Date().toISOString()).toBe(new Date(FIXED_NOW).toISOString());
    });
  });

  describe('on a leap day', () => {
    installFixedClock({ now: '2028-02-29T12:00:00-03:00' });

    it('subtracts years without rolling over to March', () => {
      const result = addYears(new Date(), -18);
      expect(result.getFullYear()).toBe(2010);
      expect(result.getMonth()).toBe(1);
      expect(result.getDate()).toBe(28);
    });

    it('adds days across the month end', () => {
      expect(addDays(new Date(), 1).getMonth()).toBe(2);
    });
  });

  describe('near the UTC midnight', () => {
    installFixedClock({ now: '2026-11-05T22:30:00-03:00' });

    it('keeps the local calendar day', () => {
      expect(new Date().getDate()).toBe(5);
      expect(new Date().getUTCDate()).toBe(6);
    });
  });
});

describe('fixed clock advancing with real time', () => {
  installFixedClock({ advanceWithRealTime: true });

  it('starts at the fixed instant and moves forward', async () => {
    const start = Date.now();
    expect(new Date(start).toISOString()).toBe(new Date(FIXED_NOW).toISOString());
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(Date.now()).toBeGreaterThan(start);
  });
});
