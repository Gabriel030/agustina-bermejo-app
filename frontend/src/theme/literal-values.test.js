import { describe, expect, it } from 'vitest';

const sources = import.meta.glob('/src/**/*.{js,jsx}', { query: '?raw', import: 'default', eager: true });

const EXEMPT_PATHS = [/^\/src\/theme\//, /^\/src\/components\/ui\//, /^\/src\/test\//, /\.test\.jsx?$/];

const ALLOWED = {};

const RULES = [
  { name: 'hex color', pattern: /#[0-9a-fA-F]{3,8}\b/g },
  { name: 'oklch()', pattern: /oklch\(/g },
  { name: 'rgb()/rgba()', pattern: /\brgba?\(/g },
  { name: 'hsl()/hsla()', pattern: /\bhsla?\(/g },
  { name: 'literal text/bg/border white or black', pattern: /(?<![\w-])(?:[a-z-]+:)*(?:text|bg|border|ring|from|to|via|fill|stroke)-(?:white|black)(?![\w-])/g },
  { name: 'literal shadow value', pattern: /(?<![\w-])shadow-\[/g },
];

function findViolations(path, text) {
  if (EXEMPT_PATHS.some((exempt) => exempt.test(path))) return [];
  const allowedRules = ALLOWED[path] ?? [];
  return RULES.filter((rule) => !allowedRules.includes(rule.name)).flatMap((rule) =>
    [...text.matchAll(rule.pattern)].map((match) => `${path}: ${rule.name} -> ${match[0]}`),
  );
}

describe('literal design values outside tokens and ui components', () => {
  it('no source file declares a literal color or shadow', () => {
    const violations = Object.entries(sources).flatMap(([path, text]) => findViolations(path, text));
    expect(violations).toEqual([]);
  });

  it('the detector flags every kind of literal', () => {
    const sample = [
      'const a = "#1a2b3c";',
      'const b = "oklch(50% 0.1 200)";',
      'const c = "rgb(0 0 0 / 0.5)";',
      'const d = "hsl(10 20% 30%)";',
      'const e = "text-white";',
      'const f = "hover:bg-black/40";',
      'const g = "shadow-[0_0_4px_red]";',
    ].join('\n');
    expect(findViolations('/src/features/x/Sample.jsx', sample)).toHaveLength(7);
  });

  it('the detector ignores token based classes and exempt paths', () => {
    const sample = 'const a = "text-on-accent bg-(--overlay-bg) shadow-(--sh-popover) bg-surface";';
    expect(findViolations('/src/features/x/Sample.jsx', sample)).toEqual([]);
    expect(findViolations('/src/components/ui/button.jsx', 'const a = "text-white #fff";')).toEqual([]);
  });

  it('every allowed exception points at an existing file and rule', () => {
    const ruleNames = RULES.map((rule) => rule.name);
    for (const [path, rules] of Object.entries(ALLOWED)) {
      expect(sources[path], path).toBeDefined();
      for (const rule of rules) expect(ruleNames, rule).toContain(rule);
    }
  });
});
