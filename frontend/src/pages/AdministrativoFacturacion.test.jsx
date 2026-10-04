import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdministrativoFacturacion } from '@/pages/AdministrativoFacturacion';

const API = 'http://localhost:8080';

const pendingReceipt = {
  id: 7,
  invoiceId: 61,
  filePath: 'students/2/comprobantes_1.pdf',
  status: 'PENDING_REVIEW',
  uploadedAt: '2026-10-02T10:00:00',
  studentName: 'Martina Acosta',
  studentDni: '28016166',
  invoicePeriod: '2026-09',
  invoiceFeeType: 'RECURRING_FEE',
  productName: 'Profesorado de Educación Inicial',
};

function renderPage(route = '/', receipts = [pendingReceipt]) {
  server.use(
    http.get(`${API}/students`, () =>
      HttpResponse.json([{ id: 2, nombre: 'Martina', apellido: 'Acosta', dni: '28016166' }]),
    ),
    http.get(`${API}/payment-receipts`, () => HttpResponse.json(receipts)),
  );
  return renderWithProviders(
    <>
      <Toaster />
      <AdministrativoFacturacion />
    </>,
    { route },
  );
}

async function openReceiptsTab(user) {
  await user.click(screen.getByRole('tab', { name: /Comprobantes/ }));
  return screen.findByText('Martina Acosta');
}

