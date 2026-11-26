import { describe, expect, it } from 'vitest';

const sources = import.meta.glob('/src/**/*.{js,jsx}', { query: '?raw', import: 'default', eager: true });

const EXEMPT_PATHS = [/^\/src\/components\/ui\//, /^\/src\/test\//, /\.test\.jsx?$/];

const MONEY_MONO_ALLOWED = [
  '/src/features/enrollment/components/BankAccountDetails.jsx',
  '/src/features/identity/pages/PadronAlumnos.jsx',
  '/src/features/identity/pages/GestionPersonal.jsx',
  '/src/features/evaluation/components/ClosurePrerequisitesSheet.jsx',
  '/src/features/billing/pages/AlumnoPagarTransferencia.jsx',
  '/src/features/backups/components/BackupRestoreAndDisk.jsx',
  '/src/features/backups/components/BackupTiersTable.jsx',
  '/src/features/backups/components/BackupWhatIsBackedUp.jsx',
  '/src/features/backups/components/CopyableCommand.jsx',
];

const MONEY_MONO_PENDING = [
  '/src/features/billing/components/InvoiceSummary.jsx',
  '/src/features/billing/components/AmountBreakdown.jsx',
];

const ARBITRARY_RADIUS_PENDING = [
  '/src/components/InstitutionBrand.jsx',
  '/src/features/billing/components/MonthPicker.jsx',
  '/src/components/Sidebar.jsx',
  '/src/features/evaluation/pages/PreceptorMesaExamen.jsx',
];

const SHELL_ALLOWED = [
  '/src/layouts/RoleShell.jsx',
  '/src/components/Sidebar.jsx',
  '/src/components/TopHeader.jsx',
  '/src/components/MobileMenu.jsx',
  '/src/components/ThemeToggle.jsx',
];

const SHELL_PENDING = [];

const RULES = [
  {
    name: 'arbitrary rounded value',
    pattern: /(?<![\w-])(?:[a-z-]+:)*rounded(?:-[a-z]{1,2})?-\[(?!inherit\])[^\]]*\]/g,
    allowed: [...ARBITRARY_RADIUS_PENDING],
    exemptUi: true,
  },
  {
    name: 'font-mono outside identifiers',
    pattern: /(?<![\w-])(?:[a-z-]+:)*font-mono(?![\w-])/g,
    allowed: [...MONEY_MONO_ALLOWED, ...MONEY_MONO_PENDING],
    exemptUi: true,
  },
  {
    name: 'MeshBackground import or usage',
    pattern: /\bMeshBackground\b/g,
    allowed: [],
    exemptUi: true,
  },
  {
    name: 'design-b variant prefix',
    pattern: /(?<![\w-])design-b:/g,
    allowed: [...SHELL_ALLOWED, ...SHELL_PENDING, '/src/components/ui/button.jsx'],
    exemptUi: false,
  },
  {
    name: 'gradient background utility',
    pattern: /(?<![\w-])(?:[a-z-]+:)*bg-gradient-to-/g,
    allowed: [],
    exemptUi: true,
  },
];

function isExempt(path, rule) {
  if (/\.test\.jsx?$/.test(path) || /^\/src\/test\//.test(path)) return true;
  return rule.exemptUi && EXEMPT_PATHS.some((exempt) => exempt.test(path));
}

function findViolations(path, text, rules = RULES) {
  return rules
    .filter((rule) => !isExempt(path, rule) && !rule.allowed.includes(path))
    .flatMap((rule) => [...text.matchAll(rule.pattern)].map((match) => `${path}: ${rule.name} -> ${match[0]}`));
}

describe('restyle regression guards', () => {
  it('no new source file breaks a guarded style rule', () => {
    const violations = Object.entries(sources).flatMap(([path, text]) => findViolations(path, text));
    expect(violations).toEqual([]);
  });

  it('flags an arbitrary rounded value but not rounded-[inherit]', () => {
    expect(findViolations('/src/features/x/A.jsx', 'const a = "rounded-[17px] md:rounded-t-[3px]";')).toHaveLength(2);
    expect(findViolations('/src/features/x/A.jsx', 'const a = "rounded-[inherit] rounded-2xl rounded-control";')).toEqual([]);
  });

  it('flags a gradient background outside ui but not inside it', () => {
    const sample = 'const a = "md:bg-gradient-to-r from-transparent to-dialog-bg";';
    expect(findViolations('/src/features/x/Fade.jsx', sample)).toHaveLength(1);
    expect(findViolations('/src/components/table/DataTable.jsx', sample)).toHaveLength(1);
    expect(findViolations('/src/components/ui/card.jsx', sample)).toEqual([]);
  });

  it('flags font-mono in a file that is not allowed', () => {
    expect(findViolations('/src/features/x/Money.jsx', 'const a = "font-mono tabular-nums";')).toHaveLength(1);
    expect(findViolations('/src/features/x/Money.jsx', 'const a = "tabular-nums font-monospace-x";')).toEqual([]);
  });

  it('allows font-mono in identifier files', () => {
    expect(findViolations('/src/features/identity/pages/PadronAlumnos.jsx', 'const a = "font-mono";')).toEqual([]);
  });

  it('flags any MeshBackground import or usage', () => {
    const sample = "import { MeshBackground } from '@/components/MeshBackground'; <MeshBackground />";
    expect(findViolations('/src/features/x/Page.jsx', sample).length).toBeGreaterThan(0);
    expect(findViolations('/src/layouts/RoleShell.jsx', sample).length).toBeGreaterThan(0);
  });

  it('flags the design-b prefix outside the shell and button', () => {
    const sample = 'const a = "design-b:rounded-md";';
    expect(findViolations('/src/features/x/Page.jsx', sample)).toHaveLength(1);
    expect(findViolations('/src/components/ui/button.jsx', sample)).toEqual([]);
    expect(findViolations('/src/components/Sidebar.jsx', sample)).toEqual([]);
  });

  it('every listed exception points at an existing file that still breaks the rule', () => {
    for (const rule of RULES) {
      for (const path of rule.allowed) {
        expect(sources[path], path).toBeDefined();
      }
    }
    const pending = [
      [ARBITRARY_RADIUS_PENDING, RULES[0]],
      [MONEY_MONO_PENDING, RULES[1]],
      [SHELL_PENDING, { pattern: /\bMeshBackground\b|(?<![\w-])design-b:/g }],
    ];
    for (const [paths, rule] of pending) {
      for (const path of paths) {
        expect([...sources[path].matchAll(rule.pattern)].length, path).toBeGreaterThan(0);
      }
    }
  });
});
