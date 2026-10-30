import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes, useLocation } from 'react-router-dom';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { creditBalanceFixture, creditNoteFixture, invoiceFixture, money } from '@/test/fixtures/invoices';
import { CreateCreditNotePage } from './CreateCreditNotePage';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 11, nombre: 'Rodolfo', apellido: 'Fernández', roles: auth.roles }, roles: auth.roles }),
}));

let invoice;
let issueBodies;
let balance;

function twoLineInvoice(overrides = {}) {
  const base = invoiceFixture({ id: 62, period: '2026-04', dueDate: '2026-04-10', total: '48000.00', paymentStatus: 'OVERDUE', ...overrides });
  base.lines = [
    { ...base.lines[0], lineNumber: 1, description: 'Cuota mensual', amount: money('45000.00'), creditableAmount: money('45000.00') },
    { ...base.lines[0], lineNumber: 2, description: 'Seguro escolar', amount: money('3000.00'), creditableAmount: money('3000.00') },
  ];
  return base;
}

function mockServer({ issue } = {}) {
  issueBodies = [];
  server.use(
    http.get(`${API}/students/2`, () => HttpResponse.json({ id: 2, nombre: 'Martina', apellido: 'Acosta', dni: '28016166' })),
    http.get(`${API}/students/2/billing-overview`, () =>
      HttpResponse.json([{ enrollmentType: 'PROGRAM', enrollmentId: 2, productName: 'Profesorado', paymentStatus: 'OVERDUE', invoices: [invoice] }]),
    ),
    http.get(`${API}/students/2/credit-balance`, () => HttpResponse.json(balance)),
    http.post(`${API}/invoices/:id/credit-notes`, async ({ request, params }) => {
      issueBodies.push({ id: params.id, body: await request.json() });
      return issue ? issue() : HttpResponse.json(creditNoteFixture({ id: 9, creditNoteNumber: 'NC-0000142' }), { status: 201 });
    }),
  );
}

function FichaStub() {
  const location = useLocation();
  return <div data-testid="ficha">Ficha {location.search} {JSON.stringify(location.state)}</div>;
}

function renderPage() {
  return renderWithProviders(
    <>
      <Toaster />
      <Routes>
        <Route path="/:role/facturacion/cuotas/:invoiceId/nota-de-credito" element={<CreateCreditNotePage />} />
        <Route path="/:role/facturacion" element={<FichaStub />} />
        <Route path="/:role/aprobaciones" element={<div>Bandeja</div>} />
      </Routes>
    </>,
    { route: `/${auth.roles[0].toLowerCase()}/facturacion/cuotas/62/nota-de-credito?alumno=2` },
  );
}

async function typeAmount(user, name, value) {
  await user.type(await screen.findByRole('textbox', { name: `Importe a acreditar de ${name}` }), value);
}

async function fillValidNote(user) {
  await typeAmount(user, 'Seguro escolar', '3000');
  await user.type(screen.getByLabelText(/Justificación/), 'Se cobró de más el seguro escolar.');
}

beforeEach(() => {
  auth.roles = ['DIRECTOR'];
  invoice = twoLineInvoice();
  balance = creditBalanceFixture('0.00');
});

describe('Nota de crédito — paso 1 (importes)', () => {
  it('shows the fixed reason, the amount and the maximum of each line', async () => {
    mockServer();
    renderPage();

    expect(await screen.findByLabelText('Motivo')).toHaveValue('Error de facturación');
    expect(screen.getByLabelText('Motivo')).toHaveAttribute('readonly');
    expect(screen.getAllByText('$ 45.000,00').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('$ 3.000,00').length).toBeGreaterThanOrEqual(2);
  });

  it('keeps the next step blocked and says why until there is an amount and a justification', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();
    const next = await screen.findByRole('button', { name: 'Revisar la nota' });

    expect(next).toBeDisabled();
    expect(screen.getByText('Ingresá al menos un importe.')).toBeInTheDocument();

    await typeAmount(user, 'Seguro escolar', '3000');
    expect(next).toBeDisabled();
    expect(screen.getAllByText('Falta la justificación.').length).toBeGreaterThan(0);

    await user.type(screen.getByLabelText(/Justificación/), 'Se cobró de más.');
    expect(next).toBeEnabled();
  });

  it('marks a line above its maximum with an inline error and blocks', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await typeAmount(user, 'Seguro escolar', '3500');

    expect(await screen.findByText(/Supera el máximo de \$ 3\.000,00/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revisar la nota' })).toBeDisabled();
  });

  it('shows "Ya acreditado" instead of an input for a fully credited line', async () => {
    invoice = twoLineInvoice();
    invoice.lines[1].creditableAmount = money('0.00');
    mockServer();
    renderPage();

    expect(await screen.findByText('Ya acreditado')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Importe a acreditar de Seguro escolar' })).not.toBeInTheDocument();
  });

  it('blocks a note that covers the whole debt and sends to the cancellation', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();
    await typeAmount(user, 'Cuota mensual', '45000');
    await typeAmount(user, 'Seguro escolar', '3000');
    await user.type(screen.getByLabelText(/Justificación/), 'Todo mal.');

    expect(await screen.findByText(/Esa nota cubre toda la deuda/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revisar la nota' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Cancelar cuota' }));

    expect(await screen.findByTestId('ficha')).toHaveTextContent('?alumno=2');
    expect(screen.getByTestId('ficha')).toHaveTextContent('"cancelInvoiceId":62');
  });

  it('on a paid installment the summary speaks of credit in favor of the student', async () => {
    invoice = twoLineInvoice({ paymentStatus: 'PAID', paid: '48000.00' });
    balance = creditBalanceFixture('1000.00');
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await typeAmount(user, 'Seguro escolar', '3000');

    expect(await screen.findByText('Crédito a favor hoy')).toBeInTheDocument();
    expect(screen.getByText('$ 1.000,00')).toBeInTheDocument();
    expect(screen.getByText('$ 4.000,00')).toBeInTheDocument();
  });
});

