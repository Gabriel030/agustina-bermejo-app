import { describe, expect, it } from 'vitest';
import baseCss from './tokens.css?raw';
import bCss from './tokens-b.css?raw';
import { contrastRatio, isInSrgbGamut, oklchToLinearRgb } from './contrast';

const SHARED_WITH_BASE = ['--focus-ring'];

const OKLCH = /^oklch\(\s*([\d.]+)%\s+([\d.]+)\s+([\d.]+)\s*\)$/;
const OKLCH_ANY = /oklch\(\s*([\d.]+)%\s+([\d.]+)\s+([\d.]+)(?:\s*\/\s*[\d.]+)?\s*\)/g;
const ALIAS = /^var\((--[\w-]+)\)$/;

function blockBody(css, selector) {
  const start = css.indexOf(`${selector} {`);
  if (start < 0) throw new Error(`missing block ${selector}`);
  const open = css.indexOf('{', start);
  return css.slice(open + 1, css.indexOf('}', open));
}

function declarations(body) {
  const result = {};
  for (const match of body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    result[match[1]] = match[2].trim();
  }
  return result;
}

const baseRoot = declarations(blockBody(baseCss, ':root'));
const bLight = declarations(blockBody(bCss, ":root[data-design='b'][data-theme='light']"));
const bDark = declarations(blockBody(bCss, ":root[data-design='b'][data-theme='dark']"));
const bShared = declarations(blockBody(bCss, ":root[data-design='b']"));

function resolveColor(map, name, seen = new Set()) {
  if (seen.has(name)) throw new Error(`cycle on ${name}`);
  seen.add(name);
  const value = map[name];
  if (value === undefined) throw new Error(`unknown token ${name}`);
  const oklch = OKLCH.exec(value);
  if (oklch) return oklchToLinearRgb(Number(oklch[1]) / 100, Number(oklch[2]), Number(oklch[3]));
  const alias = ALIAS.exec(value);
  if (alias) return resolveColor(map, alias[1], seen);
  throw new Error(`token ${name} is not a plain color: ${value}`);
}

const themes = {
  light: { ...baseRoot, ...bShared, ...bLight },
  dark: { ...baseRoot, ...bShared, ...bDark },
};

const TEXT_PAIRS = [
  ['--ink', '--bg1'],
  ['--ink', '--surface'],
  ['--ink-soft', '--surface'],
  ['--ink-soft', '--bg1'],
  ['--ink-faint', '--surface'],
  ['--ink-faint', '--bg1'],
  ['--ink-faint', '--neutral-bg'],
  ['--ink-faint', '--table-head'],
  ['--accent', '--surface'],
  ['--on-accent', '--accent'],
  ['--accent', '--accent-soft'],
  ['--success-ink', '--success-bg'],
  ['--warning-ink', '--warning-bg'],
  ['--danger-ink', '--danger-bg'],
  ['--neutral-ink', '--neutral-bg'],
  ['--ink', '--dialog-bg'],
  ['--ink', '--row-hover'],
  ['--ink', '--row-selected'],
  ['--ink-faint', '--row-hover'],
];

const NON_TEXT_PAIRS = [
  ['--warning', '--surface'],
  ['--danger', '--surface'],
  ['--success', '--surface'],
  ['--focus-ring', '--surface'],
];

describe('design B tokens', () => {
  it('every variable declared by design B exists in the base root', () => {
    const allB = [...Object.keys(bLight), ...Object.keys(bDark), ...Object.keys(bShared)];
    const missing = allB.filter((name) => !(name in baseRoot));
    expect(missing).toEqual([]);
  });

  it('every variable declared in the base root is redefined by design B or explicitly shared', () => {
    const redefined = new Set([...Object.keys(bLight), ...Object.keys(bDark), ...Object.keys(bShared)]);
    const untouched = Object.keys(baseRoot).filter((name) => !redefined.has(name));
    expect(untouched.sort()).toEqual([...SHARED_WITH_BASE].sort());
  });

  it('light and dark blocks of design B redefine the same color variables', () => {
    expect(Object.keys(bLight).sort()).toEqual(Object.keys(bDark).sort());
  });

  it('dark base block keeps parity with the light base root for new variables', () => {
    const darkBase = declarations(blockBody(baseCss, ":root[data-theme='dark']"));
    const misplaced = Object.keys(darkBase).filter((name) => !(name in baseRoot));
    expect(misplaced).toEqual([]);
  });

  for (const theme of ['light', 'dark']) {
    it(`design B text pairs meet WCAG AA in ${theme}`, () => {
      const failures = TEXT_PAIRS.map(([fg, bg]) => {
        const ratio = contrastRatio(resolveColor(themes[theme], fg), resolveColor(themes[theme], bg));
        return { pair: `${fg}/${bg}`, ratio: Number(ratio.toFixed(2)) };
      }).filter(({ ratio }) => ratio < 4.5);
      expect(failures).toEqual([]);
    });

    it(`design B non-text pairs meet 3:1 in ${theme}`, () => {
      const failures = NON_TEXT_PAIRS.map(([fg, bg]) => {
        const ratio = contrastRatio(resolveColor(themes[theme], fg), resolveColor(themes[theme], bg));
        return { pair: `${fg}/${bg}`, ratio: Number(ratio.toFixed(2)) };
      }).filter(({ ratio }) => ratio < 3);
      expect(failures).toEqual([]);
    });
  }

  it('every oklch color of design B falls inside sRGB without clipping', () => {
    const outOfGamut = [...bCss.matchAll(OKLCH_ANY)]
      .filter(([, l, c, h]) => !isInSrgbGamut(Number(l) / 100, Number(c), Number(h)))
      .map(([whole]) => whole);
    expect(outOfGamut).toEqual([]);
  });

  it('design B defines the dialog shadow variable', () => {
    expect(bCss).toContain('--sh-dialog');
    expect(baseRoot['--sh-dialog']).toBe('none');
  });

  it('the contrast helper rejects a known failing pair', () => {
    const white = oklchToLinearRgb(1, 0, 0);
    const lightGray = oklchToLinearRgb(0.9, 0, 0);
    expect(contrastRatio(lightGray, white)).toBeLessThan(4.5);
  });
});
