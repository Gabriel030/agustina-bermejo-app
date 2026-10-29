import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { invoiceFixture } from '@/test/fixtures/invoices';
import { AdministrativoCargarComprobante } from '@/pages/AdministrativoCargarComprobante';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 9, nombre: 'Marcos', apellido: 'Ledesma', roles: ['ADMINISTRATIVO'] } }),
}));

const API = 'http://localhost:8080';

const SEPTEMBER = invoiceFixture({ id: 61, period: '2026-09', dueDate: '2026-09-10', total: '100000.00', paymentStatus: 'OVERDUE' });
const OCTOBER = invoiceFixture({ id: 62, period: '2026-10', dueDate: '2026-10-10', total: '100000.00', paymentStatus: 'OVERDUE' });
const NOVEMBER = invoiceFixture({ id: 63, period: '2026-11', dueDate: '2026-11-10', total: '100000.00', paymentStatus: 'PENDING' });
const AUGUST = invoiceFixture({ id: 60, period: '2026-08', dueDate: '2026-08-10', total: '100000.00', paymentStatus: 'PAYMENT_UNDER_REVIEW' });

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
}

function renderPage(invoices = [NOVEMBER, SEPTEMBER, OCTOBER, AUGUST]) {
  server.use(
    http.get(`${API}/students/2`, () => HttpResponse.json({ id: 2, nombre: 'Valentina', apellido: 'Suárez', dni: '41256903' })),
    http.get(`${API}/students/2/billing-overview`, () =>
      HttpResponse.json([{ enrollmentType: 'PROGRAM', enrollmentId: 2, productName: 'Tecnicatura en Enfermería', paymentStatus: 'OVERDUE', invoices }]),
    ),
  );
  return renderWithProviders(
    <>
      <LocationProbe />
      <Routes>
        <Route path="/administrativo/facturacion/alumnos/:studentId/cargar-comprobante" element={<AdministrativoCargarComprobante />} />
        <Route path="*" element={null} />
      </Routes>
    </>,
    { route: '/administrativo/facturacion/alumnos/2/cargar-comprobante' },
  );
}

function reviewButton() {
  return screen.getAllByRole('button', { name: 'Revisar y confirmar' }).find((button) => button.type === 'submit');
}

async function findReviewButton() {
  await screen.findByLabelText('Número de operación');
  return reviewButton();
}

function pdf(name = 'comprobante.pdf') {
  return new File(['pdf'], name, { type: 'application/pdf' });
}

async function fillValidForm(user, { amount } = {}) {
  await user.upload(await screen.findByLabelText('Comprobante de transferencia'), pdf());
  await user.type(screen.getByLabelText('Número de operación'), '4821937');
  await user.type(screen.getByLabelText('CBU o alias de origen'), 'valentina.suarez.mp');
  if (amount) {
    const amountInput = screen.getByLabelText('Monto transferido');
    await user.clear(amountInput);
    await user.type(amountInput, amount);
  }
}

function captureSubmission(respond = () => HttpResponse.json({ receiptId: 302 }, { status: 201 })) {
  const captured = { calls: 0 };
  server.use(
    http.post(`${API}/students/2/bank-transfer-receipts`, async ({ request }) => {
      captured.url = new URL(request.url);
      captured.idempotencyKey = request.headers.get('Idempotency-Key');
      captured.keys = [...(captured.keys ?? []), request.headers.get('Idempotency-Key')];
      captured.calls += 1;
      return respond(captured.calls);
    }),
  );
  return captured;
}

