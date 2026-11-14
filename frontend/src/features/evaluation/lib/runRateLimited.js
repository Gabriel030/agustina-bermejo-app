export const GRADE_SAVE_CONCURRENCY = 4;
const DEFAULT_WAIT_MS = 5000;
const MAX_WAIT_MS = 30000;
const MAX_RETRIES = 3;

const realSleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export async function runRateLimited(items, run, options = {}) {
  const { concurrency = GRADE_SAVE_CONCURRENCY, maxRetries = MAX_RETRIES, sleep = realSleep } = options;
  const results = new Array(items.length);
  let nextIndex = 0;
  let pausedUntil = 0;

  async function worker() {
    while (nextIndex < items.length) {
      const index = nextIndex++;
      let attempt = 0;
      let settled = false;
      while (!settled) {
        const wait = pausedUntil - Date.now();
        if (wait > 0) await sleep(wait);
        try {
          results[index] = { status: 'fulfilled', value: await run(items[index]) };
          settled = true;
        } catch (error) {
          if (error?.name === 'RateLimitedError' && attempt < maxRetries) {
            attempt += 1;
            const waitMs = Math.min(MAX_WAIT_MS, error.retryAfterSeconds ? error.retryAfterSeconds * 1000 : DEFAULT_WAIT_MS);
            pausedUntil = Math.max(pausedUntil, Date.now() + waitMs);
          } else {
            results[index] = { status: 'rejected', reason: error };
            settled = true;
          }
        }
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker));
  return results;
}
