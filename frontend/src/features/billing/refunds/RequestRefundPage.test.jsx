import { beforeEach, describe, expect, it, vi } from 'vitest';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { RequestRefundPage } from './RequestRefundPage';
import { studentFixture } from '@/test/fixtures/students';

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

async function submitAndConfirm(user, name) {
  await user.click(screen.getByRole('button', { name }));
  const dialog = await screen.findByRole('alertdialog');
  await user.click(within(dialog).getByRole('button', { name }));
}

function renderPage() {
  server.use(http.get(`${API}/students/7`, () => HttpResponse.json(studentFixture({ id: 7, nombre: 'Martina', apellido: 'Acosta' }))));
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

  it('sends 80,50 when the user types 80.5 with a dot decimal', async () => {
    const received = [];
    mockPreview(previewResponse(), (body) => received.push(body));
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');

    await fillForm(user, { amount: '80.5' });

    await waitFor(() => expect(received.at(-1)?.amount).toEqual(money('80.50')));
  });

  it('fills the amount with the maximum refundable when asked', async () => {
    const received = [];
    mockPreview(previewResponse(), (body) => received.push(body));
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');

    await user.click(screen.getByRole('button', { name: 'Usar el máximo' }));

    expect(screen.getByLabelText('Monto')).toHaveValue('80.000,00');
    await waitFor(() => expect(received.at(-1)?.amount).toEqual(money('80000.00')));
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

  it('names the student and asks for confirmation before sending, without sending on cancel', async () => {
    let created = 0;
    server.use(http.post(`${API}/students/7/refund-requests`, () => { created += 1; return HttpResponse.json({ authorizationRequestId: null, parts: [] }, { status: 201 }); }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    expect(await screen.findByText(/Acosta, Martina · Reembolsable ahora/)).toBeInTheDocument();
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Pedir reembolso' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/Acosta, Martina/)).toBeInTheDocument();
    expect(created).toBe(0);
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(created).toBe(0);
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
    await submitAndConfirm(user, 'Pedir reembolso');

    await waitFor(() => expect(received).toEqual({
      amount: money('40000.50'),
      destinationAccount: '0170099220000067890123',
      destinationHolder: 'Florencia Ledesma',
    }));
    expect(await screen.findByText('Ficha del alumno')).toBeInTheDocument();
    expect(toastMock.success).toHaveBeenCalledWith(
      'Pedido enviado a la dirección.',
      expect.objectContaining({
        description: expect.stringContaining('pendiente de autorización del director'),
        action: expect.objectContaining({ label: 'Ver mis pedidos' }),
      }),
    );
    const { action } = toastMock.success.mock.calls[0][1];
    expect(action.onClick).toEqual(expect.any(Function));
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
    await submitAndConfirm(user, 'Registrar reembolso');

    expect(await screen.findByText('Ficha del alumno')).toBeInTheDocument();
    expect(toastMock.success).toHaveBeenCalledWith('Reembolso aprobado.', expect.anything());
  });

  it('shows the destination error returned by the backend next to the field', async () => {
    server.use(
      http.post(`${API}/students/7/refund-requests`, () => HttpResponse.json(errorBody('REFUND_DESTINATION_INVALID'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');

    expect(await screen.findAllByText('El CBU o alias tiene hasta 50 caracteres y el titular hasta 120.')).toHaveLength(2);
    expect(screen.queryByText('Ficha del alumno')).not.toBeInTheDocument();
  });

  it.each([
    ['INSUFFICIENT_ACCOUNT_CREDIT', /no tiene saldo a favor suficiente/],
    ['REFUND_EXCEEDS_AVAILABLE_AMOUNT', /supera lo que se puede devolver ahora/],
    ['REFUND_AMOUNT_INVALID', /mayor a cero/],
  ])('shows the %s conflict on the amount field and keeps the form', async (code, message) => {
    server.use(http.post(`${API}/students/7/refund-requests`, () => HttpResponse.json({ code, violations: [] }, { status: 409 })));
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByLabelText('Monto')).toHaveValue('40.000');
  });

  it('reports other errors in a toast', async () => {
    server.use(
      http.post(`${API}/students/7/refund-requests`, () => HttpResponse.json(errorBody('REFUND_BLOCKED_BY_OVERDUE_DEBT'), { status: 409 })),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('No se puede devolver saldo a favor mientras haya deuda vencida.'));
  });
});

describe('RequestRefundPage — clave de idempotencia', () => {
  beforeEach(() => {
    auth.roles = ['ADMINISTRATIVO'];
    toastMock.success.mockClear();
    toastMock.error.mockClear();
    mockEligibility();
    mockPreview();
  });

  function mockRequest(responses) {
    const keys = [];
    let calls = 0;
    server.use(
      http.post(`${API}/students/7/refund-requests`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        const response = responses[Math.min(calls, responses.length - 1)];
        calls += 1;
        return response();
      }),
    );
    return keys;
  }

  const created = () => HttpResponse.json({ authorizationRequestId: 31, parts: [] }, { status: 202 });
  const failed = () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });

  it('sends an Idempotency-Key header', async () => {
    const keys = mockRequest([created]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');

    await screen.findByText('Ficha del alumno');
    expect(keys).toHaveLength(1);
    expect(keys[0]).toMatch(/\S{16,}/);
  });

  it('retries after an unknown outcome with the same key', async () => {
    const keys = mockRequest([failed, created]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');

    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/No pudimos confirmar si se emitió el pedido/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Reintentar' }));

    await screen.findByText('Ficha del alumno');
    expect(keys).toHaveLength(2);
    expect(keys[1]).toBe(keys[0]);
  });

  it('uses a new key when the request content changes', async () => {
    const keys = mockRequest([failed, created]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    await user.type(screen.getByLabelText('Titular de la cuenta'), ' Jr');
    await submitAndConfirm(user, 'Pedir reembolso');

    await screen.findByText('Ficha del alumno');
    expect(keys).toHaveLength(2);
    expect(keys[1]).not.toBe(keys[0]);
  });

  it('uses a new key when the form is opened again', async () => {
    const keys = mockRequest([failed]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');
    await screen.findByText(/No pudimos confirmar si se emitió el pedido/);
    cleanup();

    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');
    await screen.findByText(/No pudimos confirmar si se emitió el pedido/);

    expect(keys).toHaveLength(2);
    expect(keys[1]).not.toBe(keys[0]);
  });

  it('has no accessibility violations while showing the retry action', async () => {
    mockRequest([failed]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');
    await screen.findByRole('button', { name: 'Reintentar' });

    expect(await axe(document.body)).toHaveNoViolations();
  });

  it('shows the Spanish message of an IDEMPOTENCY_KEY_CONFLICT', async () => {
    mockRequest([() => HttpResponse.json(errorBody('IDEMPOTENCY_KEY_CONFLICT'), { status: 409 })]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith(expect.stringContaining('ya se había enviado con otros datos')));
  });

  it('shows the Spanish message of an IDEMPOTENCY_KEY_INVALID', async () => {
    mockRequest([() => HttpResponse.json(errorBody('IDEMPOTENCY_KEY_INVALID'), { status: 400 })]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Monto');
    await fillForm(user);
    await submitAndConfirm(user, 'Pedir reembolso');

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith(expect.stringContaining('No pudimos identificar el pedido')));
  });
});
