import { describe, expect, it } from 'vitest';
import { PUBLIC_ROUTES, SHARED_ROUTES, ROLE_ROUTES } from '@/routes/featureRoutes';

const describeRoute = (scope, route) =>
  `${scope}|${route.roles?.join(',') ?? '-'}|${route.path}|${route.element.type.displayName ?? route.element.type.name}`;

describe('route table', () => {
  it('keeps every path, role and page unchanged', () => {
    const table = [
      ...PUBLIC_ROUTES.map((route) => describeRoute('public', route)),
      ...SHARED_ROUTES.map((route) => describeRoute('shared', route)),
      ...ROLE_ROUTES.map((route) => describeRoute('role', route)),
    ].sort();
    expect(table).toMatchSnapshot();
  });
});
