import { describe, expect, it } from 'vitest';
import { ROLE_MENU } from '@/routes/roleMenu';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { roleRoutesFor } from '@/routes/featureRoutes';

const REDIRECTED_PAGE = 'inicio';
const PLACEHOLDER_PAGES = {
  alumno: ['opciones'],
  administrativo: ['constancias'],
  director: ['constancias'],
};

describe('menu items and registered routes', () => {
  it('registersARouteForEveryMenuItemOfEveryRole', () => {
    const uncovered = [];
    for (const role of Object.keys(ROLE_MENU)) {
      const paths = new Set(roleRoutesFor(role).map((route) => route.path));
      const allowed = new Set(PLACEHOLDER_PAGES[role] ?? []);
      for (const entry of ROLE_MENU[role]) {
        if (entry.slug && entry.slug !== REDIRECTED_PAGE && !paths.has(entry.slug) && !allowed.has(entry.slug)) uncovered.push(`${role}:${entry.slug}`);
      }
    }
    expect(uncovered).toEqual([]);
  });

  it('keepsTheRouterRoleConfigInSyncWithTheMenu', () => {
    for (const role of Object.keys(ROLE_MENU)) {
      const slugs = ROLE_MENU[role].filter((entry) => entry.slug).map((entry) => entry.slug);
      expect(ROLE_CONFIG[role].pages).toEqual(slugs);
    }
  });

  it('pointsEveryExternalMenuEntryToAnAbsolutePath', () => {
    for (const role of Object.keys(ROLE_MENU)) {
      for (const entry of ROLE_MENU[role].filter((item) => item.to)) {
        expect(entry.to.startsWith('/')).toBe(true);
      }
    }
  });
});
