import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC_DIR = join(import.meta.dirname, '..');
const SELF_FILE = join(import.meta.dirname, 'noStickyBottomOutsideActionBar.test.js');
const SOURCE_FILE_PATTERN = /\.(jsx?|tsx?)$/;
const PINNED_BOTTOM_PATTERN_STICKY_OR_FIXED = /\b(sticky|fixed)\b[^"'`]*\bbottom-/;
const ALLOWED_PINNED_BOTTOM_FILES_PENDING_MIGRATION = ['components/StickyActionBar.jsx', 'components/ui/sheet.jsx'];

function sourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return entry.name === 'node_modules' ? [] : sourceFiles(path);
    }
    return SOURCE_FILE_PATTERN.test(entry.name) ? [path] : [];
  });
}

function relativePath(path) {
  return relative(SRC_DIR, path).split('\\').join('/');
}

function stickyBottomUsagesIn(path) {
  return readFileSync(path, 'utf8')
    .split('\n')
    .map((line, index) => ({ line, lineNumber: index + 1 }))
    .filter(({ line }) => PINNED_BOTTOM_PATTERN_STICKY_OR_FIXED.test(line))
    .map(({ lineNumber }) => `${relativePath(path)}:${lineNumber}`);
}

describe('sticky bottom action bars', () => {
  it('only StickyActionBar pins a bar to the bottom', () => {
    const usages = sourceFiles(SRC_DIR)
      .filter((path) => path !== SELF_FILE)
      .filter((path) => !ALLOWED_PINNED_BOTTOM_FILES_PENDING_MIGRATION.includes(relativePath(path)))
      .flatMap(stickyBottomUsagesIn);
    expect(usages).toEqual([]);
  });

  it('every allowed exception still pins a bar to the bottom', () => {
    const exceptionsWithoutSticky = ALLOWED_PINNED_BOTTOM_FILES_PENDING_MIGRATION.filter(
      (file) => stickyBottomUsagesIn(join(SRC_DIR, file)).length === 0,
    );
    expect(exceptionsWithoutSticky).toEqual([]);
  });
});
