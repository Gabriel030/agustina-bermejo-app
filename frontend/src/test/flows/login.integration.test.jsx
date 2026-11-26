import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { profileFixture } from '@/test/fixtures/profile';
import { contractList } from '@/test/fixtures/fromContract';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';

/*
 * Integration test del flujo más crítico del roadmap (login) — varios
 * componentes reales montados juntos, solo MSW mockeando el backend. Ver
 * docs/context/testing.md: este es uno de los pocos flujos que se blindan a
 * este nivel, no se hace uno por pantalla.
 */
function renderApp(initialPath = '/login') {
  // QueryClientProvider: RoleShell.jsx usa React Query para CUALQUIER rol
  // (useBreadcrumb.jsx llama useMiInscripcion en todo momento, aunque
  // deshabilitado fuera de Detalle de materia — useQuery igual exige el
  // provider en el árbol) — mismo patrón que registro.integration.test.jsx.
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

async function login() {
  const user = userEvent.setup();
  await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
  await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
  await user.click(screen.getByRole('button', { name: /ingresar/i }));
}

const DIRECTOR_HOME_SOURCES = [
  '/graduation-requests',
  '/academic-offering-cycles',
  '/section-evaluation-configs',
  '/director-approval-requests',
  '/commission-closures',
  '/exam-boards',
  '/authorization-requests',
];

describe('flujo de login end-to-end', () => {
  it('login válido redirige a /preceptor/inicio para un usuario con rol PRECEPTOR (mock default)', async () => {
    renderApp();
    await login();

    // "Preceptor" aparece 3 veces en el DOM a la vez (Sidebar desktop + título
    // mobile de TopHeader + rol junto al nombre en TopHeader, RoleShell.jsx) —
    // la visibilidad entre ellos es puramente CSS (hidden/lg:hidden/sm:block),
    // jsdom no oculta ninguno.
    expect(await screen.findAllByText('Preceptor')).toHaveLength(3);
    // "Prueba Login" aparece 2 veces (Sidebar desktop + TopHeader) — mismo
    // criterio que "Preceptor" arriba.
    expect(await screen.findAllByText('Prueba Login')).toHaveLength(2);
  });

  it('un Preceptor logueado que entra a /director/aprobaciones es redirigido a No autorizado', async () => {
    renderApp('/director/aprobaciones');
    await login();

    // ProtectedRoute redirige antes de renderizar el layout de Director.
    expect(await screen.findByText('No autorizado')).toBeInTheDocument();
  });

  it('después de cerrar sesión, el siguiente usuario no hereda la página del anterior', async () => {
    const user = userEvent.setup();
    renderApp();
    await login();
    expect(await screen.findAllByText('Preceptor')).toHaveLength(3);

    server.use(
      http.get('http://localhost:8080/me', () =>
        HttpResponse.json(
          profileFixture({ id: 2, nombre: 'Otra', apellido: 'Persona', email: 'otra@example.com', roles: ['DIRECTOR'] }),
        ),
      ),
      ...DIRECTOR_HOME_SOURCES.map((path) => http.get(`http://localhost:8080${path}`, () => HttpResponse.json(contractList(path, [])))),
    );
    await user.click(screen.getAllByRole('button', { name: 'Cerrar sesión' })[0]);
    await screen.findByPlaceholderText('DNI o email');
    await login();

    expect(await screen.findAllByText('Otra Persona')).toHaveLength(3);
    expect(screen.queryByText('No autorizado')).not.toBeInTheDocument();
  });

  it('un Preceptor puede abrir las solicitudes de cambio de asistencia', async () => {
    server.use(http.get('http://localhost:8080/attendance/change-requests', () => HttpResponse.json([])));
    renderApp('/preceptor/solicitudes-de-asistencia');
    await login();

    expect(await screen.findByRole('heading', { name: 'Solicitudes de asistencia' })).toBeInTheDocument();
  });

  it('answers the Director pending configs request even after the test handlers are reset', async () => {
    server.resetHandlers();
    const response = await fetch('http://localhost:8080/section-evaluation-configs?status=PENDING_APPROVAL');
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual([]);
  });

  it('recargar con la marca de sesión restaura la sesión sin pasar por /login', async () => {
    localStorage.setItem('sessionHint', '1');
    renderApp('/preceptor/inicio');

    expect(await screen.findAllByText('Prueba Login')).toHaveLength(2);
  });
});