describe('AdministrativoCargarComprobante', () => {
  it('shows the breadcrumb, the student header with the staff pill and the two steps', async () => {
    renderPage();

    expect(await screen.findAllByText('Suárez, Valentina')).toHaveLength(2);
    expect(screen.getByText('DNI 41256903')).toBeInTheDocument();
    expect(screen.getByText('Carga por secretaría')).toBeInTheDocument();
    const crumbs = screen.getByRole('navigation', { name: 'Breadcrumb' });
    expect(within(crumbs).getByRole('link', { name: 'Facturación' })).toHaveAttribute('href', '/administrativo/facturacion');
    expect(within(crumbs).getByRole('link', { name: 'Suárez, Valentina' })).toHaveAttribute('href', '/administrativo/facturacion?alumno=2');
    expect(screen.getByRole('button', { name: /Datos del comprobante/ })).toHaveAttribute('aria-current', 'step');
    expect(screen.getAllByRole('button', { name: /Revisar y confirmar/ })).toHaveLength(2);
  });

  it('lists the installments oldest first and keeps the one already under review apart', async () => {
    renderPage();
    const list = await screen.findByRole('list', { name: 'Cuotas que cubre' });

    const titles = within(list)
      .getAllByRole('listitem')
      .map((item) => item.textContent);

    expect(titles[0]).toContain('sep 2026');
    expect(titles[1]).toContain('oct 2026');
    expect(titles[2]).toContain('nov 2026');
    expect(titles[3]).toContain('ago 2026');
    expect(titles[3]).toContain('Ya está en un comprobante en revisión');
    expect(within(list).getAllByRole('button')).toHaveLength(3);
  });

  it('shows an empty state with a way back when the student has no pending installments', async () => {
    renderPage([invoiceFixture({ id: 61, paymentStatus: 'PAID', paid: '48000.00' })]);

    expect(await screen.findByText('No tiene cuotas pendientes')).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: 'Volver al estado de cuenta' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/administrativo/facturacion?alumno=2');
  });

  it('keeps the transfer date fixed on today and prefills the holder with the student name', async () => {
    renderPage();

    expect(await screen.findByText(/Transferencia de hoy,/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Fecha de la transferencia')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Titular de la cuenta de origen')).toHaveValue('Valentina Suárez');
  });

  it('shows the errors per field and does not advance when the form is incomplete', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await findReviewButton());

    expect(await screen.findByText('Subí el comprobante para poder cargarlo.')).toBeInTheDocument();
    expect(screen.getByText('Ingresá el número de operación que figura en el comprobante.')).toBeInTheDocument();
    expect(screen.getByText('Ingresá el CBU o alias de la cuenta de origen.')).toBeInTheDocument();
    expect(screen.queryByText('Revisá antes de cargar')).not.toBeInTheDocument();
  });

  it('uploads on behalf of the student only after the review step, with an idempotency key and today as the date', async () => {
    const captured = captureSubmission();
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /oct 2026/ }));
    await fillValidForm(user, { amount: '200000' });
    await user.click(reviewButton());

    expect(await screen.findByText('Revisá antes de cargar')).toBeInTheDocument();
    expect(captured.calls).toBe(0);
    expect(screen.getByText('Nº 4821937')).toBeInTheDocument();
    expect(screen.getByText(/Lo cargás Marcos Ledesma/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cargar comprobante' }));

    expect(await screen.findByText('Comprobante cargado para Valentina Suárez')).toBeInTheDocument();
    expect(captured.calls).toBe(1);
    expect(captured.idempotencyKey).toBeTruthy();
    expect(captured.url.searchParams.getAll('invoiceIds')).toEqual(['61', '62']);
    expect(captured.url.searchParams.get('declaredAmount')).toBe('200000.00');
    expect(captured.url.searchParams.get('transferDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('shows the result per installment with the protection outcome and no automatic redirect', async () => {
    captureSubmission();
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /nov 2026/ }));
    await fillValidForm(user);
    await user.click(reviewButton());
    await user.click(await screen.findByRole('button', { name: 'Cargar comprobante' }));

    expect(await screen.findByText('Comprobante cargado para Valentina Suárez')).toBeInTheDocument();
    expect(screen.getAllByText('Sigue vencida, el recargo corre hasta la aprobación')).toHaveLength(2);
    expect(screen.getByText('Protegida: sin recargo mientras se revisa')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/administrativo/facturacion/alumnos/2/cargar-comprobante');

    await user.click(screen.getByRole('button', { name: 'Ir a Comprobantes' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/administrativo/facturacion?tab=comprobantes');
  });

  it('warns that the excess stays as credit of the student on the result', async () => {
    captureSubmission();
    const user = userEvent.setup();
    renderPage();
    await fillValidForm(user, { amount: '120000' });
    await user.click(reviewButton());
    await user.click(await screen.findByRole('button', { name: 'Cargar comprobante' }));

    expect(await screen.findByText(/de más\. Quedan como saldo a favor del alumno/)).toBeInTheDocument();
  });

  it('goes back to edit keeping what was typed', async () => {
    const user = userEvent.setup();
    renderPage();
    await fillValidForm(user);
    await user.click(reviewButton());
    await screen.findByText('Revisá antes de cargar');

    await user.click(screen.getByRole('button', { name: 'Volver a editar' }));

    expect(await screen.findByLabelText('Número de operación')).toHaveValue('4821937');
    expect(screen.getByLabelText('CBU o alias de origen')).toHaveValue('valentina.suarez.mp');
    expect(screen.getByText('comprobante.pdf')).toBeInTheDocument();
  });

  it('retries with the same idempotency key after an unconfirmed server failure', async () => {
    const captured = captureSubmission((call) => (call === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json({ receiptId: 302 }, { status: 201 })));
    const user = userEvent.setup();
    renderPage();
    await fillValidForm(user);
    await user.click(reviewButton());
    await user.click(await screen.findByRole('button', { name: 'Cargar comprobante' }));

    expect(await screen.findByText('No pudimos confirmar si se cargó')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Comprobante cargado para Valentina Suárez')).toBeInTheDocument();
    expect(captured.keys).toHaveLength(2);
    expect(captured.keys[0]).toBe(captured.keys[1]);
  });

  it('returns to the data step with the duplicate notice when the transfer was already registered', async () => {
    captureSubmission(() => HttpResponse.json({ code: 'POSSIBLE_DUPLICATE_BANK_TRANSFER', violations: [] }, { status: 409 }));
    const user = userEvent.setup();
    renderPage();
    await fillValidForm(user);
    await user.click(reviewButton());
    await user.click(await screen.findByRole('button', { name: 'Cargar comprobante' }));

    expect(await screen.findByText('Esa transferencia ya está registrada')).toBeInTheDocument();
    expect(screen.getByLabelText('Número de operación')).toHaveValue('4821937');
    await user.click(screen.getByRole('button', { name: 'Ver en Comprobantes' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/administrativo/facturacion?tab=comprobantes');
  });

  it('shows the server message on the review step when the upload is rejected for another reason', async () => {
    captureSubmission(() => HttpResponse.json({ code: 'INVOICE_ALREADY_UNDER_REVIEW', violations: [] }, { status: 409 }));
    const user = userEvent.setup();
    renderPage();
    await fillValidForm(user);
    await user.click(reviewButton());
    await user.click(await screen.findByRole('button', { name: 'Cargar comprobante' }));

    expect(await screen.findByText('Una de las cuotas elegidas ya está en un comprobante en revisión.')).toBeInTheDocument();
    expect(screen.getByText('Revisá antes de cargar')).toBeInTheDocument();
  });
});
