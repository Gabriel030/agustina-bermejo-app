import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { Registrarse } from '@/features/identity/pages/Registrarse';

const API = 'http://localhost:8080';

function renderRegistrarse() {
  return render(
    <MemoryRouter initialEntries={['/registrarse']}>
      <AuthProvider>
        <Registrarse />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function completarDatosBasicos(user) {
  await user.type(screen.getByPlaceholderText('Nombre'), 'Sofía');
  await user.type(screen.getByPlaceholderText('Apellido'), 'Ramírez');
  await user.type(screen.getByPlaceholderText('tu@email.com'), 'sofia@example.com');
}

describe('Registrarse', () => {
  it('rechaza el envío si las contraseñas no coinciden, sin llamar al backend', async () => {
    server.use(
      http.post(`${API}/auth/register`, () => {
        throw new Error('no debería llamarse al backend con contraseñas que no coinciden');
      }),
    );
    const user = userEvent.setup();
    renderRegistrarse();

    await completarDatosBasicos(user);
    await user.type(screen.getByPlaceholderText('Contraseña'), 'Test1234');
    await user.type(screen.getByPlaceholderText('Repetir contraseña'), 'Distinta123');
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText('Las contraseñas no coinciden.')).toBeInTheDocument();
  });

  it('muestra "Ese email ya está registrado." cuando el backend devuelve 409', async () => {
    server.use(http.post(`${API}/auth/register`, () => new HttpResponse(null, { status: 409 })));
    const user = userEvent.setup();
    renderRegistrarse();

    await completarDatosBasicos(user);
    await user.type(screen.getByPlaceholderText('Contraseña'), 'Test1234');
    await user.type(screen.getByPlaceholderText('Repetir contraseña'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText('Ese email ya está registrado.')).toBeInTheDocument();
  });
});
