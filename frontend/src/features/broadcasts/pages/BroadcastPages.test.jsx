import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { BroadcastListPage } from './BroadcastListPage';
import { BroadcastDetailPage } from './BroadcastDetailPage';

const API = 'http://localhost:8080';

const SENDING_BROADCAST = {
  id: 7,
  name: 'Aviso de inicio de clases',
  category: 'INSTITUTIONAL',
  channel: 'EMAIL',
  status: 'SENDING',
  subject: 'Inicio de clases',
  bodyText: 'Las clases comienzan el lunes.',
  createdAt: '2026-11-17T10:00:00Z',
  confirmedAt: '2026-11-17T10:05:00Z',
  recipientCounts: { SENT: 120, QUEUED: 370, FAILED: 10, EXCLUDED: 3 },
};

function renderList() {
  return renderWithProviders(
    <Routes>
      <Route path="/director/comunicaciones" element={<BroadcastListPage />} />
    </Routes>,
    { route: '/director/comunicaciones' },
  );
}

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/director/comunicaciones/:broadcastId" element={<BroadcastDetailPage />} />
    </Routes>,
    { route: '/director/comunicaciones/7' },
  );
}

function mockDetail(overrides = {}) {
  server.use(
    http.get(`${API}/broadcasts/7`, () => HttpResponse.json({ ...SENDING_BROADCAST, ...overrides })),
    http.get(`${API}/broadcasts/7/recipients`, () =>
      HttpResponse.json({
        items: [
          { personId: 10, fullName: 'Ana Pérez', status: 'SENT', addressMasked: 'a***@example.com' },
          { personId: 11, fullName: 'Luis Gómez', status: 'EXCLUDED', exclusionReason: 'OPTED_OUT' },
        ],
        page: 0,
        size: 50,
        totalElements: 2,
      }),
    ),
  );
}

describe('BroadcastListPage', () => {
  it('list shows status and confirmation date', async () => {
    renderList();
    expect(await screen.findByText('Aviso de inicio de clases')).toBeInTheDocument();
    const table = within(screen.getByRole('table'));
    expect(table.getByText('Confirmado')).toBeInTheDocument();
    expect(table.getByText('Correo')).toBeInTheDocument();
    expect(table.getByText('Institucional')).toBeInTheDocument();
  });

  it('status chip filters the list through the backend', async () => {
    const requested = [];
    server.use(
      http.get(`${API}/broadcasts`, ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('status'));
        return HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0 });
      }),
    );
    renderList();
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelado' }));
    await waitFor(() => expect(requested).toContain('CANCELLED'));
    expect(await screen.findByText('Todavía no hay comunicaciones')).toBeInTheDocument();
  });
});

describe('BroadcastDetailPage', () => {
  it('detail shows masked addresses and exclusion reasons', async () => {
    mockDetail();
    renderDetail();
    expect(await screen.findByText('a***@example.com')).toBeInTheDocument();
    expect(screen.getByText('Se dio de baja')).toBeInTheDocument();
    expect(screen.getByText('120 de 500 enviados')).toBeInTheDocument();
    expect(screen.getByText('Fallidos')).toBeInTheDocument();
  });

  it('cancel requires a reason of at least 10 characters', async () => {
    mockDetail();
    let cancelBody = null;
    server.use(
      http.post(`${API}/broadcasts/7/cancel`, async ({ request }) => {
        cancelBody = await request.json();
        return HttpResponse.json({ id: 7, status: 'CANCELLED' });
      }),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar envío' }));
    const confirm = screen.getAllByRole('button', { name: 'Cancelar envío' }).at(-1);
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByPlaceholderText('Motivo de la cancelación'), 'corto');
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByPlaceholderText('Motivo de la cancelación'), ' y suficiente');
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);
    await waitFor(() => expect(cancelBody).toEqual({ reason: 'corto y suficiente' }));
  });

  it('completed broadcasts cannot be cancelled', async () => {
    mockDetail({ status: 'COMPLETED' });
    renderDetail();
    await screen.findByText('Ana Pérez');
    expect(screen.queryByRole('button', { name: 'Cancelar envío' })).not.toBeInTheDocument();
  });
});
