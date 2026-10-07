import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagarTransferencia } from '@/pages/AlumnoPagarTransferencia';

const API = 'http://localhost:8080';

const overdueBreakdown = {
  invoiceId: 61,
  feeType: 'MONTHLY_INSTALLMENT',
  period: '2026-09',
  baseAmount: { amount: '48000.00', currency: 'ARS' },
  adjustments: [{ type: 'SURCHARGE', amount: { amount: '4800.00', currency: 'ARS' }, humanLabel: 'Recargo por mora' }],
  amountDue: { amount: '52800.00', currency: 'ARS' },
  paymentsApplied: [],
  remainingBalance: { amount: '52800.00', currency: 'ARS' },
  status: 'OVERDUE',
};

function renderTransfer({ receipts = [] } = {}) {
  server.use(
    http.get(`${API}/me/invoices/61/price-breakdown`, () => HttpResponse.json(overdueBreakdown)),
    http.get(`${API}/me/bank-transfer-receipts`, () => HttpResponse.json(receipts)),
  );
  return renderWithProviders(
    <>
      <Toaster />
      <Routes>
        <Route path="/alumno/pagos/:invoiceId/transferencia" element={<AlumnoPagarTransferencia />} />
      </Routes>
    </>,
    { route: '/alumno/pagos/61/transferencia' },
  );
}

async function goToUploadStep(user) {
  await user.click(await screen.findByRole('button', { name: 'Ya transferí — subir comprobante' }));
}

describe('AlumnoPagarTransferencia', () => {
  it('shows which installment is being paid, the exact amount and the late fee it includes', async () => {
    renderTransfer();

    expect(await screen.findByText('Cuota mensual · sep 2026')).toBeInTheDocument();
    expect(screen.getAllByText('$ 52.800,00').length).toBeGreaterThan(0);
    expect(screen.getByText('Recargo por mora')).toBeInTheDocument();
    expect(screen.getByText(/Transferí exactamente \$ 52\.800,00/)).toBeInTheDocument();
  });

  it('opens on the follow-up step when the installment already has a receipt under review', async () => {
    renderTransfer({ receipts: [{ id: 302, invoiceId: 61, status: 'PENDING_REVIEW', uploadedAt: '2026-10-02T10:00:00' }] });

    expect(await screen.findByText('Pendiente de revisión')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ya transferí — subir comprobante' })).not.toBeInTheDocument();
  });

  it('rejects a file larger than 5 MB before uploading it', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);

    const bigFile = new File(['x'], 'comprobante.pdf', { type: 'application/pdf' });
    Object.defineProperty(bigFile, 'size', { value: 6 * 1024 * 1024 });
    await user.upload(document.querySelector('input[type="file"]'), bigFile);

    expect(await screen.findByText('El archivo pesa más de 5 MB. Subí uno más liviano.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar comprobante' })).toBeDisabled();
    expect(fetchSpy.mock.calls.some(([url]) => String(url).includes('/bank-transfer-receipts') && !String(url).endsWith('/me/bank-transfer-receipts'))).toBe(false);
  });

  it('rejects a file that is not a PDF, JPG or PNG before uploading it', async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderTransfer();
    await goToUploadStep(user);

    await user.upload(document.querySelector('input[type="file"]'), new File(['hola'], 'notas.txt', { type: 'text/plain' }));

    expect(await screen.findByText('El comprobante tiene que ser un PDF, JPG o PNG.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar comprobante' })).toBeDisabled();
  });

  it('explains that there is already a receipt under review when the backend says so', async () => {
    server.use(
      http.post(`${API}/me/invoices/61/bank-transfer-receipts`, () =>
        HttpResponse.json({ code: 'PAYMENT_RECEIPT_ALREADY_PENDING' }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);

    await user.upload(document.querySelector('input[type="file"]'), new File(['pdf'], 'comprobante.pdf', { type: 'application/pdf' }));
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText('Ya enviaste un comprobante para esta cuota y está en revisión.')).toBeInTheDocument();
  });
});
