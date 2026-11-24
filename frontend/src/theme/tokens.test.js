import { describe, expect, it } from 'vitest';
import tokensCss from './tokens.css?raw';
import { contrastRatio, isInSrgbGamut, oklchToLinearRgb } from './contrast';

const SAME_IN_BOTH_THEMES = ['--on-overlay'];

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

const lightTokens = declarations(blockBody(tokensCss, ':root'));
const darkTokens = declarations(blockBody(tokensCss, ":root[data-theme='dark']"));

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
  light: lightTokens,
  dark: { ...lightTokens, ...darkTokens },
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
  ['--ink-soft', '--neutral-bg'],
  ['--ink-soft', '--table-head'],
  ['--ink-soft', '--row-hover'],
  ['--ink-soft', '--dialog-bg'],
  ['--ink-faint', '--accent-soft'],
  ['--ink-faint', '--dialog-bg'],
  ['--ink-faint', '--control-bg'],
  ['--ink', '--accent-soft'],
  ['--ink', '--control-bg'],
  ['--accent', '--bg1'],
  ['--accent', '--neutral-bg'],
  ['--accent', '--row-selected'],
  ['--accent', '--dialog-bg'],
  ['--on-accent', '--danger'],
  ['--on-accent', '--warning'],
  ['--danger', '--surface'],
  ['--danger', '--bg1'],
  ['--danger', '--danger-bg'],
  ['--danger', '--dialog-bg'],
  ['--success', '--surface'],
  ['--success', '--bg1'],
  ['--success', '--success-bg'],
  ['--warning', '--surface'],
  ['--warning', '--bg1'],
  ['--warning', '--warning-bg'],
  ['--success-ink', '--surface'],
  ['--warning-ink', '--surface'],
  ['--danger-ink', '--surface'],
  ['--neutral-ink', '--surface'],
  ['--pill-success-fg', '--success-bg'],
  ['--pill-warning-fg', '--warning-bg'],
  ['--pill-danger-fg', '--danger-bg'],
  ['--pill-neutral-fg', '--neutral-bg'],
  ['--nav-active-fg', '--accent-soft'],
  ['--ink', '--table-head-bg'],
  ['--ink-soft', '--table-head-bg'],
];

const NON_TEXT_PAIRS = [
  ['--warning', '--surface'],
  ['--danger', '--surface'],
  ['--success', '--surface'],
  ['--focus-ring', '--surface'],
  ['--focus-ring', '--bg1'],
  ['--accent', '--surface'],
  ['--ink-faint', '--surface'],
];

describe('design tokens', () => {
  it('every variable redefined in the dark block exists in the light root', () => {
    const misplaced = Object.keys(darkTokens).filter((name) => !(name in lightTokens));
    expect(misplaced).toEqual([]);
  });

  it('the dark block redefines every color variable of the light root that holds a color', () => {
    const colorVariables = Object.keys(lightTokens).filter((name) => OKLCH.test(lightTokens[name]));
    const missing = colorVariables.filter((name) => !(name in darkTokens));
    expect(missing).toEqual(SAME_IN_BOTH_THEMES);
  });

  for (const theme of ['light', 'dark']) {
    it(`text pairs meet WCAG AA in ${theme}`, () => {
      const failures = TEXT_PAIRS.map(([fg, bg]) => {
        const ratio = contrastRatio(resolveColor(themes[theme], fg), resolveColor(themes[theme], bg));
        return { pair: `${fg}/${bg}`, ratio: Number(ratio.toFixed(2)) };
      }).filter(({ ratio }) => ratio < 4.5);
      expect(failures).toEqual([]);
    });

    it(`non-text pairs meet 3:1 in ${theme}`, () => {
      const failures = NON_TEXT_PAIRS.map(([fg, bg]) => {
        const ratio = contrastRatio(resolveColor(themes[theme], fg), resolveColor(themes[theme], bg));
        return { pair: `${fg}/${bg}`, ratio: Number(ratio.toFixed(2)) };
      }).filter(({ ratio }) => ratio < 3);
      expect(failures).toEqual([]);
    });
  }

  it('every oklch color falls inside sRGB without clipping', () => {
    const outOfGamut = [...tokensCss.matchAll(OKLCH_ANY)]
      .filter(([, l, c, h]) => !isInSrgbGamut(Number(l) / 100, Number(c), Number(h)))
      .map(([whole]) => whole);
    expect(outOfGamut).toEqual([]);
  });

  it('the dialog shadow variable is declared', () => {
    expect(lightTokens['--sh-dialog']).toBeDefined();
  });

  it('the contrast helper rejects a known failing pair', () => {
    const white = oklchToLinearRgb(1, 0, 0);
    const lightGray = oklchToLinearRgb(0.9, 0, 0);
    expect(contrastRatio(lightGray, white)).toBeLessThan(4.5);
  });
});
