import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['/src/**/*.{js,jsx}', '!/src/**/*.test.{js,jsx}'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const PALE_HOVER_TEXT = /(^|[\s'"`:])text-accent(?![\w-])/;

describe('design token usage', () => {
  it('never uses text-accent for text, because that token is the pale hover background', () => {
    const offenders = Object.entries(sources)
      .filter(([, content]) => PALE_HOVER_TEXT.test(content))
      .map(([path]) => path);

    expect(offenders).toEqual([]);
  });
});
