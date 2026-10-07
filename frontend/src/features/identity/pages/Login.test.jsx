import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
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

describe('Login', () => {
  it('muestra "Usuario o contraseña incorrectos" cuando el backend rechaza el login', async () => {
    server.use(http.post(`${API}/auth/login`, () => new HttpResponse(null, { status: 401 })));
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
    await user.type(screen.getByPlaceholderText('••••••••'), 'mal');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    expect(await screen.findByText('Usuario o contraseña incorrectos.')).toBeInTheDocument();
  });

  it('muestra el mensaje de rate limit, distinto del de credenciales inválidas, en un 429', async () => {
    server.use(http.post(`${API}/auth/login`, () => new HttpResponse(null, { status: 429 })));
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    expect(await screen.findByText(/demasiados intentos/i)).toBeInTheDocument();
  });

  it('con credenciales válidas, deshabilita el botón mientras envía', async () => {
    // Delay artificial: sin esto el mock resuelve tan rápido que nunca se
    // alcanza a observar el estado intermedio "enviando".
    server.use(
      http.post(`${API}/auth/login`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 50));
        return HttpResponse.json({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 });
      }),
    );
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    await waitFor(() => expect(screen.getByRole('button', { name: /ingresando/i })).toBeDisabled());
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
