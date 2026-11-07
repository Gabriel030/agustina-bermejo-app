import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { Login } from '@/features/identity/pages/Login';

const API = 'http://localhost:8080';

function renderLogin() {
  return render(
    <GoogleOAuthProvider clientId="test-client-id">
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Login />
        </AuthProvider>
      </MemoryRouter>
    </GoogleOAuthProvider>,
  );
}

async function submitLogin(user) {
  await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
  await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
  await user.click(screen.getByRole('button', { name: /ingresar/i }));
}

describe('Login', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the countdown from Retry-After, disables the button and re-enables it when it ends', async () => {
    server.use(
      http.post(`${API}/auth/login`, () =>
        HttpResponse.json(errorBody('RATE_LIMIT_EXCEEDED', { retryable: true }), { status: 429, headers: { 'Retry-After': '3' } }),
      ),
    );
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderLogin();

    await submitLogin(user);

    const button = await screen.findByRole('button', { name: 'Reintentar en 3 s' });
    expect(button).toBeDisabled();
    expect(screen.getByText(/demasiados intentos/i)).toBeInTheDocument();

    await act(async () => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByRole('button', { name: 'Reintentar en 2 s' })).toBeDisabled();

    await act(async () => {
      vi.advanceTimersByTime(1000);
    });
    await act(async () => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeEnabled();
  });

  it('uses a default cooldown when the 429 has no Retry-After', async () => {
    server.use(
      http.post(`${API}/auth/login`, () =>
        HttpResponse.json(errorBody('RATE_LIMIT_EXCEEDED', { retryable: true }), { status: 429 }),
      ),
    );
    const user = userEvent.setup();
    renderLogin();

    await submitLogin(user);

    expect(await screen.findByRole('button', { name: 'Reintentar en 10 s' })).toBeDisabled();
  });

  it('shows the service unavailable message on EXTERNAL_SERVICE_UNAVAILABLE', async () => {
    server.use(
      http.post(`${API}/auth/login`, () =>
        HttpResponse.json(errorBody('EXTERNAL_SERVICE_UNAVAILABLE', { retryable: true }), { status: 503 }),
      ),
    );
    const user = userEvent.setup();
    renderLogin();

    await submitLogin(user);

    expect(await screen.findByText(/servicio no está disponible/i)).toBeInTheDocument();
    expect(screen.queryByText('Usuario o contraseña incorrectos.')).not.toBeInTheDocument();
  });

  it('shows a generic message on an unexpected 500, not wrong credentials', async () => {
    server.use(http.post(`${API}/auth/login`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    const user = userEvent.setup();
    renderLogin();

    await submitLogin(user);

    expect(await screen.findByText('Algo salió mal. Probá de nuevo.')).toBeInTheDocument();
  });

  it('muestra "Usuario o contraseña incorrectos" cuando el backend rechaza el login', async () => {
    server.use(http.post(`${API}/auth/login`, () => HttpResponse.json(errorBody('INVALID_CREDENTIALS'), { status: 401 })));
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
    await user.type(screen.getByPlaceholderText('••••••••'), 'mal');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    expect(await screen.findByText('Usuario o contraseña incorrectos.')).toBeInTheDocument();
  });

  it('muestra el mensaje de rate limit, distinto del de credenciales inválidas, en un 429', async () => {
    server.use(http.post(`${API}/auth/login`, () => HttpResponse.json(errorBody('RATE_LIMIT_EXCEEDED', { retryable: true }), { status: 429 })));
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    expect(await screen.findByText(/demasiados intentos/i)).toBeInTheDocument();
  });

  it('con credenciales válidas, deshabilita el botón mientras envía', async () => {
    const login = createDeferred();
    server.use(
      http.post(`${API}/auth/login`, async () => {
        await login.promise;
        return HttpResponse.json({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 });
      }),
    );
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    await waitFor(() => expect(screen.getByRole('button', { name: /ingresando/i })).toBeDisabled());
    login.resolve();
  });

  it('el ojito alterna el campo de contraseña entre oculto y visible', async () => {
    const user = userEvent.setup();
    renderLogin();

    const passwordField = screen.getByPlaceholderText('••••••••');
    expect(passwordField).toHaveAttribute('type', 'password');

    await user.click(screen.getByRole('button', { name: /mostrar contraseña/i }));
    expect(passwordField).toHaveAttribute('type', 'text');

    await user.click(screen.getByRole('button', { name: /ocultar contraseña/i }));
    expect(passwordField).toHaveAttribute('type', 'password');
  });

  it('returns to the requested route keeping its query string after signing in', async () => {
    const user = userEvent.setup();
    render(
      <GoogleOAuthProvider clientId="test-client-id">
        <MemoryRouter
          initialEntries={[
            { pathname: '/login', state: { from: { pathname: '/preceptor/alumnos', search: '?tab=prospectos', hash: '' } } },
          ]}
        >
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="*" element={<CurrentLocation />} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </GoogleOAuthProvider>,
    );

    await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    expect(await screen.findByTestId('current-location')).toHaveTextContent('/preceptor/alumnos?tab=prospectos');
  });
});

function CurrentLocation() {
  const location = useLocation();
  return <div data-testid="current-location">{`${location.pathname}${location.search}`}</div>;
}
