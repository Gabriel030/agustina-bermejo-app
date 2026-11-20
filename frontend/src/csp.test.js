import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { extractInlineScripts, resolveApiOrigin } from '../csp.js';

const html = readFileSync('index.html', 'utf8');

describe('index.html CSP', () => {
  it('declares a Content-Security-Policy meta tag', () => {
    expect(html).toContain('Content-Security-Policy');
  });

  it('forbids plugins and eval', () => {
    expect(html).toContain("object-src 'none'");
    expect(html).not.toContain('unsafe-eval');
  });

  it('leaves the API origin as a build-time placeholder instead of a hardcoded host', () => {
    expect(html).toContain('connect-src');
    expect(html).toContain('__API_ORIGIN__');
    expect(html).not.toContain('localhost:8080');
  });
});

describe('index.html fonts', () => {
  it('allowsFontsOnlyFromSelfAndNoGoogleHosts', () => {
    expect(html).toContain("font-src 'self'");
    expect(html).not.toContain('googleapis');
    expect(html).not.toContain('gstatic');
  });

  it('keepsStyleSrcWithoutExternalHosts', () => {
    const styleSrc = html.match(/style-src ([^;]+);/)[1];
    expect(styleSrc).toBe("'self' 'unsafe-inline' https://accounts.google.com/gsi/style");
  });
});

describe('resolveApiOrigin', () => {
  it('uses the configured API origin without path', () => {
    expect(resolveApiOrigin('https://api.escuela.example/v1')).toBe('https://api.escuela.example');
  });

  it('usesSelfWhenTheApiIsServedFromTheSameOrigin', () => {
    expect(resolveApiOrigin('/api')).toBe("'self'");
  });

  it('falls back to localhost only when nothing is configured', () => {
    expect(resolveApiOrigin(undefined)).toBe('http://localhost:8080');
  });
});

describe('extractInlineScripts', () => {
  it('returnsOnlyTheInlineThemeScriptAndNotTheModuleEntry', () => {
    const scripts = extractInlineScripts(html);
    expect(scripts).toHaveLength(1);
    expect(scripts[0]).toContain('dataset.theme');
  });
});
