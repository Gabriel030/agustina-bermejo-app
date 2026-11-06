import { afterEach, beforeEach, vi } from 'vitest';

export const FIXED_NOW = '2026-11-05T12:00:00-03:00';
export const FIXED_TIME_ZONE = 'America/Argentina/Buenos_Aires';

export function installFixedClock({ now = FIXED_NOW, timeZone = FIXED_TIME_ZONE, advanceWithRealTime = false } = {}) {
  let previousTimeZone;

  beforeEach(() => {
    previousTimeZone = globalThis.process.env.TZ;
    globalThis.process.env.TZ = timeZone;
    vi.useFakeTimers({ toFake: ['Date'], shouldAdvanceTime: advanceWithRealTime });
    vi.setSystemTime(new Date(now));
  });

  afterEach(() => {
    vi.useRealTimers();
    if (previousTimeZone === undefined) delete globalThis.process.env.TZ;
    else globalThis.process.env.TZ = previousTimeZone;
  });
}

export function addDays(date, days) {
  const result = new Date(date.getTime());
  result.setDate(result.getDate() + days);
  return result;
}

export function addYears(date, years) {
  const result = new Date(date.getTime());
  const month = result.getMonth();
  result.setFullYear(result.getFullYear() + years);
  if (result.getMonth() !== month) result.setDate(0);
  return result;
}
