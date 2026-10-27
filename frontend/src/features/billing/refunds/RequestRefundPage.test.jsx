import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { RequestRefundPage } from './RequestRefundPage';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles } }),
}));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

function money(amount) {
  return { amount, currency: 'ARS' };
}

function eligibility(overrides) {
  return {
    currency: 'ARS',
    unallocatedCredit: money('100000.00'),
    reservedByPendingRefunds: money('20000.00'),
    availableToRefund: money('80000.00'),
    hasOverdueDebt: false,
    blockedBy: null,
    payments: [
      {
        paymentId: 55,
        paidOn: '2026-09-01',
        methodType: 'BANK_TRANSFER',
        credited: money('100000.00'),
        refunded: money('0.00'),
        pending: money('20000.00'),
        refundableNow: money('80000.00'),
        receiptDestinationAccount: null,
        receiptDestinationHolder: null,
      },
    ],
    ...overrides,
  };
}

function previewResponse(overrides) {
  return {
    allowed: true,
    blockedBy: null,
    parts: [
      { paymentId: 56, methodType: 'CASH', amount: money('30000.00'), destinationAccount: '0170099220000067890123', destinationHolder: 'Florencia Ledesma', destinationComplete: true },
      { paymentId: 55, methodType: 'BANK_TRANSFER', amount: money('10000.00'), destinationAccount: '0170000000000555', destinationHolder: 'Pagador Original', destinationComplete: true },
    ],
    ...overrides,
  };
}

function mockEligibility(overrides) {
  server.use(http.get(`${API}/students/7/refund-eligibility`, () => HttpResponse.json(eligibility(overrides))));
}

function mockPreview(response = previewResponse(), onRequest) {
  server.use(
    http.post(`${API}/students/7/refund-requests/preview`, async ({ request }) => {
      onRequest?.(await request.json());
      return HttpResponse.json(response);
    }),
  );
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/:role/alumnos/:id/reembolsos/nuevo" element={<RequestRefundPage />} />
      <Route path="/:role/alumnos/:id" element={<p>Ficha del alumno</p>} />
    </Routes>,
    { route: '/administrativo/alumnos/7/reembolsos/nuevo' },
  );
}

async function fillForm(user, { amount = '40.000', account = '0170099220000067890123', holder = 'Florencia Ledesma' } = {}) {
  if (amount) await user.type(screen.getByLabelText('Monto'), amount);
  if (account) await user.type(screen.getByLabelText('CBU o alias'), account);
  if (holder) await user.type(screen.getByLabelText('Titular de la cuenta'), holder);
}

