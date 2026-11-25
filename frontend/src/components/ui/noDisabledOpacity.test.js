import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC_DIR = join(import.meta.dirname, '..', '..');
const SELF_FILE = join(import.meta.dirname, 'noDisabledOpacity.test.js');
const SOURCE_FILE_PATTERN = /\.(jsx?|tsx?)$/;
const DISABLED_OPACITY_PATTERN = /(disabled|aria-disabled):opacity-(?!100\b)\d+\b/;
const PENDING_FILES = ['features/announcements/components/EnvioYCaducidad.jsx'];

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

function disabledOpacityUsagesIn(path) {
  return readFileSync(path, 'utf8')
    .split('\n')
    .map((line, index) => ({ line, lineNumber: index + 1 }))
    .filter(({ line }) => DISABLED_OPACITY_PATTERN.test(line))
    .map(({ lineNumber }) => `${relativePath(path)}:${lineNumber}`);
}

function disabledOpacityUsages() {
  return sourceFiles(SRC_DIR)
    .filter((path) => path !== SELF_FILE && !PENDING_FILES.includes(relativePath(path)))
    .flatMap(disabledOpacityUsagesIn);
}

describe('disabled states', () => {
  it('no source file dims a disabled control with opacity; use the disabled text and background tokens', () => {
    expect(disabledOpacityUsages()).toEqual([]);
  });
});
