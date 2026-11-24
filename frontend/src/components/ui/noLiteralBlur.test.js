import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC_DIR = join(import.meta.dirname, '..', '..');
const SELF_FILE = join(import.meta.dirname, 'noLiteralBlur.test.js');
const SOURCE_FILE_PATTERN = /\.(jsx?|tsx?)$/;
const BLUR_PATTERN = /backdrop-(blur|saturate|filter)|(surface|overlay)-blur|surface-saturate/;

function sourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return entry.name === 'node_modules' ? [] : sourceFiles(path);
    }
    return SOURCE_FILE_PATTERN.test(entry.name) ? [path] : [];
  });
}

function blurUsagesIn(path) {
  return readFileSync(path, 'utf8')
    .split('\n')
    .map((line, index) => ({ line, lineNumber: index + 1 }))
    .filter(({ line }) => BLUR_PATTERN.test(line))
    .map(({ lineNumber }) => `${relative(SRC_DIR, path).split('\\').join('/')}:${lineNumber}`);
}

function blurUsages() {
  return sourceFiles(SRC_DIR)
    .filter((path) => path !== SELF_FILE)
    .flatMap(blurUsagesIn);
}

describe('backdrop blur and saturate', () => {
  it('no source file uses backdrop-blur, backdrop-saturate, backdrop-filter or their tokens', () => {
    expect(blurUsages()).toEqual([]);
  });
});
