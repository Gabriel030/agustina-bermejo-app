import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdministrativoFacturacion } from '@/pages/AdministrativoFacturacion';
import { invoiceFixture, money } from '@/test/fixtures/invoices';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 9, nombre: 'Ana', apellido: 'Pérez', roles: auth.roles }, roles: auth.roles }),
}));

function isoOf(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function todayIso() {
  return isoOf(new Date());
}

function daysAgoIso(days) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return isoOf(date);
}

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

function creditBalance(available = '0.00') {
  return {
    currency: 'ARS',
    unallocatedCredit: money(available),
    reservedByPendingRefunds: money('0.00'),
    availableCredit: money(available),
  };
}

function renderPage(route = '/', receipts = [pendingReceipt]) {
  server.use(
    http.get(`${API}/students`, () =>
      HttpResponse.json([{ id: 2, personId: 20, username: 'macosta', nombre: 'Martina', apellido: 'Acosta', dni: '28016166', email: 'martina.acosta@example.com', emailVerified: true, status: 'ACTIVO', onboardingStatus: 'CONFIRMADO' }]),
    ),
    http.get(`${API}/bank-transfer-receipts`, () => HttpResponse.json(receipts)),
    http.get(`${API}/students/2/payments`, () => HttpResponse.json([])),
    http.get(`${API}/students/2`, () => HttpResponse.json({ id: 2, nombre: 'Martina', apellido: 'Acosta', dni: '28016166' })),
    http.get(`${API}/students/2/credit-notes`, () => HttpResponse.json([])),
    http.get(`${API}/students/2/credit-balance`, () => HttpResponse.json(creditBalance())),
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

async function openCashPaymentModal(user, invoices = DEFAULT_MODAL_INVOICES) {
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
  const entry = await screen.findByRole('button', { name: /Cobrar en efectivo/ });
  await vi.waitFor(() => expect(entry).toBeEnabled());
  await user.click(entry);
  return screen.findByRole('dialog');
}

function previewOf(overrides = {}) {
  return {
    coveredInvoices: [{ invoiceId: 61, concept: 'Cuota mensual · sep 2026', dueDate: '2026-09-10', amount: money('52800.00') }],
    coveredByRemainingCredit: [],
    unallocatedAfterwards: money('0.00'),
    previewFingerprint: 'fp-1',
    ...overrides,
  };
}

async function fillAmountAndReview(user, dialog, amount = '52800') {
  await user.type(within(dialog).getByLabelText('Monto'), amount);
  await user.click(within(dialog).getByRole('button', { name: 'Revisar cobro' }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AdministrativoFacturacion — comprobantes', () => {
  it('offers the billing runs screen from the page header', async () => {
    renderPage();

    expect(await screen.findByRole('button', { name: 'Corridas' })).toBeInTheDocument();
  });

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

describe('AdministrativoFacturacion — cobro en efectivo', () => {
  beforeEach(() => {
    auth.roles = ['ADMINISTRATIVO'];
  });

  it('shows a single cash payment entry point instead of one per enrollment and no invoice selectors', async () => {
    const user = userEvent.setup();
    renderPage();
    await openCashPaymentModal(user);

    expect(screen.getAllByRole('button', { name: /Cobrar en efectivo/, hidden: true })).toHaveLength(1);
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.queryByText('Registrar pago')).not.toBeInTheDocument();
  });

  it('asks for amount, date defaulting to today and an optional receipt, with no invoice or method selectors', async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    expect(within(dialog).getByLabelText('Monto')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Fecha del cobro')).toHaveValue(todayIso());
    expect(within(dialog).getByLabelText('N° de recibo (opcional)')).toHaveAttribute('maxlength', '100');
    expect(within(dialog).queryByLabelText('Motivo de la fecha retroactiva')).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('combobox')).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('previews the covered invoices, then registers with the fingerprint and an idempotency key', async () => {
    let previewBody;
    let registerBody;
    let registerKey;
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, async ({ request }) => {
        previewBody = await request.json();
        return HttpResponse.json(previewOf({ unallocatedAfterwards: money('1200.00') }));
      }),
      http.post(`${API}/students/2/cash-payments`, async ({ request }) => {
        registerBody = await request.json();
        registerKey = request.headers.get('Idempotency-Key');
        return HttpResponse.json({ paymentId: 9 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    await fillAmountAndReview(user, dialog, '54000');

    expect(await within(dialog).findByText('Cuota mensual · sep 2026')).toBeInTheDocument();
    expect(within(dialog).getByText('Queda a favor')).toBeInTheDocument();
    expect(previewBody).toEqual({ amount: { amount: '54000.00', currency: 'ARS' } });
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar cobro' }));

    await vi.waitFor(() => expect(registerBody).toBeDefined());
    expect(registerBody).toEqual({
      amount: { amount: '54000.00', currency: 'ARS' },
      paidOn: todayIso(),
      previewFingerprint: 'fp-1',
    });
    expect(registerKey).toMatch(/\S{8,}/);
    expect(await screen.findByText('Cobro registrado por $ 54.000,00')).toBeInTheDocument();
  });

  it('shows the invoices paid by the remaining credit apart from the ones paid by the cash', async () => {
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () =>
        HttpResponse.json(
          previewOf({
            coveredByRemainingCredit: [
              { invoiceId: 77, concept: 'Cuota online · oct 2026', dueDate: '2026-10-10', amount: money('30000.00') },
            ],
          }),
        ),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    await fillAmountAndReview(user, dialog, '82800');

    expect(await within(dialog).findByText('Se imputa a')).toBeInTheDocument();
    expect(within(dialog).getByText(/También se paga con el saldo restante/)).toBeInTheDocument();
    expect(within(dialog).getByText('Cuota online · oct 2026')).toBeInTheDocument();
  });

  it('asks for the preview again after an outdated-preview 409, keeps what was typed and uses a new idempotency key', async () => {
    const keys = [];
    const fingerprints = [];
    let previews = 0;
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => {
        previews += 1;
        return HttpResponse.json(previewOf({ previewFingerprint: `fp-${previews}` }));
      }),
      http.post(`${API}/students/2/cash-payments`, async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        fingerprints.push((await request.json()).previewFingerprint);
        if (keys.length === 1) return HttpResponse.json({ code: 'CASH_PAYMENT_PREVIEW_OUTDATED' }, { status: 409 });
        return HttpResponse.json({ paymentId: 9 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));

    expect(await within(dialog).findByText('La cuenta cambió, revisá el detalle actualizado.')).toBeInTheDocument();
    await vi.waitFor(() => expect(previews).toBe(2));
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));

    await vi.waitFor(() => expect(keys).toHaveLength(2));
    expect(fingerprints).toEqual(['fp-1', 'fp-2']);
    expect(keys[0]).not.toBe(keys[1]);
  });

  it('also refreshes the preview on a 409 without a known code', async () => {
    let previews = 0;
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => {
        previews += 1;
        return HttpResponse.json(previewOf());
      }),
      http.post(`${API}/students/2/cash-payments`, () => HttpResponse.json({}, { status: 409 })),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));

    await vi.waitFor(() => expect(previews).toBe(2));
  });

  it('reuses the idempotency key when the same payment is retried after a server failure', async () => {
    const keys = [];
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => HttpResponse.json(previewOf())),
      http.post(`${API}/students/2/cash-payments`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        if (keys.length === 1) return HttpResponse.json({}, { status: 500 });
        return HttpResponse.json({ paymentId: 9 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));
    await vi.waitFor(() => expect(keys).toHaveLength(1));
    await user.click(await within(dialog).findByRole('button', { name: 'Reintentar cobro' }));

    await vi.waitFor(() => expect(keys).toHaveLength(2));
    expect(keys[1]).toBe(keys[0]);
  });

  it('translates the backend codes into Spanish messages', async () => {
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => HttpResponse.json(previewOf())),
      http.post(`${API}/students/2/cash-payments`, () =>
        HttpResponse.json({ code: 'CASH_PAYMENT_RECEIPT_NUMBER_INVALID' }, { status: 400 }),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));

    expect(await within(dialog).findByText('El número de recibo no puede superar los 100 caracteres.')).toBeInTheDocument();
  });

  it('refuses an amount that is not positive without calling the backend', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    await fillAmountAndReview(user, dialog, '0');

    expect(await within(dialog).findByText('El monto debe ser mayor a 0.')).toBeInTheDocument();
    expect(fetchSpy.mock.calls.some(([url]) => String(url).includes('/cash-payments'))).toBe(false);
  });

  it('stops the administrative from dating the payment more than five days back and asks nothing about a reason', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    fireEvent.change(within(dialog).getByLabelText('Fecha del cobro'), { target: { value: daysAgoIso(6) } });
    await fillAmountAndReview(user, dialog);

    expect(await within(dialog).findByText(/No podés cobrar con una fecha de más de 5 días atrás/)).toBeInTheDocument();
    expect(within(dialog).queryByLabelText('Motivo de la fecha retroactiva')).not.toBeInTheDocument();
    expect(fetchSpy.mock.calls.some(([url]) => String(url).includes('/cash-payments'))).toBe(false);
  });

  it('reads an es-AR amount, shows it formatted and sends it exact', async () => {
    let previewBody;
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, async ({ request }) => {
        previewBody = await request.json();
        return HttpResponse.json(previewOf());
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    await user.type(within(dialog).getByLabelText('Monto'), '1.500,50');
    await user.tab();
    expect(within(dialog).getByLabelText('Monto')).toHaveValue('1.500,50');
    await user.click(within(dialog).getByRole('button', { name: 'Revisar cobro' }));

    await vi.waitFor(() => expect(previewBody).toBeDefined());
    expect(previewBody).toEqual({ amount: { amount: '1500.50', currency: 'ARS' } });
  });

  it.each([
    ['1,234', 'El monto admite hasta 2 decimales.'],
    ['10.000.000.000', 'El monto supera el máximo permitido (9.999.999.999,99).'],
    ['', 'Ingresá el monto a cobrar.'],
    ['abc', 'El monto no es válido. Ejemplo: 1.500,50'],
  ])('explains an amount of "%s" with its own message', async (typed, message) => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    if (typed) await user.type(within(dialog).getByLabelText('Monto'), typed);
    await user.click(within(dialog).getByRole('button', { name: 'Revisar cobro' }));

    expect(await within(dialog).findByText(message)).toBeInTheDocument();
  });

  it('fills the amount with the whole debt in es-AR format', async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    await user.click(within(dialog).getByRole('button', { name: 'Usar total' }));

    expect(within(dialog).getByLabelText('Monto')).toHaveValue('52.800,00');
  });

  it('after an unknown outcome asks to check the account, keeps the same key on retry and blocks going back', async () => {
    const keys = [];
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => HttpResponse.json(previewOf())),
      http.post(`${API}/students/2/cash-payments`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        if (keys.length === 1) return HttpResponse.json({}, { status: 500 });
        return HttpResponse.json({ paymentId: 9 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));

    expect(await within(dialog).findByText(/Revisá el estado de cuenta del alumno antes de volver a cobrar/)).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Volver' })).not.toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Cerrar y revisar estado de cuenta' })).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Reintentar cobro' }));
    await vi.waitFor(() => expect(keys).toHaveLength(2));
    expect(keys[1]).toBe(keys[0]);
  });

  it('closes and reloads the student account after an unknown outcome', async () => {
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => HttpResponse.json(previewOf())),
      http.post(`${API}/students/2/cash-payments`, () => HttpResponse.json({}, { status: 500 })),
    );
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const overviewCalls = () => fetchSpy.mock.calls.filter(([url]) => String(url).includes('/billing-overview')).length;
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    const before = overviewCalls();
    await fillAmountAndReview(user, dialog);
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));
    await user.click(await within(dialog).findByRole('button', { name: 'Cerrar y revisar estado de cuenta' }));

    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await vi.waitFor(() => expect(overviewCalls()).toBeGreaterThan(before));
  });

  it('drops the student when the backend says it no longer exists', async () => {
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => HttpResponse.json({ code: 'STUDENT_NOT_FOUND' }, { status: 404 })),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);

    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await screen.findByPlaceholderText('Buscar alumno por nombre o DNI…')).toBeInTheDocument();
  });

  it('warns to check the account when the key was already used with other data, and refreshes the preview', async () => {
    let previews = 0;
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => {
        previews += 1;
        return HttpResponse.json(previewOf());
      }),
      http.post(`${API}/students/2/cash-payments`, () => HttpResponse.json({ code: 'IDEMPOTENCY_KEY_CONFLICT' }, { status: 409 })),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));

    expect(await within(dialog).findByText(/Este cobro ya se había registrado con otros datos/)).toBeInTheDocument();
    await vi.waitFor(() => expect(previews).toBe(2));
  });

  it('reformats an unformatted amount when leaving the field', async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    await user.type(within(dialog).getByLabelText('Monto'), '1500,5');
    await user.tab();

    expect(within(dialog).getByLabelText('Monto')).toHaveValue('1.500,50');
  });

  it('rejects an ambiguous amount with a leading zero', async () => {
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);

    await user.type(within(dialog).getByLabelText('Monto'), '0.500');
    await user.click(within(dialog).getByRole('button', { name: 'Revisar cobro' }));

    expect(await within(dialog).findByText(/Escribí el monto sin ceros a la izquierda/)).toBeInTheDocument();
  });

  async function failConfirmWithNetworkError(user) {
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => HttpResponse.json(previewOf())),
      http.post(`${API}/students/2/cash-payments`, () => HttpResponse.error()),
    );
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const overviewCalls = () => fetchSpy.mock.calls.filter(([url]) => String(url).includes('/billing-overview')).length;
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));
    await within(dialog).findByText(/Revisá el estado de cuenta del alumno antes de volver a cobrar/);
    return { dialog, overviewCalls };
  }

  it('reloads the student account when the dialog is closed with the X after an unknown outcome', async () => {
    const user = userEvent.setup();
    renderPage();
    const { dialog, overviewCalls } = await failConfirmWithNetworkError(user);
    const before = overviewCalls();

    await user.click(within(dialog).getByRole('button', { name: 'Close' }));

    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await vi.waitFor(() => expect(overviewCalls()).toBeGreaterThan(before));
  });

  it('reloads the student account when the dialog is closed with Escape after an unknown outcome', async () => {
    const user = userEvent.setup();
    renderPage();
    const { overviewCalls } = await failConfirmWithNetworkError(user);
    const before = overviewCalls();

    await user.keyboard('{Escape}');

    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await vi.waitFor(() => expect(overviewCalls()).toBeGreaterThan(before));
  });

  it('sends the payment once when the confirm button is tapped twice', async () => {
    let registers = 0;
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => HttpResponse.json(previewOf())),
      http.post(`${API}/students/2/cash-payments`, async () => {
        registers += 1;
        await new Promise((resolve) => setTimeout(resolve, 150));
        return HttpResponse.json({ paymentId: 9 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await fillAmountAndReview(user, dialog);
    const confirm = await within(dialog).findByRole('button', { name: 'Confirmar cobro' });

    act(() => {
      confirm.click();
      confirm.click();
    });

    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(registers).toBe(1);
  });

  it('refreshes the student account after a network error without closing the dialog', async () => {
    const user = userEvent.setup();
    renderPage();
    const { overviewCalls } = await failConfirmWithNetworkError(user);

    await vi.waitFor(() => expect(overviewCalls()).toBeGreaterThan(1));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('blocks charging while the student account reloads after an unknown outcome', async () => {
    const user = userEvent.setup();
    renderPage();
    const { dialog } = await failConfirmWithNetworkError(user);
    server.use(
      http.get(`${API}/students/2/billing-overview`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 600));
        return HttpResponse.json([]);
      }),
    );
    await user.click(within(dialog).getByRole('button', { name: 'Cerrar y revisar estado de cuenta' }));

    const entry = await screen.findByRole('button', { name: /Cobrar en efectivo/ });
    expect(entry).toBeDisabled();
    await vi.waitFor(() => expect(entry).toBeEnabled(), { timeout: 3000 });
  });

  it('blocks charging with a visible retry when the student account cannot be reloaded', async () => {
    const user = userEvent.setup();
    renderPage();
    const { dialog } = await failConfirmWithNetworkError(user);
    server.use(http.get(`${API}/students/2/billing-overview`, () => HttpResponse.json({}, { status: 500 })));
    await user.click(within(dialog).getByRole('button', { name: 'Cerrar y revisar estado de cuenta' }));

    expect(await screen.findByText(/No pudimos actualizar la deuda del alumno/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Cobrar en efectivo/ })).toBeDisabled();
    expect(screen.queryByText('Deuda')).not.toBeInTheDocument();
  });

  it('asks for the preview once when the review button is tapped twice', async () => {
    let previews = 0;
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, async () => {
        previews += 1;
        await new Promise((resolve) => setTimeout(resolve, 150));
        return HttpResponse.json(previewOf());
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    await user.type(within(dialog).getByLabelText('Monto'), '52800');
    const review = within(dialog).getByRole('button', { name: 'Revisar cobro' });

    act(() => {
      review.click();
      review.click();
    });

    await within(dialog).findByText('Cuota mensual · sep 2026');
    expect(previews).toBe(1);
  });

  it('lets the director backdate only with a reason, shown just when the date needs it', async () => {
    auth.roles = ['DIRECTOR'];
    let registerBody;
    server.use(
      http.post(`${API}/students/2/cash-payments/preview`, () => HttpResponse.json(previewOf())),
      http.post(`${API}/students/2/cash-payments`, async ({ request }) => {
        registerBody = await request.json();
        return HttpResponse.json({ paymentId: 9 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCashPaymentModal(user);
    expect(within(dialog).queryByLabelText('Motivo de la fecha retroactiva')).not.toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText('Fecha del cobro'), { target: { value: daysAgoIso(6) } });
    await fillAmountAndReview(user, dialog);
    expect(await within(dialog).findByText('Indicá el motivo de la fecha retroactiva.')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Motivo de la fecha retroactiva'), 'Recibo extraviado');
    await user.click(within(dialog).getByRole('button', { name: 'Revisar cobro' }));
    await user.click(await within(dialog).findByRole('button', { name: 'Confirmar cobro' }));

    await vi.waitFor(() => expect(registerBody).toBeDefined());
    expect(registerBody.backdateReason).toBe('Recibo extraviado');
    expect(registerBody.paidOn).toBe(daysAgoIso(6));
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
    expect(await screen.findByRole('tab', { name: 'Comprobantes 1' })).toBeInTheDocument();
  });

  it('hides the counter when there are no pending receipts', async () => {
    renderPage('/', []);

    expect(await screen.findByRole('tab', { name: 'Comprobantes' })).toBeInTheDocument();
  });

  it('opens the receipts tab when the url carries ?tab=comprobantes', async () => {
    renderPage('/?alumno=2&tab=comprobantes');

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

    const toggle = screen.getByRole('button', { name: 'Ver detalle de Cuota mensual · mar 2026' });
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

  it('shows the student payments card below the enrollments and links to it from an installment with payments', async () => {
    const user = userEvent.setup();
    renderPage();
    server.use(
      http.get(`${API}/students/2/payments`, () =>
        HttpResponse.json([
          {
            id: 502,
            amount: money('52000.00'),
            approvedAmount: money('52000.00'),
            refundedAmount: money('0.00'),
            status: 'APPROVED',
            gateway: 'MANUAL',
            methodType: 'BANK_TRANSFER',
            paidOn: '2026-03-10',
            registeredAt: '2026-03-10T10:00:00',
            receiptNumber: null,
            chargeback: null,
            pendingRefunds: { count: 0, amount: money('0.00'), parts: [] },
            actions: null,
          },
        ]),
      ),
    );
    await openAccountOf([paidInvoice, untouchedInvoice], user);

    expect(await screen.findByRole('heading', { name: 'Pagos del alumno' })).toBeInTheDocument();
    expect(await screen.findByText('Pago #502')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ver detalle de Cuota mensual · mar 2026' }));
    expect(screen.getByRole('button', { name: 'Ver en Pagos del alumno' })).toBeInTheDocument();
  });

  it('lets expand an installment without payments or receipts to reach its actions', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([paidInvoice, untouchedInvoice], user);

    await user.click(screen.getByRole('button', { name: 'Ver detalle de Cuota mensual · may 2026' }));

    expect(screen.getByRole('button', { name: 'Cancelar cuota' })).toBeEnabled();
  });

  it('tells how much of a multi-installment payment was applied to this installment', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([paidInvoice, sharedPaymentInvoice], user);

    await user.click(screen.getByRole('button', { name: 'Ver detalle de Cuota mensual · jul 2026' }));

    expect(screen.getByText(/Aplicado a esta cuota \$ 12\.000,00 de un pago de \$ 30\.000,00/)).toBeInTheDocument();
    expect(screen.getByText('Transferencia bancaria')).toBeInTheDocument();
  });

  it('shows "En revisión" and a link to the receipts tab when the installment has a pending receipt', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([paidInvoice, reviewInvoice], user);

    expect(screen.getByText('En revisión')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ver detalle de Cuota mensual · jun 2026' }));

    expect(screen.getByRole('link', { name: 'Revisar en Comprobantes' })).toHaveAttribute('href', '/?alumno=2&tab=comprobantes');
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
    const toggle = screen.getByRole('button', { name: 'Ver detalle de Cuota mensual · mar 2026' });
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
    await user.click(screen.getByRole('button', { name: 'Ver detalle de Cuota mensual · mar 2026' }));

    await user.click(screen.getByRole('button', { name: 'Copiar referencia' }));

    expect(writeText).toHaveBeenCalledWith('LOTE-4412');
    expect(await screen.findByText('Referencia copiada')).toBeInTheDocument();
  });
});

describe('AdministrativoFacturacion — carga de comprobante por el alumno', () => {
  beforeEach(() => {
    auth.roles = ['ADMINISTRATIVO'];
  });

  const debtInvoice = invoiceFixture({ id: 61, period: '2026-03', dueDate: '2026-03-10', total: '52800.00', paymentStatus: 'OVERDUE' });

  it('offers an outline upload button beside the single gradient cash button', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([debtInvoice], user);

    const upload = await screen.findByRole('button', { name: 'Cargar comprobante' });
    await vi.waitFor(() => expect(upload).toBeEnabled());
    expect(upload.className).toContain('bg-background');
    expect(upload.className).not.toContain('from-brand');
    expect(screen.getByRole('button', { name: /Cobrar en efectivo/ }).className).toContain('from-brand');
  });

  it('disables the upload button with an explanation when the student has no pending installments', async () => {
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([invoiceFixture({ id: 61, period: '2026-03', paymentStatus: 'PAID', paid: '48000.00' })], user);

    const upload = await screen.findByRole('button', { name: 'Cargar comprobante' });

    expect(upload).toBeDisabled();
    await user.hover(upload.parentElement);
    expect((await screen.findAllByText('No tiene cuotas pendientes.')).length).toBeGreaterThan(0);
  });

  it('hides both collection buttons from roles that cannot collect', async () => {
    auth.roles = ['PRECEPTOR'];
    const user = userEvent.setup();
    renderPage();
    await openAccountOf([debtInvoice], user);

    expect(screen.queryByRole('button', { name: 'Cargar comprobante' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cobrar en efectivo/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cambiar alumno' })).toBeInTheDocument();
  });

  it('shows where each receipt came from in the review queue', async () => {
    const user = userEvent.setup();
    renderPage('/', [
      { ...pendingReceipt, id: 1, uploadedBy: { personId: 40, name: 'Lucía Gómez', role: 'ALUMNO', internal: false } },
      { ...pendingReceipt, id: 2, studentName: 'Tomás Peralta', uploadedBy: { personId: 8, name: 'Marcos Ledesma', role: 'ADMINISTRATIVO', internal: true } },
      { ...pendingReceipt, id: 3, studentName: 'Julia Ríos', uploadedBy: { personId: 9, name: 'Ana Pérez', role: 'ADMINISTRATIVO', internal: true } },
    ]);
    await user.click(await screen.findByRole('tab', { name: /Comprobantes/ }));

    expect(await screen.findByText('Subido por el alumno')).toBeInTheDocument();
    expect(screen.getByText('Carga interna · Marcos Ledesma')).toBeInTheDocument();
    expect(screen.getByText('Carga interna · vos')).toBeInTheDocument();
    expect(screen.getAllByText(/Lo cargaste vos/)).toHaveLength(1);
  });
});
