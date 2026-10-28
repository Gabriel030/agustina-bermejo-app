import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { SimulatedMailsPage } from './SimulatedMailsPage';

const API = 'http://localhost:8080';

const mail = {
  id: 7,
  kind: 'COLLECTION_REMINDER',
  status: 'SENT_SIMULATED',
  statusReason: 'Entorno de demostración',
  maskedRecipient: 'fl***@gmail.com',
  subject: 'Tu cuota de octubre vence pronto',
  bodyText: 'Hola Florencia, subí tu comprobante desde la plataforma.',
  scheduledFor: '2026-10-28',
  sentAt: null,
};

describe('SimulatedMailsPage', () => {
  it('opens the plain-text body of a mail', async () => {
    server.use(http.get(`${API}/notification-deliveries/simulated`, () => HttpResponse.json({ items: [mail], page: 0, size: 20, totalElements: 1 })));
    const user = userEvent.setup();
    renderWithProviders(<SimulatedMailsPage />);

    expect(await screen.findByText('Recordatorio de pago')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tu cuota de octubre vence pronto' }));

    expect(await screen.findByText(/subí tu comprobante desde la plataforma/)).toBeInTheDocument();
  });

  it('shows the page as non-existent when the server answers 404', async () => {
    server.use(http.get(`${API}/notification-deliveries/simulated`, () => new HttpResponse(null, { status: 404 })));
    renderWithProviders(<SimulatedMailsPage />);

    expect(await screen.findByText('Esta pantalla no existe')).toBeInTheDocument();
  });
});
