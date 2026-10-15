import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';

const API = 'http://localhost:8080';

function renderApp(initialPath) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <GoogleOAuthProvider clientId="test-client-id">
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[initialPath]}>
            <AuthProvider>
              <AppRouter />
            </AuthProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </ThemeProvider>
    </GoogleOAuthProvider>,
  );
}

async function signInAs(role, path) {
  server.use(
    http.get(`${API}/me`, () => HttpResponse.json({ id: 2, nombre: 'Uno', apellido: 'Prueba', email: 'uno@demo.test', roles: [role] })),
    http.get(`${API}/financial-dashboard`, () => HttpResponse.json({ studentsInDebt: 1, studentsUpToDate: 2, totalsByCurrency: [] })),
    http.get(`${API}/financial-dashboard/debtors`, () => HttpResponse.json([])),
    http.get(`${API}/billing/metrics/:panel`, ({ params }) =>
      HttpResponse.json({ panel: params.panel, available: true, partial: false, months: ['2026-10'], series: [] }),
    ),
  );
  renderApp(path);
  const user = userEvent.setup();
  await user.type(await screen.findByPlaceholderText('DNI o email'), 'demo');
  await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
  await user.click(screen.getByRole('button', { name: /ingresar/i }));
}

describe('Financiero access by role', () => {
  it('d2-01 lets the administrative role open Financiero from the menu and see the page', async () => {
    await signInAs('ADMINISTRATIVO', '/administrativo/inicio');

    const links = await screen.findAllByRole('link', { name: 'Financiero' });
    await userEvent.click(links[0]);

    expect(await screen.findByRole('heading', { name: 'Financiero' })).toBeInTheDocument();
    expect(await screen.findByText('Pago a término', { selector: 'div' })).toBeInTheDocument();
  });

  it('d2-02 keeps the administrative menu with a single Facturación entry and no billing sub-menu (D-219)', async () => {
    await signInAs('ADMINISTRATIVO', '/administrativo/inicio');

    const nav = (await screen.findAllByRole('navigation'))[0];
    const labels = within(nav).getAllByRole('link').map((link) => link.textContent.trim());
    expect(labels).toContain('Facturación');
    expect(labels).toContain('Financiero');
    expect(labels.filter((label) => /Cobranza|Corridas|Configuración/.test(label))).toEqual([]);
    expect(labels.filter((label) => label === 'Facturación')).toHaveLength(1);
  });

  it('keeps the preceptor out of Financiero', async () => {
    await signInAs('PRECEPTOR', '/preceptor/financiero');

    await screen.findAllByRole('navigation');
    expect(screen.queryByRole('link', { name: 'Financiero' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Financiero' })).not.toBeInTheDocument();
  });
});
