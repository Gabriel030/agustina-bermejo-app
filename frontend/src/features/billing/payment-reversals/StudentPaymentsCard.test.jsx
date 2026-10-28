import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { todayIsoDate } from '@/lib/billing';
import { StudentPaymentsCard } from './StudentPaymentsCard';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles }, roles: auth.roles }),
}));

const money = (amount) => ({ amount, currency: 'ARS' });

const allowed = { allowed: true, blockedBy: null };

function blocked(code, message) {
  return { allowed: false, blockedBy: { code, message } };
}

function payment(overrides) {
  return {
    id: 502,
    amount: money('85000.00'),
    approvedAmount: money('85000.00'),
    refundedAmount: money('0.00'),
    status: 'APPROVED',
    gateway: 'MANUAL',
    methodType: 'BANK_TRANSFER',
    paidOn: '2026-10-03',
    registeredAt: '2026-10-03T10:00:00',
    receiptNumber: null,
    chargeback: null,
    pendingRefunds: { count: 0, amount: money('0.00'), parts: [] },
    actions: {
      registerChargeback: allowed,
      reverseChargeback: blocked('ILLEGAL_PAYMENT_STATUS_TRANSITION', 'El pago no está en un estado que admita esta acción.'),
      revokeApproval: allowed,
    },
    ...overrides,
  };
}

const pendingPart = { refundId: 71, status: 'REQUESTED', amount: money('25000.00'), requestedOn: '2026-10-20' };

const pendingRefundPayment = payment({
  pendingRefunds: { count: 1, amount: money('25000.00'), parts: [pendingPart] },
  actions: {
    registerChargeback: blocked('PAYMENT_HAS_PENDING_REFUND', 'Tiene reembolsos pendientes.'),
    reverseChargeback: blocked('ILLEGAL_PAYMENT_STATUS_TRANSITION', 'No aplica.'),
    revokeApproval: blocked('PAYMENT_HAS_PENDING_REFUND', 'Tiene reembolsos pendientes.'),
  },
});

const chargedBackPayment = payment({
  id: 501,
  status: 'CHARGED_BACK',
  paidOn: '2026-09-12',
  chargeback: { chargebackId: 32, amount: money('85000.00'), chargedBackOn: '2026-09-20', reference: 'CBK-1', reversedOn: null },
  actions: {
    registerChargeback: blocked('ILLEGAL_PAYMENT_STATUS_TRANSITION', 'No aplica.'),
    reverseChargeback: allowed,
    revokeApproval: blocked('ILLEGAL_PAYMENT_STATUS_TRANSITION', 'No aplica.'),
  },
});

function previewOf(overrides) {
  return {
    paymentId: 502,
    paymentStatus: 'APPROVED',
    allowed: true,
    blockedBy: null,
    creditToReverse: money('85000.00'),
    pendingRefunds: [],
    invoiceIdsFundedByPayment: [61],
    effects: { calculated: false, reopenedInvoices: [], settledInvoices: [], lateFeeReinstatements: [] },
    ...overrides,
  };
}

const reversalResult = {
  paymentId: 502,
  chargebackId: 32,
  paymentStatus: 'CHARGED_BACK',
  reference: 'CBK-88213',
  reopenedInvoiceIds: [61],
  settledInvoiceIds: [],
};

function mockPayments(payments) {
  server.use(http.get(`${API}/students/7/payments`, () => HttpResponse.json(payments)));
}

function renderCard({ roles = ['ADMINISTRATIVO'], payments = [payment()] } = {}) {
  auth.roles = roles;
  mockPayments(payments);
  return renderWithProviders(
    <>
      <Toaster />
      <StudentPaymentsCard studentId={7} invoices={[{ id: 61, invoiceType: 'MONTHLY_INSTALLMENT', lines: [{ period: '2026-10' }] }]} />
    </>,
  );
}

async function openMenu(user, paymentId = 502) {
  await user.click(await screen.findByRole('button', { name: `Acciones del pago ${paymentId}` }));
  return screen.findByRole('menu');
}

async function openChargebackDialog(user) {
  await openMenu(user);
  await user.click(screen.getByRole('menuitem', { name: /Registrar contracargo del banco/ }));
  return screen.findByRole('dialog');
}

function mockChargebackPreview(preview = previewOf()) {
  server.use(http.post(`${API}/payments/502/chargebacks/preview`, () => HttpResponse.json(preview)));
}

async function fillReference(user, dialog, value = 'CBK-88213') {
  await user.type(within(dialog).getByLabelText('Referencia del banco'), value);
}

