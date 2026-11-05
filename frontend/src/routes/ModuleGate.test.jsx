import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ModuleGate } from '@/routes/ModuleGate';

const API = 'http://localhost:8080';

const modulesWith = (mode) => ({
  billing: { mode },
  fiscalInvoicing: { enabled: false },
  analytics: { enabled: false },
  paymentMethods: [{ code: 'CASH', available: false }],
  billingNotifications: { enabled: false },
  externalBilling: { enabled: false },
});

function renderGate(path = 'facturacion', role = 'administrativo') {
  return renderWithProviders(
    <ModuleGate role={role} path={path}>
      <p>contenido de facturación</p>
    </ModuleGate>,
    { route: `/${role}/${path}` },
  );
}

describe('ModuleGate', () => {
  it('shows the disabled-module screen with a way back to home when billing is OFF', async () => {
    server.use(http.get(`${API}/me/modules`, () => HttpResponse.json(modulesWith('OFF'))));
    renderGate();

    expect(await screen.findByText('Este módulo no está habilitado en tu instituto')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a Inicio' })).toHaveAttribute('href', '/administrativo/inicio');
    expect(screen.queryByText('contenido de facturación')).not.toBeInTheDocument();
  });

  it.each(['MIRROR', 'FULL'])('renders the screen when billing is %s', async (mode) => {
    server.use(http.get(`${API}/me/modules`, () => HttpResponse.json(modulesWith(mode))));
    renderGate();

    expect(await screen.findByText('contenido de facturación')).toBeInTheDocument();
  });

  it('shows nothing of the screen while the modules are loading', () => {
    server.use(http.get(`${API}/me/modules`, () => new Promise(() => {})));
    renderGate();

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('contenido de facturación')).not.toBeInTheDocument();
  });

  it('keeps the screen closed and offers a retry when the modules query fails', async () => {
    server.use(http.get(`${API}/me/modules`, () => new HttpResponse(null, { status: 500 })));
    renderGate();

    expect(await screen.findByText('No pudimos cargar los módulos de tu instituto')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('contenido de facturación')).not.toBeInTheDocument();
  });

  it('does not ask for the modules on a screen that is not part of billing', () => {
    let requested = false;
    server.use(
      http.get(`${API}/me/modules`, () => {
        requested = true;
        return HttpResponse.json(modulesWith('OFF'));
      }),
    );
    renderGate('alumnos');

    expect(screen.getByText('contenido de facturación')).toBeInTheDocument();
    expect(requested).toBe(false);
  });

  it('gates the billing approvals of an administrative user but not the director approvals hub', async () => {
    server.use(http.get(`${API}/me/modules`, () => HttpResponse.json(modulesWith('OFF'))));
    const { unmount } = renderGate('aprobaciones', 'administrativo');
    expect(await screen.findByText('Este módulo no está habilitado en tu instituto')).toBeInTheDocument();
    unmount();

    renderGate('aprobaciones', 'director');
    expect(screen.getByText('contenido de facturación')).toBeInTheDocument();
  });
});
