import { cloneElement } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { FinancialDashboardPage } from './FinancialDashboardPage';

vi.mock('recharts', async (importOriginal) => {
  const original = await importOriginal();
  return { ...original, ResponsiveContainer: ({ children }) => cloneElement(children, { width: 640, height: 260 }) };
});

const API = 'http://localhost:8080';
const money = (amount) => ({ amount, currency: 'ARS' });

function monthsOf(url) {
  const params = new URL(url).searchParams;
  const months = [];
  let [year, month] = params.get('from').split('-').map(Number);
  const [lastYear, lastMonth] = params.get('to').split('-').map(Number);
  while (year < lastYear || (year === lastYear && month <= lastMonth)) {
    months.push(`${year}-${String(month).padStart(2, '0')}`);
    month += 1;
    if (month > 12) {
      year += 1;
      month = 1;
    }
  }
  return months;
}

function lastTwo(months, last, previous) {
  return months.map((_, index) => (index === months.length - 1 ? last : index === months.length - 2 ? previous : null));
}

function stockMetric(url, { available = true, partial = false } = {}) {
  const months = monthsOf(url);
  if (!available) return { panel: 'collections-stock', available: false, partial: false, months, series: [] };
  return {
    panel: 'collections-stock',
    available: true,
    partial,
    months,
    series: [
      { key: 'amount.FROM_1_TO_30', unit: 'MONEY', values: lastTwo(months, money('100000.00'), money('80000.00')) },
      { key: 'amount.FROM_31_TO_60', unit: 'MONEY', values: lastTwo(months, money('45000.00'), money('0.00')) },
      { key: 'amount.FROM_61_TO_90', unit: 'MONEY', values: lastTwo(months, money('30000.00'), money('0.00')) },
      { key: 'amount.OVER_90', unit: 'MONEY', values: lastTwo(months, money('120000.00'), money('0.00')) },
      { key: 'amount.TOTAL', unit: 'MONEY', values: lastTwo(months, money('295000.00'), money('80000.00')) },
      { key: 'students', unit: 'COUNT', values: lastTwo(months, 4, 1) },
    ],
  };
}

function install({ requests = [], ...options } = {}) {
  server.use(
    http.get(`${API}/financial-dashboard`, () =>
      HttpResponse.json({
        studentsInDebt: 4,
        studentsUpToDate: 10,
        totalsByCurrency: [{ totalDebt: money('295000.00'), monthlyProjected: money('1.00'), monthlyCollected: money('1.00'), monthlyDebt: money('1.00') }],
      }),
    ),
    http.get(`${API}/financial-dashboard/debtors`, () => HttpResponse.json([])),
    http.get(`${API}/billing/metrics/first-month`, () => HttpResponse.json({ month: '2020-01' })),
    http.get(`${API}/billing/metrics/:panel`, ({ request, params }) => {
      requests.push(new URL(request.url));
      if (params.panel === 'collections-stock') return HttpResponse.json(stockMetric(request.url, options));
      return HttpResponse.json({ panel: params.panel, available: true, partial: false, months: monthsOf(request.url), series: [] });
    }),
  );
}

function CurrentPlace() {
  const location = useLocation();
  return <div data-testid="place">{`${location.pathname}${location.search}`}</div>;
}

function renderFinancial(role = 'director') {
  return renderWithProviders(
    <Routes>
      <Route path={`/${role}/financiero`} element={<FinancialDashboardPage />} />
      <Route path="*" element={<CurrentPlace />} />
    </Routes>,
    { route: `/${role}/financiero` },
  );
}

async function openStockTable() {
  const card = (await screen.findByText('Deuda vencida por antigüedad', { selector: 'h3' })).closest('[class*="p-5"]');
  await userEvent.click(await within(card).findByRole('button', { name: 'Ver como tabla' }));
  return card;
}

