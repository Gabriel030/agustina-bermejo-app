import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { apiFetch, setOfflineSessionMode } from '@/api/client';
import { OFFLINE_OWNER_KEY, storeOfflineProfile } from '@/api/offlinePersistence';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';
import { profileFixture } from '@/test/fixtures/profile';

const API = 'http://localhost:8080';

const idbStore = new Map();

vi.mock('idb-keyval', () => ({
  get: vi.fn(async (key) => idbStore.get(key)),
  set: vi.fn(async (key, value) => {
    idbStore.set(key, value);
  }),
  del: vi.fn(async (key) => {
    idbStore.delete(key);
  }),
  keys: vi.fn(async () => [...idbStore.keys()]),
}));

let activeQueryClient = null;

function renderApp(initialPath) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  activeQueryClient = queryClient;
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
  beforeEach(() => {
    vi.stubGlobal('indexedDB', {});
    idbStore.clear();
  });

  afterEach(async () => {
    vi.useRealTimers();
    if (activeQueryClient) {
      await waitFor(() => expect(activeQueryClient.isFetching()).toBe(0));
      activeQueryClient.clear();
      activeQueryClient = null;
    }
    setOfflineSessionMode(false);
    vi.unstubAllGlobals();
  });

  it('showsTheOfflineStateInsteadOfLoginWhenTheStoredSessionCannotBeRefreshed', async () => {
    localStorage.setItem('refreshToken', 'stored');
    server.use(http.post(`${API}/auth/refresh`, () => new HttpResponse(null, { status: 503 })));
    renderApp('/director/inicio');

    expect(await screen.findByText('Sin conexión', {}, { timeout: 30000 })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('DNI o email')).not.toBeInTheDocument();
    expect(localStorage.getItem('refreshToken')).toBe('stored');
    localStorage.clear();
  }, 40000);

  it('coldStartOfflineWithStoredProfileShowsTheMenuAndTheOfflineState', async () => {
    localStorage.setItem('refreshToken', 'stored');
    idbStore.set(OFFLINE_OWNER_KEY, 2);
    await storeOfflineProfile({ nombre: 'Dora', apellido: 'Directora', roles: ['DIRECTOR'] });
    const meRequests = [];
    server.use(
      http.post(`${API}/auth/refresh`, () => new HttpResponse(null, { status: 503 })),
      http.get(`${API}/me`, () => {
        meRequests.push(1);
        return HttpResponse.json(profileFixture({ id: 2, roles: ['DIRECTOR'] }));
      }),
    );
    renderApp('/director/inicio');

    expect(await screen.findByText('Sin conexión', {}, { timeout: 30000 })).toBeInTheDocument();
    expect(screen.getAllByText(/Dora Directora/).length).toBeGreaterThan(0);
    expect(meRequests).toHaveLength(0);
    localStorage.clear();
  }, 40000);

  it('does not treat /director/alumnos/nuevo as the record of a student called "nuevo"', async () => {
    const requestedStudents = [];
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json(profileFixture({ id: 2, nombre: 'Dora', apellido: 'Directora', email: 'dora@demo.test', roles: ['DIRECTOR'] })),
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

    expect(await screen.findByRole('heading', { name: 'Alta de alumno' })).toBeInTheDocument();
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
        HttpResponse.json(profileFixture({ id: 9, nombre: 'Plataforma', apellido: 'Admin', email: 'platform@demo.test', roles: ['PLATFORM_ADMIN'] })),
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
        HttpResponse.json(profileFixture({ id: 5, nombre: 'Rol', apellido: 'Prueba', email: 'rol@demo.test', roles: [role] })),
      ),
    );
    renderApp(`/${slug}/inicio`);

    await signIn(userEvent.setup());

    expect(await screen.findByRole('heading', { name: heading }, { timeout: 30000 })).toBeInTheDocument();
    expect(screen.queryByText(/Próximamente/)).not.toBeInTheDocument();
  }, 40000);

  it('shows a readable title instead of the slug on a placeholder reached by URL', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json(profileFixture({ id: 6, nombre: 'Ana', apellido: 'Admin', email: 'ana@demo.test', roles: ['ADMINISTRATIVO'] })),
      ),
    );
    renderApp('/administrativo/constancias');

    await signIn(userEvent.setup());

    expect(await screen.findByText('Constancias')).toBeInTheDocument();
    expect(screen.getByText(/Próximamente vas a poder gestionar esta sección/)).toBeInTheDocument();
    expect(screen.queryByText('constancias')).not.toBeInTheDocument();
  });

  it('shows the disabled-module screen on a direct billing URL when billing is OFF instead of redirecting', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json(profileFixture({ id: 6, nombre: 'Ana', apellido: 'Admin', email: 'ana@demo.test', roles: ['ADMINISTRATIVO'] })),
      ),
      http.get(`${API}/me/modules`, () =>
        HttpResponse.json({
          billing: { mode: 'OFF' },
          fiscalInvoicing: { enabled: false },
          analytics: { enabled: false },
          paymentMethods: [{ code: 'CASH', available: false }],
          billingNotifications: { enabled: false },
          externalBilling: { enabled: false },
        }),
      ),
    );
    renderApp('/administrativo/facturacion');

    await signIn(userEvent.setup());

    expect(await screen.findByText('Este módulo no está habilitado en tu instituto')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a Inicio' })).toHaveAttribute('href', '/administrativo/inicio');
  });

  it('keeps a direct billing URL closed when the modules query fails', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json(profileFixture({ id: 6, nombre: 'Ana', apellido: 'Admin', email: 'ana@demo.test', roles: ['ADMINISTRATIVO'] })),
      ),
      http.get(`${API}/me/modules`, () => new HttpResponse(null, { status: 500 })),
    );
    renderApp('/administrativo/descuentos');

    await signIn(userEvent.setup());

    expect(await screen.findByText('No pudimos cargar los módulos de tu instituto')).toBeInTheDocument();
  });

  it('lets a director reach the certificates section', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json(profileFixture({ id: 6, nombre: 'Dora', apellido: 'Dir', email: 'dora@demo.test', roles: ['DIRECTOR'] })),
      ),
    );
    renderApp('/director/constancias');

    await signIn(userEvent.setup());

    expect(await screen.findByText('Constancias')).toBeInTheDocument();
    expect(screen.getByText(/Próximamente vas a poder gestionar esta sección/)).toBeInTheDocument();
  });

  it('does not let a director open the platform screens', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json(profileFixture({ id: 2, nombre: 'Dora', apellido: 'Directora', email: 'dora@demo.test', roles: ['DIRECTOR'] })),
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
    vi.useFakeTimers({ toFake: ['setTimeout', 'setInterval'] });
    await vi.advanceTimersByTimeAsync(10000);
    expect(profileRequests).toBeLessThanOrEqual(2);
    expect(refreshRequests).toBeLessThanOrEqual(1);
  });

  it('showsTheNotFoundPageInsideTheRoleLayoutForUnknownRolePaths', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json(profileFixture({ id: 6, nombre: 'Dora', apellido: 'Dir', email: 'dora@demo.test', roles: ['DIRECTOR'] })),
      ),
    );
    renderApp('/director/ruta-inexistente');

    await signIn(userEvent.setup());

    expect(await screen.findByText('Página no encontrada')).toBeInTheDocument();
    expect(screen.getAllByText('Oferta académica').length).toBeGreaterThan(0);
  });

  it('sendsAnAnonymousVisitorOnAnUnknownPathToLoginInsteadOfTheNotFoundPage', async () => {
    renderApp('/ruta-inexistente');

    expect(await screen.findByPlaceholderText('DNI o email')).toBeInTheDocument();
    expect(screen.queryByText('Página no encontrada')).not.toBeInTheDocument();
  });

  it('showsAMessageAndReturnsToTheOriginalRouteAfterAnExpiredSession', async () => {
    let refreshRequests = 0;
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json(profileFixture({ id: 6, nombre: 'Dora', apellido: 'Dir', email: 'dora@demo.test', roles: ['DIRECTOR'] })),
      ),
      http.get(`${API}/institutions`, () => new HttpResponse(null, { status: 401 })),
    );
    renderApp('/director/constancias');
    const user = userEvent.setup();
    await signIn(user);
    expect(await screen.findByText(/Próximamente vas a poder gestionar esta sección/)).toBeInTheDocument();

    server.use(
      http.post(`${API}/auth/refresh`, () => {
        refreshRequests += 1;
        return new HttpResponse(null, { status: 401 });
      }),
    );
    await act(async () => {
      await Promise.allSettled([apiFetch('/institutions'), apiFetch('/institutions')]);
    });

    expect(await screen.findByText('Tu sesión venció. Ingresá de nuevo para continuar.')).toBeInTheDocument();
    expect(refreshRequests).toBe(1);
    expect(localStorage.getItem('refreshToken')).toBeNull();

    await signIn(user);

    expect(await screen.findByText(/Próximamente vas a poder gestionar esta sección/)).toBeInTheDocument();
  });
});
