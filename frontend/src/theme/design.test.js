import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import indexHtml from '../../index.html?raw';
import { DEFAULT_DESIGN, applyDesign } from './design';

const inlineThemeScript = /<script>([\s\S]*?)<\/script>/.exec(indexHtml)[1];

function runInlineThemeScript() {
  new Function(inlineThemeScript)();
}

describe('design flag', () => {
  beforeEach(() => {
    localStorage.clear();
    window.history.replaceState(null, '', '/');
    delete document.documentElement.dataset.design;
  });

  afterEach(() => {
    localStorage.clear();
    window.history.replaceState(null, '', '/');
  });

  it('the default design is b', () => {
    expect(DEFAULT_DESIGN).toBe('b');
  });

  it('applyDesign sets data-design on the root element', () => {
    const el = document.createElement('html');
    applyDesign('b', el);
    expect(el.dataset.design).toBe('b');
  });

  it('the inline script sets data-design to b on every load', () => {
    runInlineThemeScript();
    expect(document.documentElement.dataset.design).toBe('b');
  });

  it('a stored retired design is cleared and falls back to b', () => {
    localStorage.setItem('design', 'vl');
    runInlineThemeScript();
    expect(document.documentElement.dataset.design).toBe('b');
    expect(localStorage.getItem('design')).toBeNull();
  });

  it('the design query parameter is ignored and b is applied', () => {
    window.history.replaceState(null, '', '/?design=vl');
    runInlineThemeScript();
    expect(document.documentElement.dataset.design).toBe('b');
  });

  it('the inline script no longer depends on the design build variable or a list of designs', () => {
    expect(indexHtml).not.toContain('VITE_DEFAULT_DESIGN');
    expect(indexHtml).not.toContain('validos');
  });
});
