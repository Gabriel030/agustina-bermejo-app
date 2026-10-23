import { cloneElement } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { FinancialDashboardPage } from './FinancialDashboardPage';
import { currentMonth, formatPointsDelta, rangeFor, shiftMonth } from './dashboardMetrics';

vi.mock('recharts', async (importOriginal) => {
  const original = await importOriginal();
  return { ...original, ResponsiveContainer: ({ children }) => cloneElement(children, { width: 640, height: 260 }) };
});

const API = 'http://localhost:8080';
const normalize = (text) => text.replace(/\s/g, ' ');

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

function filled(months, last, previous, fill = null) {
  return months.map((_, index) => (index === months.length - 1 ? last : index === months.length - 2 ? previous : fill));
}

const money = (amount, currency = 'ARS') => ({ amount, currency });

function onTimeMetric(url, { goal = true, last = 80, previous = 75, evaluation = 'BELOW' } = {}) {
  const months = monthsOf(url);
  return {
    panel: 'on-time-payment',
    available: true,
    partial: false,
    months,
    series: [
      { key: 'onTimePercent', unit: 'PERCENT', values: filled(months, last, previous) },
      { key: 'consideredInvoices', unit: 'COUNT', values: filled(months, 40, 38, 0) },
      { key: 'averageDelayDays', unit: 'DAYS', values: filled(months, 4.5, 3) },
      { key: 'collected', unit: 'MONEY', values: filled(months, money('500000.00'), money('450000.00'), money('0.00')) },
    ],
    ...(goal
      ? {
          goal: {
            indicator: 'ON_TIME_PAYMENT',
            comparator: 'AT_LEAST',
            thresholdPercent: 85,
            evaluation: months.map((_, index) => (index === months.length - 1 ? evaluation : 'NO_DATA')),
          },
        }
      : {}),
  };
}

function issuedMetric(url) {
  const months = monthsOf(url);
  return {
    panel: 'issued-invoices',
    available: true,
    partial: false,
    months,
    series: [
      { key: 'zeroTotalScholarship', unit: 'COUNT', values: filled(months, 1, 0, 0) },
      { key: 'count.MONTHLY_INSTALLMENT', unit: 'COUNT', values: filled(months, 38, 30, 0) },
      { key: 'count.ENROLLMENT_FEE', unit: 'COUNT', values: filled(months, 2, 1, 0) },
      { key: 'amount.MONTHLY_INSTALLMENT', unit: 'MONEY', values: filled(months, money('380000.00'), money('300000.00'), money('0.00')) },
      { key: 'amount.ENROLLMENT_FEE', unit: 'MONEY', values: filled(months, money('10000.00'), money('5000.00'), money('0.00')) },
    ],
  };
}

function lateFeesMetric(url, available = true) {
  const months = monthsOf(url);
  if (!available) return { panel: 'late-fees', available: false, partial: false, months, series: [] };
  return {
    panel: 'late-fees',
    available: true,
    partial: false,
    months,
    series: [
      { key: 'issuedCount', unit: 'COUNT', values: filled(months, 5, 7, 0) },
      { key: 'annulledCount', unit: 'COUNT', values: filled(months, 1, 0, 0) },
      { key: 'issuedAmount', unit: 'MONEY', values: filled(months, money('25000.00'), money('35000.00'), money('0.00')) },
    ],
  };
}

function emptyMetric(panel, url) {
  return { panel, available: true, partial: false, months: monthsOf(url), series: [] };
}

