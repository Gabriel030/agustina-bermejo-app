import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const html = readFileSync('index.html', 'utf8');

describe('index.html CSP', () => {
  it('declares a Content-Security-Policy meta tag', () => {
    expect(html).toContain('Content-Security-Policy');
  });

  it('forbids plugins and eval', () => {
    expect(html).toContain("object-src 'none'");
    expect(html).not.toContain('unsafe-eval');
  });
});
