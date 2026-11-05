import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { resolveApiOrigin } from '../csp.js';

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

describe('resolveApiOrigin', () => {
  it('uses the configured API origin without path', () => {
    expect(resolveApiOrigin('https://api.escuela.example/v1')).toBe('https://api.escuela.example');
  });

  it('falls back to localhost only when nothing is configured', () => {
    expect(resolveApiOrigin(undefined)).toBe('http://localhost:8080');
  });
});
