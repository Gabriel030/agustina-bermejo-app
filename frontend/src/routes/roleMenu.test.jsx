import { describe, expect, it } from 'vitest';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { ROLE_MENU, groupMenuItems, menuGroupSlugs, menuItemsFor, pagesFor } from '@/routes/roleMenu';
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
  it('maps every visible director menu entry to a group', () => {
    const slugs = ROLE_MENU.director.filter((entry) => entry.slug && !entry.hidden).map((entry) => entry.slug);
    expect(slugs.filter((slug) => !menuGroupSlugs('director').includes(slug))).toEqual([]);
  });

  it('keeps every director item once and only in its group', () => {
    const items = menuItemsFor('director');
    const groups = groupMenuItems('director', items);
    expect(groups.flatMap((group) => group.items.map((item) => item.to)).sort()).toEqual(items.map((item) => item.to).sort());
    expect(groups.find((group) => group.id === 'billing').items.map((item) => item.label)).toContain('Emisión mensual');
  });

  it('drops groups left without visible items', () => {
    const withoutAcademic = menuItemsFor('director').filter(
      (item) => !['oferta-academica', 'gestion-academica', 'esquemas-evaluacion', 'mesas-de-examen', 'cierres-de-comision', 'periodos', 'constancias'].some((slug) => item.to.endsWith(slug)),
    );
    expect(groupMenuItems('director', withoutAcademic).map((group) => group.id)).not.toContain('academic');
  });

  it('keeps unmapped items in the headless first group instead of losing them', () => {
    const extra = { to: '/director/nuevo', label: 'Nuevo', icon: null };
    const groups = groupMenuItems('director', [...menuItemsFor('director'), extra]);
    expect(groups[0].label).toBeNull();
    expect(groups[0].items).toContain(extra);
  });

  it('has no grouping for roles without a definition', () => {
    expect(groupMenuItems('alumno', menuItemsFor('alumno'))).toBeNull();
  });

  it.each(['docente', 'preceptor', 'administrativo', 'director'])('exposes the exam boards screen to %s with its route', (role) => {
    expect(pagesFor(role)).toContain('mesas-de-examen');
    expect(roleRoutesFor(role).map((route) => route.path)).toContain('mesas-de-examen');
  });

  it.each(['alumno', 'platform_admin'])('keeps the exam boards screen away from %s', (role) => {
    expect(pagesFor(role)).not.toContain('mesas-de-examen');
  });

  it('lists the exam boards of the director under the academic group', () => {
    const academic = groupMenuItems('director', menuItemsFor('director')).find((group) => group.id === 'academic');
    expect(academic.items.map((item) => item.to)).toContain('/director/mesas-de-examen');
  });

  it.each(['administrativo', 'director'])('exposes the commission closures list to %s with its route', (role) => {
    expect(pagesFor(role)).toContain('cierres-de-comision');
    expect(roleRoutesFor(role).map((route) => route.path)).toContain('cierres-de-comision');
  });

  it.each(['docente', 'preceptor', 'alumno', 'platform_admin'])('keeps the commission closures list away from %s', (role) => {
    expect(pagesFor(role)).not.toContain('cierres-de-comision');
  });

  it('lists the commission closures of the director under the academic group', () => {
    const academic = groupMenuItems('director', menuItemsFor('director')).find((group) => group.id === 'academic');
    expect(academic.items.map((item) => item.to)).toContain('/director/cierres-de-comision');
  });
});
