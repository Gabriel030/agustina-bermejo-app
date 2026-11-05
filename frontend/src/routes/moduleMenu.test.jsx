import { describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { AdministrativoLayout } from '@/layouts/AdministrativoLayout';
import { DirectorLayout } from '@/layouts/DirectorLayout';
import { AlumnoLayout } from '@/layouts/AlumnoLayout';
import { DocenteLayout } from '@/layouts/DocenteLayout';

const API = 'http://localhost:8080';
const roleState = { current: 'ADMINISTRATIVO' };

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: [roleState.current] }, roles: [roleState.current], logout: vi.fn() }),
}));

const modulesWith = (mode) => ({
  billing: { mode },
  fiscalInvoicing: { enabled: false },
  analytics: { enabled: false },
  paymentMethods: [{ code: 'CASH', available: false }],
  billingNotifications: { enabled: false },
  externalBilling: { enabled: false },
});

const BILLING_LABELS = {
  ADMINISTRATIVO: ['Facturación', 'Descuentos', 'Recordatorios de pago', 'Emisión mensual', 'Aprobaciones', 'Financiero'],
  DIRECTOR: ['Facturación', 'Descuentos', 'Recordatorios de pago', 'Emisión mensual', 'Financiero'],
  ALUMNO: ['Pagos'],
};

const LAYOUTS = {
  ADMINISTRATIVO: [AdministrativoLayout, 'Alumnos'],
  DIRECTOR: [DirectorLayout, 'Alumnos'],
  ALUMNO: [AlumnoLayout, 'Mis cursos'],
};

function renderMenu(role, Layout) {
  roleState.current = role;
  return renderWithProviders(<ThemeProvider><Layout /></ThemeProvider>, { route: '/x' });
}

const hasLink = (name) => screen.queryAllByRole('link', { name }).length > 0;

describe('menu according to the institute modules', () => {
  describe.each(Object.keys(LAYOUTS))('%s', (role) => {
    const [Layout, alwaysVisible] = LAYOUTS[role];

    it('hides every billing entry and keeps the rest when billing is OFF', async () => {
      server.use(http.get(`${API}/me/modules`, () => HttpResponse.json(modulesWith('OFF'))));
      renderMenu(role, Layout);

      expect((await screen.findAllByRole('link', { name: alwaysVisible })).length).toBeGreaterThan(0);
      await waitFor(() => expect(screen.queryAllByRole('link', { name: BILLING_LABELS[role][0] })).toHaveLength(0));
      BILLING_LABELS[role].forEach((label) => expect(hasLink(label)).toBe(false));
    });

    it.each(['MIRROR', 'FULL'])('shows every billing entry when billing is %s', async (mode) => {
      server.use(http.get(`${API}/me/modules`, () => HttpResponse.json(modulesWith(mode))));
      renderMenu(role, Layout);

      for (const label of BILLING_LABELS[role]) {
        expect((await screen.findAllByRole('link', { name: label })).length).toBeGreaterThan(0);
      }
    });

    it('hides the billing entries when the modules query fails (safe default: closed)', async () => {
      let answered = false;
      server.use(
        http.get(`${API}/me/modules`, () => {
          answered = true;
          return new HttpResponse(null, { status: 500 });
        }),
      );
      renderMenu(role, Layout);

      expect((await screen.findAllByRole('link', { name: alwaysVisible })).length).toBeGreaterThan(0);
      await waitFor(() => expect(answered).toBe(true));
      BILLING_LABELS[role].forEach((label) => expect(hasLink(label)).toBe(false));
    });
  });

  it('keeps the director approvals hub when billing is OFF', async () => {
    server.use(http.get(`${API}/me/modules`, () => HttpResponse.json(modulesWith('OFF'))));
    renderMenu('DIRECTOR', DirectorLayout);

    expect((await screen.findAllByRole('link', { name: 'Aprobaciones' })).length).toBeGreaterThan(0);
  });

  it('does not ask for the modules in a menu without billing entries', async () => {
    let requested = false;
    server.use(
      http.get(`${API}/me/modules`, () => {
        requested = true;
        return HttpResponse.json(modulesWith('OFF'));
      }),
    );
    renderMenu('DOCENTE', DocenteLayout);

    expect((await screen.findAllByRole('link', { name: 'Mis comisiones' })).length).toBeGreaterThan(0);
    expect(requested).toBe(false);
  });
});
