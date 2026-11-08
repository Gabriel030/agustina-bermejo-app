import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { AlumnoPagarTransferencia } from './AlumnoPagarTransferencia';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Valentina', apellido: 'Suárez' } }),
}));

const API = 'http://localhost:8080';
const BANK = { accountHolder: 'ISAB', bankCbu: '0000003100012345678901', bankAlias: 'isab.pagos' };

function renderTransfer() {
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/pagos/:invoiceId/transferencia" element={<AlumnoPagarTransferencia />} />
    </Routes>,
    { route: '/alumno/pagos/61/transferencia' },
  );
}

describe('AlumnoPagarTransferencia load error', () => {
  it('shows the retry state instead of empty bank data when the payment config fails', async () => {
    server.use(
      http.get(`${API}/payment-config`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
      http.get(`${API}/me/invoices/61/price-breakdown`, () => HttpResponse.json({})),
      http.get(`${API}/me/bank-transfer-receipts`, () => HttpResponse.json([])),
    );
    renderTransfer();

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ya transferí — subir comprobante' })).not.toBeInTheDocument();
  });

  it('shows the retry state instead of an endless skeleton when the price breakdown fails', async () => {
    server.use(
      http.get(`${API}/payment-config`, () => HttpResponse.json(BANK)),
      http.get(`${API}/me/invoices/61/price-breakdown`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
      http.get(`${API}/me/bank-transfer-receipts`, () => HttpResponse.json([])),
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderTransfer();

    await user.click(await screen.findByRole('button', { name: 'Ya transferí — subir comprobante' }));

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
