import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ListasDeEspera } from '@/features/enrollment/pages/ListasDeEspera';

vi.mock('@/hooks/useActiveRole', async () => await import('@/test/activeRoleFromUrl'));

const API = 'http://localhost:8080';

const queues = [
  { kind: 'PROGRAM_SECTION', id: 1, name: 'Programación II, Comisión A', termName: '1.er cuatrimestre 2027', capacity: 30, occupied: 30, free: 0, waiting: 7, nextExpiry: null },
  { kind: 'COURSE', id: 3, name: 'Cosmetología Facial', termName: 'Cohorte marzo 2027', capacity: 25, occupied: 24, free: 1, waiting: 3, nextExpiry: '2027-03-20' },
];

function renderList() {
  return renderWithProviders(
    <Routes>
      <Route path="/preceptor/inscripciones/listas-de-espera" element={<ListasDeEspera />} />
      <Route path="/preceptor/inscripciones/listas-de-espera/:kind/:id" element={<div>Detalle de la lista</div>} />
    </Routes>,
    { route: '/preceptor/inscripciones/listas-de-espera' },
  );
}

describe('ListasDeEspera', () => {
  it('shows every queue with its seats and waiting count', async () => {
    server.use(http.get(`${API}/waitlists`, () => HttpResponse.json({ items: queues, page: 0, size: 20, totalElements: 2 })));

    renderList();

    expect(await screen.findByText('Programación II, Comisión A')).toBeInTheDocument();
    expect(screen.getByText('Cosmetología Facial')).toBeInTheDocument();
    expect(screen.getByText('1 lugar libre')).toBeInTheDocument();
    expect(screen.getByText('30 / 30')).toBeInTheDocument();
  });

  it('opens the queue detail when a row is clicked', async () => {
    server.use(http.get(`${API}/waitlists`, () => HttpResponse.json({ items: queues, page: 0, size: 20, totalElements: 2 })));
    renderList();

    await userEvent.click(await screen.findByText('Cosmetología Facial'));

    expect(await screen.findByText('Detalle de la lista')).toBeInTheDocument();
  });

  it('asks only for queues expiring soon when the chip is pressed', async () => {
    const requested = [];
    server.use(
      http.get(`${API}/waitlists`, ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('expiringWithinDays'));
        return HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0 });
      }),
    );
    renderList();
    await screen.findByText('Nadie está esperando');

    await userEvent.click(screen.getByRole('button', { name: /Vence en 7 días/ }));

    await waitFor(() => expect(requested).toContain('7'));
    expect(await screen.findByText('Ninguna lista coincide')).toBeInTheDocument();
  });

  it('shows the empty state when nobody is waiting', async () => {
    renderList();

    expect(await screen.findByText('Nadie está esperando')).toBeInTheDocument();
  });

  it('offers a retry when the lists fail to load', async () => {
    server.use(http.get(`${API}/waitlists`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));

    renderList();

    expect(await screen.findByText('No pudimos cargar las listas de espera')).toBeInTheDocument();
  });
});
