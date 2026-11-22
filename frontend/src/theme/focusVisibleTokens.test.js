import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';
import indexCss from '../index.css?raw';
import baseCss from './tokens.css?raw';
import bCss from './tokens-b.css?raw';

const SRC_DIR = join(import.meta.dirname, '..');
const SELF_FILE = join(import.meta.dirname, 'focusVisibleTokens.test.js');
const SOURCE_FILE_PATTERN = /\.(jsx?|tsx?|css)$/;
const FOCUS_RULE = /([^{}]*:focus-visible[^{}]*)\{([^}]*)\}/g;
const VAR_USAGE = /var\(\s*(--[\w-]+)/g;
const DECLARATION = /(--[\w-]+)\s*:/g;
const TRANSLUCENT_RING = /(focus-visible|focus-within|focus):ring-ring\/\d+/;
const SHADOW_TOKEN_USAGE = /shadow-\(--([\w-]+)\)/g;

function focusVisibleVariables(css) {
  return [...css.matchAll(FOCUS_RULE)].flatMap(([, , body]) => [...body.matchAll(VAR_USAGE)].map(([, name]) => name));
}

function declaredVariables(...sources) {
  return new Set(sources.flatMap((css) => [...css.matchAll(DECLARATION)].map(([, name]) => name)));
}

function sourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return entry.name === 'node_modules' ? [] : sourceFiles(path);
    }
    return SOURCE_FILE_PATTERN.test(entry.name) && path !== SELF_FILE && !entry.name.includes('.test.') ? [path] : [];
  });
}

function sourceTexts() {
  return sourceFiles(SRC_DIR).map((path) => ({ path, text: readFileSync(path, 'utf8') }));
}

function declarationValue(css, name) {
  const match = new RegExp(`${name}\\s*:\\s*([^;]+);`).exec(css);
  return match ? match[1].trim() : undefined;
}

describe('focus-visible outline variables', () => {
  it('every variable used by a :focus-visible rule is defined in the theme files', () => {
    const declared = declaredVariables(indexCss, baseCss, bCss);
    const undefinedVariables = focusVisibleVariables(indexCss).filter((name) => !declared.has(name));
    expect(undefinedVariables).toEqual([]);
  });

  it('the focus ring token is declared in the shared root so both designs inherit it', () => {
    expect(declaredVariables(baseCss.slice(0, baseCss.indexOf(':root[data-theme')))).toContain('--focus-ring');
    expect(focusVisibleVariables(indexCss)).toContain('--focus-ring');
  });

  it('no source file draws a focus ring with translucent alpha', () => {
    const translucent = sourceTexts()
      .filter(({ text }) => TRANSLUCENT_RING.test(text))
      .map(({ path }) => relative(SRC_DIR, path));
    expect(translucent).toEqual([]);
  });

  it('no shadow token used with a ring in source resolves to none in design B', () => {
    const usedTokens = new Set(sourceTexts().flatMap(({ text }) => [...text.matchAll(SHADOW_TOKEN_USAGE)].map(([, name]) => `--${name}`)));
    const noneTokens = [...usedTokens].filter((name) => {
      const value = declarationValue(bCss, name) ?? declarationValue(baseCss, name);
      return value === 'none';
    });
    expect(noneTokens).toEqual([]);
  });
});
