import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { RefundsSection } from './RefundsSection';

const API = 'http://localhost:8080';

function refund(overrides) {
  return {
    id: 1,
    refundRequestId: 10,
    paymentId: 55,
    status: 'APPROVED',
    methodType: 'BANK_TRANSFER',
    amount: { amount: '30000.00', currency: 'ARS' },
    destinationAccount: '0170000000000123',
    destinationHolder: 'Florencia Ledesma',
    requestedOn: '2026-10-01',
    completedOn: null,
    completionReference: null,
    failureReason: null,
    rejectionReason: null,
    ...overrides,
  };
}

function mockRefunds(refunds) {
  server.use(http.get(`${API}/students/7/refunds`, () => HttpResponse.json(refunds)));
}

async function expandFirstRequest(user) {
  await user.click(await screen.findByRole('button', { name: 'Ver partes' }));
}

function eligibility(overrides) {
  return {
    currency: 'ARS',
    unallocatedCredit: { amount: '100000.00', currency: 'ARS' },
    reservedByPendingRefunds: { amount: '20000.00', currency: 'ARS' },
    availableToRefund: { amount: '80000.00', currency: 'ARS' },
    hasOverdueDebt: false,
    blockedBy: null,
    payments: [
      {
        paymentId: 55,
        paidOn: '2026-09-01',
        methodType: 'BANK_TRANSFER',
        credited: { amount: '100000.00', currency: 'ARS' },
        refunded: { amount: '0.00', currency: 'ARS' },
        pending: { amount: '20000.00', currency: 'ARS' },
        refundableNow: { amount: '80000.00', currency: 'ARS' },
        receiptDestinationAccount: null,
        receiptDestinationHolder: null,
      },
    ],
    ...overrides,
  };
}

function mockEligibility(overrides) {
  server.use(http.get(`${API}/students/7/refund-eligibility`, () => HttpResponse.json(eligibility(overrides))));
}

