import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { SimulatedMailsPage } from './SimulatedMailsPage';

const API = 'http://localhost:8080';

const mail = {
  id: 7,
  kind: 'OWN_NOTICE:BEFORE_DUE_REMINDER',
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

    expect(await screen.findByText('Recordatorio antes del vencimiento')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tu cuota de octubre vence pronto' }));

    expect(await screen.findByText(/subí tu comprobante desde la plataforma/)).toBeInTheDocument();
  });

  it('labels a daily digest and a post withdrawal summary in Spanish', async () => {
    const mails = [
      { ...mail, id: 8, kind: 'DAILY_DIGEST:AFTER_DUE_REMINDER', subject: 'Resumen de tus cuotas' },
      { ...mail, id: 9, kind: 'POST_WITHDRAWAL_SUMMARY', subject: 'Resumen de tu baja' },
    ];
    server.use(http.get(`${API}/notification-deliveries/simulated`, () => HttpResponse.json({ items: mails, page: 0, size: 20, totalElements: 2 })));
    renderWithProviders(<SimulatedMailsPage />);

    expect(await screen.findByText('Resumen diario')).toBeInTheDocument();
    expect(screen.getByText('Resumen tras una baja')).toBeInTheDocument();
  });

  it('shows the page as non-existent when the server answers 404', async () => {
    server.use(http.get(`${API}/notification-deliveries/simulated`, () => new HttpResponse(null, { status: 404 })));
    renderWithProviders(<SimulatedMailsPage />);

    expect(await screen.findByText('Esta pantalla no existe')).toBeInTheDocument();
  });

  it('shows each mail as a card with its status on mobile instead of a scrolling table', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    server.use(http.get(`${API}/notification-deliveries/simulated`, () => HttpResponse.json({ items: [mail], page: 0, size: 20, totalElements: 1 })));
    renderWithProviders(<SimulatedMailsPage />);

    expect(await screen.findByRole('button', { name: 'Tu cuota de octubre vence pronto' })).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText('Simulado')).toBeInTheDocument();
  });

  it('opens the status reason with a tap on the status', async () => {
    server.use(http.get(`${API}/notification-deliveries/simulated`, () => HttpResponse.json({ items: [mail], page: 0, size: 20, totalElements: 1 })));
    const user = userEvent.setup();
    renderWithProviders(<SimulatedMailsPage />);

    await user.click(await screen.findByRole('button', { name: 'Simulado: ver el motivo' }));

    expect(await screen.findByText('Entorno de demostración')).toBeInTheDocument();
  });
});
