import { describe, expect, it } from 'vitest';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { ROLE_MENU, groupMenuItems, menuItemsFor, pagesFor } from '@/routes/roleMenu';

const SCREEN = 'invitaciones';
const PATHS = [SCREEN, `${SCREEN}/nueva`, `${SCREEN}/:waveId`, `${SCREEN}/:waveId/vista-previa`];
const ROLES_WITH_ACCESS = ['administrativo', 'director'];
const ROLES_WITHOUT_ACCESS = Object.keys(ROLE_MENU).filter((role) => !ROLES_WITH_ACCESS.includes(role));

describe('invitation waves screens access by role', () => {
  it.each(ROLES_WITH_ACCESS)('%s has the list, creation, preview and detail routes', (role) => {
    const paths = roleRoutesFor(role).map((route) => route.path);
    PATHS.forEach((path) => expect(paths).toContain(path));
  });

  it.each(ROLES_WITH_ACCESS)('%s has the menu entry', (role) => {
    expect(pagesFor(role)).toContain(SCREEN);
    expect(ROLE_CONFIG[role].pages).toContain(SCREEN);
    expect(menuItemsFor(role).map((item) => item.to)).toContain(`/${role}/${SCREEN}`);
    expect(menuItemsFor(role).map((item) => item.label)).toContain('Invitaciones');
  });

  it.each(ROLES_WITHOUT_ACCESS)('%s has none of the invitation routes nor the menu entry', (role) => {
    const paths = roleRoutesFor(role).map((route) => route.path);
    PATHS.forEach((path) => expect(paths).not.toContain(path));
    expect(pagesFor(role)).not.toContain(SCREEN);
    expect(menuItemsFor(role).map((item) => item.label)).not.toContain('Invitaciones');
  });

  it('covers subdirector, teacher, student and preceptor among those without access', () => {
    expect(ROLES_WITHOUT_ACCESS).toEqual(expect.arrayContaining(['subdirector', 'docente', 'alumno', 'preceptor']));
  });

  it('groupsTheDirectorEntryUnderPersonas', () => {
    const groups = groupMenuItems('director', menuItemsFor('director'));
    const people = groups.find((group) => group.id === 'people');
    expect(people.items.map((item) => item.label)).toContain('Invitaciones');
  });
});
