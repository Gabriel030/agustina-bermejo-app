import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { NotificationMatrix } from './NotificationMatrix';

const API = 'http://localhost:8080';

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
  it('shows the collection block with the mandatory channel locked and the email switch free', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: { PAYMENT_DUE_SOON: ['APP'] } });
    renderWithProviders(<NotificationMatrix />);

    expect(await screen.findByText('Recordatorio antes del vencimiento')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Recordatorio antes del vencimiento — En la app: .*obligatorio/ })).toBeInTheDocument();
    expect(screen.queryByRole('switch', { name: /Recordatorio antes del vencimiento — En la app/ })).not.toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Recordatorio antes del vencimiento — Por email: activado' })).toBeEnabled();
  });

  it('locks whatever the server reports as mandatory instead of a fixed list', async () => {
    mockPreferences({ preferences: {}, mandatoryChannels: { GRADE_POSTED: ['EMAIL'], PAYMENT_DUE_SOON: [] } });
    renderWithProviders(<NotificationMatrix />);

    await screen.findByText('Nota publicada');
    expect(screen.getByRole('button', { name: /Nota publicada — Por email/ })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Recordatorio antes del vencimiento — En la app/ })).toBeInTheDocument();
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

    expect(screen.getAllByText('Siempre activo, no se puede apagar')).toHaveLength(4);
  });
});
