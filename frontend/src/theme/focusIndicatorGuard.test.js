import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['/src/**/*.js', '/src/**/*.jsx', '!/src/**/*.test.*', '!/src/test/**'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const SUPPRESSES_OUTLINE = /(?<![\w-])(?:focus-visible:|focus:)?outline-(?:none|hidden)/;
const FOCUS_ALTERNATIVE =
  /focus-visible:(?:ring|border|bg|underline|shadow)|focus:(?:ring|bg|border)|group-focus-visible|has-\[:focus-visible\]|tabIndex=\{-1\}|focus-within:/;

const PROGRAMMATIC_FOCUS_TARGETS = [
  '/src/layouts/RoleShell.jsx',
  '/src/components/ui/dialog.jsx',
  '/src/components/ui/alert-dialog.jsx',
  '/src/components/ui/tabs.jsx',
  '/src/components/ui/command.jsx',
  '/src/components/ui/popover.jsx',
  '/src/features/identity/pages/ReviewEmailChangePage.jsx',
  '/src/features/workspace/pages/AlumnoInicio.jsx',
  '/src/features/workspace/components/HomeTaskCard.jsx',
];

function offendingLines(text) {
  return text
    .split('\n')
    .map((line, index) => ({ line, number: index + 1 }))
    .filter(({ line }) => SUPPRESSES_OUTLINE.test(line) && !FOCUS_ALTERNATIVE.test(line));
}

describe('focus indicator guard', () => {
  it('everyOutlineSuppressionComesWithAFocusAlternativeOrIsAProgrammaticFocusTarget', () => {
    const offenders = Object.entries(sources)
      .filter(([path]) => !PROGRAMMATIC_FOCUS_TARGETS.includes(path))
      .flatMap(([path, text]) => offendingLines(text).map(({ number }) => `${path}:${number}`));
    expect(offenders).toEqual([]);
  });

  it('theGuardFlagsAnOutlineSuppressionWithoutAlternative', () => {
    expect(offendingLines('<a className="text-ink outline-none">x</a>')).toHaveLength(1);
    expect(offendingLines('<a className="outline-none focus-visible:ring-2">x</a>')).toHaveLength(0);
  });

  it('everyListedProgrammaticFocusTargetStillSuppressesTheOutline', () => {
    const stale = PROGRAMMATIC_FOCUS_TARGETS.filter((path) => !SUPPRESSES_OUTLINE.test(sources[path] ?? ''));
    expect(stale).toEqual([]);
  });
});