describe('StudentPaymentsCard', () => {
  it('shows each payment with the amount returned under it and an empty state when there are none', async () => {
    renderCard({ payments: [payment({ refundedAmount: money('10000.00') })] });

    expect(await screen.findByText('Pago #502')).toBeInTheDocument();
    expect(screen.getByText('Transferencia')).toBeInTheDocument();
    expect(screen.getByText(/Devuelto .*10\.000,00/)).toBeInTheDocument();
    expect(screen.getByText('Con devolución parcial')).toBeInTheDocument();
  });

  it('shows an empty state when the student has no payments', async () => {
    renderCard({ payments: [] });

    expect(await screen.findByText('Sin pagos registrados')).toBeInTheDocument();
  });

  it('shows an error with retry when the payments cannot be loaded', async () => {
    server.use(http.get(`${API}/students/7/payments`, () => new HttpResponse(null, { status: 500 })));
    auth.roles = ['ADMINISTRATIVO'];
    renderWithProviders(<StudentPaymentsCard studentId={7} />);

    expect(await screen.findByText('No pudimos cargar los pagos.')).toBeInTheDocument();
    mockPayments([]);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Sin pagos registrados')).toBeInTheDocument();
  });

  describe('row menu by role and status', () => {
    it('offers only the chargeback to the administrative role and explains that revocation is the director\'s', async () => {
      const user = userEvent.setup();
      renderCard();

      await openMenu(user);

      expect(screen.getByText('Movimiento del banco')).toBeInTheDocument();
      expect(screen.getByRole('menuitem', { name: /Registrar contracargo del banco/ })).toBeInTheDocument();
      expect(screen.queryByRole('menuitem', { name: /Revocar aprobación/ })).not.toBeInTheDocument();
      expect(screen.getByText('La revocación de aprobaciones la hace el director.')).toBeInTheDocument();
    });

    it('offers the chargeback and the revocation of a bank transfer to the director', async () => {
      const user = userEvent.setup();
      renderCard({ roles: ['DIRECTOR'] });

      await openMenu(user);

      expect(screen.getByText('Corrección interna')).toBeInTheDocument();
      expect(screen.getByRole('menuitem', { name: /Registrar contracargo del banco/ })).toBeInTheDocument();
      expect(screen.getByRole('menuitem', { name: /Revocar aprobación por error/ })).toBeInTheDocument();
      expect(screen.queryByText('La revocación de aprobaciones la hace el director.')).not.toBeInTheDocument();
    });

    it('does not offer the revocation for payments that are not bank transfers', async () => {
      const user = userEvent.setup();
      renderCard({ roles: ['DIRECTOR'], payments: [payment({ methodType: 'CASH' })] });

      await openMenu(user);

      expect(screen.queryByRole('menuitem', { name: /Revocar aprobación/ })).not.toBeInTheDocument();
    });

    it('offers only the reversal for a charged back payment', async () => {
      const user = userEvent.setup();
      renderCard({ payments: [chargedBackPayment] });

      expect(await screen.findByText('Contracargo CBK-1 · 20/09/2026')).toBeInTheDocument();
      await openMenu(user, 501);

      expect(screen.getByRole('menuitem', { name: /Revertir contracargo del banco/ })).toBeInTheDocument();
      expect(screen.queryByRole('menuitem', { name: /Registrar contracargo/ })).not.toBeInTheDocument();
    });

    it('has no menu for a rejected payment', async () => {
      renderCard({ payments: [payment({ status: 'REJECTED' })] });

      expect(await screen.findByLabelText('Sin acciones disponibles')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Acciones del pago 502' })).not.toBeInTheDocument();
    });

    it('shows the reason inline and disables an action the backend does not allow', async () => {
      const user = userEvent.setup();
      renderCard({
        payments: [
          payment({
            actions: {
              registerChargeback: blocked('PAYMENT_ALREADY_REFUNDED', 'Ya tiene dinero devuelto.'),
              reverseChargeback: allowed,
              revokeApproval: allowed,
            },
          }),
        ],
      });

      await openMenu(user);

      expect(screen.getByRole('menuitem', { name: /Registrar contracargo del banco/ })).toHaveAttribute('aria-disabled', 'true');
      expect(screen.getByText('Ya tiene dinero devuelto.')).toBeInTheDocument();
    });

    it('flags a payment with refunds in progress', async () => {
      renderCard({ payments: [pendingRefundPayment] });

      expect(await screen.findByText(/Reembolso en curso \(1\)/)).toBeInTheDocument();
    });
  });

  describe('register chargeback', () => {
    it('previews the impact, keeps confirm disabled until the bank reference is loaded and sends the chargeback', async () => {
      const user = userEvent.setup();
      let previewBody;
      let body;
      server.use(
        http.post(`${API}/payments/502/chargebacks/preview`, async ({ request }) => {
          previewBody = await request.json();
          return HttpResponse.json(previewOf());
        }),
        http.post(`${API}/payments/502/chargebacks`, async ({ request }) => {
          body = await request.json();
          return HttpResponse.json(reversalResult, { status: 201 });
        }),
      );
      renderCard();

      const dialog = await openChargebackDialog(user);
      expect(await within(dialog).findByText('Cuota mensual · oct 2026')).toBeInTheDocument();
      expect(within(dialog).getByText('Qué va a pasar')).toBeInTheDocument();
      expect(within(dialog).getByText(/Esta acción no se puede deshacer/)).toBeInTheDocument();
      expect(within(dialog).getByLabelText('Monto').value).toMatch(/85\.000,00/);
      const confirm = within(dialog).getByRole('button', { name: 'Registrar contracargo' });

      await user.click(confirm);
      expect(await within(dialog).findByText('Ingresá la referencia que informa el banco.')).toBeInTheDocument();
      expect(body).toBeUndefined();

      await fillReference(user, dialog);
      await waitFor(() => expect(previewBody).toEqual({ amount: money('85000.00'), chargedBackOn: todayIsoDate(), reference: 'CBK-88213' }));
      await waitFor(() => expect(confirm).toBeEnabled());
      await user.click(confirm);

      await waitFor(() => expect(body).toEqual({ amount: money('85000.00'), chargedBackOn: todayIsoDate(), reference: 'CBK-88213' }));
      expect(await screen.findByText('Contracargo CBK-88213 registrado. 1 cuota reabierta.')).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    });

    it('lists the invoices and late fees when the preview calculates the effects', async () => {
      const user = userEvent.setup();
      mockChargebackPreview(
        previewOf({
          effects: {
            calculated: true,
            reopenedInvoices: [
              {
                invoiceId: 61,
                paymentStatusBefore: 'PAID',
                paymentStatusAfter: 'OVERDUE',
                outstandingBefore: money('0.00'),
                outstandingAfter: money('85000.00'),
              },
            ],
            settledInvoices: [],
            lateFeeReinstatements: [{ invoiceId: 61, days: 12, amount: money('4200.00') }],
          },
        }),
      );
      renderCard();

      const dialog = await openChargebackDialog(user);

      expect(await within(dialog).findByText('Cuotas que se reabren')).toBeInTheDocument();
      expect(within(dialog).getByText('Pagada')).toBeInTheDocument();
      expect(within(dialog).getByText('Vencida')).toBeInTheDocument();
      expect(within(dialog).getByText('Cuota mensual · oct 2026 · 12 días')).toBeInTheDocument();
      expect(within(dialog).getByText(/4\.200,00/)).toBeInTheDocument();
      expect(within(dialog).queryByText(/se informa al confirmar/)).not.toBeInTheDocument();
    });

    it('shows the pending refunds with a way to the refunds section when the preview is blocked by them', async () => {
      const user = userEvent.setup();
      renderCard({ payments: [pendingRefundPayment] });

      await openMenu(user);
      await user.click(screen.getByRole('menuitem', { name: /Registrar contracargo del banco/ }));

      const dialog = await screen.findByRole('dialog');
      expect(within(dialog).getByText('Todavía no se puede hacer')).toBeInTheDocument();
      expect(within(dialog).getByText('Reembolso #71')).toBeInTheDocument();
      expect(within(dialog).getByText('Pendiente de aprobación')).toBeInTheDocument();
      expect(within(dialog).getByRole('link', { name: /Ir a reembolsos/ })).toHaveAttribute('href', '/administrativo/alumnos/7?view=financiero');
      expect(within(dialog).queryByRole('button', { name: 'Registrar contracargo' })).not.toBeInTheDocument();
    });

    it('turns the dialog into the blocked state when the server answers PAYMENT_HAS_PENDING_REFUND', async () => {
      const user = userEvent.setup();
      mockChargebackPreview();
      server.use(http.post(`${API}/payments/502/chargebacks`, () => HttpResponse.json({ code: 'PAYMENT_HAS_PENDING_REFUND' }, { status: 409 })));
      renderCard();

      const dialog = await openChargebackDialog(user);
      await fillReference(user, dialog);
      const confirm = within(dialog).getByRole('button', { name: 'Registrar contracargo' });
      await waitFor(() => expect(confirm).toBeEnabled());
      mockPayments([pendingRefundPayment]);
      await user.click(confirm);

      expect(await within(dialog).findByText('Todavía no se puede hacer')).toBeInTheDocument();
      expect(await within(dialog).findByText('Reembolso #71')).toBeInTheDocument();
      expect(within(dialog).getByRole('link', { name: /Ir a reembolsos/ })).toBeInTheDocument();
    });

    it('blocks the dialog without a refunds link when the payment already has money returned', async () => {
      const user = userEvent.setup();
      mockChargebackPreview(previewOf({ allowed: false, blockedBy: { code: 'PAYMENT_ALREADY_REFUNDED', message: 'El pago ya tiene dinero devuelto.' } }));
      renderCard();

      const dialog = await openChargebackDialog(user);

      expect(await within(dialog).findByText('No se puede hacer')).toBeInTheDocument();
      expect(within(dialog).getByText('El pago ya tiene dinero devuelto.')).toBeInTheDocument();
      expect(within(dialog).queryByRole('link', { name: /Ir a reembolsos/ })).not.toBeInTheDocument();
      expect(within(dialog).queryByRole('button', { name: 'Registrar contracargo' })).not.toBeInTheDocument();
    });

    it('shows the field error when the server rejects the bank reference', async () => {
      const user = userEvent.setup();
      mockChargebackPreview();
      server.use(http.post(`${API}/payments/502/chargebacks`, () => HttpResponse.json({ code: 'CHARGEBACK_REFERENCE_INVALID' }, { status: 400 })));
      renderCard();

      const dialog = await openChargebackDialog(user);
      await fillReference(user, dialog);
      const confirm = within(dialog).getByRole('button', { name: 'Registrar contracargo' });
      await waitFor(() => expect(confirm).toBeEnabled());
      await user.click(confirm);

      expect(await within(dialog).findByText('Ingresá la referencia que informa el banco (hasta 120 caracteres).')).toBeInTheDocument();
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('keeps the dialog open and reports the error when the account has no credit', async () => {
      const user = userEvent.setup();
      mockChargebackPreview();
      server.use(http.post(`${API}/payments/502/chargebacks`, () => HttpResponse.json({ code: 'INSUFFICIENT_ACCOUNT_CREDIT' }, { status: 409 })));
      renderCard();

      const dialog = await openChargebackDialog(user);
      await fillReference(user, dialog);
      const confirm = within(dialog).getByRole('button', { name: 'Registrar contracargo' });
      await waitFor(() => expect(confirm).toBeEnabled());
      await user.click(confirm);

      expect(await screen.findByText('La cuenta del alumno no tiene crédito suficiente para hacer esta operación.')).toBeInTheDocument();
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('closes the dialog and refreshes the list when the payment changed state', async () => {
      const user = userEvent.setup();
      mockChargebackPreview();
      server.use(http.post(`${API}/payments/502/chargebacks`, () => HttpResponse.json({ code: 'ILLEGAL_PAYMENT_STATUS_TRANSITION' }, { status: 409 })));
      renderCard();

      const dialog = await openChargebackDialog(user);
      await fillReference(user, dialog);
      const confirm = within(dialog).getByRole('button', { name: 'Registrar contracargo' });
      await waitFor(() => expect(confirm).toBeEnabled());
      mockPayments([payment({ status: 'REJECTED' })]);
      await user.click(confirm);

      expect(await screen.findByText('El estado del pago cambió. Actualizamos la lista, revisá y probá de nuevo.')).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(await screen.findByLabelText('Sin acciones disponibles')).toBeInTheDocument();
    });

    it('lets the user retry when the preview fails and keeps confirm disabled', async () => {
      const user = userEvent.setup();
      server.use(http.post(`${API}/payments/502/chargebacks/preview`, () => new HttpResponse(null, { status: 500 })));
      renderCard();

      const dialog = await openChargebackDialog(user);
      await fillReference(user, dialog);

      expect(await within(dialog).findByText('No pudimos calcular el impacto.')).toBeInTheDocument();
      expect(within(dialog).getByRole('button', { name: 'Registrar contracargo' })).toBeDisabled();
      mockChargebackPreview();
      await user.click(within(dialog).getByRole('button', { name: 'Reintentar' }));

      await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Registrar contracargo' })).toBeEnabled());
    });
  });

  describe('revoke approval', () => {
    it('requires a reason, previews with it and revokes the approval', async () => {
      const user = userEvent.setup();
      let previewBody;
      let body;
      server.use(
        http.post(`${API}/payments/502/approval-revocation/preview`, async ({ request }) => {
          previewBody = await request.json();
          return HttpResponse.json(previewOf());
        }),
        http.post(`${API}/payments/502/approval-revocation`, async ({ request }) => {
          body = await request.json();
          return HttpResponse.json({ ...reversalResult, paymentStatus: 'REJECTED' });
        }),
      );
      renderCard({ roles: ['DIRECTOR'] });

      await openMenu(user);
      await user.click(screen.getByRole('menuitem', { name: /Revocar aprobación por error/ }));
      const dialog = await screen.findByRole('dialog');
      const confirm = within(dialog).getByRole('button', { name: 'Revocar aprobación' });

      await user.click(confirm);
      await user.type(within(dialog).getByLabelText('Motivo'), 'Se aprobó sin transferencia');
      await waitFor(() => expect(previewBody).toEqual({ reason: 'Se aprobó sin transferencia' }));
      await waitFor(() => expect(confirm).toBeEnabled());
      await user.click(confirm);

      await waitFor(() => expect(body).toEqual({ reason: 'Se aprobó sin transferencia' }));
      expect(await screen.findByText('Aprobación revocada. 1 cuota reabierta.')).toBeInTheDocument();
    });

    it('shows the required reason error when the reason is empty', async () => {
      const user = userEvent.setup();
      server.use(http.post(`${API}/payments/502/approval-revocation/preview`, () => HttpResponse.json(previewOf())));
      renderCard({ roles: ['DIRECTOR'] });

      await openMenu(user);
      await user.click(screen.getByRole('menuitem', { name: /Revocar aprobación por error/ }));
      const dialog = await screen.findByRole('dialog');
      await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Revocar aprobación' })).toBeEnabled());
      await user.click(within(dialog).getByRole('button', { name: 'Revocar aprobación' }));

      expect(await within(dialog).findByText('El motivo es obligatorio.')).toBeInTheDocument();
    });
  });

  describe('reverse chargeback', () => {
    it('explains how the credit is reapplied and reverses the chargeback', async () => {
      const user = userEvent.setup();
      let called = false;
      server.use(
        http.post(`${API}/chargebacks/32/reversal/preview`, () =>
          HttpResponse.json(previewOf({ paymentId: 501, paymentStatus: 'CHARGED_BACK', invoiceIdsFundedByPayment: [] })),
        ),
        http.post(`${API}/chargebacks/32/reversal`, () => {
          called = true;
          return HttpResponse.json({ ...reversalResult, paymentId: 501, paymentStatus: 'APPROVED', reopenedInvoiceIds: [], settledInvoiceIds: [61, 62] });
        }),
      );
      renderCard({ payments: [chargedBackPayment] });

      await openMenu(user, 501);
      await user.click(screen.getByRole('menuitem', { name: /Revertir contracargo del banco/ }));
      const dialog = await screen.findByRole('dialog');

      expect(await within(dialog).findByText('Se acredita al alumno')).toBeInTheDocument();
      expect(within(dialog).getByText(/cuotas abiertas más antiguas/)).toBeInTheDocument();
      expect(within(dialog).getByText('CBK-1')).toBeInTheDocument();
      expect(within(dialog).queryByText(/no se puede deshacer/)).not.toBeInTheDocument();
      const confirm = within(dialog).getByRole('button', { name: 'Revertir contracargo' });
      await waitFor(() => expect(confirm).toBeEnabled());
      await user.click(confirm);

      expect(await screen.findByText('Contracargo revertido. 2 cuotas saldadas.')).toBeInTheDocument();
      expect(called).toBe(true);
    });

    it('closes and refreshes when the chargeback was already reversed', async () => {
      const user = userEvent.setup();
      server.use(
        http.post(`${API}/chargebacks/32/reversal/preview`, () => HttpResponse.json(previewOf({ paymentId: 501 }))),
        http.post(`${API}/chargebacks/32/reversal`, () => HttpResponse.json({ code: 'CHARGEBACK_ALREADY_REVERSED' }, { status: 409 })),
      );
      renderCard({ payments: [chargedBackPayment] });

      await openMenu(user, 501);
      await user.click(screen.getByRole('menuitem', { name: /Revertir contracargo del banco/ }));
      const dialog = await screen.findByRole('dialog');
      const confirm = within(dialog).getByRole('button', { name: 'Revertir contracargo' });
      await waitFor(() => expect(confirm).toBeEnabled());
      await user.click(confirm);

      expect(await screen.findByText('Este contracargo ya fue revertido. Actualizamos la lista.')).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    });
  });
});
