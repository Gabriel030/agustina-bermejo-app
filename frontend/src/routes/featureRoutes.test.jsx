import { describe, expect, it } from 'vitest';
import { isValidElement } from 'react';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { FEATURE_ROUTES, PUBLIC_ROUTES, SHARED_ROUTES, ROLE_ROUTES, roleRoutesFor } from '@/routes/featureRoutes';

const declaredRoutes = [
  ...PUBLIC_ROUTES.map((route) => ({ ...route, scope: 'public' })),
  ...SHARED_ROUTES.map((route) => ({ ...route, scope: 'shared' })),
  ...ROLE_ROUTES.map((route) => ({ ...route, scope: 'role' })),
];

describe('feature routes', () => {
  it('declares routes for every backend module', () => {
    expect(Object.keys(FEATURE_ROUTES).sort()).toEqual(
      [
        'academic-structure',
        'announcements',
        'attendance',
        'billing',
        'enrollment',
        'evaluation',
        'graduation',
        'identity',
        'notifications',
        'workspace',
      ].sort(),
    );
  });

  it.each(declaredRoutes.map((route) => [`${route.scope}:${route.roles?.join(',') ?? ''}:${route.path}`, route]))(
    'resolves a component for %s',
    (_name, route) => {
      expect(typeof route.path).toBe('string');
      expect(isValidElement(route.element)).toBe(true);
      expect(route.element.type).toBeTruthy();
    },
  );

  it('only targets roles that have a layout', () => {
    ROLE_ROUTES.forEach((route) => {
      route.roles.forEach((role) => expect(Object.keys(ROLE_CONFIG)).toContain(role));
    });
  });

  it('does not declare the same path twice for the same role', () => {
    Object.keys(ROLE_CONFIG).forEach((role) => {
      const paths = roleRoutesFor(role).map((route) => route.path);
      expect(new Set(paths).size).toBe(paths.length);
    });
  });
});
