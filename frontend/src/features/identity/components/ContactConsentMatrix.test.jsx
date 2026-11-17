import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ContactConsentMatrix } from './ContactConsentMatrix';

const API = 'http://localhost:8080';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const person = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Paz',
  email: 'ana@example.com',
  dni: '30111222',
  documentType: 'DNI',
  legajo: 'L-1',
  sexo: 'F',
  street: 'Calle',
  number: '1',
  floor: '',
  city: 'CABA',
  state: 'CABA',
  country: 'Argentina',
  postalCode: '1000',
  hasAvatar: false,
  roles: ['ALUMNO'],
};

const entries = (granted = {}) =>
  ['EMAIL', 'WHATSAPP'].flatMap((channel) =>
    ['INSTITUTIONAL', 'PROMOTIONAL'].map((category) => ({
      channel,
      category,
      granted: granted[`${channel}:${category}`] ?? false,
      recordedAt: '2026-11-01T10:00:00Z',
    })),
  );

function mockConsents({ granted, phone = '1155550000', onPut, failOn = [] } = {}) {
  server.use(
    http.get(`${API}/me/contact-consents`, () => HttpResponse.json(entries(granted))),
    http.get(`${API}/me`, () => HttpResponse.json({ ...person, telefono: phone })),
    http.put(`${API}/me/contact-consents`, async ({ request }) => {
      const body = await request.json();
      onPut?.(body);
      if (failOn.includes(`${body.channel}:${body.category}`)) return new HttpResponse(null, { status: 500 });
      return HttpResponse.json({ ...body, recordedAt: '2026-11-17T10:00:00Z' });
    }),
  );
}

describe('ContactConsentMatrix', () => {
  it('saves only changed cells', async () => {
    const puts = [];
    mockConsents({ granted: { 'EMAIL:INSTITUTIONAL': true }, onPut: (body) => puts.push(body) });
    renderWithProviders(<ContactConsentMatrix />);

    await userEvent.click(await screen.findByRole('switch', { name: /Correo — Institucional/ }));
    await userEvent.click(screen.getByRole('switch', { name: /WhatsApp — Institucional/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(puts).toHaveLength(2));
    expect(puts).toEqual([
      { channel: 'EMAIL', category: 'INSTITUTIONAL', granted: false },
      { channel: 'WHATSAPP', category: 'INSTITUTIONAL', granted: true },
    ]);
  });

  it('reports which cells failed', async () => {
    mockConsents({ failOn: ['WHATSAPP:INSTITUTIONAL'] });
    renderWithProviders(<ContactConsentMatrix />);

    await userEvent.click(await screen.findByRole('switch', { name: /Correo — Institucional/ }));
    await userEvent.click(screen.getByRole('switch', { name: /WhatsApp — Institucional/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalled());
    expect(toastMock.error.mock.calls[0][0]).toContain('WhatsApp · Institucional');
    expect(toastMock.error.mock.calls[0][0]).not.toContain('Correo');
    expect(screen.getByRole('switch', { name: /WhatsApp — Institucional: activado/ })).toBeInTheDocument();
  });

  it('disables whatsapp without phone and keeps stored consent', async () => {
    mockConsents({ phone: '', granted: { 'WHATSAPP:INSTITUTIONAL': true } });
    renderWithProviders(<ContactConsentMatrix />);

    const whatsapp = await screen.findByRole('switch', { name: /WhatsApp — Institucional: activado/ });
    await waitFor(() => expect(whatsapp).toBeDisabled());
    expect(whatsapp).toBeChecked();
    expect(screen.getByRole('switch', { name: /Correo — Institucional/ })).toBeEnabled();
  });

  it('shows institutional note when turning it off', async () => {
    mockConsents({ granted: { 'EMAIL:INSTITUTIONAL': true } });
    renderWithProviders(<ContactConsentMatrix />);

    expect(screen.queryByText(/La cobranza y los avisos obligatorios siguen llegando/)).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole('switch', { name: /Correo — Institucional/ }));

    expect(await screen.findByText(/La cobranza y los avisos obligatorios siguen llegando/)).toBeInTheDocument();
  });

  it('promotional switch is disabled', async () => {
    mockConsents();
    renderWithProviders(<ContactConsentMatrix />);

    expect(await screen.findByRole('switch', { name: /Correo — Promocional/ })).toBeDisabled();
    expect(screen.getByRole('switch', { name: /WhatsApp — Promocional/ })).toBeDisabled();
  });
});
