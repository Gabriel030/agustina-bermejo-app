import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC_DIR = join(import.meta.dirname, '..', '..');
const SELF_FILE = join(import.meta.dirname, 'noLiteralBlur.test.js');
const SOURCE_FILE_PATTERN = /\.(jsx?|tsx?)$/;
const LITERAL_BLUR_PATTERN = /backdrop-(blur|saturate)-\[/;
const ALLOWED_LITERAL_BLUR_FILES = [];

function sourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return entry.name === 'node_modules' ? [] : sourceFiles(path);
    }
    return SOURCE_FILE_PATTERN.test(entry.name) ? [path] : [];
  });
}

function literalBlurUsagesIn(path) {
  return readFileSync(path, 'utf8')
    .split('\n')
    .map((line, index) => ({ line, lineNumber: index + 1 }))
    .filter(({ line }) => LITERAL_BLUR_PATTERN.test(line))
    .map(({ lineNumber }) => `${relative(SRC_DIR, path).split('\\').join('/')}:${lineNumber}`);
}

function literalBlurUsages() {
  return sourceFiles(SRC_DIR)
    .filter((path) => path !== SELF_FILE)
    .filter((path) => !ALLOWED_LITERAL_BLUR_FILES.includes(relative(SRC_DIR, path).split('\\').join('/')))
    .flatMap(literalBlurUsagesIn);
}

describe('literal backdrop blur and saturate', () => {
  it('no source file uses a literal value for backdrop-blur or backdrop-saturate', () => {
    expect(literalBlurUsages()).toEqual([]);
  });

  it('every allowed literal exception still uses a literal value', () => {
    const exceptionsWithoutLiteral = ALLOWED_LITERAL_BLUR_FILES.filter(
      (file) => literalBlurUsagesIn(join(SRC_DIR, file)).length === 0,
    );
    expect(exceptionsWithoutLiteral).toEqual([]);
  });
});
