import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { server } from '@/test/mocks/server';
import { BajaComunicaciones } from '@/features/identity/pages/BajaComunicaciones';

const API = 'http://localhost:8080';

function renderPage(route) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <BajaComunicaciones />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('BajaComunicaciones', () => {
  it('get does not call post', async () => {
    let posts = 0;
    server.use(
      http.post(`${API}/public/unsubscribe/:token`, () => {
        posts += 1;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderPage('/baja?token=abc');

    expect(await screen.findByRole('button', { name: 'Confirmar baja' })).toBeInTheDocument();
    expect(posts).toBe(0);
  });

  it('confirms and shows done', async () => {
    let posted = null;
    server.use(
      http.post(`${API}/public/unsubscribe/:token`, ({ params }) => {
        posted = params.token;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderPage('/baja?token=abc');

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar baja' }));

    expect(await screen.findByText('Listo, te diste de baja')).toBeInTheDocument();
    expect(posted).toBe('abc');
  });

  it('institutional shows warning before confirming', async () => {
    server.use(
      http.get(`${API}/public/unsubscribe/:token`, () =>
        HttpResponse.json({ channel: 'EMAIL', category: 'INSTITUTIONAL' }),
      ),
    );
    renderPage('/baja?token=abc');

    expect(await screen.findByRole('alert')).toHaveTextContent('La cobranza y los avisos obligatorios siguen llegando');
    expect(screen.getByRole('button', { name: 'Confirmar baja' })).toBeInTheDocument();
  });

  it('invalid token shows generic page', async () => {
    server.use(http.get(`${API}/public/unsubscribe/:token`, () => new HttpResponse(null, { status: 404 })));
    renderPage('/baja?token=bad');

    expect(await screen.findByText('Este enlace no es válido')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar baja' })).not.toBeInTheDocument();
  });
});