function install({ currencies = ['ARS'], onTime = {}, lateFeesAvailable = true, failing = [], requests = [], dashboardStatus = 200, debtorsStatus = 200, debtorRequests = [], discounts, firstMonth = '2020-01' } = {}) {
  server.use(
    http.get(`${API}/financial-dashboard`, () =>
      dashboardStatus !== 200 ? new HttpResponse(null, { status: dashboardStatus }) : HttpResponse.json({
        studentsInDebt: 12,
        studentsUpToDate: 140,
        totalsByCurrency: currencies.map((currency) => ({
          totalDebt: money('1743500.00', currency),
          monthlyProjected: money('1.00', currency),
          monthlyCollected: money('1.00', currency),
          monthlyDebt: money('1.00', currency),
        })),
      }),
    ),
    http.get(`${API}/financial-dashboard/debtors`, () => {
      debtorRequests.push(1);
      return debtorsStatus !== 200
        ? new HttpResponse(null, { status: debtorsStatus })
        : HttpResponse.json([{ studentId: 7, fullName: 'Ana Pérez', dni: '30111222', enrollmentName: 'Tecnicatura', totalDebt: money('150000.00'), debtAgeDays: 20 }]);
    }),
    http.get(`${API}/billing/metrics/first-month`, () => HttpResponse.json({ month: firstMonth })),
    http.get(`${API}/billing/metrics/:panel`, ({ request, params }) => {
      requests.push(new URL(request.url));
      if (failing.includes(params.panel)) return new HttpResponse(null, { status: 500 });
      if (params.panel === 'on-time-payment') return HttpResponse.json(onTimeMetric(request.url, onTime));
      if (params.panel === 'issued-invoices') return HttpResponse.json(issuedMetric(request.url));
      if (params.panel === 'discounts' && discounts) return HttpResponse.json(discounts(request.url));
      if (params.panel === 'late-fees') return HttpResponse.json(lateFeesMetric(request.url, lateFeesAvailable));
      return HttpResponse.json(emptyMetric(params.panel, request.url));
    }),
  );
}

async function openIssuingTab() {
  await userEvent.click(await screen.findByRole('tab', { name: 'Emisión' }));
}

