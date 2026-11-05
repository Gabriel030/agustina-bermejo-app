import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen } from '@testing-library/react';
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

describe('AppRouter', () => {
  it('does not treat /director/alumnos/nuevo as the record of a student called "nuevo"', async () => {
    const requestedStudents = [];
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 2, nombre: 'Dora', apellido: 'Directora', email: 'dora@demo.test', roles: ['DIRECTOR'] }),
      ),
      http.get(`${API}/students/:id`, ({ params }) => {
        requestedStudents.push(params.id);
        return new HttpResponse(null, { status: 400 });
      }),
    );
    renderApp('/director/alumnos/nuevo');

    const user = userEvent.setup();
    await user.type(await screen.findByPlaceholderText('DNI o email'), 'director.demo');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    expect(await screen.findByText('No autorizado')).toBeInTheDocument();
    expect(requestedStudents).not.toContain('nuevo');
  });
  async function signIn(user) {
    await user.type(await screen.findByPlaceholderText('DNI o email'), 'platform.admin');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));
  }

  it('sends a platform admin to the first director screen with the platform layout', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 9, nombre: 'Plataforma', apellido: 'Admin', email: 'platform@demo.test', roles: ['PLATFORM_ADMIN'] }),
      ),
      http.get(`${API}/platform/provisioning/first-director/status`, () => HttpResponse.json({ directorExists: false, institutionName: null })),
    );
    renderApp('/platform_admin/inicio');

    await signIn(userEvent.setup());

    expect(await screen.findByRole('button', { name: 'Crear Director' })).toBeInTheDocument();
    expect(screen.getAllByText('Plataforma').length).toBeGreaterThan(0);
  });

  it.each([
    ['DOCENTE', 'docente', 'Mis comisiones'],
    ['PRECEPTOR', 'preceptor', 'Padrón de alumnos'],
  ])('lands a %s without a home screen on its first real screen instead of a placeholder', async (role, slug, heading) => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 5, nombre: 'Rol', apellido: 'Prueba', email: 'rol@demo.test', roles: [role] }),
      ),
      http.get(`${API}/students`, () => HttpResponse.json([])),
    );
    renderApp(`/${slug}/inicio`);

    await signIn(userEvent.setup());

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    expect(screen.queryByText(/Próximamente/)).not.toBeInTheDocument();
  });

  it('shows a readable title instead of the slug on a placeholder reached by URL', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 6, nombre: 'Ana', apellido: 'Admin', email: 'ana@demo.test', roles: ['ADMINISTRATIVO'] }),
      ),
    );
    renderApp('/administrativo/constancias');

    await signIn(userEvent.setup());

    expect(await screen.findByText('Constancias')).toBeInTheDocument();
    expect(screen.getByText(/Próximamente vas a poder gestionar esta sección/)).toBeInTheDocument();
    expect(screen.queryByText('constancias')).not.toBeInTheDocument();
  });

  it('does not let a director open the platform screens', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 2, nombre: 'Dora', apellido: 'Directora', email: 'dora@demo.test', roles: ['DIRECTOR'] }),
      ),
    );
    renderApp('/platform_admin/primer-director');

    await signIn(userEvent.setup());

    expect(await screen.findByText('No autorizado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Crear Director' })).not.toBeInTheDocument();
  });
  it('stops after a single refresh and shows the login error when /me answers 403 after logging in', async () => {
    let profileRequests = 0;
    let refreshRequests = 0;
    server.use(
      http.get(`${API}/me`, () => {
        profileRequests += 1;
        return new HttpResponse(null, { status: 403 });
      }),
      http.post(`${API}/auth/refresh`, () => {
        refreshRequests += 1;
        return HttpResponse.json({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 900 });
      }),
    );
    renderApp('/login');

    await signIn(userEvent.setup());

    expect(await screen.findByText('Usuario o contraseña incorrectos.')).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 500));
    expect(profileRequests).toBeLessThanOrEqual(2);
    expect(refreshRequests).toBeLessThanOrEqual(1);
  });
});
