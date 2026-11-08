import { describe, expect, it } from 'vitest';
import { applyDesign, resolveDesign } from './design';

describe('design flag', () => {
  it('query parameter wins over stored design', () => {
    expect(resolveDesign({ search: '?design=b', stored: 'vl' })).toBe('b');
  });

  it('stored design is used when query is absent', () => {
    expect(resolveDesign({ stored: 'b' })).toBe('b');
  });

  it('invalid values fall back to the default', () => {
    expect(resolveDesign({ search: '?design=x', stored: 'y', fallback: 'z' })).toBe('vl');
  });

  it('valid fallback is used when query and storage are empty', () => {
    expect(resolveDesign({ fallback: 'b' })).toBe('b');
  });

  it('applyDesign sets data-design on the root element', () => {
    const el = document.createElement('html');
    applyDesign('b', el);
    expect(el.dataset.design).toBe('b');
  });
});
