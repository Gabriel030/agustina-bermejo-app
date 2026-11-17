import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { FirstLoginConsentGate } from './FirstLoginConsentGate';

const API = 'http://localhost:8080';

vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: { id: 1 } }) }));

function renderGate() {
  return renderWithProviders(
    <FirstLoginConsentGate>
      <p>Contenido de la app</p>
    </FirstLoginConsentGate>,
  );
}

describe('FirstLoginConsentGate', () => {
  it('first login prompt shows only when there is no record', async () => {
    server.use(http.get(`${API}/me/contact-consents`, () => HttpResponse.json([])));
    renderGate();

    expect(await screen.findByText(/antes de seguir, aceptá los avisos/i)).toBeInTheDocument();
    expect(screen.queryByText('Contenido de la app')).not.toBeInTheDocument();
  });

  it('does not show the prompt when a record already exists', async () => {
    renderGate();

    expect(await screen.findByText('Contenido de la app')).toBeInTheDocument();
    expect(screen.queryByText(/antes de seguir, aceptá los avisos/i)).not.toBeInTheDocument();
  });

  it('continue stays blocked until accepting and then records the consent and closes the prompt', async () => {
    const captured = {};
    server.use(
      http.get(`${API}/me/contact-consents`, () => HttpResponse.json([])),
      http.put(`${API}/me/contact-consents`, async ({ request }) => {
        captured.body = await request.json();
        return HttpResponse.json({ ...captured.body, recordedAt: '2026-11-17T10:00:00Z' });
      }),
    );
    const user = userEvent.setup();
    renderGate();

    const notices = await screen.findByRole('checkbox', { name: /avisos institucionales/i });
    expect(notices).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: /omitir/i })).not.toBeInTheDocument();

    await user.click(notices);
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Contenido de la app')).toBeInTheDocument();
    expect(captured.body).toEqual({ channel: 'EMAIL', category: 'INSTITUTIONAL', granted: true });
  });
});
