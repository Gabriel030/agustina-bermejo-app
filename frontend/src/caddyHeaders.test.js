import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const caddyfile = readFileSync('Caddyfile', 'utf8');

function headerBlock() {
  const match = caddyfile.match(/\(security_headers\) \{\s*header \{([\s\S]*?)\n\t\}\n\}/);
  return match[1];
}

function headerValue(name) {
  const line = headerBlock()
    .split('\n')
    .map((entry) => entry.trim())
    .find((entry) => entry.startsWith(`${name} `));
  return line.slice(name.length).trim().replace(/^"|"$/g, '');
}

function cspDirective(name) {
  const directives = headerValue('Content-Security-Policy').split(';').map((entry) => entry.trim());
  const directive = directives.find((entry) => entry.startsWith(`${name} `));
  return directive.slice(name.length).trim().split(/\s+/);
}

describe('Caddyfile security headers', () => {
  it('appliesTheSecurityHeadersToTheSiteBlock', () => {
    const siteBlock = caddyfile.slice(caddyfile.indexOf('{$SITE_ADDRESS'));
    expect(siteBlock).toContain('import security_headers');
  });

  it('sendsHstsForAtLeastOneYear', () => {
    const maxAge = Number(headerValue('Strict-Transport-Security').match(/max-age=(\d+)/)[1]);
    expect(maxAge).toBeGreaterThanOrEqual(31536000);
  });

  it('disablesContentTypeSniffingAndReferrers', () => {
    expect(headerValue('X-Content-Type-Options')).toBe('nosniff');
    expect(headerValue('Referrer-Policy')).toBe('no-referrer');
  });

  it('deniesSensitiveBrowserFeatures', () => {
    const policy = headerValue('Permissions-Policy');
    ['camera', 'microphone', 'geolocation', 'payment', 'usb'].forEach((feature) => {
      expect(policy).toContain(`${feature}=()`);
    });
  });

  it('forbidsFramingFromAnyOriginInBothHeaders', () => {
    expect(cspDirective('frame-ancestors')).toEqual(["'none'"]);
    expect(headerValue('X-Frame-Options')).toBe('DENY');
  });

  it('hidesTheServerHeader', () => {
    expect(headerBlock()).toMatch(/^\s*-Server\s*$/m);
  });

  it('startsTheCspFromDefaultSrcSelfAndForbidsPluginsAndBaseHijacking', () => {
    expect(cspDirective('default-src')).toEqual(["'self'"]);
    expect(cspDirective('object-src')).toEqual(["'none'"]);
    expect(cspDirective('base-uri')).toEqual(["'self'"]);
    expect(cspDirective('form-action')).toEqual(["'self'"]);
  });

  it('keepsScriptSrcWithoutInlineOrEval', () => {
    const scriptSrc = cspDirective('script-src');
    expect(scriptSrc).not.toContain("'unsafe-inline'");
    expect(scriptSrc).not.toContain("'unsafe-eval'");
    expect(scriptSrc).toEqual(["'self'", 'https://accounts.google.com/gsi/client']);
  });

  it('allowsUnsafeInlineOnlyInStyleSrcUntilTheNonceMigration', () => {
    const policy = headerValue('Content-Security-Policy');
    expect(policy.match(/unsafe-inline/g)).toHaveLength(1);
    expect(cspDirective('style-src')).toContain("'unsafe-inline'");
  });

  it('neverAllowsWildcardOrInsecureSources', () => {
    const policy = headerValue('Content-Security-Policy');
    expect(policy).not.toMatch(/(^|\s)\*(\s|;|$)/);
    expect(policy).not.toContain('http:');
    expect(policy).not.toContain('unsafe-eval');
  });
});
