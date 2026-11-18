import { describe, expect, it } from 'vitest';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { ROLE_MENU, groupMenuItems, menuItemsFor, pagesFor } from '@/routes/roleMenu';
import { roleRoutesFor } from '@/routes/featureRoutes';

const AUDIT_SCREEN = 'auditoria';
const ALLOWED = ['director', 'platform_admin'];
const DENIED = Object.keys(ROLE_MENU).filter((role) => !ALLOWED.includes(role));

describe('audit screen access by role', () => {
  it('covers every role in the manifest', () => {
    expect(DENIED).toEqual(expect.arrayContaining(['alumno', 'docente', 'preceptor', 'administrativo']));
  });

  it.each(ALLOWED)('%s has the route, the manifest entry and the menu item', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).toContain(AUDIT_SCREEN);
    expect(pagesFor(role)).toContain(AUDIT_SCREEN);
    expect(ROLE_CONFIG[role].pages).toContain(AUDIT_SCREEN);
    expect(menuItemsFor(role).map((item) => item.to)).toContain(`/${role}/${AUDIT_SCREEN}`);
  });

  it.each(DENIED)('%s has neither the route nor the menu item', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).not.toContain(AUDIT_SCREEN);
    expect(pagesFor(role)).not.toContain(AUDIT_SCREEN);
    expect(menuItemsFor(role).map((item) => item.to)).not.toContain(`/${role}/${AUDIT_SCREEN}`);
  });

  it('places the director item in the institution group', () => {
    const groups = groupMenuItems('director', menuItemsFor('director'));
    const institution = groups.find((group) => group.id === 'institution');
    expect(institution.items.map((item) => item.to)).toContain(`/director/${AUDIT_SCREEN}`);
  });

  it('routes each role to its own endpoint', () => {
    const endpointOf = (role) => roleRoutesFor(role).find((route) => route.path === AUDIT_SCREEN).element.props.endpoint;
    expect(endpointOf('director')).toBe('/audit-log');
    expect(endpointOf('platform_admin')).toBe('/platform/audit-log');
  });
});