describe('FinancialDashboardPage', () => {
  it('d2-04 marks the month as below the goal on the card, the chart badge and draws the goal line', async () => {
    install();
    const { container } = renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    const card = (await screen.findByText('Pago a término', { selector: 'div' })).closest('[class*="p-5"]');
    expect(await within(card).findByText('Por debajo de la meta')).toBeInTheDocument();
    expect(normalize(within(card).getByText(/80/).textContent)).toBe('80 %');
    await waitFor(() => expect(container.querySelector('.recharts-reference-line')).not.toBeNull());
    expect(container.textContent).toContain('Meta 85 %');
    expect(screen.getAllByText(/Por debajo de la meta/).length).toBeGreaterThanOrEqual(2);
  });

  it('d2-04 shows "Cumplida" when the month met the goal', async () => {
    install({ onTime: { last: 90, evaluation: 'MET' } });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    expect((await screen.findAllByText(/Cumplida/)).length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText(/Por debajo de la meta/)).not.toBeInTheDocument();
  });

  it('draws no goal line and no chip when the goal is not configured', async () => {
    install({ onTime: { goal: false } });
    const { container } = renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await screen.findByText('Pago a término', { selector: 'h3' });
    await waitFor(() => expect(container.querySelector('.recharts-line')).not.toBeNull());
    expect(container.querySelector('.recharts-reference-line')).toBeNull();
    expect(screen.queryByText(/Cumplida|Por debajo de la meta/)).not.toBeInTheDocument();
  });

  it('d2-07 shows the delta in percentage points against the previous month', async () => {
    install();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    expect(await screen.findByText('+5 pp')).toBeInTheDocument();
    expect(formatPointsDelta(5)).toBe('+5 pp');
    expect(formatPointsDelta(-2.5)).toBe('−2,5 pp');
    expect(formatPointsDelta(null)).toBeNull();
  });

  it('shows the invoices issued with the delta in invoices against the previous month', async () => {
    install();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    const card = (await screen.findByText('Facturas emitidas')).closest('[class*="p-5"]');
    expect(await within(card).findByText('40')).toBeInTheDocument();
    expect(within(card).getByText(/\+9 facturas/)).toBeInTheDocument();
  });

  it('d2-05 shows "Disponible cuando esté la mora" for an unavailable panel while the others load', async () => {
    install({ lateFeesAvailable: false });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await openIssuingTab();

    expect(await screen.findByText('Disponible cuando esté la mora', { selector: 'div.font-heading' })).toBeInTheDocument();
    expect(await screen.findByText('Emisión mensual', { selector: 'h3' })).toBeInTheDocument();
    expect(screen.queryByText('No se pudo cargar')).not.toBeInTheDocument();
  });

  it('keeps the other panels when one fails and retries only that one', async () => {
    const requests = [];
    install({ failing: ['discounts'], requests });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await openIssuingTab();
    expect(await screen.findByText('No se pudo cargar')).toBeInTheDocument();
    expect(await screen.findByText('Emisión mensual', { selector: 'h3' })).toBeInTheDocument();

    const before = requests.filter((url) => url.pathname.endsWith('/discounts')).length;
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(requests.filter((url) => url.pathname.endsWith('/discounts')).length).toBe(before + 1));
  });

  it('d2-03 reloads every panel with the other currency without leaving the page', async () => {
    const requests = [];
    install({ currencies: ['ARS', 'USD'], requests });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await screen.findByText('Pago a término', { selector: 'h3' });
    await waitFor(() => expect(requests.some((url) => url.searchParams.get('currency') === 'ARS')).toBe(true));
    expect(requests.every((url) => url.searchParams.get('currency') === 'ARS')).toBe(true);

    requests.length = 0;
    await userEvent.click(screen.getByRole('radio', { name: 'USD' }));

    await waitFor(() => expect(requests.length).toBeGreaterThanOrEqual(4));
    expect(requests.every((url) => url.searchParams.get('currency') === 'USD')).toBe(true);
    expect(new Set(requests.map((url) => url.pathname.split('/').pop()))).toContain('on-time-payment');
  });

  async function pickOffer(name) {
    await userEvent.click(await screen.findByRole('combobox', { name: /Oferta/ }));
    await userEvent.click(await screen.findByRole('option', { name }));
  }

  function installOffers() {
    server.use(
      http.get(`${API}/programs`, () => HttpResponse.json([{ id: 5, name: 'Tecnicatura en Diseño' }])),
      http.get(`${API}/courses`, () => HttpResponse.json([{ id: 9, name: 'Curso de Fotografía' }])),
    );
  }

  it('d2-03 reloads every panel with the chosen program and replaces it when a course is chosen', async () => {
    const requests = [];
    install({ requests });
    installOffers();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    expect(requests.every((url) => !url.searchParams.has('programId') && !url.searchParams.has('courseId'))).toBe(true);
    expect(screen.getByText('Todas las ofertas', { selector: 'p' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    requests.length = 0;
    await pickOffer('Tecnicatura en Diseño');

    await waitFor(() => expect(requests.length).toBeGreaterThanOrEqual(4));
    expect(requests.every((url) => url.searchParams.get('programId') === '5' && !url.searchParams.has('courseId'))).toBe(true);
    expect(new Set(requests.map((url) => url.pathname.split('/').pop()))).toContain('on-time-payment');

    requests.length = 0;
    await pickOffer('Curso de Fotografía');

    await waitFor(() => expect(requests.length).toBeGreaterThanOrEqual(4));
    expect(requests.every((url) => url.searchParams.get('courseId') === '9' && !url.searchParams.has('programId'))).toBe(true);
    expect(screen.getByRole('combobox', { name: /Oferta/ })).toHaveTextContent('Curso de Fotografía');
    expect(screen.getByText('Curso: Curso de Fotografía')).toBeInTheDocument();
  });

  it('replaces the course with the program when the program is chosen after it', async () => {
    const requests = [];
    install({ requests });
    installOffers();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });

    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await pickOffer('Curso de Fotografía');
    await waitFor(() => expect(requests.some((url) => url.searchParams.get('courseId') === '9')).toBe(true));
    requests.length = 0;
    await pickOffer('Tecnicatura en Diseño');

    await waitFor(() => expect(requests.length).toBeGreaterThanOrEqual(4));
    expect(requests.every((url) => url.searchParams.get('programId') === '5' && !url.searchParams.has('courseId'))).toBe(true);
  });

  it('shows the active offer on a chip that removes the scope and shows it in the subtitle', async () => {
    const requests = [];
    install({ requests });
    installOffers();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });

    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await pickOffer('Tecnicatura en Diseño');
    expect(await screen.findByText('Carrera: Tecnicatura en Diseño')).toBeInTheDocument();
    expect(screen.getByText('Tecnicatura en Diseño', { selector: 'p' })).toBeInTheDocument();

    requests.length = 0;
    await userEvent.click(screen.getByRole('button', { name: 'Quitar filtro de oferta' }));

    await waitFor(() => expect(requests.length).toBeGreaterThanOrEqual(4));
    expect(requests.every((url) => !url.searchParams.has('programId'))).toBe(true);
    expect(screen.queryByText('Carrera: Tecnicatura en Diseño')).not.toBeInTheDocument();
    expect(screen.getByText('Todas las ofertas', { selector: 'p' })).toBeInTheDocument();
  });

  it('removes the offer with "Quitar filtros"', async () => {
    const requests = [];
    install({ requests });
    installOffers();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });

    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await pickOffer('Tecnicatura en Diseño');
    await waitFor(() => expect(requests.some((url) => url.searchParams.get('programId') === '5')).toBe(true));

    requests.length = 0;
    await userEvent.click(screen.getByRole('button', { name: 'Quitar filtros' }));

    await waitFor(() => expect(requests.length).toBeGreaterThanOrEqual(4));
    expect(requests.every((url) => !url.searchParams.has('programId'))).toBe(true);
  });

  it('tells there are no offers loaded when both lists are empty', async () => {
    install();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });

    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await userEvent.click(await screen.findByRole('combobox', { name: /Oferta/ }));

    expect(await screen.findByText('No hay carreras ni cursos cargados')).toBeInTheDocument();
  });

  it('searches the offers by name ignoring case and accents, keeping the fixed option and the groups', async () => {
    install();
    installOffers();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await userEvent.click(await screen.findByRole('combobox', { name: /Oferta/ }));
    const search = await screen.findByPlaceholderText('Buscar carrera o curso…');

    await userEvent.type(search, 'FOTOGRAFIA');

    expect(screen.getByRole('option', { name: /Curso de Fotografía/ })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Tecnicatura en Diseño/ })).not.toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Todas las ofertas/ })).toBeInTheDocument();
    expect(screen.getByText('Cursos')).toBeVisible();
    expect(screen.getByText('Carreras')).not.toBeVisible();

    await userEvent.clear(search);
    await userEvent.type(search, 'diseno');

    expect(screen.getByRole('option', { name: /Tecnicatura en Diseño/ })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Curso de Fotografía/ })).not.toBeInTheDocument();
  });

  it('tells there are no results when the search matches no offer', async () => {
    install();
    installOffers();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await userEvent.click(await screen.findByRole('combobox', { name: /Oferta/ }));

    await userEvent.type(await screen.findByPlaceholderText('Buscar carrera o curso…'), 'zzz');

    expect(screen.getByText('Sin resultados')).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Curso de Fotografía/ })).not.toBeInTheDocument();
  });

  it('picks the searched offer with the keyboard and keeps the scope state', async () => {
    const requests = [];
    install({ requests });
    installOffers();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await userEvent.click(await screen.findByRole('combobox', { name: /Oferta/ }));
    await userEvent.type(await screen.findByPlaceholderText('Buscar carrera o curso…'), 'tecni');
    requests.length = 0;

    await userEvent.keyboard('{Enter}');

    await waitFor(() => expect(requests.length).toBeGreaterThanOrEqual(4));
    expect(requests.every((url) => url.searchParams.get('programId') === '5' && !url.searchParams.has('courseId'))).toBe(true);
    expect(screen.getByRole('combobox', { name: /Oferta/ })).toHaveTextContent('Tecnicatura en Diseño');
    expect(screen.getByText('Carrera: Tecnicatura en Diseño')).toBeInTheDocument();
  });

  it('closes the offer search with Escape without changing the offer', async () => {
    install();
    installOffers();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await userEvent.click(await screen.findByRole('combobox', { name: /Oferta/ }));
    await screen.findByPlaceholderText('Buscar carrera o curso…');

    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByPlaceholderText('Buscar carrera o curso…')).not.toBeInTheDocument());
    expect(screen.getByRole('combobox', { name: /Oferta/ })).toHaveTextContent('Todas las ofertas');
  });

  it('extends the chart range to include a month picked before it and does not extend it for a month inside', async () => {
    const requests = [];
    install({ requests });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    const today = currentMonth();
    const year = Number(today.slice(0, 4));
    const picked = `${year - 2}-01`;
    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    expect(screen.queryByText(/Rango ampliado/)).not.toBeInTheDocument();

    requests.length = 0;
    await userEvent.click(screen.getByRole('button', { name: /^Mes, / }));
    await userEvent.click(screen.getByRole('button', { name: `Elegir año, ${year}` }));
    await userEvent.click(screen.getByRole('button', { name: String(year - 2) }));
    await userEvent.click(screen.getByRole('button', { name: new RegExp(`^enero ${year - 2}$`) }));

    await waitFor(() => expect(requests.some((url) => url.searchParams.get('from') === shiftMonth(picked, -1))).toBe(true));
    expect(requests.every((url) => url.searchParams.get('to') === today)).toBe(true);
    expect(screen.getByText(`Rango ampliado para incluir enero de ${year - 2}`)).toBeInTheDocument();
  });

  it('asks one month earlier, without the notice, when the picked month is exactly the start of the range', async () => {
    const requests = [];
    install({ requests });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    const { from } = rangeFor('LAST_12');
    const [fromYear, fromMonth] = from.split('-').map(Number);
    const monthName = new Intl.DateTimeFormat('es-AR', { month: 'long', timeZone: 'UTC' }).format(new Date(Date.UTC(2000, fromMonth - 1, 1)));
    await waitFor(() => expect(requests.length).toBeGreaterThan(0));

    requests.length = 0;
    await userEvent.click(screen.getByRole('button', { name: /^Mes, / }));
    if (fromYear !== Number(currentMonth().slice(0, 4))) await userEvent.click(screen.getByRole('button', { name: 'Año anterior' }));
    await userEvent.click(screen.getByRole('button', { name: `${monthName} ${fromYear}` }));

    await waitFor(() => expect(requests.some((url) => url.searchParams.get('from') === shiftMonth(from, -1))).toBe(true));
    expect(screen.queryByText(/Rango ampliado/)).not.toBeInTheDocument();
  });

  it('does not offer months before the first billed month', async () => {
    install({ firstMonth: '2025-06' });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    await waitFor(() => expect(screen.getByRole('button', { name: /^Mes, / })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: /^Mes, / }));
    const year = Number(currentMonth().slice(0, 4));
    await userEvent.click(screen.getByRole('button', { name: `Elegir año, ${year}` }));

    expect(await screen.findByRole('button', { name: '2024, sin datos de este año' })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('button', { name: '2025' })).not.toHaveAttribute('aria-disabled');
  });

  it('disables the month picker with a reason when nothing was ever billed', async () => {
    install({ firstMonth: null });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });

    const picker = await screen.findByRole('button', { name: /^Mes, / });
    await waitFor(() => expect(picker).toHaveAttribute('aria-disabled', 'true'));
    await userEvent.hover(picker);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Todavía no hay meses con datos');
  });

  it('labels the cards of today as independent from the offer filter', async () => {
    install();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    expect(await screen.findByRole('heading', { name: 'Hoy · todas las ofertas' })).toBeInTheDocument();
  });

  it('shows no currency toggle when only one currency is in use', async () => {
    install();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await screen.findByText('Pago a término', { selector: 'h3' });
    expect(screen.queryByRole('radio', { name: 'USD' })).not.toBeInTheDocument();
  });

  it('asks for the selected range and filters the invoice type', async () => {
    const requests = [];
    install({ requests });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await screen.findByText('Pago a término', { selector: 'h3' });
    const expected = rangeFor('LAST_12');
    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    expect(requests[0].searchParams.get('from')).toBe(expected.from);
    expect(requests[0].searchParams.get('to')).toBe(expected.to);
    expect(requests[0].searchParams.get('invoiceType')).toBeNull();
  });

  it('shows the amounts of the day for the chosen currency and the director link only for the director', async () => {
    install();
    const { unmount } = renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    expect(await screen.findByText(/1\.743\.500,00/)).toBeInTheDocument();
    expect(screen.getByText(/de 152/)).toBeInTheDocument();
    expect(screen.getByText('Alumnos al día')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Configurar el cobro/ })).toBeInTheDocument();
    unmount();

    renderWithProviders(<FinancialDashboardPage />, { route: '/administrativo/financiero' });
    await screen.findByText('Pago a término', { selector: 'h3' });
    expect(screen.queryByRole('link', { name: /Configurar el cobro/ })).not.toBeInTheDocument();
  });

  it('does not ask the administrative role for the debtors list (director only) nor draw the section', async () => {
    const debtorRequests = [];
    install({ debtorRequests });
    renderWithProviders(<FinancialDashboardPage />, { route: '/administrativo/financiero' });

    await screen.findByText('Pago a término', { selector: 'h3' });
    expect(await screen.findByText('Alumnos al día')).toBeInTheDocument();
    expect(debtorRequests).toHaveLength(0);
    expect(screen.queryByLabelText('Ordenar por')).not.toBeInTheDocument();
    expect(screen.queryByText('Sin alumnos con deuda vencida')).not.toBeInTheDocument();
  });

  it('asks the director for the debtors list and offers each student as a focusable button', async () => {
    const debtorRequests = [];
    install({ debtorRequests });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    const open = await screen.findByRole('button', { name: 'Ana Pérez' });
    expect(debtorRequests.length).toBeGreaterThan(0);
    open.focus();
    expect(open).toHaveFocus();
  });

  it.each([403, 500])('shows an error with retry, never "no data", when the debtors list answers %i', async (status) => {
    install({ debtorsStatus: status });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    const sort = await screen.findByLabelText('Ordenar por');
    const section = sort.parentElement.parentElement;
    expect(await within(section).findByText('No se pudo cargar')).toBeInTheDocument();
    expect(within(section).getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('Sin alumnos con deuda vencida')).not.toBeInTheDocument();
  });

  it('shows an error with retry instead of zeros when the dashboard of the day fails', async () => {
    install({ dashboardStatus: 500 });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    const card = (await screen.findByText('Alumnos con deuda vencida', { selector: 'div' })).closest('[class*="p-5"]');
    expect(await within(card).findByText('No se pudo cargar')).toBeInTheDocument();
    expect(within(card).queryByText(/^de \d+/)).not.toBeInTheDocument();
    expect(within(card).queryByText('0')).not.toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('shows an error with retry on the month card whose metric failed', async () => {
    install({ failing: ['on-time-payment'] });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    const card = (await screen.findByText('Pago a término', { selector: 'div' })).closest('[class*="p-5"]');
    expect(await within(card).findByText('No se pudo cargar')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('shows the empty state, and no axes, when the range has no values', async () => {
    install();
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await userEvent.click(await screen.findByRole('tab', { name: 'Emisión' }));
    const card = (await screen.findByText('Vencidas detectadas', { selector: 'h3' })).closest('[class*="p-5"]');
    expect(await within(card).findByText('Sin datos para mostrar')).toBeInTheDocument();
    expect(card.querySelector('.recharts-yAxis')).toBeNull();
  });

  it('shows the empty state when every value of the panel is null', async () => {
    install({
      discounts: (url) => {
        const months = monthsOf(url);
        return { panel: 'discounts', available: true, partial: false, months, series: [{ key: 'percentOfGross', unit: 'PERCENT', values: months.map(() => null) }] };
      },
    });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await userEvent.click(await screen.findByRole('tab', { name: 'Emisión' }));
    const card = (await screen.findByText('Becas y descuentos', { selector: 'h3' })).closest('[class*="p-5"]');
    expect(await within(card).findByText('Sin datos para mostrar')).toBeInTheDocument();
  });

  it('draws a discounts panel that only has the percentage series on a single axis', async () => {
    install({
      discounts: (url) => {
        const months = monthsOf(url);
        return { panel: 'discounts', available: true, partial: false, months, series: [{ key: 'percentOfGross', unit: 'PERCENT', values: months.map(() => 8) }] };
      },
    });
    renderWithProviders(<FinancialDashboardPage />, { route: '/director/financiero' });

    await userEvent.click(await screen.findByRole('tab', { name: 'Emisión' }));
    const card = (await screen.findByText('Becas y descuentos', { selector: 'h3' })).closest('[class*="p-5"]');
    await waitFor(() => expect(card.querySelectorAll('.recharts-yAxis').length).toBe(1));
    expect(card.querySelector('.recharts-line')).not.toBeNull();
  });
});
