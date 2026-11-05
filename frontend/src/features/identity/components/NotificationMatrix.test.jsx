import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { NotificationMatrix } from './NotificationMatrix';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ roles: ['ALUMNO'] }));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ roles: auth.roles }) }));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

function mockPreferences(body, onSave) {
  server.use(
    http.get(`${API}/me/notification-preferences`, () => HttpResponse.json(body)),
    http.put(`${API}/me/notification-preferences`, async ({ request }) => {
      onSave?.(await request.json());
      return HttpResponse.json(body);
    }),
  );
}

describe('NotificationMatrix', () => {
  beforeEach(() => {
    auth.roles = ['ALUMNO'];
  });

  it('shows the collection block with the mandatory channel locked and the email switch free', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: { PAYMENT_DUE_SOON: ['APP'] } });
    renderWithProviders(<NotificationMatrix />);

    expect(await screen.findByText('Recordatorio antes del vencimiento')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Recordatorio antes del vencimiento — En la app: .*obligatorio/ })).toBeInTheDocument();
    expect(screen.queryByRole('switch', { name: /Recordatorio antes del vencimiento — En la app/ })).not.toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Recordatorio antes del vencimiento — Por email: activado' })).toBeEnabled();
  });

  it('locks whatever the server reports as mandatory instead of a fixed list', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: { INSTITUTIONAL_NOTICE: ['EMAIL'], PAYMENT_DUE_SOON: [] } });
    renderWithProviders(<NotificationMatrix />);

    await screen.findByText('Aviso institucional');
    expect(screen.getByRole('button', { name: /Aviso institucional — Por email/ })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Recordatorio antes del vencimiento — En la app/ })).toBeInTheDocument();
  });

  it('does not list notifications that no process emits', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: { ACCOUNT_CREATED: ['APP', 'EMAIL'] } });
    renderWithProviders(<NotificationMatrix />);

    await screen.findByText('Aviso institucional');
    for (const label of ['Cuenta creada', 'Nota publicada', 'Factura generada']) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
    expect(screen.getByText('Mesa de examen registrada')).toBeInTheDocument();
  });

  it('shows the holiday reminder only to administrative staff', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: {} });
    const { unmount } = renderWithProviders(<NotificationMatrix />);
    await screen.findByText('Aviso institucional');
    expect(screen.queryByText('Aviso de feriados por cargar')).not.toBeInTheDocument();
    unmount();

    auth.roles = ['ADMINISTRATIVO'];
    renderWithProviders(<NotificationMatrix />);
    expect(await screen.findByText('Aviso de feriados por cargar')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Aviso de feriados por cargar — En la app: activado' })).toBeEnabled();
  });

  it('saves only after confirming and sends the email switch turned off', async () => {
    const saved = [];
    mockPreferences({ preferences: {}, mandatoryChannels: { PAYMENT_DUE_SOON: ['APP'] } }, (body) => saved.push(body));
    const user = userEvent.setup();
    renderWithProviders(<NotificationMatrix />);

    await user.click(await screen.findByRole('switch', { name: 'Recordatorio antes del vencimiento — Por email: activado' }));

    expect(saved).toHaveLength(0);
    expect(screen.getByRole('status')).toHaveTextContent('no te escribimos antes del vencimiento');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await vi.waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0].preferences.PAYMENT_DUE_SOON.EMAIL).toBe(false);
    expect(toastMock.success).toHaveBeenCalled();
  });

  it('drops the pending change on cancel', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: { PAYMENT_DUE_SOON: ['APP'] } });
    const user = userEvent.setup();
    renderWithProviders(<NotificationMatrix />);

    await user.click(await screen.findByRole('switch', { name: 'Recordatorio antes del vencimiento — Por email: activado' }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.getByRole('switch', { name: 'Recordatorio antes del vencimiento — Por email: activado' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument();
  });

  it('explains the lock when tapping the lock of a mandatory notice', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: { PAYMENT_DUE_SOON: ['APP'] } });
    const user = userEvent.setup();
    renderWithProviders(<NotificationMatrix />);

    await user.click(await screen.findByRole('button', { name: /Recordatorio antes del vencimiento — En la app/ }));

    expect(await screen.findByText('Este aviso es obligatorio: no se puede desactivar.')).toBeInTheDocument();
  });

  it('writes the always-on state under the name of fully mandatory notices only', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: { PAYMENT_DUE_SOON: ['APP'] } });
    renderWithProviders(<NotificationMatrix />);

    await screen.findByText('Recordatorio antes del vencimiento');

    expect(screen.getAllByText('Siempre te lo enviamos')).toHaveLength(4);
  });
});

describe('NotificationMatrix load failure', () => {
  it('shows a load error with retry and no switches when the preferences fail to load', async () => {
    let puts = 0;
    let fail = true;
    server.use(
      http.get(`${API}/me/notification-preferences`, () =>
        fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json({ preferences: {}, mandatoryChannels: {} }),
      ),
      http.put(`${API}/me/notification-preferences`, () => {
        puts += 1;
        return HttpResponse.json({});
      }),
    );
    renderWithProviders(<NotificationMatrix />);

    await screen.findByText('No pudimos cargar tus preferencias de notificaciones');
    expect(screen.queryAllByRole('switch')).toHaveLength(0);
    expect(screen.queryAllByRole('checkbox')).toHaveLength(0);
    expect(puts).toBe(0);

    fail = false;
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }));
    await screen.findByText('Cobranza');
  });
});