describe('RefundsSection', () => {
  beforeEach(() => mockEligibility());

  it('shows the credit, the refundable amount and a link to request a refund', async () => {
    mockRefunds([]);
    renderWithProviders(<RefundsSection studentId={7} />);

    const link = await screen.findByRole('link', { name: 'Pedir reembolso' });
    expect(link).toHaveAttribute('href', '/reembolsos/nuevo');
    expect(screen.getByText(/100\.000,00/)).toBeInTheDocument();
    expect(screen.getByText(/Incluye .*20\.000,00.* reservados/)).toBeInTheDocument();
    expect(screen.getAllByText(/80\.000,00/).length).toBeGreaterThan(0);
  });

  it('disables the request and explains the overdue debt block', async () => {
    mockRefunds([]);
    mockEligibility({
      hasOverdueDebt: true,
      blockedBy: { code: 'REFUND_BLOCKED_BY_OVERDUE_DEBT', message: 'No se puede devolver saldo a favor mientras haya deuda vencida.' },
    });
    renderWithProviders(<RefundsSection studentId={7} />);

    expect(await screen.findByText('Tiene deuda vencida')).toBeInTheDocument();
    const button = screen.getByRole('button', { name: 'Pedir reembolso' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-describedby', 'refund-block');
    expect(screen.queryByRole('link', { name: 'Pedir reembolso' })).not.toBeInTheDocument();
  });

  it('disables the request when nothing is available to refund', async () => {
    mockRefunds([]);
    mockEligibility({ availableToRefund: { amount: '0.00', currency: 'ARS' } });
    renderWithProviders(<RefundsSection studentId={7} />);

    expect(await screen.findByText('No tiene saldo a favor disponible')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pedir reembolso' })).toBeDisabled();
  });

  it('shows an empty state when the student has no refunds', async () => {
    mockRefunds([]);
    renderWithProviders(<RefundsSection studentId={7} />);

    expect(await screen.findByText('Sin reembolsos')).toBeInTheDocument();
  });

  it('shows an error with retry when the refunds cannot be loaded', async () => {
    server.use(http.get(`${API}/students/7/refunds`, () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<RefundsSection studentId={7} />);

    expect(await screen.findByText('No pudimos cargar los reembolsos.')).toBeInTheDocument();
    mockRefunds([]);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Sin reembolsos')).toBeInTheDocument();
  });

  it('groups the parts of a request into one row with its aggregate status and total', async () => {
    mockRefunds([
      refund({ id: 1, status: 'COMPLETED', amount: { amount: '10000.00', currency: 'ARS' } }),
      refund({ id: 2, paymentId: 56, status: 'APPROVED', amount: { amount: '20000.00', currency: 'ARS' } }),
      refund({ id: 3, refundRequestId: 11, status: 'REJECTED', requestedOn: '2026-09-01', amount: { amount: '5000.00', currency: 'ARS' } }),
    ]);
    renderWithProviders(<RefundsSection studentId={7} />);

    expect(await screen.findByText('2 pedidos')).toBeInTheDocument();
    expect(screen.getByText('En curso · 1 de 2 hechas')).toBeInTheDocument();
    expect(screen.getByText('Rechazado')).toBeInTheDocument();
    expect(screen.getByText(/30\.000,00/)).toBeInTheDocument();
  });

  it('filters requests by aggregate status', async () => {
    mockRefunds([
      refund({ id: 1, refundRequestId: 10, status: 'APPROVED' }),
      refund({ id: 3, refundRequestId: 11, status: 'REJECTED', requestedOn: '2026-09-01' }),
    ]);
    const user = userEvent.setup();
    renderWithProviders(<RefundsSection studentId={7} />);

    await user.click(await screen.findByRole('button', { name: 'Filtrar por estado' }));
    await user.click(screen.getByRole('checkbox', { name: /Rechazado/ }));

    expect(screen.queryByText('Pedido #11')).not.toBeInTheDocument();
    expect(screen.getByText('Pedido #10')).toBeInTheDocument();
  });

  it('only offers the actions on parts that are ready to transfer', async () => {
    mockRefunds([
      refund({ id: 1, status: 'COMPLETED', completedOn: '2026-10-03', completionReference: 'OP-99' }),
      refund({ id: 2, paymentId: 56, status: 'APPROVED' }),
      refund({ id: 4, paymentId: 57, status: 'FAILED', failureReason: 'El banco la rechazó' }),
    ]);
    const user = userEvent.setup();
    renderWithProviders(<RefundsSection studentId={7} />);
    await expandFirstRequest(user);

    expect(screen.getAllByRole('button', { name: /Marcar como hecho/ })).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Marcar como hecho el pago #56' })).toBeInTheDocument();
    expect(screen.getByText('03/10/2026 · ref. OP-99')).toBeInTheDocument();
    expect(screen.getByText('El banco la rechazó')).toBeInTheDocument();
  });

  it('completes a part sending the date, the reference and the method of the original payment', async () => {
    mockRefunds([refund({ id: 2, paymentId: 56, requestedOn: '2020-01-01' })]);
    let received;
    server.use(
      http.post(`${API}/refunds/2/completion`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json(refund({ id: 2, status: 'COMPLETED' }));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<RefundsSection studentId={7} />);
    await expandFirstRequest(user);

    await user.click(screen.getByRole('button', { name: 'Marcar como hecho el pago #56' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('Método')).toHaveValue('Transferencia');
    await user.type(within(dialog).getByLabelText('Referencia'), ' OP-123 ');
    await user.click(within(dialog).getByRole('button', { name: 'Marcar como hecho' }));

    await waitFor(() => expect(received).toMatchObject({ reference: 'OP-123', methodType: 'BANK_TRANSFER' }));
    expect(received.completedOn).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('requires a reference before completing', async () => {
    mockRefunds([refund({ id: 2, paymentId: 56 })]);
    const user = userEvent.setup();
    renderWithProviders(<RefundsSection studentId={7} />);
    await expandFirstRequest(user);

    await user.click(screen.getByRole('button', { name: 'Marcar como hecho el pago #56' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Marcar como hecho' }));

    expect(within(dialog).getByText('Indicá la referencia de la operación.')).toBeInTheDocument();
  });

  it.each([
    ['REFUND_COMPLETION_INVALID', 400, /La fecha no puede ser futura/],
    ['INSUFFICIENT_ACCOUNT_CREDIT', 409, /no tiene saldo a favor suficiente/],
  ])('keeps the dialog open and explains the %s error', async (code, status, message) => {
    mockRefunds([refund({ id: 2, paymentId: 56, requestedOn: '2020-01-01' })]);
    server.use(http.post(`${API}/refunds/2/completion`, () => HttpResponse.json({ code }, { status })));
    const user = userEvent.setup();
    renderWithProviders(<RefundsSection studentId={7} />);
    await expandFirstRequest(user);

    await user.click(screen.getByRole('button', { name: 'Marcar como hecho el pago #56' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Referencia'), 'OP-1');
    await user.click(within(dialog).getByRole('button', { name: 'Marcar como hecho' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(message);
  });

  it.each(['ILLEGAL_REFUND_STATUS_TRANSITION', 'REFUND_METHOD_MISMATCH'])(
    'closes the dialog and reloads the list when the part is stale (%s)',
    async (code) => {
      let loads = 0;
      server.use(
        http.get(`${API}/students/7/refunds`, () => {
          loads += 1;
          return HttpResponse.json([refund({ id: 2, paymentId: 56, requestedOn: '2020-01-01' })]);
        }),
        http.post(`${API}/refunds/2/completion`, () => HttpResponse.json({ code }, { status: 409 })),
      );
      const user = userEvent.setup();
      renderWithProviders(<RefundsSection studentId={7} />);
      await expandFirstRequest(user);

      await user.click(screen.getByRole('button', { name: 'Marcar como hecho el pago #56' }));
      const dialog = await screen.findByRole('dialog');
      await user.type(within(dialog).getByLabelText('Referencia'), 'OP-1');
      await user.click(within(dialog).getByRole('button', { name: 'Marcar como hecho' }));

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      await waitFor(() => expect(loads).toBeGreaterThan(1));
    },
  );

  it('marks a part as failed with a reason', async () => {
    mockRefunds([refund({ id: 2, paymentId: 56 })]);
    let received;
    server.use(
      http.post(`${API}/refunds/2/failure`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json(refund({ id: 2, status: 'FAILED' }));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<RefundsSection studentId={7} />);
    await expandFirstRequest(user);

    await user.click(screen.getByRole('button', { name: 'No se pudo el pago #56' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Registrar fallo' }));
    expect(within(dialog).getByText('Indicá por qué no se pudo transferir.')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Motivo'), ' El banco rechazó la transferencia ');
    await user.click(within(dialog).getByRole('button', { name: 'Registrar fallo' }));

    await waitFor(() => expect(received).toEqual({ reason: 'El banco rechazó la transferencia' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('shows the server error when the failure reason is rejected', async () => {
    mockRefunds([refund({ id: 2, paymentId: 56 })]);
    server.use(http.post(`${API}/refunds/2/failure`, () => HttpResponse.json({ code: 'REFUND_FAILURE_REASON_INVALID' }, { status: 400 })));
    const user = userEvent.setup();
    renderWithProviders(<RefundsSection studentId={7} />);
    await expandFirstRequest(user);

    await user.click(screen.getByRole('button', { name: 'No se pudo el pago #56' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Motivo'), 'x');
    await user.click(within(dialog).getByRole('button', { name: 'Registrar fallo' }));

    expect(await within(dialog).findByText('El motivo es obligatorio y tiene hasta 500 caracteres.')).toBeInTheDocument();
  });
});
