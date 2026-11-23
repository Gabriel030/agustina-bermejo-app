import { execFileSync } from 'node:child_process';
import { describe, expect, it } from 'vitest';
import baseline from './unhandled-baseline.json';
import { grownEntries, inheritRenamedBaseline, renamedKeysFrom } from './unhandledBaselineGuard';

const readFromDevelop = (command, args) => {
  try {
    return execFileSync(command, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  } catch {
    return null;
  }
};

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
    const raw = readFromDevelop('git', ['show', 'origin/develop:frontend/src/test/unhandled-baseline.json']);
    if (raw === null) return;
    const base = JSON.parse(raw);
    const nameStatus = readFromDevelop('git', ['diff', '--find-renames', '--name-status', 'origin/develop']) ?? '';
    const expectedBase = inheritRenamedBaseline(base, renamedKeysFrom(nameStatus));
    expect(grownEntries(baseline, expectedBase)).toEqual([]);
    expect(total(baseline)).toBeLessThanOrEqual(total(base));
  });
});
