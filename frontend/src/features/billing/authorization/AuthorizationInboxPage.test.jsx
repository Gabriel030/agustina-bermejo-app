import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AuthorizationInboxPage } from './AuthorizationInboxPage';
import { API, isoDateInDays, listItem, page } from './authorizationFixtures';

const auth = vi.hoisted(() => ({ user: null }));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: auth.user }) }));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

vi.setConfig({ testTimeout: 20000 });

const refund = listItem();
const invoice = listItem({
  id: 12,
  operationType: 'CANCEL_INVOICE',
  operationLabel: 'Cancelación de factura',
  family: 'INVOICES_AND_NOTES',
  familyLabel: 'Facturas y notas',
  studentName: 'Benítez, Joaquín',
  summary: 'Cuota octubre 2026',
  amount: { amount: '520000.00', currency: 'ARS' },
  requestedBy: { id: 31, name: 'Pablo Ríos', roles: ['ADMINISTRATIVO'] },
  expiresOn: null,
});

function mockList(handler) {
  server.use(http.get(`${API}/authorization-requests`, ({ request }) => handler(new URL(request.url).searchParams)));
}

function renderInbox(role, route) {
  return renderWithProviders(
    <Routes>
      <Route path="/director/aprobaciones/facturacion" element={<AuthorizationInboxPage />} />
      <Route path="/director/aprobaciones/facturacion/:id" element={<p>Detalle del pedido</p>} />
      <Route path="/administrativo/aprobaciones" element={<AuthorizationInboxPage />} />
      <Route path="/administrativo/aprobaciones/:id" element={<p>Detalle del pedido</p>} />
    </Routes>,
    { route: route ?? (role === 'director' ? '/director/aprobaciones/facturacion' : '/administrativo/aprobaciones') },
  );
}

