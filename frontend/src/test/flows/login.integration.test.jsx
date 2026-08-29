import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '@/context/AuthContext';
import { AppRouter } from '@/routes/AppRouter';

/*
 * Integration test del flujo más crítico del roadmap (login) — varios
 * componentes reales montados juntos, solo MSW mockeando el backend. Ver
 * docs/context/testing.md: este es uno de los pocos flujos que se blindan a
 * este nivel, no se hace uno por pantalla.
 */
function renderApp(initialPath = '/login') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <AppRouter />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function login() {
  const user = userEvent.setup();
  await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
  await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
  await user.click(screen.getByRole('button', { name: /ingresar/i }));
}

describe('flujo de login end-to-end', () => {
  it('login válido redirige a /preceptor/inicio para un usuario con rol PRECEPTOR (mock default)', async () => {
    renderApp();
    await login();

    // "Preceptor" aparece 2 veces en el DOM a la vez (Sidebar desktop + topbar
    // del menú hamburguesa mobile, RoleShell.jsx) — la visibilidad entre ambos
    // es puramente CSS (hidden/lg:hidden), jsdom no oculta ninguno de los dos.
    expect(await screen.findAllByText('Preceptor')).toHaveLength(2);
    expect(screen.getByText('Prueba Login')).toBeInTheDocument();
  });

  it('un Preceptor logueado que entra a /director/aprobaciones es redirigido a No autorizado', async () => {
    renderApp('/director/aprobaciones');
    await login();

    // ProtectedRoute redirige antes de renderizar el layout de Director.
    expect(await screen.findByText('No autorizado')).toBeInTheDocument();
  });

  it('recargar con un refreshToken guardado restaura la sesión sin pasar por /login', async () => {
    localStorage.setItem('refreshToken', 'refresh-1');
    renderApp('/preceptor/inicio');

    expect(await screen.findByText('Prueba Login')).toBeInTheDocument();
  });
});
