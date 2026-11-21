import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import indexCss from '../index.css?raw';
import baseCss from './tokens.css?raw';
import bCss from './tokens-b.css?raw';

const UI_DIR = join(import.meta.dirname, '..', 'components', 'ui');
const TRANSLUCENT_RING = /focus-visible:ring-ring\/\d+/;

const FOCUS_RULE = /([^{}]*:focus-visible[^{}]*)\{([^}]*)\}/g;
const VAR_USAGE = /var\(\s*(--[\w-]+)/g;
const DECLARATION = /(--[\w-]+)\s*:/g;

function focusVisibleVariables(css) {
  return [...css.matchAll(FOCUS_RULE)].flatMap(([, , body]) => [...body.matchAll(VAR_USAGE)].map(([, name]) => name));
}

function declaredVariables(...sources) {
  return new Set(sources.flatMap((css) => [...css.matchAll(DECLARATION)].map(([, name]) => name)));
}

describe('focus-visible outline variables', () => {
  it('every variable used by a :focus-visible rule is defined in the theme files', () => {
    const declared = declaredVariables(indexCss, baseCss, bCss);
    const undefinedVariables = focusVisibleVariables(indexCss).filter((name) => !declared.has(name));
    expect(undefinedVariables).toEqual([]);
  });

  it('index.css declares at least one :focus-visible rule to guard', () => {
    expect(focusVisibleVariables(indexCss).length).toBeGreaterThan(0);
  });

  it('no ui component draws its focus ring with translucent alpha', () => {
    const translucent = readdirSync(UI_DIR)
      .filter((name) => name.endsWith('.jsx'))
      .filter((name) => TRANSLUCENT_RING.test(readFileSync(join(UI_DIR, name), 'utf8')));
    expect(translucent).toEqual([]);
  });

  it('design B primary button shadow is not none so focus rings can extend the box-shadow list', () => {
    const [, primaryShadow] = bCss.match(/--btn-primary-shadow:\s*([^;]+);/) ?? [];
    expect(primaryShadow).toBeDefined();
    expect(primaryShadow.trim()).not.toBe('none');
  });
});