describe('AuthorizationInboxPage as director', () => {
  beforeEach(() => {
    auth.user = { id: 1, roles: ['DIRECTOR'] };
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('lists the pending requests asking the server for pending only, by due date', async () => {
    let params;
    mockList((searchParams) => {
      params = searchParams;
      return HttpResponse.json(page([refund, invoice]));
    });
    renderInbox('director');

    expect(await screen.findByText('Ledesma, Florencia')).toBeInTheDocument();
    expect(screen.getByText('Benítez, Joaquín')).toBeInTheDocument();
    expect(params.getAll('status')).toEqual(['PENDING']);
    expect(params.get('sort')).toBe('EXPIRES_ON');
    expect(screen.getByRole('tab', { name: 'Pendientes (2)' })).toBeInTheDocument();
    expect(screen.getByText('En 5 días')).toBeInTheDocument();
    expect(screen.getByText('No vence')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText(/120\.000,00/)).toBeInTheDocument();
  });

  it('summarizes what expires in 7 days and the amount to return', async () => {
    mockList(() => HttpResponse.json(page([refund, invoice, listItem({ id: 13, expiresOn: isoDateInDays(20), studentName: 'Otro, Alumno' })])));
    renderInbox('director');

    await screen.findByText('Ledesma, Florencia');
    const expiring = screen.getByText('Vencen en 7 días').closest('div').parentElement;
    expect(within(expiring).getByText('1')).toBeInTheDocument();
    const toReturn = screen.getByText('A devolver').closest('div').parentElement;
    expect(within(toReturn).getByText(/240\.000,00/)).toBeInTheDocument();
  });

  it('filters by type family from the column header', async () => {
    mockList(() => HttpResponse.json(page([refund, invoice])));
    const user = userEvent.setup();
    renderInbox('director');

    await screen.findByText('Ledesma, Florencia');
    await user.click(screen.getByRole('button', { name: 'Filtrar por tipo' }));
    await user.click(await screen.findByRole('checkbox', { name: /Facturas y notas/ }));

    await waitFor(() => expect(screen.queryByText('Benítez, Joaquín')).not.toBeInTheDocument());
    expect(screen.getByText('Ledesma, Florencia')).toBeInTheDocument();
  });

  it('opens the detail when clicking a row', async () => {
    mockList(() => HttpResponse.json(page([refund])));
    const user = userEvent.setup();
    renderInbox('director');

    await user.click(await screen.findByText('Reembolso'));

    expect(await screen.findByText('Detalle del pedido')).toBeInTheDocument();
  });

  it('links each request to its detail', async () => {
    mockList(() => HttpResponse.json(page([refund])));
    renderInbox('director');

    const link = await screen.findByRole('link', { name: 'Ledesma, Florencia' });
    expect(link).toHaveAttribute('href', '/director/aprobaciones/facturacion/11');
  });

  it('asks the server for the history and shows the status of each decision', async () => {
    const approved = listItem({ id: 20, status: 'APPROVED', resumeOutcome: 'PROCESSED', decidedAt: '2026-10-23T10:00:00Z' });
    const notApplied = listItem({ id: 21, status: 'APPROVED', resumeOutcome: 'REJECTED_ON_RESUME', studentName: 'Suárez, Valentina', decidedAt: '2026-10-23T11:00:00Z' });
    const calls = [];
    mockList((params) => {
      calls.push(params);
      return HttpResponse.json(params.getAll('status').includes('PENDING') ? page([refund]) : page([approved, notApplied]));
    });
    const user = userEvent.setup();
    renderInbox('director');

    await screen.findByText('Ledesma, Florencia');
    await user.click(screen.getByRole('tab', { name: 'Historial' }));

    expect(await screen.findByText('Suárez, Valentina')).toBeInTheDocument();
    expect(screen.getByText('Aprobada')).toBeInTheDocument();
    expect(screen.getByText('No se pudo ejecutar')).toBeInTheDocument();
    const historyCall = calls.find((params) => params.getAll('status').includes('APPROVED'));
    expect(historyCall.getAll('status').sort()).toEqual(['APPROVED', 'EXPIRED', 'REJECTED', 'WITHDRAWN']);
    expect(historyCall.get('descending')).toBe('true');
  });

  it('searches the history by student', async () => {
    const other = listItem({ id: 22, status: 'REJECTED', studentName: 'Suárez, Valentina', decidedAt: '2026-10-23T11:00:00Z' });
    const done = listItem({ id: 23, status: 'APPROVED', resumeOutcome: 'PROCESSED', decidedAt: '2026-10-23T10:00:00Z' });
    mockList((params) => HttpResponse.json(params.getAll('status').includes('PENDING') ? page([]) : page([other, done])));
    const user = userEvent.setup();
    renderInbox('director');

    await user.click(await screen.findByRole('tab', { name: 'Historial' }));
    await screen.findByText('Suárez, Valentina');
    await user.type(screen.getByPlaceholderText('Buscar por alumno'), 'suarez');

    await waitFor(() => expect(screen.queryByText('Ledesma, Florencia')).not.toBeInTheDocument());
    expect(screen.getByText('Suárez, Valentina')).toBeInTheDocument();
  });

  it('shows a skeleton while loading', async () => {
    mockList(async () => {
      await delay('infinite');
      return HttpResponse.json(page([]));
    });
    renderInbox('director');

    expect(await screen.findByLabelText('Cargando')).toBeInTheDocument();
  });

  it('shows the empty state when nothing waits for the director', async () => {
    mockList(() => HttpResponse.json(page([])));
    renderInbox('director');

    expect(await screen.findByText('Nada espera tu decisión')).toBeInTheDocument();
  });

  it('shows an error with retry when the list fails', async () => {
    let fail = true;
    mockList(() => (fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(page([refund]))));
    const user = userEvent.setup();
    renderInbox('director');

    expect(await screen.findByText('No pudimos cargar los pedidos.')).toBeInTheDocument();
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Ledesma, Florencia')).toBeInTheDocument();
  });
});

describe('AuthorizationInboxPage as administrative', () => {
  beforeEach(() => {
    auth.user = { id: 30, roles: ['ADMINISTRATIVO'] };
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('shows "Mis pedidos" without the requester column and without a "Por decidir" tab', async () => {
    mockList(() => HttpResponse.json(page([refund])));
    renderInbox('administrativo');

    expect(await screen.findByText('Ledesma, Florencia')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Mis pedidos (1)' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Por decidir/ })).not.toBeInTheDocument();
    expect(screen.queryByText('Pedido por')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ledesma, Florencia' })).toHaveAttribute('href', '/administrativo/aprobaciones/11');
  });

  it('withdraws an own pending request after confirming', async () => {
    mockList(() => HttpResponse.json(page([refund])));
    let withdrawn = false;
    server.use(
      http.post(`${API}/authorization-requests/11/withdraw`, () => {
        withdrawn = true;
        return HttpResponse.json({ id: 11, operationType: 'ISSUE_REFUND', status: 'WITHDRAWN', resumeOutcome: null });
      }),
    );
    const user = userEvent.setup();
    renderInbox('administrativo');

    await user.click(await screen.findByRole('button', { name: 'Retirar' }));
    const dialog = await screen.findByRole('dialog');
    expect(withdrawn).toBe(false);
    await user.click(within(dialog).getByRole('button', { name: 'Retirar pedido' }));

    await waitFor(() => expect(withdrawn).toBe(true));
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Pedido retirado.'));
  });

  it('explains a 403 when withdrawing', async () => {
    mockList(() => HttpResponse.json(page([refund])));
    server.use(http.post(`${API}/authorization-requests/11/withdraw`, () => new HttpResponse(null, { status: 403 })));
    const user = userEvent.setup();
    renderInbox('administrativo');

    await user.click(await screen.findByRole('button', { name: 'Retirar' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Retirar pedido' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('Solo quien hizo el pedido puede retirarlo.'));
  });

  it('shows the empty state of an administrative without pending requests', async () => {
    mockList(() => HttpResponse.json(page([])));
    renderInbox('administrativo');

    expect(await screen.findByText('No tenés pedidos pendientes')).toBeInTheDocument();
  });
});
