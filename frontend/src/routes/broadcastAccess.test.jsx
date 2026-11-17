import { describe, expect, it } from 'vitest';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { ROLE_MENU, menuItemsFor, pagesFor } from '@/routes/roleMenu';

const BROADCASTS_SCREEN = 'comunicaciones';
const ROLES_WITH_ACCESS = ['administrativo', 'director'];
const ROLES_WITHOUT_ACCESS = Object.keys(ROLE_MENU).filter((role) => !ROLES_WITH_ACCESS.includes(role));

describe('broadcasts screen access by role', () => {
  it.each(ROLES_WITH_ACCESS)('%s has the route and the menu entry', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).toContain(BROADCASTS_SCREEN);
    expect(pagesFor(role)).toContain(BROADCASTS_SCREEN);
    expect(ROLE_CONFIG[role].pages).toContain(BROADCASTS_SCREEN);
    expect(menuItemsFor(role).map((item) => item.to)).toContain(`/${role}/${BROADCASTS_SCREEN}`);
    expect(menuItemsFor(role).map((item) => item.label)).toContain('Comunicaciones');
  });

  it.each(ROLES_WITHOUT_ACCESS)('%s has neither the route nor the menu entry', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).not.toContain(BROADCASTS_SCREEN);
    expect(pagesFor(role)).not.toContain(BROADCASTS_SCREEN);
    expect(ROLE_CONFIG[role].pages).not.toContain(BROADCASTS_SCREEN);
    expect(menuItemsFor(role).map((item) => item.label)).not.toContain('Comunicaciones');
  });

  it('covers the teacher, student and preceptor roles among those without access', () => {
    expect(ROLES_WITHOUT_ACCESS).toEqual(expect.arrayContaining(['docente', 'alumno', 'preceptor']));
  });
});
