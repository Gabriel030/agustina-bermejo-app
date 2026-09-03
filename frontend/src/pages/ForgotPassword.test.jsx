import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { ForgotPassword } from '@/pages/ForgotPassword';

const API = 'http://localhost:8080';

function renderForgotPassword() {
  return render(
    <MemoryRouter initialEntries={['/olvide-mi-contrasena']}>
      <ForgotPassword />
    </MemoryRouter>,
  );
}

async function pedirCodigo(user, email = 'persona@example.com') {
  await user.type(screen.getByPlaceholderText('tu@email.com'), email);
  await user.click(screen.getByRole('button', { name: /enviar código de recuperación/i }));
}

describe('ForgotPassword', () => {
  it('al pedir el código, la misma pantalla pasa a mostrar el mensaje de éxito y el campo de código, sin navegar', async () => {
    const user = userEvent.setup();
    renderForgotPassword();

    await pedirCodigo(user);

    expect(await screen.findByText(/te mandamos un código a persona@example.com/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText('123456')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('tu@email.com')).not.toBeInTheDocument();
  });

  it('rechaza en el cliente un código que no tiene 6 dígitos, sin llamar al backend', async () => {
    const user = userEvent.setup();
    renderForgotPassword();

    await pedirCodigo(user);
    await user.type(await screen.findByPlaceholderText('123456'), '12');
    await user.click(screen.getByRole('button', { name: /continuar/i }));

    expect(await screen.findByText(/el código tiene que tener 6 dígitos/i)).toBeInTheDocument();
  });

  it('muestra "código incorrecto o vencido" cuando el backend rechaza la verificación', async () => {
    server.use(http.post(`${API}/auth/verify-reset-code`, () => new HttpResponse(null, { status: 401 })));
    const user = userEvent.setup();
    renderForgotPassword();

    await pedirCodigo(user);
    await user.type(await screen.findByPlaceholderText('123456'), '000000');
    await user.click(screen.getByRole('button', { name: /continuar/i }));

    expect(await screen.findByText(/código incorrecto o vencido/i)).toBeInTheDocument();
  });

  it('con un código válido, pasa a la pantalla de elegir contraseña nueva', async () => {
    server.use(
      http.post(`${API}/auth/verify-reset-code`, () => HttpResponse.json({ resetToken: 'exchange-token-de-prueba' })),
    );
    const user = userEvent.setup();
    renderForgotPassword();

    await pedirCodigo(user);
    await user.type(await screen.findByPlaceholderText('123456'), '123456');
    await user.click(screen.getByRole('button', { name: /continuar/i }));

    expect(await screen.findByPlaceholderText('Nueva contraseña')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('123456')).not.toBeInTheDocument();
  });

  it('rechaza en el cliente una contraseña sin mayúscula', async () => {
    server.use(
      http.post(`${API}/auth/verify-reset-code`, () => HttpResponse.json({ resetToken: 'exchange-token-de-prueba' })),
    );
    const user = userEvent.setup();
    renderForgotPassword();

    await pedirCodigo(user);
    await user.type(await screen.findByPlaceholderText('123456'), '123456');
    await user.click(screen.getByRole('button', { name: /continuar/i }));
    await user.type(await screen.findByPlaceholderText('Nueva contraseña'), 'test1234');
    await user.type(screen.getByPlaceholderText('Repetir contraseña'), 'test1234');
    await user.click(screen.getByRole('button', { name: /cambiar contraseña/i }));

    expect(await screen.findByText(/al menos una mayúscula y un número/i)).toBeInTheDocument();
  });

  it('en la pantalla de contraseña nueva, un reset exitoso muestra el mensaje de éxito', async () => {
    server.use(
      http.post(`${API}/auth/verify-reset-code`, () => HttpResponse.json({ resetToken: 'exchange-token-de-prueba' })),
      http.post(`${API}/auth/reset-password`, () => new HttpResponse(null, { status: 204 })),
    );
    const user = userEvent.setup();
    renderForgotPassword();

    await pedirCodigo(user);
    await user.type(await screen.findByPlaceholderText('123456'), '123456');
    await user.click(screen.getByRole('button', { name: /continuar/i }));
    await user.type(await screen.findByPlaceholderText('Nueva contraseña'), 'Test1234');
    await user.type(screen.getByPlaceholderText('Repetir contraseña'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /cambiar contraseña/i }));

    expect(await screen.findByText(/contraseña actualizada/i)).toBeInTheDocument();
  });

  it('el botón de reenviar código está deshabilitado hasta que termina la cuenta regresiva', async () => {
    const user = userEvent.setup();
    renderForgotPassword();

    await pedirCodigo(user);

    expect(await screen.findByRole('button', { name: /reenviar código \(60s\)/i })).toBeDisabled();
  });
});
