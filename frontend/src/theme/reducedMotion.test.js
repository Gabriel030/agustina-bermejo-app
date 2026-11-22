import { describe, expect, it } from 'vitest';

const stylesheets = import.meta.glob('/src/**/*.css', { query: '?raw', import: 'default', eager: true });
const sources = import.meta.glob(['/src/**/*.js', '/src/**/*.jsx', '!/src/**/*.test.*', '!/src/test/**'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const GUARD_START = '@media (prefers-reduced-motion: reduce)';

function reducedMotionBlock(css) {
  const start = css.indexOf(GUARD_START);
  if (start < 0) return '';
  const open = css.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < css.length; i += 1) {
    if (css[i] === '{') depth += 1;
    if (css[i] === '}') depth -= 1;
    if (depth === 0) return css.slice(open, i + 1);
  }
  return '';
}

describe('reduced motion', () => {
  const indexCss = stylesheets['/src/index.css'];

  it('globalGuardNeutralizesAnimationsAndTransitionsForEveryElement', () => {
    const block = reducedMotionBlock(indexCss);
    expect(block).toMatch(/\*,\s*\*::before,\s*\*::after/);
    expect(block).toMatch(/animation-duration:\s*0\.01ms\s*!important/);
    expect(block).toMatch(/animation-iteration-count:\s*1\s*!important/);
    expect(block).toMatch(/transition-duration:\s*0\.01ms\s*!important/);
  });

  it('everyStylesheetDeclaringKeyframesIsCoveredByTheGlobalGuard', () => {
    const withKeyframes = Object.entries(stylesheets)
      .filter(([, css]) => css.includes('@keyframes'))
      .map(([path]) => path);
    const unguarded = withKeyframes.filter((path) => path !== '/src/index.css');
    expect(unguarded).toEqual([]);
    expect(withKeyframes).toContain('/src/index.css');
  });

  it('noStylesheetOverridesTheGuardWithAnImportantAnimation', () => {
    const offenders = Object.entries(stylesheets)
      .filter(([path, css]) => path !== '/src/index.css' && /animation[^;{}]*!important/.test(css))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('smoothScrollingGoesThroughTheReducedMotionHelper', () => {
    const offenders = Object.entries(sources)
      .filter(([path, code]) => path !== '/src/lib/scrollBehavior.js' && /behavior:\s*['"]smooth['"]/.test(code))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('everyInlineAnimationNameReferencesAKeyframesDeclaredInIndexCss', () => {
    const declared = new Set([...indexCss.matchAll(/@keyframes\s+([\w-]+)/g)].map((match) => match[1]));
    const referenced = Object.values(sources).flatMap((code) =>
      [...code.matchAll(/animation:\s*'([\w-]+)\s/g)].map((match) => match[1]),
    );
    expect(referenced.filter((name) => !declared.has(name))).toEqual([]);
  });
});
