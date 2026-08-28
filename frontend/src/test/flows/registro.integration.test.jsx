import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { AppRouter } from '@/routes/AppRouter';

const API = 'http://localhost:8080';

/*
 * Integration test del flujo crítico de la Épica 7 Fase 2 — registro → catálogo
 * → solicitar alta → ver la solicitud en PENDING en la bandeja, con datos reales
 * del backend (mockeados vía MSW), mismo criterio que login.integration.test.jsx.
 */
function renderApp(initialPath = '/registrarse') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <AppRouter />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('flujo de registro end-to-end', () => {
  it('registrarse sin rol asignado redirige a /catalogo, no a una pantalla de rol', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 9, nombre: 'Sofía', apellido: 'Ramírez', email: 'sofia@example.com', roles: [] }),
      ),
      http.get(`${API}/programs`, () => HttpResponse.json([{ id: 1, name: 'Diseño Gráfico', totalYears: 3 }])),
    );
    const user = userEvent.setup();
    renderApp();

    await user.type(screen.getByPlaceholderText('Nombre'), 'Sofía');
    await user.type(screen.getByPlaceholderText('Apellido'), 'Ramírez');
    await user.type(screen.getByPlaceholderText('tu@email.com'), 'sofia@example.com');
    await user.type(screen.getByPlaceholderText('Contraseña'), 'Test1234');
    await user.type(screen.getByPlaceholderText('Repetir contraseña'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.getAllByText('Catálogo').length).toBeGreaterThan(0);
  });

  it('registrarse → solicitar alta → ver la solicitud PENDING en Mis solicitudes', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 9, nombre: 'Sofía', apellido: 'Ramírez', email: 'sofia@example.com', roles: [] }),
      ),
      http.get(`${API}/programs`, () => HttpResponse.json([{ id: 1, name: 'Diseño Gráfico', totalYears: 3 }])),
      http.post(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json({ id: 42, programId: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }, { status: 201 }),
      ),
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 42, programId: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
    );
    const user = userEvent.setup();
    renderApp();

    await user.type(screen.getByPlaceholderText('Nombre'), 'Sofía');
    await user.type(screen.getByPlaceholderText('Apellido'), 'Ramírez');
    await user.type(screen.getByPlaceholderText('tu@email.com'), 'sofia@example.com');
    await user.type(screen.getByPlaceholderText('Contraseña'), 'Test1234');
    await user.type(screen.getByPlaceholderText('Repetir contraseña'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    await user.click(await screen.findByRole('button', { name: /solicitar alta/i }));
    expect(await screen.findByText('Solicitud enviada')).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Mis solicitudes' }));

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
  });
});
