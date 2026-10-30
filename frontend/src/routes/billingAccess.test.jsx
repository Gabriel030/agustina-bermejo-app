import { describe, expect, it } from 'vitest';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { roleRoutesFor } from '@/routes/featureRoutes';

const BILLING_SCREEN = 'facturacion';

describe('billing screen access by role', () => {
  it.each(['administrativo', 'director'])('%s has the route and the menu entry', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).toContain(BILLING_SCREEN);
    expect(ROLE_CONFIG[role].pages).toContain(BILLING_SCREEN);
  });

  it.each(['alumno', 'docente', 'preceptor'])('%s has neither the route nor the menu entry', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).not.toContain(BILLING_SCREEN);
    expect(ROLE_CONFIG[role].pages).not.toContain(BILLING_SCREEN);
  });

  it('keeps the administrative menu order untouched', () => {
    expect(ROLE_CONFIG.administrativo.pages).toEqual([
      'inicio', 'alumnos', 'personal', 'facturacion', 'descuentos', 'recordatorios-de-pago', 'corridas-de-facturacion', 'emision-mensual', 'aprobaciones', 'financiero', 'constancias', 'solicitudes-de-asistencia', 'gestion-academica',
    ]);
  });
});
