import { afterEach, describe, expect, it, vi } from 'vitest';
import { newIdempotencyKey } from '@/api/idempotencyKey';

describe('newIdempotencyKey', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('works without randomUUID, as in a page served over plain http', () => {
    vi.stubGlobal('crypto', { getRandomValues: (bytes) => bytes.fill(171) });
    expect(newIdempotencyKey()).toBe('ab'.repeat(16));
  });

  it('works without any crypto at all and gives different keys', () => {
    vi.stubGlobal('crypto', undefined);
    const first = newIdempotencyKey();
    expect(first).toMatch(/^[0-9a-f]{32}$/);
    expect(newIdempotencyKey()).not.toBe(first);
  });
});
