import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AuthorizationRequestDetailPage } from './AuthorizationRequestDetailPage';
import { API, detail } from './authorizationFixtures';

const auth = vi.hoisted(() => ({ user: null }));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: auth.user }) }));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

vi.setConfig({ testTimeout: 20000 });

function mockDetail(data) {
  let current = data;
  server.use(http.get(`${API}/authorization-requests/11`, () => HttpResponse.json(current)));
  return (next) => {
    current = next;
  };
}

function renderDetail(role = 'director') {
  const base = role === 'director' ? '/director/aprobaciones/facturacion' : '/administrativo/aprobaciones';
  return renderWithProviders(
    <Routes>
      <Route path={`${base}/:id`} element={<AuthorizationRequestDetailPage />} />
      <Route path={base} element={<p>Bandeja</p>} />
    </Routes>,
    { route: `${base}/11` },
  );
}

describe('AuthorizationRequestDetailPage as director', () => {
  beforeEach(() => {
    auth.user = { id: 1, roles: ['DIRECTOR'] };
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('shows what is asked, the split by payment, the effects and the history', async () => {
    mockDetail(detail());
    renderDetail();

    expect(await screen.findByRole('heading', { name: 'Reembolso · Ledesma, Florencia' })).toBeInTheDocument();
    expect(screen.getByText('Reembolso de $120.000')).toBeInTheDocument();
    expect(screen.getByText('La alumna dejó la carrera y pidió que le devuelvan el saldo.')).toBeInTheDocument();
    expect(screen.getByText('Reparto por pago')).toBeInTheDocument();
    expect(screen.getByText('Pago #55')).toBeInTheDocument();
    expect(screen.getByText('Si aprobás')).toBeInTheDocument();
    expect(screen.getByText(/quedan listas para transferir/)).toBeInTheDocument();
    expect(screen.getByText('Pedido por Romina Castro')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Aprobar.*120\.000,00/ })).toBeInTheDocument();
  });

  it('shows the Hoy / Nuevo / Diferencia table for a change request', async () => {
    mockDetail(
      detail(
        { operationType: 'CHANGE_PRICE', operationLabel: 'Cambio de precio', amount: null },
        {
          detailBlock: {
            type: 'CHANGE',
            refundParts: [],
            changeRows: [{ label: 'Cuota mensual', today: '$100.000', proposed: '$110.000', difference: '+$10.000' }],
          },
        },
      ),
    );
    renderDetail();

    expect(await screen.findByText('Qué cambia')).toBeInTheDocument();
    expect(screen.getByText('Cuota mensual')).toBeInTheDocument();
    expect(screen.getByText('+$10.000')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprobar' })).toBeInTheDocument();
  });

  it('approves in two steps, sending the optional note', async () => {
    mockDetail(detail());
    let body;
    server.use(
      http.post(`${API}/authorization-requests/11/approve`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 11, operationType: 'ISSUE_REFUND', status: 'APPROVED', resumeOutcome: 'PROCESSED' });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.type(await screen.findByLabelText('Nota (opcional)'), 'Verificado con el comprobante.');
    await user.click(screen.getByRole('button', { name: /Aprobar.*120\.000,00/ }));
    expect(body).toBeUndefined();
    expect(screen.getByText('Se ejecuta ahora y no se puede deshacer.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirmar aprobación' }));

    await waitFor(() => expect(body).toEqual({ decisionReason: 'Verificado con el comprobante.' }));
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Pedido aprobado.'));
  });

  it('lets the director go back from the confirmation step', async () => {
    mockDetail(detail());
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: /Aprobar.*120\.000,00/ }));
    await user.click(screen.getByRole('button', { name: 'Volver' }));

    expect(screen.getByRole('button', { name: /Aprobar.*120\.000,00/ })).toBeInTheDocument();
  });

  it('requires a reason to reject and sends it', async () => {
    mockDetail(detail());
    let body;
    server.use(
      http.post(`${API}/authorization-requests/11/reject`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 11, operationType: 'ISSUE_REFUND', status: 'REJECTED', resumeOutcome: null });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Rechazar' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Rechazar' }));
    expect(await within(dialog).findByText('El motivo del rechazo es obligatorio.')).toBeInTheDocument();
    expect(body).toBeUndefined();

    await user.type(within(dialog).getByPlaceholderText('Motivo del rechazo'), 'Falta el comprobante.');
    await user.click(within(dialog).getByRole('button', { name: 'Rechazar' }));

    await waitFor(() => expect(body).toEqual({ decisionReason: 'Falta el comprobante.' }));
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Pedido rechazado.'));
  });

  it('does not offer to decide a request the director made', async () => {
    mockDetail(detail({ requestedBy: { id: 1, name: 'Director Propio', roles: ['DIRECTOR'] } }));
    renderDetail();

    expect(await screen.findByText('Esperando a la dirección')).toBeInTheDocument();
    expect(screen.getByText('Lo tiene que decidir otra persona de dirección.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Aprobar/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument();
    expect(screen.getByText('Si la dirección aprueba')).toBeInTheDocument();
  });

  it('explains the 403 when the server refuses the decision', async () => {
    mockDetail(detail());
    server.use(http.post(`${API}/authorization-requests/11/approve`, () => new HttpResponse(null, { status: 403 })));
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: /Aprobar.*120\.000,00/ }));
    await user.click(screen.getByRole('button', { name: 'Confirmar aprobación' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('Solo la dirección puede aprobar pedidos.'));
  });

  it('reports that the request was already decided (409) and refreshes it', async () => {
    const setCurrent = mockDetail(detail());
    server.use(
      http.post(`${API}/authorization-requests/11/approve`, () => {
        setCurrent(detail({ status: 'REJECTED', decidedAt: '2026-10-24T09:00:00Z' }, { decidedBy: { id: 2, name: 'Otra Directora', roles: [] } }));
        return HttpResponse.json({ code: 'ILLEGAL_AUTHORIZATION_REQUEST_TRANSITION' }, { status: 409 });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: /Aprobar.*120\.000,00/ }));
    await user.click(screen.getByRole('button', { name: 'Confirmar aprobación' }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith('Este pedido ya no está pendiente: alguien lo resolvió antes. Actualizamos los datos.'),
    );
    expect(await screen.findByText('Rechazada por')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Aprobar/ })).not.toBeInTheDocument();
  });

  it('stays on the detail and explains when the approved operation could not run', async () => {
    const setCurrent = mockDetail(detail());
    server.use(
      http.post(`${API}/authorization-requests/11/approve`, () => {
        setCurrent(
          detail(
            { status: 'APPROVED', resumeOutcome: 'REJECTED_ON_RESUME', decidedAt: '2026-10-24T09:00:00Z' },
            { decidedBy: { id: 1, name: 'Director', roles: [] }, effectLines: [] },
          ),
        );
        return HttpResponse.json({ code: 'REFUND_EXCEEDS_AVAILABLE_AMOUNT' }, { status: 409 });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: /Aprobar.*120\.000,00/ }));
    await user.click(screen.getByRole('button', { name: 'Confirmar aprobación' }));

    expect(await screen.findByText('Aprobada, pero no se pudo ejecutar.')).toBeInTheDocument();
    expect(screen.getAllByText('No se pudo ejecutar').length).toBeGreaterThan(0);
    expect(toastMock.error).toHaveBeenCalledWith(expect.stringContaining('no se pudo ejecutar'));
  });

  it('shows a read-only decision with the reason for a rejected request', async () => {
    mockDetail(
      detail(
        { status: 'REJECTED', decidedAt: '2026-10-24T09:00:00Z' },
        {
          decidedBy: { id: 2, name: 'Otra Directora', roles: [] },
          decisionReason: 'Falta el comprobante.',
          effectLines: [],
        },
      ),
    );
    renderDetail();

    expect(await screen.findByText('Decisión')).toBeInTheDocument();
    expect(screen.getByText('Falta el comprobante.')).toBeInTheDocument();
    expect(screen.getByText('No se hizo ningún cambio en la cuenta.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument();
  });

  it('shows a not found state when the request does not exist', async () => {
    server.use(http.get(`${API}/authorization-requests/11`, () => new HttpResponse(null, { status: 404 })));
    renderDetail();

    expect(await screen.findByText('No encontramos este pedido. Puede que ya no exista.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a Aprobaciones' })).toHaveAttribute('href', '/director/aprobaciones/facturacion');
  });

  it('shows a loading skeleton and an error with retry', async () => {
    server.use(http.get(`${API}/authorization-requests/11`, () => new HttpResponse(null, { status: 500 })));
    renderDetail();

    expect(await screen.findByText('No pudimos cargar el pedido.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});

describe('AuthorizationRequestDetailPage as administrative', () => {
  beforeEach(() => {
    auth.user = { id: 30, roles: ['ADMINISTRATIVO'] };
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('shows the own pending request waiting for the director, without decision buttons, and withdraws it', async () => {
    mockDetail(detail());
    let withdrawn = false;
    server.use(
      http.post(`${API}/authorization-requests/11/withdraw`, () => {
        withdrawn = true;
        return HttpResponse.json({ id: 11, operationType: 'ISSUE_REFUND', status: 'WITHDRAWN', resumeOutcome: null });
      }),
    );
    const user = userEvent.setup();
    renderDetail('administrativo');

    expect(await screen.findByText('Esperando a la dirección')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Aprobar/ })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Nota (opcional)')).not.toBeInTheDocument();
    expect(screen.getByText('Si la dirección aprueba')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retirar pedido' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Retirar pedido' }));

    await waitFor(() => expect(withdrawn).toBe(true));
  });

  it('explains that the request is not accessible when the server answers 403', async () => {
    server.use(http.get(`${API}/authorization-requests/11`, () => new HttpResponse(null, { status: 403 })));
    renderDetail('administrativo');

    expect(await screen.findByText('No tenés acceso a este pedido.')).toBeInTheDocument();
  });
});
