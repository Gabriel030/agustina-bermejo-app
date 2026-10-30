import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdministrativoFacturacion } from '@/pages/AdministrativoFacturacion';
import {
  creditBalanceFixture,
  creditNoteFixture,
  invoiceFixture,
  money,
  pendingAuthorizationFixture,
} from '@/test/fixtures/invoices';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'], id: 10 }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: auth.id, nombre: 'Ana', apellido: 'Pérez', roles: auth.roles }, roles: auth.roles }),
}));

const MAY = 'Ver detalle de Cuota mensual · may 2026';

function previewOf(overrides = {}) {
  return {
    allowed: true,
    blockedBy: [],
    creditNoteAmount: money('48000.00'),
    releasesChargeKey: true,
    executesImmediately: false,
    lateFeesToAnnul: [],
    discountConsumptionReleased: [],
    enrollmentFee: null,
    ...overrides,
  };
}

function noteOf(overrides = {}) {
  return creditNoteFixture({ id: 5, creditNoteNumber: 'NC-0000140', ...overrides });
}

let invoices;
let overviewCalls;
let previewBodies;
let cancelBodies;

function mockServer({ preview = previewOf(), cancel, notes = [] } = {}) {
  overviewCalls = 0;
  previewBodies = [];
  cancelBodies = [];
  server.use(
    http.get(`${API}/students`, () => HttpResponse.json([{ id: 2, personId: 20, username: 'macosta', nombre: 'Martina', apellido: 'Acosta', dni: '28016166', email: 'martina.acosta@example.com', emailVerified: true, status: 'ACTIVO', onboardingStatus: 'CONFIRMADO' }])),
    http.get(`${API}/students/2`, () => HttpResponse.json({ id: 2, nombre: 'Martina', apellido: 'Acosta', dni: '28016166' })),
    http.get(`${API}/students/2/billing-overview`, () => {
      overviewCalls += 1;
      return HttpResponse.json([
        { enrollmentType: 'PROGRAM', enrollmentId: 2, productName: 'Profesorado de Educación Inicial', paymentStatus: 'OVERDUE', invoices },
      ]);
    }),
    http.get(`${API}/students/2/payments`, () => HttpResponse.json([])),
    http.get(`${API}/students/2/credit-notes`, () => HttpResponse.json(notes)),
    http.get(`${API}/students/2/credit-balance`, () => HttpResponse.json(creditBalanceFixture())),
    http.get(`${API}/bank-transfer-receipts`, () => HttpResponse.json([])),
    http.post(`${API}/invoices/:id/cancellations/preview`, async ({ request }) => {
      previewBodies.push(await request.json());
      return HttpResponse.json(typeof preview === 'function' ? preview(previewBodies.at(-1)) : preview);
    }),
    http.post(`${API}/invoices/:id/cancellations`, async ({ request, params }) => {
      cancelBodies.push({ id: params.id, body: await request.json() });
      return cancel ? cancel() : HttpResponse.json(noteOf(), { status: 201 });
    }),
  );
}

function renderPage() {
  return renderWithProviders(
    <>
      <Toaster />
      <AdministrativoFacturacion />
    </>,
    { route: '/?alumno=2' },
  );
}

async function openInvoice(user, label) {
  await user.click(await screen.findByRole('button', { name: label }));
}

async function openCancelDialog(user, { label = MAY, button = 'Cancelar cuota' } = {}) {
  await openInvoice(user, label);
  await user.click(await screen.findByRole('button', { name: button }));
  return screen.findByRole('dialog');
}

async function writeJustification(user, dialog, text = 'Se emitió con el importe equivocado.') {
  await user.type(within(dialog).getByLabelText(/Justificación/), text);
}

beforeEach(() => {
  auth.roles = ['ADMINISTRATIVO'];
  auth.id = 10;
  invoices = [
    invoiceFixture({ id: 61, period: '2026-05', dueDate: '2026-05-10', total: '48000.00', paymentStatus: 'OVERDUE' }),
    invoiceFixture({ id: 62, period: '2026-06', dueDate: '2026-06-10', total: '48000.00', paymentStatus: 'PENDING' }),
  ];
});

