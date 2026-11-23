import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { ThemeProvider } from '@/context/ThemeContext';
import { StudentOptions } from '@/features/identity/pages/StudentOptions';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ logout: vi.fn() }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ roles: ['ALUMNO'], logout: auth.logout }),
}));

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

function renderOptions() {
  return renderWithProviders(
    <ThemeProvider>
      <StudentOptions />
    </ThemeProvider>,
    { route: '/alumno/opciones' },
  );
}

async function fillPasswordForm(user, { next = 'Nueva1234', confirm = next } = {}) {
  const [currentInput, nextInput, confirmInput] = document.querySelectorAll('input[type="password"]');
  await user.type(currentInput, 'Actual123');
  await user.type(nextInput, next);
  await user.type(confirmInput, confirm);
  await user.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
}

describe('StudentOptions', () => {
  beforeEach(() => {
    localStorage.clear();
    auth.logout.mockReset();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('offers exactly the four sections: appearance, notifications, security and session', async () => {
    mockProfileEndpoints();
    renderOptions();

    expect(await screen.findByRole('heading', { name: 'Apariencia' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Notificaciones' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Seguridad' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Sesión' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(4);
  });

  it('stores the chosen theme in this browser so it survives a reload', async () => {
    mockProfileEndpoints();
    const user = userEvent.setup();
    renderOptions();

    const themeSwitch = await screen.findByRole('switch', { name: 'Modo oscuro' });
    expect(themeSwitch).toHaveAttribute('aria-checked', 'false');
    await user.click(themeSwitch);

    expect(localStorage.getItem('tema')).toBe('dark');
    expect(screen.getByRole('switch', { name: 'Modo oscuro' })).toHaveAttribute('aria-checked', 'true');
  });

  it('offers the theme switch on mobile, where the header toggle is hidden', async () => {
    mockProfileEndpoints();
    const user = userEvent.setup();
    renderOptions();

    const themeSwitch = await screen.findByRole('switch', { name: 'Modo oscuro' });
    await user.click(themeSwitch);

    expect(localStorage.getItem('tema')).toBe('dark');
  });

  it('saves the notification matrix changes', async () => {
    const saved = vi.fn();
    mockProfileEndpoints({ onSavePreferences: saved });
    const user = userEvent.setup();
    renderOptions();

    await user.click(await screen.findByRole('switch', { name: 'Recordatorio antes del vencimiento — Por email: activado' }));
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
    expect(saved.mock.calls[0][0]).toEqual({ preferences: { PAYMENT_DUE_SOON: { EMAIL: false } } });
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
    renderOptions();

    await fillPasswordForm(user, { next: 'Nueva1234', confirm: 'Otra12345' });

    expect(await screen.findByText('Las contraseñas nuevas no coinciden.')).toBeInTheDocument();
    expect(changed).not.toHaveBeenCalled();
  });

  it('explains the password policy when the server rejects the new password', async () => {
    mockProfileEndpoints();
    server.use(
      http.put(`${API}/me/password`, () => HttpResponse.json(errorBody('INVALID_PASSWORD_POLICY'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderOptions();

    await fillPasswordForm(user, { next: 'nueva1234' });

    expect(
      await screen.findByText('La nueva contraseña tiene que tener al menos 8 caracteres, una mayúscula y un número.'),
    ).toBeInTheDocument();
  });

  it('closes the session from the session section', async () => {
    mockProfileEndpoints();
    const user = userEvent.setup();
    renderOptions();

    await user.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));

    expect(auth.logout).toHaveBeenCalledTimes(1);
  });

  it('has no accessibility violations', async () => {
    mockProfileEndpoints();
    const { container } = renderOptions();

    await screen.findByRole('heading', { name: 'Apariencia' });
    expect(await axe(container)).toHaveNoViolations();
  });
});
