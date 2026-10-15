import { describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { ROLE_ROUTES } from '@/routes/featureRoutes';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { AdministrativoLayout } from '@/layouts/AdministrativoLayout';
import { DirectorLayout } from '@/layouts/DirectorLayout';

const shown = vi.hoisted(() => ({ items: [] }));

vi.mock('@/layouts/RoleShell', () => ({
  RoleShell: ({ items }) => {
    shown.items = items;
    return null;
  },
}));

describe('Financiero navigation', () => {
  it('d2-01 gives the administrative role the Financiero page and menu entry', () => {
    const route = ROLE_ROUTES.find((candidate) => candidate.path === 'financiero');
    expect(route.roles).toEqual(['administrativo', 'director']);
    expect(ROLE_CONFIG.administrativo.pages).toContain('financiero');

    render(<AdministrativoLayout />);
    expect(shown.items.map((item) => item.to)).toContain('/administrativo/financiero');
  });

  it('d2-02 keeps Facturación as a single entry and adds no billing sub-entries (D-219)', () => {
    render(<AdministrativoLayout />);
    const billing = shown.items.filter((item) => item.to.includes('facturacion') || item.to.includes('cobranza') || item.to.includes('corridas'));
    expect(billing.map((item) => item.to)).toEqual(['/administrativo/facturacion']);

    render(<DirectorLayout />);
    expect(shown.items.filter((item) => item.to.endsWith('/financiero'))).toHaveLength(1);
  });

  it('keeps the preceptor and the student out of the page', () => {
    const route = ROLE_ROUTES.find((candidate) => candidate.path === 'financiero');
    expect(route.roles).not.toContain('preceptor');
    expect(route.roles).not.toContain('alumno');
  });
});