describe('collections stock panel', () => {
  it('shows the overdue debt of each age bucket with the total and the students in debt', async () => {
    install();
    renderFinancial();

    const card = await openStockTable();
    const table = within(card).getByRole('table');
    ['1 a 30 días', '31 a 60 días', '61 a 90 días', 'Más de 90 días', 'Deuda vencida total', 'Alumnos con deuda'].forEach((name) =>
      expect(within(table).getByRole('columnheader', { name })).toBeInTheDocument(),
    );
    const lastRow = within(table).getAllByRole('row').at(-1);
    expect(within(lastRow).getByText(/295\.000/)).toBeInTheDocument();
    expect(within(lastRow).getByText(/120\.000/)).toBeInTheDocument();
    expect(within(lastRow).getByText('4')).toBeInTheDocument();
  });

  it('shows months without a photo as no data and not as zero', async () => {
    install();
    renderFinancial();

    const card = await openStockTable();
    const rows = within(within(card).getByRole('table')).getAllByRole('row');
    expect(within(rows[1]).getAllByText('—').length).toBeGreaterThanOrEqual(6);
    expect(within(rows[1]).queryByText(/\$\s?0,00/)).not.toBeInTheDocument();
  });

  it('flags the series as partial only when the backend says so', async () => {
    install({ partial: true });
    const first = renderFinancial();
    expect(await screen.findByText('Último dato del mes')).toBeInTheDocument();
    first.unmount();

    install({ partial: false });
    renderFinancial();
    await screen.findByText('Deuda vencida por antigüedad', { selector: 'h3' });
    await waitFor(() => expect(document.querySelector('.recharts-area')).not.toBeNull());
    expect(screen.queryByText('Último dato del mes')).not.toBeInTheDocument();
  });

  it('draws the buckets as stacked areas and the students as a line', async () => {
    install();
    const { container } = renderFinancial();

    await screen.findByText('Deuda vencida por antigüedad', { selector: 'h3' });
    await waitFor(() => expect(container.querySelectorAll('.recharts-area').length).toBe(4));
    expect(container.querySelector('.recharts-line')).not.toBeNull();
  });

  it('asks for the stock with the chosen currency and program', async () => {
    const requests = [];
    install({ requests });
    server.use(http.get(`${API}/programs`, () => HttpResponse.json([{ id: 5, name: 'Tecnicatura en Diseño' }])), http.get(`${API}/courses`, () => HttpResponse.json([])));
    renderFinancial();
    await screen.findByText('Deuda vencida por antigüedad', { selector: 'h3' });

    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await userEvent.click(await screen.findByRole('combobox', { name: /Oferta/ }));
    await userEvent.click(await screen.findByRole('option', { name: 'Tecnicatura en Diseño' }));

    await waitFor(() =>
      expect(requests.some((url) => url.pathname.endsWith('/collections-stock') && url.searchParams.get('programId') === '5' && url.searchParams.get('currency') === 'ARS')).toBe(true),
    );
  });

  it('says when the stock is not available while the other panels keep loading', async () => {
    install({ available: false });
    renderFinancial();

    expect(await screen.findByText('Disponible cuando esté el stock de deuda', { selector: 'div.font-heading' })).toBeInTheDocument();
  });

  it('opens the collections page of the director from a month of the table', async () => {
    install();
    renderFinancial('director');

    const card = await openStockTable();
    const open = within(card).getAllByRole('button', { name: /^Ver en Cobranza:/ });
    await userEvent.click(open.at(-1));

    expect(await screen.findByTestId('place')).toHaveTextContent('/director/facturacion?tab=para-contactar');
  });

  it('opens the collections page of the administrative role under its own prefix', async () => {
    install();
    renderFinancial('administrativo');

    const card = await openStockTable();
    await userEvent.click(within(card).getAllByRole('button', { name: /^Ver en Cobranza:/ }).at(-1));

    expect(await screen.findByTestId('place')).toHaveTextContent('/administrativo/facturacion?tab=para-contactar');
  });
});
