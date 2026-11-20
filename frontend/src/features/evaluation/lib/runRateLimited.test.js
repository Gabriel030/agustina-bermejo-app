import { describe, expect, it, vi } from 'vitest';
import { RateLimitedError } from '@/api/client';
import { GRADE_SAVE_CONCURRENCY, runRateLimited } from './runRateLimited';

const instantSleep = () => Promise.resolve();

describe('runRateLimited', () => {
  it('never has more requests in flight than the concurrency limit', async () => {
    let inFlight = 0;
    let peak = 0;
    const run = async (item) => {
      inFlight += 1;
      peak = Math.max(peak, inFlight);
      await new Promise((resolve) => setTimeout(resolve, 1));
      inFlight -= 1;
      return item;
    };
    const items = Array.from({ length: 130 }, (_, index) => index);

    const results = await runRateLimited(items, run, { sleep: instantSleep });

    expect(peak).toBeLessThanOrEqual(GRADE_SAVE_CONCURRENCY);
    expect(results.every((result) => result.status === 'fulfilled')).toBe(true);
    expect(results.map((result) => result.value)).toEqual(items);
  });

  it('waits what the server asked and retries a rate limited request', async () => {
    const sleep = vi.fn(instantSleep);
    const run = vi.fn().mockRejectedValueOnce(new RateLimitedError(2)).mockResolvedValue('ok');

    const results = await runRateLimited(['a'], run, { sleep });

    expect(results).toEqual([{ status: 'fulfilled', value: 'ok' }]);
    expect(run).toHaveBeenCalledTimes(2);
    expect(sleep).toHaveBeenCalledWith(expect.any(Number));
    expect(sleep.mock.calls[0][0]).toBeGreaterThan(1000);
  });

  it('gives up on a request after the retries are spent and keeps going with the rest', async () => {
    const run = vi.fn(async (item) => {
      if (item === 'bad') throw new RateLimitedError(1);
      return item;
    });

    const results = await runRateLimited(['bad', 'good'], run, { sleep: instantSleep, maxRetries: 2 });

    expect(results[0].status).toBe('rejected');
    expect(results[1]).toEqual({ status: 'fulfilled', value: 'good' });
    expect(run.mock.calls.filter(([item]) => item === 'bad')).toHaveLength(3);
  });

  it('does not retry errors that are not rate limits', async () => {
    const run = vi.fn().mockRejectedValue(new Error('boom'));

    const results = await runRateLimited(['a'], run, { sleep: instantSleep });

    expect(results[0].status).toBe('rejected');
    expect(run).toHaveBeenCalledTimes(1);
  });

  it('returns nothing for an empty list', async () => {
    expect(await runRateLimited([], vi.fn(), { sleep: instantSleep })).toEqual([]);
  });
});