describe('RequestRefundPage', () => {
  beforeEach(() => {
    auth.roles = ['ADMINISTRATIVO'];
    toastMock.success.mockClear();
    toastMock.error.mockClear();
    mockEligibility();
    mockPreview();
  });

  it('shows the maximum refundable amount', async () => {
    renderPage();

    expect(await screen.findByText(/Podés pedir hasta/)).toHaveTextContent('80.000,00');
    expect(screen.getByText(/Reembolsable ahora/)).toHaveTextContent('80.000,00');
  });

  it('blocks the form when the student has overdue debt', async () => {
    mockEligibility({
      hasOverdueDebt: true,
      blockedBy: { code: 'REFUND_BLOCKED_BY_OVERDUE_DEBT', message: 'No se puede devolver saldo a favor mientras haya deuda vencida.' },
    });
    renderPage();

    expect(await screen.findByText('Tiene deuda vencida')).toBeInTheDocument();
    expect(screen.getByText('No se puede devolver saldo a favor mientras haya deuda vencida.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Monto')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a la ficha' })).toHaveAttribute('href', '/administrativo/alumnos/7?view=financiero');
  });

  it('blocks the form when there is no credit available', async () => {
    mockEligibility({ availableToRefund: money('0.00') });
    renderPage();

    expect(await screen.findByText('No tiene saldo a favor disponible')).toBeInTheDocument();
    expect(screen.queryByLabelText('Monto')).not.toBeInTheDocument();
  });

  it('shows the split calculated by the backend after the debounce', async () => {
    const received = [];
    mockPreview(previewResponse(), (body) => received.push(body));
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');

    expect(screen.getByText('Ingresá un monto válido para ver cómo se reparte.')).toBeInTheDocument();
    await fillForm(user);

    expect(await screen.findByText(/Pago #56 · Efectivo/)).toBeInTheDocument();
    expect(screen.getByText(/Pago #55 · Transferencia/)).toBeInTheDocument();
    expect(screen.getByText(/Pagador Original/)).toBeInTheDocument();
    expect(received.at(-1)).toEqual({
      amount: money('40000.00'),
      destinationAccount: '0170099220000067890123',
      destinationHolder: 'Florencia Ledesma',
    });
    expect(received.length).toBeLessThan(10);
  });

  it('marks the destination as missing in the split while it is not filled in', async () => {
    mockPreview(
      previewResponse({
        allowed: false,
        blockedBy: { code: 'REFUND_DESTINATION_REQUIRED', message: 'Indicá el CBU' },
        parts: [{ paymentId: 56, methodType: 'CASH', amount: money('30000.00'), destinationAccount: null, destinationHolder: null, destinationComplete: false }],
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await user.type(screen.getByLabelText('Monto'), '30000');

    expect(await screen.findByText('Falta completar el destino')).toBeInTheDocument();
  });

  it('explains the maximum when the backend says the amount exceeds what is available', async () => {
    mockPreview(previewResponse({ allowed: false, blockedBy: { code: 'REFUND_EXCEEDS_AVAILABLE_AMOUNT', message: 'x' }, parts: [] }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await user.type(screen.getByLabelText('Monto'), '999.999');

    expect(await screen.findByText(/Se pueden devolver hasta .*80\.000,00\. El resto sigue a favor\./)).toBeInTheDocument();
  });

  it('requires the bank destination before sending', async () => {
    let created = false;
    server.use(http.post(`${API}/students/7/refund-requests`, () => { created = true; return HttpResponse.json({}, { status: 202 }); }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await user.type(screen.getByLabelText('Monto'), '40.000');
    await user.click(screen.getByRole('button', { name: 'Pedir reembolso' }));

    expect(await screen.findByText('Indicá el CBU o alias de destino.')).toBeInTheDocument();
    expect(screen.getByText('Indicá el titular de la cuenta.')).toBeInTheDocument();
    expect(created).toBe(false);
  });

  it('validates the amount and the destination lengths', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await user.type(screen.getByLabelText('Monto'), 'abc');
    await user.type(screen.getByLabelText('CBU o alias'), 'a'.repeat(51));
    await user.type(screen.getByLabelText('Titular de la cuenta'), 'b'.repeat(121));
    await user.click(screen.getByRole('button', { name: 'Pedir reembolso' }));

    expect(await screen.findByText('Ingresá un monto mayor a cero, con hasta dos decimales.')).toBeInTheDocument();
    expect(screen.getByText('Máximo 50 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('Máximo 120 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('51/50')).toBeInTheDocument();
  });

  it('sends the request as administrative and tells it is pending the director authorization', async () => {
    let received;
    server.use(
      http.post(`${API}/students/7/refund-requests`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ authorizationRequestId: 31, parts: [] }, { status: 202 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    expect(screen.getByText('Lo aprueba la dirección')).toBeInTheDocument();
    await fillForm(user, { amount: '40.000,50', account: ' 0170099220000067890123 ', holder: 'Florencia Ledesma' });
    await user.click(screen.getByRole('button', { name: 'Pedir reembolso' }));

    await waitFor(() => expect(received).toEqual({
      amount: money('40000.50'),
      destinationAccount: '0170099220000067890123',
      destinationHolder: 'Florencia Ledesma',
    }));
    expect(await screen.findByText('Ficha del alumno')).toBeInTheDocument();
    expect(toastMock.success).toHaveBeenCalledWith(
      'Pedido enviado a la dirección.',
      expect.objectContaining({ description: expect.stringContaining('pendiente de autorización del director') }),
    );
  });

  it('registers the refund as approved when the director requests it', async () => {
    auth.roles = ['DIRECTOR'];
    server.use(
      http.post(`${API}/students/7/refund-requests`, () => HttpResponse.json({ authorizationRequestId: null, parts: [] }, { status: 201 })),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    expect(screen.getAllByText('Se aprueba ahora')).toHaveLength(2);
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrar reembolso' }));

    expect(await screen.findByText('Ficha del alumno')).toBeInTheDocument();
    expect(toastMock.success).toHaveBeenCalledWith('Reembolso aprobado.', expect.anything());
  });

  it('shows the destination error returned by the backend next to the field', async () => {
    server.use(
      http.post(`${API}/students/7/refund-requests`, () => HttpResponse.json({ code: 'REFUND_DESTINATION_INVALID' }, { status: 400 })),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Pedir reembolso' }));

    expect(await screen.findAllByText('El CBU o alias tiene hasta 50 caracteres y el titular hasta 120.')).toHaveLength(2);
    expect(screen.queryByText('Ficha del alumno')).not.toBeInTheDocument();
  });

  it.each([
    ['INSUFFICIENT_ACCOUNT_CREDIT', /no tiene saldo a favor suficiente/],
    ['REFUND_EXCEEDS_AVAILABLE_AMOUNT', /supera lo que se puede devolver ahora/],
    ['REFUND_AMOUNT_INVALID', /mayor a cero/],
  ])('shows the %s conflict on the amount field and keeps the form', async (code, message) => {
    server.use(http.post(`${API}/students/7/refund-requests`, () => HttpResponse.json({ code }, { status: 409 })));
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Pedir reembolso' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByLabelText('Monto')).toHaveValue('40.000');
  });

  it('reports other errors in a toast', async () => {
    server.use(
      http.post(`${API}/students/7/refund-requests`, () => HttpResponse.json({ code: 'REFUND_BLOCKED_BY_OVERDUE_DEBT' }, { status: 409 })),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Pedir reembolso' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('No se puede devolver saldo a favor mientras haya deuda vencida.'));
  });
});
