import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { extractRoleRoutes, findCoverageGaps } from './coverage.mjs';
import { roles, excludedRoles } from './screens.mjs';

const FEATURES_DIR = fileURLToPath(new URL('../../frontend/src/features', import.meta.url));

test('every static route of every role is listed in screens', () => {
  assert.deepEqual(findCoverageGaps(extractRoleRoutes(FEATURES_DIR), roles, excludedRoles), []);
});

test('an unlisted route and an unknown role are reported as gaps', () => {
  const routes = { director: new Set([...roles.Director.screens, ...roles.Director.details.map((d) => d.route), 'brand-new-screen']), ghost: new Set(['inicio']) };
  const gaps = findCoverageGaps(routes, roles, excludedRoles);
  assert.deepEqual(gaps.map((gap) => `${gap.role}:${gap.route}`), ['director:brand-new-screen', 'ghost:*']);
});

test('a listed screen that no longer exists is reported as a gap', () => {
  const gaps = findCoverageGaps({ docente: new Set(['inicio']) }, roles, excludedRoles);
  assert.ok(gaps.some((gap) => gap.role === 'docente' && gap.route === 'mis-comisiones'));
});

test('excluded roles are skipped', () => {
  assert.deepEqual(findCoverageGaps({ subdirector: new Set(['permisos']) }, roles, excludedRoles), []);
});