describe('Nota de crédito — paso 2 (confirmar)', () => {
  async function goToConfirm(user) {
    await fillValidNote(user);
    await user.click(screen.getByRole('button', { name: 'Revisar la nota' }));
  }

  it('shows the change table with today, new and difference of the debt', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole('button', { name: 'Revisar la nota' });
    await goToConfirm(user);

    const row = await screen.findByRole('group', { name: 'Deuda de la cuota' });
    expect(within(row).getByText('$ 48.000,00')).toBeInTheDocument();
    expect(within(row).getByText('$ 45.000,00')).toBeInTheDocument();
    expect(within(row).getByText('− $ 3.000,00')).toBeInTheDocument();
    expect(screen.getByText('Se cobró de más el seguro escolar.')).toBeInTheDocument();
    expect(screen.getByText(/La nota de crédito no se puede deshacer/)).toBeInTheDocument();
  });

  it('goes back to edit keeping what was typed', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Revisar la nota' });
    await goToConfirm(user);

    await user.click(await screen.findByRole('button', { name: 'Volver a editar' }));

    expect(screen.getByRole('textbox', { name: 'Importe a acreditar de Seguro escolar' })).toHaveValue('3000');
  });

  it('as director issues the note (201), sends the exact payload and goes back to the account', async () => {
    mockServer();
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Revisar la nota' });
    await goToConfirm(user);

    await user.click(await screen.findByRole('button', { name: 'Emitir nota de crédito' }));

    expect(await screen.findByText('Nota de crédito NC-0000142 emitida.')).toBeInTheDocument();
    expect(screen.getByText('La deuda de la cuota bajó a $ 45.000,00.')).toBeInTheDocument();
    expect(issueBodies).toEqual([
      {
        id: '62',
        body: { reason: 'BILLING_ERROR', justification: 'Se cobró de más el seguro escolar.', lines: [{ lineNumber: 2, amount: money('3000.00') }] },
      },
    ]);
    expect(await screen.findByTestId('ficha')).toBeInTheDocument();
  });

  it('as administrative asks for authorization (202) in warning tone', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    mockServer({ issue: () => HttpResponse.json({ authorizationRequestId: 90 }, { status: 202 }) });
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Revisar la nota' });
    await goToConfirm(user);
    expect(await screen.findByText(/Necesita autorización de la dirección/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Enviar a autorización' }));

    expect(await screen.findByText('Pedido enviado. Todavía no se aplicó.')).toBeInTheDocument();
    expect(screen.getByText('La nota de crédito se emite cuando la autorice la dirección.')).toBeInTheDocument();
    expect(await screen.findByTestId('ficha')).toBeInTheDocument();
  });

  it('on a paid installment offers to ask for a refund after issuing the note', async () => {
    invoice = twoLineInvoice({ paymentStatus: 'PAID', paid: '48000.00' });
    mockServer();
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Revisar la nota' });
    await goToConfirm(user);
    expect(await screen.findByText(/No es una devolución de dinero/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Emitir nota de crédito' }));

    expect(await screen.findByText('$ 3.000,00 quedaron como crédito a favor del alumno.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pedir reembolso' })).toBeInTheDocument();
  });

  it.each([
    ['CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT', /La nota supera lo que se puede acreditar/],
    ['CREDIT_NOTE_DISTRIBUTION_MISMATCH', /La cuota cambió mientras armabas la nota/],
  ])('on %s shows the conflict with a reload action that restarts the amounts', async (code, message) => {
    mockServer({ issue: () => HttpResponse.json({ code, violations: [] }, { status: 409 }) });
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Revisar la nota' });
    await goToConfirm(user);
    await user.click(await screen.findByRole('button', { name: 'Emitir nota de crédito' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByText(code)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Recargar la cuota' }));

    expect(await screen.findByRole('textbox', { name: 'Importe a acreditar de Seguro escolar' })).toHaveValue('');
  });

  it('links to Comprobantes when the installment has a receipt in review', async () => {
    mockServer({ issue: () => HttpResponse.json({ code: 'PENDING_BANK_TRANSFER_ON_INVOICE', violations: [] }, { status: 409 }) });
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('button', { name: 'Revisar la nota' });
    await goToConfirm(user);
    await user.click(await screen.findByRole('button', { name: 'Emitir nota de crédito' }));

    const link = await screen.findByRole('link', { name: 'Revisar en Comprobantes' });

    expect(link).toHaveAttribute('href', '/director/facturacion?alumno=2&tab=comprobantes');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Emitir nota de crédito' })).toBeEnabled());
  });
});
