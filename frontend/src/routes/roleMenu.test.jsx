import { describe, expect, it } from 'vitest';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { ROLE_MENU, menuItemsFor, pagesFor } from '@/routes/roleMenu';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { hasRealScreen } from '@/routes/menuAccess';

const PENDING_SCREENS = { alumno: ['opciones'], administrativo: ['constancias'], director: ['constancias'] };

describe('role menu manifest', () => {
  it('defines a manifest entry for every configured role', () => {
    expect(Object.keys(ROLE_MENU).sort()).toEqual(Object.keys(ROLE_CONFIG).sort());
  });

  it.each(Object.keys(ROLE_MENU))('derives the pages of %s from the manifest', (role) => {
    expect(ROLE_CONFIG[role].pages).toEqual(pagesFor(role));
  });

  it.each(Object.keys(ROLE_MENU))('gives every top level route of %s a manifest entry', (role) => {
    const slugs = pagesFor(role);
    const topLevel = roleRoutesFor(role).map((route) => route.path).filter((path) => !path.includes('/'));
    const orphans = topLevel.filter((path) => !slugs.includes(path));
    expect(orphans).toEqual([]);
  });

  it.each(Object.keys(ROLE_MENU))('gives every menu item of %s a real screen except the declared pending ones', (role) => {
    const pending = PENDING_SCREENS[role] ?? [];
    const missing = pagesFor(role).filter((slug) => !hasRealScreen(role, slug));
    expect(missing.filter((slug) => !pending.includes(slug) && slug !== 'inicio')).toEqual([]);
  });

  it.each(Object.keys(ROLE_MENU))('does not repeat destinations in the menu of %s', (role) => {
    const targets = menuItemsFor(role).map((item) => item.to);
    expect(new Set(targets).size).toBe(targets.length);
  });

  it('keeps the billing corridas screen reachable without a menu entry', () => {
    expect(pagesFor('administrativo')).toContain('corridas-de-facturacion');
    expect(menuItemsFor('administrativo').map((item) => item.to)).not.toContain('/administrativo/corridas-de-facturacion');
  });

  it('labels the academic management entry with the same name for administrative and director', () => {
    const label = (role) => menuItemsFor(role).find((item) => item.to.endsWith('/gestion-academica')).label;
    expect(label('administrativo')).toBe('Gestión académica');
    expect(label('director')).toBe('Gestión académica');
  });

  it('gives the director the academic offering and keeps the administrative out of it', () => {
    expect(pagesFor('director')).toContain('oferta-academica');
    expect(pagesFor('administrativo')).not.toContain('oferta-academica');
  });
});
