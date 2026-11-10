import { describe, expect, it } from 'vitest';
import indexHtml from '../../index.html?raw';
import baseCss from './tokens.css?raw';
import bCss from './tokens-b.css?raw';
import { DEFAULT_DESIGN, DESIGNS, applyDesign, resolveDesign } from './design';

const TOKEN_FILES = { vl: baseCss, b: bCss };

describe('design flag', () => {
  it('query parameter wins over stored design', () => {
    expect(resolveDesign({ search: '?design=vl', stored: 'b' })).toBe('vl');
  });

  it('stored design is used when query is absent', () => {
    expect(resolveDesign({ stored: 'vl' })).toBe('vl');
  });

  it('invalid values fall back to the default', () => {
    expect(resolveDesign({ search: '?design=x', stored: 'y', fallback: 'z' })).toBe(DEFAULT_DESIGN);
  });

  it('the default design is b', () => {
    expect(DEFAULT_DESIGN).toBe('b');
    expect(resolveDesign()).toBe('b');
  });

  it('valid fallback is used when query and storage are empty', () => {
    expect(resolveDesign({ fallback: 'vl' })).toBe('vl');
  });

  it('applyDesign sets data-design on the root element', () => {
    const el = document.createElement('html');
    applyDesign('b', el);
    expect(el.dataset.design).toBe('b');
  });
});

describe('design registry', () => {
  it('every registered design has a token source and vice versa', () => {
    expect(Object.keys(TOKEN_FILES).sort()).toEqual([...DESIGNS].sort());
  });

  it('every non-base design declares its data-design block', () => {
    for (const design of DESIGNS.filter((name) => name !== 'vl')) {
      expect(TOKEN_FILES[design]).toContain(`:root[data-design='${design}']`);
    }
  });

  it('the inline script in index.html accepts the same designs and falls back to the default', () => {
    const valid = /var validos = \[([^\]]*)\]/.exec(indexHtml)[1].match(/'([^']+)'/g).map((item) => item.slice(1, -1));
    expect(valid).toEqual(DESIGNS);
    expect(indexHtml).toMatch(new RegExp(`\\? porDefecto\\s*:\\s*'${DEFAULT_DESIGN}';`));
  });
});
