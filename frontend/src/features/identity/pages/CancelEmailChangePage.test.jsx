import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { CancelEmailChangePage } from '@/features/identity/pages/CancelEmailChangePage';

const API = 'http://localhost:8080';

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{`${location.pathname}${location.search}`}</span>;
}

function renderPage(route = '/cancelar-cambio-email?token=abc') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <CancelEmailChangePage />
      <LocationProbe />
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

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

    expect(await screen.findByRole('button', { name: 'Cancelar el cambio' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/cancelar-cambio-email$/));
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

  it('sends the token in the request body', async () => {
    let received;
    server.use(
      http.post(`${API}/cancel-email-change`, async ({ request }) => {
        received = { url: request.url, body: await request.json() };
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));
    await screen.findByText('Cambio cancelado');

    expect(received.body).toEqual({ token: 'abc' });
    expect(received.url).not.toContain('token=');
  });

  it('sends a single request on double click', async () => {
    let calls = 0;
    server.use(
      http.post(`${API}/cancel-email-change`, async () => {
        calls += 1;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderPage();

    await userEvent.dblClick(screen.getByRole('button', { name: 'Cancelar el cambio' }));
    await screen.findByText('Cambio cancelado');

    expect(calls).toBe(1);
  });

  it('keeps the token out of the URL after a successful cancellation', async () => {
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));
    await screen.findByText('Cambio cancelado');

    expect(screen.getByTestId('location')).not.toHaveTextContent('token');
  });

  it('announces the result in a live region and moves focus to it', async () => {
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));

    const region = await screen.findByRole('status');
    expect(region).toHaveTextContent('Cambio cancelado');
    await waitFor(() => expect(region).toHaveFocus());
  });

  it('announces an invalid link as an alert', async () => {
    renderPage('/cancelar-cambio-email');

    expect(screen.getByRole('alert')).toHaveTextContent('El enlace venció o no es válido');
  });
});
