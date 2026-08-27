import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { Login } from '@/pages/Login';

const API = 'http://localhost:8080';

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Login />
      </AuthProvider>
    </MemoryRouter>,
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

  it('el botón de Google está deshabilitado — el backend hoy solo soporta AuthProvider.LOCAL', () => {
    renderLogin();
    expect(screen.getByText('Continuar con Google').closest('div')).toHaveAttribute('aria-disabled', 'true');
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

    await waitFor(() => expect(screen.getByRole('button')).toBeDisabled());
  });
});
