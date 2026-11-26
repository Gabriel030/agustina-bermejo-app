import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { MiPerfil } from '@/features/identity/pages/MiPerfil';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ roles: ['ALUMNO'], logout: vi.fn() }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ roles: auth.roles, logout: auth.logout }),
}));

const TAB_NAMES = ['Mis datos', 'Seguridad', 'Notificaciones', 'Mis aceptaciones'];

function mockProfileEndpoints({ onSavePreferences } = {}) {
  server.use(
    http.get(`${API}/me/sessions`, () => HttpResponse.json([])),
    http.get(`${API}/me/notification-preferences`, () =>
      HttpResponse.json({ preferences: {}, mandatoryChannels: { PAYMENT_DUE_SOON: ['APP'] } }),
    ),
    http.put(`${API}/me/notification-preferences`, async ({ request }) => {
      onSavePreferences?.(await request.json());
      return HttpResponse.json({ preferences: {}, mandatoryChannels: { PAYMENT_DUE_SOON: ['APP'] } });
    }),
    http.get(`${API}/me/avatar`, () => new HttpResponse(null, { status: 404 })),
  );
}

function renderProfile() {
  return renderWithProviders(<MiPerfil />, { route: '/perfil' });
}

async function openTab(user, name) {
  await user.click(await screen.findByRole('tab', { name }));
}

describe('MiPerfil tabs for every role', () => {
  beforeEach(() => {
    auth.roles = ['ALUMNO'];
    auth.logout.mockReset();
  });

  it.each([['ALUMNO'], ['DOCENTE'], ['DIRECTOR']])('shows the four tabs and no link to options for %s', async (role) => {
    mockProfileEndpoints();
    auth.roles = [role];
    renderProfile();

    await screen.findByRole('tab', { name: 'Mis datos' });
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(TAB_NAMES);
    expect(screen.queryByRole('link', { name: /Opciones/ })).not.toBeInTheDocument();
  });

  it('lets the student see the active sessions from the security tab', async () => {
    mockProfileEndpoints();
    const user = userEvent.setup();
    renderProfile();

    await openTab(user, 'Seguridad');

    expect(await screen.findByRole('heading', { name: 'Cambiar contraseña' })).toBeInTheDocument();
    expect(await screen.findByText('No hay sesiones activas.')).toBeInTheDocument();
  });

  it('refuses a new password that does not match its confirmation', async () => {
    const changed = vi.fn();
    mockProfileEndpoints();
    server.use(
      http.put(`${API}/me/password`, async ({ request }) => {
        changed(await request.json());
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    renderProfile();

    await openTab(user, 'Seguridad');
    await user.type(await screen.findByLabelText('Contraseña actual'), 'Actual123');
    await user.type(screen.getByLabelText('Nueva contraseña'), 'Nueva1234');
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'Otra12345');
    await user.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));

    expect(await screen.findByText('Las contraseñas nuevas no coinciden.')).toBeInTheDocument();
    expect(changed).not.toHaveBeenCalled();
  });

  it('explains the password policy when the server rejects the new password', async () => {
    mockProfileEndpoints();
    server.use(
      http.put(`${API}/me/password`, () => HttpResponse.json(errorBody('INVALID_PASSWORD_POLICY'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderProfile();

    await openTab(user, 'Seguridad');
    await user.type(await screen.findByLabelText('Contraseña actual'), 'Actual123');
    await user.type(screen.getByLabelText('Nueva contraseña'), 'nueva1234');
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'nueva1234');
    await user.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));

    expect(
      await screen.findByText('La nueva contraseña tiene que tener al menos 8 caracteres, una mayúscula y un número.'),
    ).toBeInTheDocument();
  });

  it('saves the notification matrix changes made by the student', async () => {
    const saved = vi.fn();
    mockProfileEndpoints({ onSavePreferences: saved });
    const user = userEvent.setup();
    renderProfile();

    await openTab(user, 'Notificaciones');
    await user.click(await screen.findByRole('switch', { name: 'Recordatorio antes del vencimiento — Por email: activado' }));
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
    expect(saved.mock.calls[0][0]).toEqual({ preferences: { PAYMENT_DUE_SOON: { EMAIL: false } } });
  });
});