describe('Facturación — acciones sobre la cuota por rol y estado', () => {
  it.each([['ADMINISTRATIVO'], ['DIRECTOR']])('offers the credit note and the cancellation to the %s', async (role) => {
    auth.roles = [role];
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await openInvoice(user, MAY);

    expect(await screen.findByRole('button', { name: 'Emitir nota de crédito' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Cancelar cuota' })).toBeEnabled();
  });

  it('lets expand an installment that has no payments, notes or receipts', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();

    const toggle = await screen.findByRole('button', { name: MAY });
    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
  });

  it('on a paid installment offers only the credit note and explains why it is not cancelled', async () => {
    invoices = [invoiceFixture({ id: 61, period: '2026-05', paymentStatus: 'PAID', paid: '48000.00' })];
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await openInvoice(user, MAY);

    expect(await screen.findByRole('button', { name: 'Emitir nota de crédito' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Cancelar cuota' })).not.toBeInTheDocument();
    expect(screen.getByText(/Una cuota con pagos no se cancela/)).toBeInTheDocument();
  });

  it('on the late fee row offers "Anular recargo" and no credit note', async () => {
    invoices = [invoiceFixture({ id: 63, period: '2026-05', invoiceType: 'LATE_FEE', paymentStatus: 'OVERDUE', total: '2400.00' })];
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await openInvoice(user, 'Ver detalle de Recargo por mora · may 2026');

    expect(await screen.findByRole('button', { name: 'Anular recargo' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Emitir nota de crédito' })).not.toBeInTheDocument();
  });

  it('disables both actions while a receipt is under review', async () => {
    invoices = [invoiceFixture({ id: 61, period: '2026-05', paymentStatus: 'PAYMENT_UNDER_REVIEW' })];
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await openInvoice(user, MAY);

    expect(await screen.findByRole('button', { name: 'Emitir nota de crédito' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancelar cuota' })).toBeDisabled();
  });

  it('shows the cancelled installment as without effect and offers nothing else', async () => {
    invoices = [invoiceFixture({ id: 61, period: '2026-05', paymentStatus: 'CANCELLED' })];
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await openInvoice(user, MAY);

    expect(await screen.findByText(/La cuota quedó sin efecto/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancelar cuota' })).not.toBeInTheDocument();
  });
});

describe('Facturación — diálogo de cancelación', () => {
  it('previews the effects with the default reason before asking anything else', async () => {
    mockServer({
      preview: previewOf({
        lateFeesToAnnul: [{ debitNoteId: 9, invoiceNumber: 'RC-0000045', amountToAnnul: money('2400.00'), paidAmount: money('0.00') }],
        discountConsumptionReleased: [{ studentDiscountId: 3, reactivatesDiscount: true }],
      }),
    });
    const user = userEvent.setup();
    renderPage();

    const dialog = await openCancelDialog(user);

    expect(await within(dialog).findByText(/Se emite una nota de crédito por \$ 48\.000,00/)).toBeInTheDocument();
    expect(within(dialog).getByText('La cuota se puede volver a emitir.')).toBeInTheDocument();
    expect(within(dialog).getByText(/Se anula el recargo RC-0000045 por \$ 2\.400,00/)).toBeInTheDocument();
    expect(within(dialog).getByText(/Se devuelve el consumo del descuento y vuelve a estar disponible/)).toBeInTheDocument();
    expect(previewBodies[0]).toMatchObject({ reason: 'BILLING_ERROR' });
  });

  it('asks the server again when the reason changes and updates the effects', async () => {
    mockServer({
      preview: (body) => previewOf({ releasesChargeKey: body.reason === 'BILLING_ERROR' }),
    });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await within(dialog).findByText('La cuota se puede volver a emitir.');

    await user.click(within(dialog).getByRole('radio', { name: /Cancelación/ }));

    expect(await within(dialog).findByText('La cuota no se vuelve a emitir.')).toBeInTheDocument();
    expect(previewBodies.at(-1)).toMatchObject({ reason: 'CANCELLATION' });
    expect(within(dialog).getByRole('radio', { name: /Cancelación/ })).toHaveAttribute('aria-checked', 'true');
  });

  it('moves between the two reasons with the arrow keys', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);

    const first = within(dialog).getByRole('radio', { name: /Error de facturación/ });
    first.focus();
    await user.keyboard('{ArrowRight}');

    expect(within(dialog).getByRole('radio', { name: /Cancelación/ })).toHaveAttribute('aria-checked', 'true');
  });

  it('warns about the enrollment discount only for a billing error and only when the preview says so', async () => {
    mockServer({
      preview: previewOf({ enrollmentFee: { carriesDiscount: true, otherLiveEnrollmentFeeInSameLectiveYear: true, reissueWouldLoseDiscount: true } }),
    });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);

    expect(await within(dialog).findByText(/Antes de reemitir la matrícula/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('radio', { name: /Cancelación/ }));

    expect(within(dialog).queryByText(/Antes de reemitir la matrícula/)).not.toBeInTheDocument();
  });

  it('does not allow confirming without a justification', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await within(dialog).findByText(/Se emite una nota de crédito/);

    expect(within(dialog).getByRole('button', { name: 'Enviar a autorización' })).toBeDisabled();

    await writeJustification(user, dialog);

    await vi.waitFor(() => expect(within(dialog).getByRole('button', { name: 'Enviar a autorización' })).toBeEnabled());
  });

  it('as director applies right away (201), sends reason and justification and tells what was released', async () => {
    auth.roles = ['DIRECTOR'];
    mockServer({ preview: previewOf({ executesImmediately: true, discountConsumptionReleased: [{ studentDiscountId: 3, reactivatesDiscount: false }] }) });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await within(dialog).findByText(/Como director, se aplica de inmediato/);
    await writeJustification(user, dialog);
    const confirm = within(dialog).getByRole('button', { name: 'Cancelar cuota' });
    await vi.waitFor(() => expect(confirm).toBeEnabled());

    await user.click(confirm);

    expect(await screen.findByText('Cuota sin efecto. Ya se puede volver a emitir.')).toBeInTheDocument();
    expect(screen.getByText(/Nota de crédito NC-0000140\. Se devolvió el consumo del descuento\./)).toBeInTheDocument();
    expect(cancelBodies).toEqual([{ id: '61', body: { reason: 'BILLING_ERROR', justification: 'Se emitió con el importe equivocado.' } }]);
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('for a cancellation reason tells that the installment is not issued again', async () => {
    auth.roles = ['DIRECTOR'];
    mockServer({ preview: previewOf({ executesImmediately: true, releasesChargeKey: false }) });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await user.click(within(dialog).getByRole('radio', { name: /Cancelación/ }));
    await within(dialog).findByText('La cuota no se vuelve a emitir.');
    await writeJustification(user, dialog);
    const confirm = within(dialog).getByRole('button', { name: 'Cancelar cuota' });
    await vi.waitFor(() => expect(confirm).toBeEnabled());

    await user.click(confirm);

    expect(await screen.findByText('Cuota sin efecto. Esta cuota no se vuelve a emitir.')).toBeInTheDocument();
    expect(cancelBodies[0].body.reason).toBe('CANCELLATION');
  });

  it('as administrative sends the request to authorization (202) in warning tone and shows the pending state', async () => {
    mockServer({
      cancel: () => {
        invoices = [{ ...invoices[0], pendingAuthorizations: [pendingAuthorizationFixture()] }, invoices[1]];
        return HttpResponse.json({ authorizationRequestId: 90 }, { status: 202 });
      },
    });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await within(dialog).findByText(/Necesita autorización de la dirección/);
    await writeJustification(user, dialog);
    const confirm = within(dialog).getByRole('button', { name: 'Enviar a autorización' });
    await vi.waitFor(() => expect(confirm).toBeEnabled());

    await user.click(confirm);

    expect(await screen.findByText('Pedido enviado. Todavía no se aplicó.')).toBeInTheDocument();
    expect(screen.getByText('La cuota sigue vigente hasta que lo autorice la dirección.')).toBeInTheDocument();
    expect(await screen.findByText('Autorización pendiente')).toBeInTheDocument();
    expect(screen.getByRole('status', { name: '' })).toHaveTextContent('Pedido enviado, todavía no se aplicó');
  });

  it('on a late fee row uses the fixed late fee annulment reason and no reason picker', async () => {
    invoices = [invoiceFixture({ id: 63, period: '2026-05', invoiceType: 'LATE_FEE', paymentStatus: 'OVERDUE', total: '2400.00' })];
    mockServer({ preview: previewOf({ creditNoteAmount: money('2400.00'), releasesChargeKey: false }) });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user, { label: 'Ver detalle de Recargo por mora · may 2026', button: 'Anular recargo' });

    expect(within(dialog).queryByRole('radiogroup')).not.toBeInTheDocument();
    expect(await within(dialog).findByText('El recargo deja de contar en la deuda del alumno.')).toBeInTheDocument();
    expect(previewBodies[0]).toMatchObject({ reason: 'LATE_FEE_ANNULMENT' });
  });

  it('blocks the confirmation and links to Comprobantes when the preview reports a receipt under review', async () => {
    mockServer({
      preview: previewOf({ allowed: false, blockedBy: [{ code: 'PENDING_BANK_TRANSFER_ON_INVOICE', message: 'x' }] }),
    });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await writeJustification(user, dialog);

    expect(await within(dialog).findByText(/comprobante de transferencia esperando revisión/)).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Revisar en Comprobantes' })).toHaveAttribute('href', '/?alumno=2&tab=comprobantes');
    expect(within(dialog).getByRole('button', { name: 'Enviar a autorización' })).toBeDisabled();
  });

  it('keeps the written justification and explains a receipt conflict returned by the server', async () => {
    mockServer({ cancel: () => HttpResponse.json({ code: 'PENDING_BANK_TRANSFER_ON_INVOICE', violations: [] }, { status: 409 }) });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await writeJustification(user, dialog, 'Motivo escrito');
    const confirm = within(dialog).getByRole('button', { name: 'Enviar a autorización' });
    await vi.waitFor(() => expect(confirm).toBeEnabled());

    await user.click(confirm);

    expect(await within(dialog).findByText(/No se hizo ningún cambio\. Resolvé el comprobante/)).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Justificación/)).toHaveValue('Motivo escrito');
    expect(within(dialog).getByRole('link', { name: 'Revisar en Comprobantes' })).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Enviar a autorización' })).toBeDisabled();
  });

  it('rejects the whole cancellation when a late fee has a receipt in review', async () => {
    mockServer({ cancel: () => HttpResponse.json({ code: 'LATE_FEE_UNDER_PAYMENT_REVIEW', violations: [] }, { status: 409 }) });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await writeJustification(user, dialog);
    const confirm = within(dialog).getByRole('button', { name: 'Enviar a autorización' });
    await vi.waitFor(() => expect(confirm).toBeEnabled());

    await user.click(confirm);

    expect(await within(dialog).findByText(/tampoco los otros recargos/)).toBeInTheDocument();
  });

  it('when the installment changed offers to reload it, which refreshes the account and closes the dialog', async () => {
    mockServer({ cancel: () => HttpResponse.json({ code: 'ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION', violations: [] }, { status: 409 }) });
    const user = userEvent.setup();
    renderPage();
    const dialog = await openCancelDialog(user);
    await writeJustification(user, dialog);
    const confirm = within(dialog).getByRole('button', { name: 'Enviar a autorización' });
    await vi.waitFor(() => expect(confirm).toBeEnabled());
    await user.click(confirm);
    await within(dialog).findByText(/La cuota cambió y ya no se puede cancelar/);
    const before = overviewCalls;

    await user.click(within(dialog).getByRole('button', { name: 'Recargar la cuota' }));

    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await vi.waitFor(() => expect(overviewCalls).toBeGreaterThan(before));
  });
});

describe('Facturación — pedido pendiente de autorización', () => {
  function withPending(overrides = {}) {
    invoices = [{ ...invoices[0], pendingAuthorizations: [pendingAuthorizationFixture(overrides)] }, invoices[1]];
  }

  it('shows the banner, the pill and no actions on the pending installment', async () => {
    withPending();
    mockServer();
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Pedido enviado, todavía no se aplicó')).toBeInTheDocument();
    expect(screen.getByText('Autorización pendiente')).toBeInTheDocument();
    await openInvoice(user, MAY);
    expect(screen.queryByRole('button', { name: 'Cancelar cuota' })).not.toBeInTheDocument();
    expect(screen.getByText(/Pedido de Marcela Ibarra enviado el 25\/10\/2026/)).toBeInTheDocument();
  });

  it('keeps the other installments from asking again while one request is pending', async () => {
    withPending();
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await openInvoice(user, 'Ver detalle de Cuota mensual · jun 2026');

    expect(await screen.findByRole('button', { name: 'Cancelar cuota' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Emitir nota de crédito' })).toBeDisabled();
  });

  it('lets only the person who asked withdraw the request', async () => {
    withPending({ requestedByPersonId: 77, requestedByName: 'Otra persona' });
    mockServer();
    renderPage();

    await screen.findByText('Pedido enviado, todavía no se aplicó');

    expect(screen.queryByRole('button', { name: 'Retirar pedido' })).not.toBeInTheDocument();
  });

  it('withdraws the request when the requester confirms and refreshes the account', async () => {
    withPending();
    const withdrawn = vi.fn();
    mockServer();
    server.use(
      http.post(`${API}/authorization-requests/90/withdraw`, () => {
        withdrawn();
        invoices = invoices.map((invoice) => ({ ...invoice, pendingAuthorizations: [] }));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Retirar pedido' }));
    await user.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: 'Retirar pedido' }));

    await vi.waitFor(() => expect(withdrawn).toHaveBeenCalled());
    await vi.waitFor(() => expect(screen.queryByText('Pedido enviado, todavía no se aplicó')).not.toBeInTheDocument());
  });

  it('links to the inbox of the role', async () => {
    withPending();
    mockServer();
    renderPage();

    expect(await screen.findByRole('link', { name: 'Ver mis pedidos' })).toHaveAttribute('href', '/administrativo/aprobaciones');
  });
});

describe('Facturación — notas de crédito del alumno', () => {
  const notes = [
    noteOf({ id: 1, creditNoteNumber: 'NC-0000098', reason: 'BILLING_ERROR' }),
    noteOf({ id: 2, creditNoteNumber: 'NC-0000099', reason: 'CANCELLATION', freeCreditAmount: money('0.00'), offsetAmount: money('48000.00'), totalAmount: money('48000.00') }),
  ];

  it('counts the notes in the section title and lists them when opened', async () => {
    mockServer({ notes });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /Notas de crédito del alumno · 2/ }));

    expect(await screen.findByText('NC-0000098')).toBeInTheDocument();
    expect(screen.getByText('NC-0000099')).toBeInTheDocument();
    expect(screen.getByText('Crédito a favor')).toBeInTheDocument();
    expect(screen.getByText('Reduce deuda')).toBeInTheDocument();
  });

  it('shows who requested and who authorized a note when it is expanded', async () => {
    mockServer({ notes: [notes[0]] });
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /Notas de crédito del alumno · 1/ }));

    await user.click(await screen.findByRole('button', { name: 'Ver detalle de la nota' }));

    expect(screen.getByText('Marcela Ibarra')).toBeInTheDocument();
    expect(screen.getByText(/Rodolfo Fernández · 02\/09\/2026/)).toBeInTheDocument();
    expect(screen.getByText('Se cobró de más el seguro escolar.')).toBeInTheDocument();
  });

  it('shows an empty state when the student has no notes', async () => {
    mockServer({ notes: [] });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /Notas de crédito del alumno · 0/ }));

    expect(await screen.findByText('Sin notas de crédito')).toBeInTheDocument();
  });
});
