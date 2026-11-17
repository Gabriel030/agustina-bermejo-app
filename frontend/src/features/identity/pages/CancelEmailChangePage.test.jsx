import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { CancelEmailChangePage } from '@/features/identity/pages/CancelEmailChangePage';

const API = 'http://localhost:8080';

function renderPage(route = '/cancelar-cambio-email?token=abc') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <CancelEmailChangePage />
    </MemoryRouter>,
  );
}

describe('CancelEmailChangePage', () => {
  it('does not call the endpoint until the button is pressed', async () => {
    let calls = 0;
    server.use(
      http.post(`${API}/cancel-email-change`, () => {
        calls += 1;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderPage();

    expect(screen.getByRole('button', { name: 'Cancelar el cambio' })).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(calls).toBe(0);

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));
    expect(await screen.findByText('Cambio cancelado')).toBeInTheDocument();
    expect(calls).toBe(1);
  });

  it('shows success after confirming cancellation', async () => {
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));

    expect(
      await screen.findByText('Cancelamos el cambio de email; tu cuenta sigue con el mismo correo.'),
    ).toBeInTheDocument();
  });

  it('shows invalid link message for an unknown token', async () => {
    server.use(
      http.post(`${API}/cancel-email-change`, () =>
        HttpResponse.json(errorBody('INVALID_EMAIL_VERIFICATION_TOKEN'), { status: 400 }),
      ),
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));

    expect(await screen.findByText('El enlace venció o no es válido')).toBeInTheDocument();
  });

  it('shows invalid link message when the token is missing', () => {
    renderPage('/cancelar-cambio-email');

    expect(screen.getByText('El enlace venció o no es válido')).toBeInTheDocument();
  });

  it('offers retry after a network error', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('Failed to fetch'));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));
    expect(await screen.findByText('No pudimos conectarnos')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Cambio cancelado')).toBeInTheDocument();
  });
});
