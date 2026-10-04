import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorFinanciero } from '@/pages/DirectorFinanciero';

const API = 'http://localhost:8080';

const totals = (currency, factor = 1) => ({
  totalDebt: { amount: String(1743500 * factor), currency },
  monthlyProjected: { amount: '5200000.00', currency },
  monthlyCollected: { amount: '3100000.00', currency },
  monthlyDebt: { amount: '2100000.00', currency },
});

function renderWith(totalsByCurrency) {
  server.use(
    http.get(`${API}/financial-dashboard`, () =>
      HttpResponse.json({ studentsInDebt: 12, studentsUpToDate: 140, totalsByCurrency }),
    ),
    http.get(`${API}/financial-dashboard/debtors`, () => HttpResponse.json([])),
  );
  return renderWithProviders(<DirectorFinanciero />);
}

describe('DirectorFinanciero', () => {
  it('shows the page title and one row of four amounts for a single currency, without a currency heading', async () => {
    renderWith([totals('ARS')]);

    expect(await screen.findByText('Deuda total')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Resumen financiero' })).toBeInTheDocument();
    expect(screen.getByText('Proyectado del mes')).toBeInTheDocument();
    expect(screen.getByText('Cobrado del mes')).toBeInTheDocument();
    expect(screen.getByText('Pendiente del mes')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'ARS' })).not.toBeInTheDocument();
  });

  it('shows one row per currency with a heading and labels without the currency suffix', async () => {
    renderWith([totals('ARS'), totals('USD', 0.001)]);

    expect(await screen.findByRole('heading', { name: 'ARS' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'USD' })).toBeInTheDocument();
    expect(screen.getAllByText('Deuda total')).toHaveLength(2);
    expect(screen.queryByText(/\(ARS\)/)).not.toBeInTheDocument();
    const usdRow = screen.getByRole('region', { name: 'USD' });
    expect(within(usdRow).getAllByText(/US\$|USD/).length).toBeGreaterThan(0);
  });

  it('keeps the long amounts inside a card that can shrink so they do not overflow', async () => {
    renderWith([totals('ARS')]);

    const amount = await screen.findByText(/1\.743\.500,00/);

    expect(amount.parentElement.className).toContain('min-w-0');
    expect(amount.parentElement.className).toContain('overflow-hidden');
    expect(amount.closest('[class*="min-w-0"][class*="p-5"]')).not.toBeNull();
  });
});