async function openRegisterPaymentModal(user) {
  server.use(
    http.get(`${API}/students/2/billing-overview`, () =>
      HttpResponse.json([
        {
          enrollmentType: 'PROGRAM',
          enrollmentId: 2,
          productName: 'Profesorado de Educación Inicial',
          billingStanding: 'OVERDUE',
          invoices: [
            { id: 61, feeType: 'RECURRING_FEE', period: '2026-09', dueDate: '2026-09-10', remainingBalance: { amount: '52800.00', currency: 'ARS' }, status: 'OVERDUE' },
          ],
        },
      ]),
    ),
  );
  await user.type(screen.getByPlaceholderText('Buscar alumno por nombre o DNI…'), 'Acosta');
  await user.click(await screen.findByRole('button', { name: /Acosta, Martina/ }));
  await user.click(await screen.findByRole('button', { name: /Registrar pago/ }));
  return screen.findByRole('dialog');
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AdministrativoFacturacion — comprobantes', () => {
  it('shows the program next to the receipt', async () => {
    const user = userEvent.setup();
    renderPage();

    await openReceiptsTab(user);

    expect(screen.getByText('Profesorado de Educación Inicial')).toBeInTheDocument();
  });

  it('opens the uploaded receipt through the authenticated download endpoint', async () => {
    const preview = { location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(preview);
    URL.createObjectURL = vi.fn(() => 'blob:comprobante-7');
    server.use(
      http.get(`${API}/payment-receipts/7/file`, () =>
        new HttpResponse('contenido', {
          headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="comprobantes_1.pdf"' },
        }),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    await openReceiptsTab(user);

    await user.click(screen.getByRole('button', { name: /Ver comprobante/ }));

    await vi.waitFor(() => expect(preview.location.href).toBe('blob:comprobante-7'));
    expect(screen.queryByRole('link', { name: /Ver comprobante/ })).not.toBeInTheDocument();
  });

  it('explains why a receipt cannot be approved when its installment was already paid at the counter', async () => {
    server.use(
      http.post(`${API}/payment-receipts/7/approve`, () =>
        HttpResponse.json({ code: 'INVOICE_NOT_PAYABLE' }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    await openReceiptsTab(user);

    await user.click(screen.getByRole('button', { name: /Aprobar/ }));

    expect(
      await screen.findByText('La cuota de este comprobante ya está saldada (por ejemplo, se cobró en mostrador). Rechazalo indicando el motivo.'),
    ).toBeInTheDocument();
  });
});

describe('AdministrativoFacturacion — registrar pago', () => {
  it('does not send an amount above the installment balance', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderPage();
    const dialog = await openRegisterPaymentModal(user);

    const amount = within(dialog).getByRole('spinbutton');
    await user.clear(amount);
    await user.type(amount, '999999');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pago' }));

    expect(await within(dialog).findByText('El monto de Cuota mensual · sep 2026 supera su saldo ($ 52.800,00).')).toBeInTheDocument();
    expect(fetchSpy.mock.calls.some(([url, options]) => String(url).endsWith('/payments') && options?.method === 'POST')).toBe(false);
  });

  it('does not accept negative amounts', async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openRegisterPaymentModal(user);

    const amount = within(dialog).getByRole('spinbutton');
    await user.clear(amount);
    await user.type(amount, '-5');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pago' }));

    expect(await within(dialog).findByText('Los montos no pueden ser negativos.')).toBeInTheDocument();
  });

  it('explains when the backend rejects the amount for exceeding the balance', async () => {
    server.use(
      http.post(`${API}/payments`, () =>
        HttpResponse.json({ code: 'PAYMENT_EXCEEDS_INVOICE_BALANCE' }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openRegisterPaymentModal(user);

    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pago' }));

    expect(
      await within(dialog).findByText('El monto supera el saldo de la cuota. Actualizá la pantalla y revisá los saldos.'),
    ).toBeInTheDocument();
  });
});

const money = (amount) => ({ amount, currency: 'ARS' });

const paidInvoice = {
  id: 70,
  feeType: 'RECURRING_FEE',
  period: '2026-03',
  dueDate: '2026-03-10',
  baseAmount: money('52000.00'),
  amountDue: money('52000.00'),
  remainingBalance: money('0.00'),
  status: 'PAID',
  adjustments: [],
  paymentDate: '2026-03-12T09:05:00',
  payments: [
    {
      paymentId: 812,
      status: 'CONFIRMED',
      method: 'MERCADO_PAGO',
      cardType: 'CREDIT',
      cardInstallments: 3,
      amountApplied: money('52000.00'),
      paymentTotal: money('52000.00'),
      paidAt: '2026-03-12T09:05:00',
      externalReference: 'LOTE-4412',
      notes: 'Pagó en mostrador',
      registeredByName: 'Ana Ruiz',
      receiptId: 55,
    },
  ],
  receipts: [
    {
      id: 55,
      status: 'APPROVED',
      fileName: 'comprobante.pdf',
      uploadedAt: '2026-03-11T16:20:00',
      uploadedByName: 'Lucía Pérez',
      reviewedByName: 'Laura Gómez',
      reviewedAt: '2026-03-12T09:00:00',
    },
  ],
};

const partialInvoice = {
  id: 71,
  feeType: 'RECURRING_FEE',
  period: '2026-04',
  dueDate: '2026-04-10',
  baseAmount: money('52000.00'),
  amountDue: money('52000.00'),
  remainingBalance: money('20000.00'),
  status: 'PARTIALLY_PAID',
  adjustments: [],
  paymentDate: '2026-04-20T09:05:00',
  payments: [
    { paymentId: 1, status: 'CONFIRMED', method: 'CASH', amountApplied: money('12000.00'), paymentTotal: money('30000.00'), paidAt: '2026-04-20T09:05:00', registeredByName: 'Ana Ruiz' },
    { paymentId: 2, status: 'CONFIRMED', method: 'TRANSFER', amountApplied: money('20000.00'), paymentTotal: money('20000.00'), paidAt: '2026-04-15T10:00:00', registeredByName: 'Ana Ruiz' },
  ],
  receipts: [],
};

const untouchedInvoice = {
  id: 72,
  feeType: 'RECURRING_FEE',
  period: '2026-05',
  dueDate: '2026-05-10',
  baseAmount: money('52000.00'),
  amountDue: money('52000.00'),
  remainingBalance: money('52000.00'),
  status: 'PENDING',
  adjustments: [],
  paymentDate: null,
  payments: [],
  receipts: [],
};

const reviewInvoice = {
  ...untouchedInvoice,
  id: 73,
  period: '2026-06',
  dueDate: '2026-06-10',
  receipts: [{ id: 90, status: 'PENDING_REVIEW', fileName: 'x.pdf', uploadedAt: '2026-06-02T10:00:00', uploadedByName: 'Lucía Pérez' }],
};

async function openAccountOf(invoices, user) {
  server.use(
    http.get(`${API}/students/2/billing-overview`, () =>
      HttpResponse.json([
        { enrollmentType: 'PROGRAM', enrollmentId: 2, productName: 'Profesorado de Educación Inicial', billingStanding: 'UP_TO_DATE', invoices },
      ]),
    ),
  );
  await user.type(screen.getByPlaceholderText('Buscar alumno por nombre o DNI…'), 'Acosta');
  await user.click(await screen.findByRole('button', { name: /Acosta, Martina/ }));
  return screen.findByText('Cuota mensual · mar 2026');
}

describe('AdministrativoFacturacion — pestañas', () => {
  it('shows the page title and compact tabs with the pending receipts count', async () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Facturación' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Estado de cuenta' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByRole('tab', { name: 'Comprobantes · 1' })).toBeInTheDocument();
  });

  it('hides the counter when there are no pending receipts', async () => {
    renderPage('/', []);

    expect(await screen.findByRole('tab', { name: 'Comprobantes' })).toBeInTheDocument();
  });

  it('opens the receipts tab when the url carries ?tab=comprobantes', async () => {
    renderPage('/?tab=comprobantes');

    expect(await screen.findByText('Martina Acosta')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Comprobantes/ })).toHaveAttribute('aria-selected', 'true');
  });
});

describe('AdministrativoFacturacion — estado de cuenta', () => {
  it('shows the period inside the concept, one date format, the payment date and the balance under the amount', async () => {
    const user = userEvent.setup();
    renderPage();

    await openAccountOf([paidInvoice, partialInvoice, untouchedInvoice], user);

    expect(screen.getByText('Cuota mensual · abr 2026')).toBeInTheDocument();
    expect(screen.getByText('10/03/2026')).toBeInTheDocument();
    expect(screen.getByText('12/03/2026')).toBeInTheDocument();
    expect(screen.getByText('20/04/2026')).toBeInTheDocument();
    expect(screen.getByText('2 pagos')).toBeInTheDocument();
    expect(screen.getByText('Saldo $ 20.000,00')).toBeInTheDocument();
    expect(screen.getAllByText('$ 52.000,00').length).toBeGreaterThan(0);
  });

  it('expands an installment by click and by keyboard and shows each payment with its details', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([paidInvoice, untouchedInvoice], user);

    const toggle = screen.getByRole('button', { name: 'Ver pagos de Cuota mensual · mar 2026' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('Mercado Pago · Tarjeta de crédito · 3 cuotas')).toBeInTheDocument();
    expect(screen.getByText(/Ref\. LOTE-4412/)).toBeInTheDocument();
    expect(screen.getByText('Registrado por Ana Ruiz')).toBeInTheDocument();
    expect(screen.getByText('Nota: Pagó en mostrador')).toBeInTheDocument();
    expect(screen.getByText('Aprobado')).toBeInTheDocument();

    toggle.focus();
    await user.keyboard('{Enter}');
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('does not make an installment without payments or receipts expandable', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([paidInvoice, untouchedInvoice], user);

    expect(screen.queryByRole('button', { name: 'Ver pagos de Cuota mensual · may 2026' })).not.toBeInTheDocument();
  });

  it('tells how much of a multi-installment payment was applied to this installment', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([paidInvoice, partialInvoice], user);

    await user.click(screen.getByRole('button', { name: 'Ver pagos de Cuota mensual · abr 2026' }));

    expect(screen.getByText(/Aplicado a esta cuota \$ 12\.000,00 de un pago de \$ 30\.000,00/)).toBeInTheDocument();
    expect(screen.getByText('Transferencia bancaria')).toBeInTheDocument();
  });

  it('shows "En revisión" and a link to the receipts tab when the installment has a pending receipt', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([paidInvoice, reviewInvoice], user);

    expect(screen.getByText('En revisión')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ver pagos de Cuota mensual · jun 2026' }));

    expect(screen.getByRole('link', { name: 'Revisar en Comprobantes' })).toHaveAttribute('href', '/?tab=comprobantes');
  });

  it('opens the receipt file from the installment detail without collapsing the row', async () => {
    const preview = { location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(preview);
    URL.createObjectURL = vi.fn(() => 'blob:comprobante-55');
    server.use(
      http.get(`${API}/payment-receipts/55/file`, () =>
        new HttpResponse('contenido', { headers: { 'Content-Type': 'application/pdf' } }),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([paidInvoice], user);
    const toggle = screen.getByRole('button', { name: 'Ver pagos de Cuota mensual · mar 2026' });
    await user.click(toggle);

    await user.click(screen.getByRole('button', { name: 'Ver comprobante' }));

    await vi.waitFor(() => expect(preview.location.href).toBe('blob:comprobante-55'));
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
  });

  it('copies the payment reference', async () => {
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, 'writeText');
    renderPage();
    await openAccountOf([paidInvoice], user);
    await user.click(screen.getByRole('button', { name: 'Ver pagos de Cuota mensual · mar 2026' }));

    await user.click(screen.getByRole('button', { name: 'Copiar referencia' }));

    expect(writeText).toHaveBeenCalledWith('LOTE-4412');
    expect(await screen.findByText('Referencia copiada')).toBeInTheDocument();
  });
});
