import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagarSeleccion } from '@/pages/AlumnoPagarSeleccion';

const API = 'http://localhost:8080';

function renderSelection(breakdown) {
  server.use(
    http.get(`${API}/me/invoices/61/price-breakdown`, () => HttpResponse.json(breakdown)),
    http.get(`${API}/payment-config`, () =>
      HttpResponse.json({ bankAlias: 'isab.pagos', bankCbu: '0070123430004567890123', accountHolder: 'Instituto', mpEnabled: true }),
    ),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/pagos/:invoiceId/seleccion" element={<AlumnoPagarSeleccion />} />
    </Routes>,
    { route: '/alumno/pagos/61/seleccion' },
  );
}

const baseBreakdown = {
  invoiceId: 61,
  feeType: 'RECURRING_FEE',
  period: '2026-09',
  baseAmount: 48000,
  adjustments: [{ type: 'SCHOLARSHIP', amount: -24000, humanLabel: 'Beca 50%' }],
  amountDue: 24000,
  paymentsApplied: [],
  remainingBalance: 24000,
  status: 'PENDING',
};

describe('AlumnoPagarSeleccion', () => {
  it('shows which installment and amount the student is about to pay, with its scholarship', async () => {
    renderSelection(baseBreakdown);

    expect(await screen.findByText('Cuota mensual · sep 2026')).toBeInTheDocument();
    expect(screen.getByText('Beca')).toBeInTheDocument();
    expect(screen.getByText('− $ 24.000')).toBeInTheDocument();
    expect(screen.getByText('Transferencia bancaria')).toBeInTheDocument();
  });

  it('does not offer payment methods for an installment that is already paid', async () => {
    renderSelection({ ...baseBreakdown, status: 'PAID', remainingBalance: 0 });

    expect(await screen.findByText('Esta cuota ya está saldada. No tenés que pagar nada más.')).toBeInTheDocument();
    expect(screen.queryByText('Transferencia bancaria')).not.toBeInTheDocument();
  });
});
