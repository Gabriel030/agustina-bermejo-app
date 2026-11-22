import { describe, expect, it } from 'vitest';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { ROLE_MENU, menuItemsFor, pagesFor } from '@/routes/roleMenu';

const BROADCASTS_SCREEN = 'comunicaciones';
const BROADCAST_WIZARD_PATH = 'comunicaciones/nuevo';
const BROADCAST_DETAIL_PATH = 'comunicaciones/:broadcastId';
const BROADCAST_ROUTES = [BROADCAST_WIZARD_PATH, BROADCASTS_SCREEN, BROADCAST_DETAIL_PATH];
const ROLES_WITH_ACCESS = ['administrativo', 'subdirector', 'director'];
const ROLES_WITHOUT_ACCESS = Object.keys(ROLE_MENU).filter((role) => !ROLES_WITH_ACCESS.includes(role));

describe('broadcasts screens access by role', () => {
  it.each(ROLES_WITH_ACCESS)('%s has the wizard, list and detail routes', (role) => {
    const paths = roleRoutesFor(role).map((route) => route.path);
    BROADCAST_ROUTES.forEach((path) => expect(paths).toContain(path));
  });

  it.each(ROLES_WITH_ACCESS)('%s has the menu entry', (role) => {
    expect(pagesFor(role)).toContain(BROADCASTS_SCREEN);
    expect(ROLE_CONFIG[role].pages).toContain(BROADCASTS_SCREEN);
    expect(menuItemsFor(role).map((item) => item.to)).toContain(`/${role}/${BROADCASTS_SCREEN}`);
    expect(menuItemsFor(role).map((item) => item.label)).toContain('Comunicaciones');
  });

  it.each(ROLES_WITHOUT_ACCESS)('%s has none of the broadcast routes nor the menu entry', (role) => {
    const paths = roleRoutesFor(role).map((route) => route.path);
    BROADCAST_ROUTES.forEach((path) => expect(paths).not.toContain(path));
    expect(pagesFor(role)).not.toContain(BROADCASTS_SCREEN);
    expect(ROLE_CONFIG[role].pages).not.toContain(BROADCASTS_SCREEN);
    expect(menuItemsFor(role).map((item) => item.label)).not.toContain('Comunicaciones');
  });

  it('covers the teacher, student and preceptor roles among those without access', () => {
    expect(ROLES_WITHOUT_ACCESS).toEqual(expect.arrayContaining(['docente', 'alumno', 'preceptor']));
  });
});
