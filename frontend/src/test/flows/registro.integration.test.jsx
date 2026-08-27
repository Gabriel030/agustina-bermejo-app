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
 * con datos reales del backend (mockeados vía MSW), mismo criterio que
 * login.integration.test.jsx. No cubre "solicitar alta"/bandeja: esas pantallas
 * (Mis solicitudes, Detalle de solicitud) todavía no existen — ver spec técnica.
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
});
