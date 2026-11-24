import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import indexHtml from '../../index.html?raw';

const inlineThemeScript = /<script>([\s\S]*?)<\/script>/.exec(indexHtml)[1];

function runInlineThemeScript() {
  new Function(inlineThemeScript)();
}

function mockSystemTheme(prefersDark) {
  window.matchMedia = vi.fn().mockReturnValue({ matches: prefersDark });
}

describe('inline theme script', () => {
  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset.theme;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('applies the stored dark theme', () => {
    localStorage.setItem('tema', 'dark');
    runInlineThemeScript();
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('applies the stored light theme even when the system prefers dark', () => {
    localStorage.setItem('tema', 'light');
    mockSystemTheme(true);
    runInlineThemeScript();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('falls back to the system dark preference when nothing is stored', () => {
    mockSystemTheme(true);
    runInlineThemeScript();
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('falls back to light when the system prefers light and an invalid value is stored', () => {
    localStorage.setItem('tema', 'purple');
    mockSystemTheme(false);
    runInlineThemeScript();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('does not write or depend on the retired design attribute', () => {
    mockSystemTheme(false);
    runInlineThemeScript();
    expect(document.documentElement.getAttribute('data-' + 'design')).toBeNull();
    expect(indexHtml).not.toContain('design');
  });
});
