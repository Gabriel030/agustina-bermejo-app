import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC_DIR = join(import.meta.dirname, '..', '..');
const UI_DIR = import.meta.dirname;
const SELF_FILE = join(import.meta.dirname, 'noGradientVariant.test.js');
const BUTTON_TEST_FILE = join(import.meta.dirname, 'button.test.jsx');
const SOURCE_FILE_PATTERN = /\.(jsx?|tsx?)$/;
const RETIRED_VARIANT_PATTERN = /\b(variant|confirmVariant)\s*(=\s*\{?\s*|:\s*)(['"])gradient\3/;
const GRADIENT_CLASS_PATTERN = /(?<![\w-])(bg-gradient-|from-|via-|to-)[\w[(]/;

function sourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return entry.name === 'node_modules' ? [] : sourceFiles(path);
    }
    return SOURCE_FILE_PATTERN.test(entry.name) ? [path] : [];
  });
}

function linesMatching(files, pattern) {
  return files.flatMap((path) =>
    readFileSync(path, 'utf8')
      .split('\n')
      .map((line, index) => ({ line, lineNumber: index + 1 }))
      .filter(({ line }) => pattern.test(line))
      .map(({ lineNumber }) => `${relative(SRC_DIR, path)}:${lineNumber}`),
  );
}

function retiredVariantUsages() {
  return linesMatching(
    sourceFiles(SRC_DIR).filter((path) => path !== SELF_FILE && path !== BUTTON_TEST_FILE),
    RETIRED_VARIANT_PATTERN,
  );
}

function gradientClassUsagesInUi() {
  return linesMatching(
    sourceFiles(UI_DIR).filter((path) => path !== SELF_FILE),
    GRADIENT_CLASS_PATTERN,
  );
}

describe('retired gradient variant', () => {
  it('no source file uses variant gradient on Button', () => {
    expect(retiredVariantUsages()).toEqual([]);
  });

  it('components/ui uses solid tokens instead of gradient classes', () => {
    expect(gradientClassUsagesInUi()).toEqual([]);
  });
});
