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

function renderPage() {
  server.use(
    http.get(`${API}/students`, () =>
      HttpResponse.json([{ id: 2, nombre: 'Martina', apellido: 'Acosta', dni: '28016166' }]),
    ),
    http.get(`${API}/payment-receipts`, () => HttpResponse.json([pendingReceipt])),
  );
  return renderWithProviders(
    <>
      <Toaster />
      <AdministrativoFacturacion />
    </>,
  );
}

async function openReceiptsTab(user) {
  await user.click(screen.getByRole('tab', { name: 'Comprobantes' }));
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
  await user.click(await screen.findByRole('button', { name: /Carrera: Profesorado de Educación Inicial/ }));
  await user.click(screen.getByRole('button', { name: /Registrar pago/ }));
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
