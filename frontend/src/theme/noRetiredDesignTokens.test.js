import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['../**/*.{js,jsx,css}', '../../index.html'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const RETIRED_TERMS = ['accent-' + '2', 'accent' + '2', 'data-' + 'design', 'dataset.' + 'design', 'isDesign' + 'B', 'apply' + 'Design', 'resolve' + 'Design', 'DEFAULT_' + 'DESIGN', 'DESIGN_STORAGE_' + 'KEY'];

function findOffenders() {
  const offenders = [];
  for (const [path, content] of Object.entries(sources)) {
    content.split('\n').forEach((line, index) => {
      if (RETIRED_TERMS.some((term) => line.includes(term))) offenders.push(`${path}:${index + 1}`);
    });
  }
  return offenders;
}

describe('retired design vocabulary', () => {
  it('is not referenced anywhere in the sources', () => {
    expect(findOffenders()).toEqual([]);
  });
});
