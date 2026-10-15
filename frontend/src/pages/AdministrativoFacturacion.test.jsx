import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdministrativoFacturacion } from '@/pages/AdministrativoFacturacion';
import { invoiceFixture, money } from '@/test/fixtures/invoices';

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
  invoiceFeeType: 'MONTHLY_INSTALLMENT',
  productName: 'Profesorado de Educación Inicial',
};

function renderPage(route = '/', receipts = [pendingReceipt]) {
  server.use(
    http.get(`${API}/students`, () =>
      HttpResponse.json([{ id: 2, nombre: 'Martina', apellido: 'Acosta', dni: '28016166' }]),
    ),
    http.get(`${API}/bank-transfer-receipts`, () => HttpResponse.json(receipts)),
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

const DEFAULT_MODAL_INVOICES = [
  invoiceFixture({ id: 61, period: '2026-09', dueDate: '2026-09-10', total: '52800.00', paymentStatus: 'OVERDUE' }),
];

async function openRegisterPaymentModal(user, invoices = DEFAULT_MODAL_INVOICES) {
  server.use(
    http.get(`${API}/students/2/billing-overview`, () =>
      HttpResponse.json([
        {
          enrollmentType: 'PROGRAM',
          enrollmentId: 2,
          productName: 'Profesorado de Educación Inicial',
          paymentStatus: 'OVERDUE',
          invoices,
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
      http.get(`${API}/bank-transfer-receipts/7/file`, () =>
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
      http.post(`${API}/bank-transfer-receipts/7/approve`, () =>
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
  it('shows each installment with its full amount and offers no field to edit it', async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openRegisterPaymentModal(user);

    expect(within(dialog).queryByRole('spinbutton')).not.toBeInTheDocument();
    expect(within(dialog).getByText('Las cuotas se cobran completas: no se admiten pagos parciales.')).toBeInTheDocument();
    expect(within(dialog).getByText('Saldo a cobrar')).toBeInTheDocument();
    expect(within(dialog).getByText('Vence el 10/09/2026')).toBeInTheDocument();
    expect(within(dialog).getAllByText('$ 52.800,00').length).toBeGreaterThan(0);
  });

  it('starts with only the installment closest to its due date selected and sets the receipt-under-review ones apart', async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openRegisterPaymentModal(user, [
      invoiceFixture({ id: 62, period: '2026-10', dueDate: '2026-10-10', paymentStatus: 'PENDING' }),
      invoiceFixture({ id: 61, period: '2026-09', dueDate: '2026-09-10', paymentStatus: 'OVERDUE' }),
      invoiceFixture({
        id: 63,
        period: '2026-11',
        dueDate: '2026-11-10',
        paymentStatus: 'PENDING',
        receipts: [{ id: 5, status: 'PENDING_REVIEW', uploadedAt: '2026-10-01T10:00:00' }],
      }),
    ]);

    expect(within(dialog).getByRole('checkbox', { name: 'Cobrar Cuota mensual · sep 2026' })).toBeChecked();
    expect(within(dialog).getByRole('checkbox', { name: 'Cobrar Cuota mensual · oct 2026' })).not.toBeChecked();
    expect(within(dialog).queryByRole('checkbox', { name: 'Cobrar Cuota mensual · nov 2026' })).not.toBeInTheDocument();
    expect(within(dialog).getByText('En revisión — resolvelas en Comprobantes')).toBeInTheDocument();
    expect(within(dialog).getByText('Vencida')).toBeInTheDocument();
    expect(within(dialog).getByText('Pendiente')).toBeInTheDocument();
  });

  it('registers the payment for the full outstanding amount of the selected installment', async () => {
    let body;
    server.use(
      http.post(`${API}/payments`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openRegisterPaymentModal(user);

    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pago' }));

    await vi.waitFor(() => expect(body).toBeDefined());
    expect(body.allocations).toEqual([{ invoiceId: 61, amountApplied: { amount: '52800.00', currency: 'ARS' } }]);
  });

  it('asks to pick at least one installment instead of sending an empty payment', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderPage();
    const dialog = await openRegisterPaymentModal(user);

    await user.click(within(dialog).getByRole('checkbox', { name: 'Cobrar Cuota mensual · sep 2026' }));
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pago' }));

    expect(await within(dialog).findByText('Elegí al menos una cuota para cobrar.')).toBeInTheDocument();
    expect(fetchSpy.mock.calls.some(([url, options]) => String(url).endsWith('/payments') && options?.method === 'POST')).toBe(false);
  });

  it('explains when the backend rejects a partial payment', async () => {
    server.use(
      http.post(`${API}/payments`, () =>
        HttpResponse.json({ code: 'PARTIAL_PAYMENT_NOT_ALLOWED' }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openRegisterPaymentModal(user);

    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pago' }));

    expect(
      await within(dialog).findByText('Las cuotas se cobran completas: el monto cambió. Actualizá la pantalla y volvé a intentar.'),
    ).toBeInTheDocument();
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

const paidInvoice = invoiceFixture({
  id: 70,
  period: '2026-03',
  dueDate: '2026-03-10',
  total: '52000.00',
  paid: '52000.00',
  paymentStatus: 'PAID',
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
});

const creditedInvoice = invoiceFixture({
  id: 71,
  period: '2026-04',
  dueDate: '2026-04-10',
  total: '52000.00',
  credited: '32000.00',
  payments: [],
  receipts: [],
});

const sharedPaymentInvoice = invoiceFixture({
  id: 74,
  period: '2026-07',
  dueDate: '2026-07-10',
  total: '12000.00',
  paid: '12000.00',
  paymentStatus: 'PAID',
  paymentDate: '2026-07-20T09:05:00',
  payments: [
    { paymentId: 1, status: 'CONFIRMED', method: 'TRANSFER', amountApplied: money('12000.00'), paymentTotal: money('30000.00'), paidAt: '2026-07-20T09:05:00', registeredByName: 'Ana Ruiz' },
  ],
  receipts: [],
});

const untouchedInvoice = invoiceFixture({
  id: 72,
  period: '2026-05',
  dueDate: '2026-05-10',
  total: '52000.00',
  payments: [],
  receipts: [],
});

const reviewInvoice = {
  ...untouchedInvoice,
  id: 73,
  lines: [{ ...untouchedInvoice.lines[0], period: '2026-06' }],
  dueDate: '2026-06-10',
  receipts: [{ id: 90, status: 'PENDING_REVIEW', fileName: 'x.pdf', uploadedAt: '2026-06-02T10:00:00', uploadedByName: 'Lucía Pérez' }],
};

async function openAccountOf(invoices, user) {
  server.use(
    http.get(`${API}/students/2/billing-overview`, () =>
      HttpResponse.json([
        { enrollmentType: 'PROGRAM', enrollmentId: 2, productName: 'Profesorado de Educación Inicial', paymentStatus: 'UP_TO_DATE', invoices },
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
  it('shows the period inside the concept, one date format, the payment date and the outstanding balance under the amount', async () => {
    const user = userEvent.setup();
    renderPage();

    await openAccountOf([paidInvoice, creditedInvoice, untouchedInvoice], user);

    expect(screen.getByText('Cuota mensual · abr 2026')).toBeInTheDocument();
    expect(screen.getByText('10/03/2026')).toBeInTheDocument();
    expect(screen.getByText('12/03/2026')).toBeInTheDocument();
    expect(screen.getByText('10/04/2026')).toBeInTheDocument();
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
    await openAccountOf([paidInvoice, sharedPaymentInvoice], user);

    await user.click(screen.getByRole('button', { name: 'Ver pagos de Cuota mensual · jul 2026' }));

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
      http.get(`${API}/bank-transfer-receipts/55/file`, () =>
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
