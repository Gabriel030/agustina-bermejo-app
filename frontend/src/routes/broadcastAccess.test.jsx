import { describe, expect, it } from 'vitest';
import { roleRoutesFor } from '@/routes/featureRoutes';

const BROADCAST_WIZARD_PATH = 'comunicaciones/nuevo';

describe('broadcast wizard access by role', () => {
  it.each(['administrativo', 'director'])('%s has the wizard route', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).toContain(BROADCAST_WIZARD_PATH);
  });

  it.each(['alumno', 'docente', 'preceptor'])('%s does not have the wizard route', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).not.toContain(BROADCAST_WIZARD_PATH);
  });
});
