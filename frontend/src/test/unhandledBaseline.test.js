import { execFileSync } from 'node:child_process';
import { describe, expect, it } from 'vitest';
import baseline from './unhandled-baseline.json';

const total = (entries) =>
  Object.values(entries).reduce((sum, perFile) => sum + Object.values(perFile).reduce((a, b) => a + b, 0), 0);

describe('unhandled requests baseline', () => {
  it('everyEntryIsAPositiveCountForAMethodAndPath', () => {
    const invalid = Object.entries(baseline).flatMap(([file, perKey]) =>
      Object.entries(perKey)
        .filter(([key, count]) => !/^(GET|POST|PUT|PATCH|DELETE) \//.test(key) || !Number.isInteger(count) || count < 1)
        .map(([key]) => `${file} ${key}`),
    );
    expect(invalid).toEqual([]);
  });

  it('baselineCanOnlyShrink', () => {
    let base;
    try {
      const raw = execFileSync('git', ['show', 'origin/develop:frontend/src/test/unhandled-baseline.json'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
      });
      base = JSON.parse(raw);
    } catch {
      return;
    }
    const grown = Object.entries(baseline).flatMap(([file, perKey]) =>
      Object.entries(perKey)
        .filter(([key, count]) => count > (base[file]?.[key] ?? 0))
        .map(([key, count]) => `${file} ${key} ${count} > ${base[file]?.[key] ?? 0}`),
    );
    expect(grown).toEqual([]);
    expect(total(baseline)).toBeLessThanOrEqual(total(base));
  });
});
