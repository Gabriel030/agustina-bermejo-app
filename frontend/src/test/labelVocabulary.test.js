import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['/src/**/*.{js,jsx}', '!/src/**/*.test.{js,jsx}', '!/src/test/**'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const BARE_LABELS = /(['"`>])(Regular|Regulares|No regular|Libre|Libres|Ausencias?|Faltas)(\1|<)/;
const LOOSE_WORDS = /\b(faltas|ausencias)\b/i;
const ABSENCE_UNIT_PHRASES = /\b\d+ (falta|ausencia)s?\b/i;

const ALLOWED_FILES = new Set(['/src/features/academic-structure/lib/classroomAvailability.js']);

function offendingLines(pattern) {
  return Object.entries(sources)
    .filter(([path]) => !ALLOWED_FILES.has(path))
    .flatMap(([path, text]) =>
      text
        .split('\n')
        .map((line, index) => ({ path, line, number: index + 1 }))
        .filter(({ line }) => !line.trim().startsWith('//') && pattern.test(line))
        .map(({ path: file, number, line }) => `${file}:${number}: ${line.trim()}`),
    );
}

describe('label vocabulary', () => {
  it('never shows bare Regular or Libre labels', () => {
    expect(offendingLines(BARE_LABELS)).toEqual([]);
  });

  it('uses inasistencias instead of faltas or ausencias', () => {
    expect(offendingLines(LOOSE_WORDS)).toEqual([]);
    expect(offendingLines(ABSENCE_UNIT_PHRASES)).toEqual([]);
  });
});
