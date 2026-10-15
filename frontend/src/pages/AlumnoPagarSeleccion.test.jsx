import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagarSeleccion } from '@/pages/AlumnoPagarSeleccion';

const API = 'http://localhost:8080';

const bankConfig = { bankAlias: 'isab.pagos', bankCbu: '0070123430004567890123', accountHolder: 'Instituto' };

function renderSelection(breakdown, config = { ...bankConfig, mpEnabled: true }) {
  server.use(
    http.get(`${API}/me/invoices/61/price-breakdown`, () => HttpResponse.json(breakdown)),
    http.get(`${API}/payment-config`, () =>
      HttpResponse.json(config),
    ),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/pagos/:invoiceId/seleccion" element={<AlumnoPagarSeleccion />} />
      <Route path="/alumno/pagos/:invoiceId/online" element={<p>online page</p>} />
    </Routes>,
    { route: '/alumno/pagos/61/seleccion' },
  );
}

const baseBreakdown = {
  invoiceId: 61,
  feeType: 'MONTHLY_INSTALLMENT',
  period: '2026-09',
  baseAmount: { amount: '48000.00', currency: 'ARS' },
  adjustments: [{ type: 'SCHOLARSHIP', amount: { amount: '-24000.00', currency: 'ARS' }, humanLabel: 'Beca 50%' }],
  amountDue: { amount: '24000.00', currency: 'ARS' },
  paymentsApplied: [],
  remainingBalance: { amount: '24000.00', currency: 'ARS' },
  status: 'PENDING',
};

describe('AlumnoPagarSeleccion', () => {
  it('shows which installment and amount the student is about to pay, with its scholarship', async () => {
    renderSelection(baseBreakdown);

    expect(await screen.findByText('Cuota mensual · sep 2026')).toBeInTheDocument();
    expect(screen.getByText('Beca')).toBeInTheDocument();
    expect(screen.getByText('− $ 24.000,00')).toBeInTheDocument();
    expect(screen.getByText('Transferencia bancaria')).toBeInTheDocument();
  });

  it('does not offer payment methods for an installment that is already paid', async () => {
    renderSelection({ ...baseBreakdown, status: 'PAID', remainingBalance: { amount: '0.00', currency: 'ARS' } });

    expect(await screen.findByText('Esta cuota ya está saldada. No tenés que pagar nada más.')).toBeInTheDocument();
    expect(screen.queryByText('Transferencia bancaria')).not.toBeInTheDocument();
  });

  it('shows the card option disabled with a coming soon badge while the gateway is off', async () => {
    renderSelection(baseBreakdown, { ...bankConfig, mpEnabled: false });

    expect(await screen.findByText('Pagar con tarjeta')).toBeInTheDocument();
    expect(screen.getByText('Próximamente')).toBeInTheDocument();
    expect(screen.getByText(/todavía no está disponible/)).toBeInTheDocument();
    expect(screen.getByText('Transferencia bancaria')).toBeInTheDocument();
  });

  it('does not navigate when the disabled card option is clicked', async () => {
    renderSelection(baseBreakdown, { ...bankConfig, mpEnabled: false });

    await userEvent.click(await screen.findByText('Pagar con tarjeta'));

    expect(screen.queryByText('online page')).not.toBeInTheDocument();
    expect(screen.getByText('Pagar con tarjeta')).toBeInTheDocument();
  });

  it('navigates to the online payment when the gateway is enabled', async () => {
    renderSelection(baseBreakdown);

    await userEvent.click(await screen.findByText('Pagar con tarjeta'));

    expect(await screen.findByText('online page')).toBeInTheDocument();
  });

  it('explains that no payment means are available when bank data is missing', async () => {
    renderSelection(baseBreakdown, { mpEnabled: false });

    expect(await screen.findByText('No hay medios de pago disponibles')).toBeInTheDocument();
    expect(screen.getByText('Próximamente')).toBeInTheDocument();
  });
});
